package de.jbecker.tractivewatch.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TractiveApiError(val code: Int, val apiMessage: String) :
    Exception("Tractive API Fehler $code: $apiMessage")

class NoCredentialsError : Exception("Keine Zugangsdaten gespeichert")

class ReauthRequiredError : Exception("Anmeldung abgelaufen – bitte erneut anmelden")

class TractiveClient(context: Context) {

    private val store = CredentialsStore(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    // EXPLIZITER Kotlin-Adapter: ohne ihn kann Moshi unsere Data-Classes
    // nicht serialisieren ("Cannot serialize Kotlin type ..." — Login-Bug v1.9)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val authMutex = Mutex()

    @Volatile private var userId: String? = null
    @Volatile private var accessToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L

    private val baseUrl = "https://graph.tractive.com/4/"
    private val channelUrl = "https://channel.tractive.com/3/channel"
    private val jsonMedia = "application/json;charset=UTF-8".toMediaType()

    private val baseHeaders = mapOf(
        "x-tractive-client" to "625e533dc3c3b41c28a669f0",
        "content-type" to "application/json;charset=UTF-8",
        "accept" to "application/json, text/plain, */*",
    )

    init {
        // Gespeicherte Session wiederherstellen (übersteht App-Neustart)
        val storedToken = store.accessToken
        val storedUser = store.userId
        val storedExp = store.tokenExpiresAt ?: 0L
        if (storedToken != null && storedUser != null &&
            System.currentTimeMillis() < storedExp - 120_000L
        ) {
            userId = storedUser
            accessToken = storedToken
            tokenExpiresAt = storedExp
        }
    }

    private fun hdrs(extra: Map<String, String> = emptyMap()): Headers {
        val b = Headers.Builder()
        (baseHeaders + extra).forEach { (k, v) -> b.add(k, v) }
        return b.build()
    }

    val hasStoredCredentials: Boolean
        get() = store.provider != null

    /** true, wenn der Google-Login abgelaufen ist und neu gestartet werden muss */
    val needsReauth: Boolean
        get() = store.provider == "google" &&
            (accessToken == null || System.currentTimeMillis() >= tokenExpiresAt - 120_000L)

    // ---------- Auth ----------

    suspend fun login(email: String, password: String) {
        Log.i("TractiveWatch", "login: POST /4/auth/token (E-Mail-Länge ${email.length}, Passwort-Länge ${password.length})")
        val body = moshi.adapter(LoginRequest::class.java)
            .toJson(LoginRequest(email, password))
            .toRequestBody(jsonMedia)
        val resp = execute(
            Request.Builder()
                .url(baseUrl + "auth/token")
                .headers(hdrs())
                .post(body)
                .build()
        )
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            Log.i("TractiveWatch", "login: HTTP ${r.code} von Tractive: ${text.take(200)}")
            if (!r.isSuccessful) throw TractiveApiError(r.code, cleanMessage(text))
            val tok = moshi.adapter(AuthToken::class.java).fromJson(text)
                ?: throw TractiveApiError(r.code, "Unerwartete Antwort")
            if (tok.accessToken.isNullOrBlank()) throw TractiveApiError(r.code, "Kein Token erhalten")
            userId = tok.userId
            accessToken = tok.accessToken
            tokenExpiresAt = expiresAtMillis(tok)
            store.save(email, password)
            store.setSession(userId, tok.accessToken, tokenExpiresAt)
        }
    }

