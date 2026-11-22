package de.jbecker.tractivewatch.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke-Test für den Login-Bug v1.9:
 * "Cannot serialize Kotlin type de.jbecker.tractivewatch.data.LoginRequest"
 * Ursache: Moshi ohne Kotlin-Adapter (moshi-kotlin fehlte in den Abhängigkeiten).
 * Diese Tests laufen auf der JVM und verifizieren exakt die Code-Pfade,
 * die auf der Watch fehlgeschlagen sind (ohne Emulator).
 */
class MoshiSmokeTest {

    // EXAKT derselbe Moshi-Setup wie TractiveClient
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun serializesLoginRequest() {
        val body = moshi.adapter(LoginRequest::class.java)
            .toJson(LoginRequest("a@b.example", "pw"))
        assertTrue("expected platform_email in: $body",
            body.contains("\"platform_email\":\"a@b.example\""))
        assertTrue("expected platform_token in: $body",
            body.contains("\"platform_token\":\"pw\""))
        assertTrue("expected grant_type in: $body",
            body.contains("\"grant_type\":\"tractive\""))
    }

    @Test
    fun serializesGoogleLoginRequest() {
        val body = moshi.adapter(GoogleLoginRequest::class.java)
            .toJson(GoogleLoginRequest("auth-code-123"))
        assertTrue(body.contains("\"auth_code\":\"auth-code-123\""))
        assertTrue(body.contains("\"all_terms_accepted\":true"))
    }

    @Test
    fun deserializesAuthToken() {
        val tok = moshi.adapter(AuthToken::class.java)
            .fromJson("{\"user_id\":\"u1\",\"access_token\":\"tok123\",\"expires_at\":1700000000}")
        assertNotNull("AuthToken must parse", tok)
        assertEquals("u1", tok?.userId)
        assertEquals("tok123", tok?.accessToken)
        assertEquals(1700000000L, tok?.expiresAt)
    }

    @Test
    fun deserializesPosReport() {
        val p = moshi.adapter(PosReport::class.java)
            .fromJson(
                """{"latlong":[52.52,13.405],"pos_uncertainty":10.5,"sensor_used":"gps","time":"2026-11-22T12:00:00Z"}""",
            )
        assertNotNull(p)
        assertEquals(2, p?.latlong?.size)
        val pos = p?.toPosition()
        assertNotNull("toPosition() muss Position liefern", pos)
        assertEquals(13.405, pos!!.lon, 0.0001)
        assertEquals(52.52, pos.lat, 0.0001)
    }

    @Test
    fun deserializesTrackableObjectList() {
        val type = com.squareup.moshi.Types.newParameterizedType(
            List::class.java, TrackableObjectSummary::class.java,
        )
        val list = moshi.adapter<List<TrackableObjectSummary>>(type)
            .fromJson("""[{"_id":"abc"},{"_id":"def"}]""")
        assertEquals(2, list?.size)
        assertEquals("abc", list?.first()?.id)
    }

    @Test
    fun deserializesChannelEvent() {
        val ev = moshi.adapter(ChannelEvent::class.java)
            .fromJson(
                """{"message":"position","tracker_id":"t1","position":{"latlong":[1.0,2.0],"pos_uncertainty":5.0}}""",
            )
        assertEquals("position", ev?.message)
        assertEquals("t1", ev?.trackerId)
        assertNotNull(ev?.position?.latlong)
    }
}