    /** Google-Login: Auth-Code aus Google Sign-In gegen Tractive-Token tauschen */
    suspend fun loginGoogle(authCode: String) {
        Log.i("TractiveWatch", "loginGoogle: Auth-Code wird an Tractive gesendet (Länge ${authCode.length})")
        val body = moshi.adapter(GoogleLoginRequest::class.java)
            .toJson(GoogleLoginRequest(authCode))
            .toRequestBody(jsonMedia)
        val resp = execute(
            Request.Builder()
                .url(baseUrl + "auth/token/google")
                .headers(hdrs(mapOf("x-tractive-app" to "site.tractivegps")))
                .post(body)
                .build()
        )
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            Log.i("TractiveWatch", "loginGoogle: HTTP ${r.code} von Tractive: ${text.take(200)}")
            if (!r.isSuccessful) throw TractiveApiError(r.code, cleanMessage(text))
            val tok = moshi.adapter(AuthToken::class.java).fromJson(text)
                ?: throw TractiveApiError(r.code, "Unerwartete Antwort")
            if (tok.accessToken.isNullOrBlank()) throw TractiveApiError(r.code, "Kein Token erhalten")
            userId = tok.userId
            accessToken = tok.accessToken
            tokenExpiresAt = expiresAtMillis(tok)
            store.markGoogle()
            store.setSession(userId, tok.accessToken, tokenExpiresAt)
        }
    }

    private fun expiresAtMillis(tok: AuthToken): Long =
        if (tok.expiresAt != null) tok.expiresAt * 1000L
        else System.currentTimeMillis() + 24 * 3_600_000L

    suspend fun ensureAuth() {
        authMutex.withLock {
            if (userId != null && accessToken != null &&
                System.currentTimeMillis() < tokenExpiresAt - 120_000L
            ) {
                return
            }
            val email = store.email
            val password = store.password
            if (store.provider == "email" && !email.isNullOrBlank() && !password.isNullOrBlank()) {
                login(email, password)
            } else {
                userId = null
                accessToken = null
                tokenExpiresAt = 0L
                throw ReauthRequiredError()
            }
        }
    }

    private suspend fun execute(req: Request): okhttp3.Response = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }
    }

    private val authExtra: Map<String, String>
        get() = mapOf(
            "x-tractive-user" to (userId ?: ""),
            "authorization" to "Bearer ${accessToken.orEmpty()}",
        )

    private suspend fun apiGet(path: String, retry: Boolean = true): String {
        ensureAuth()
        val resp = execute(Request.Builder().url(baseUrl + path).headers(hdrs(authExtra)).build())
        val text: String
        resp.use { r -> text = r.body?.string().orEmpty() }
        if (!resp.isSuccessful) {
            if (retry && (resp.code == 401 || resp.code == 403) && !path.startsWith("auth/")) {
                userId = null
                accessToken = null
                return apiGet(path, retry = false)
            }
            throw TractiveApiError(resp.code, cleanMessage(text))
        }
        return text
    }

    private fun cleanMessage(body: String): String =
        moshi.adapter(ApiErrorBody::class.java).fromJson(body)?.message
            ?.takeIf { it.isNotBlank() }
            ?: body.take(200)

    // ---------- Daten ----------

    suspend fun trackableObjects(): List<TrackableObjectSummary> {
        val body = apiGet("user/${userId}/trackable_objects")
        val type = Types.newParameterizedType(List::class.java, TrackableObjectSummary::class.java)
        return moshi.adapter<List<TrackableObjectSummary>>(type).fromJson(body) ?: emptyList()
    }

    suspend fun trackableObject(id: String): TrackableObject? =
        moshi.adapter(TrackableObject::class.java).fromJson(apiGet("trackable_object/$id"))

    suspend fun hwReport(trackerId: String): HwReport =
        moshi.adapter(HwReport::class.java).fromJson(apiGet("device_hw_report/$trackerId/"))
            ?: throw TractiveApiError(-1, "Unerwartete Antwort")

    suspend fun posReport(trackerId: String): PosReport =
        moshi.adapter(PosReport::class.java).fromJson(apiGet("device_pos_report/$trackerId"))
            ?: throw TractiveApiError(-1, "Unerwartete Antwort")

    // ---------- Befehle ----------

    private suspend fun apiPost(path: String, retry: Boolean = true): String {
        ensureAuth()
        val empty = ByteArray(0).toRequestBody(null)
        val resp = execute(
            Request.Builder()
                .url(baseUrl + path)
                .headers(hdrs(authExtra))
                .post(empty)
                .build()
        )
        val text: String
        resp.use { r -> text = r.body?.string().orEmpty() }
        if (!resp.isSuccessful) {
            if (retry && (resp.code == 401 || resp.code == 403)) {
                userId = null
                accessToken = null
                return apiPost(path, retry = false)
            }
            throw TractiveApiError(resp.code, cleanMessage(text))
        }
        return text
    }

    suspend fun command(trackerId: String, kind: String, on: Boolean) {
        apiPost("tracker/$trackerId/command/$kind/" + (if (on) "on" else "off"))
    }

    fun logout() {
        userId = null
        accessToken = null
        tokenExpiresAt = 0L
        store.clear()
    }

    // ---------- Echtzeit-Channel (Long-Poll, NDJSON) ----------

    fun startChannel(
        scope: CoroutineScope,
        onEvent: (ChannelEvent) -> Unit,
        onStatus: (String?) -> Unit,
    ): Job = scope.launch(Dispatchers.IO) {
        var backoff = 5_000L
        while (isActive) {
            try {
                ensureAuth()
                val req = Request.Builder()
                    .url(channelUrl)
                    .headers(hdrs(authExtra))
                    .post(ByteArray(0).toRequestBody(null))
                    .build()
                val resp = http.newCall(req).execute()
                resp.use { r ->
                    if (!r.isSuccessful) {
                        if (r.code == 401 || r.code == 403) {
                            userId = null
                            accessToken = null
                        }
                        throw TractiveApiError(r.code, "Channel: ${r.body?.string()?.take(120).orEmpty()}")
                    }
                    onStatus("live")
                    backoff = 5_000L
                    r.body?.byteStream()?.bufferedReader()?.let { reader ->
                        for (line in reader.lineSequence()) {
                            if (!isActive) break
                            if (line.isBlank()) continue
                            val ev = try {
                                moshi.adapter(ChannelEvent::class.java).fromJson(line)
                            } catch (e: Exception) {
                                null
                            } ?: continue
                            if (ev.message == "keep-alive" || ev.message == "handshake") continue
                            onEvent(ev)
                        }
                    }
                }
                // Stream wurde vom Server geschlossen -> neu verbinden
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStatus("error")
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }
}
