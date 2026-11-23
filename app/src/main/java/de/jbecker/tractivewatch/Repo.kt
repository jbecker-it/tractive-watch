package de.jbecker.tractivewatch

import android.content.Context
import de.jbecker.tractivewatch.data.ChannelEvent
import de.jbecker.tractivewatch.data.ChannelSwitch
import de.jbecker.tractivewatch.data.Position
import de.jbecker.tractivewatch.data.TractiveClient
import de.jbecker.tractivewatch.data.TrackerUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Prozessweiter Singleton ([Repo.get]): überlebt Activity-Neuerstellung, damit
 * nicht pro Activity-Instanz ein zweiter Channel-Longpoll gestartet wird.
 */
class Repo private constructor(context: Context) {

    companion object {
        @Volatile private var instance: Repo? = null

        fun get(context: Context): Repo =
            instance ?: synchronized(this) {
                instance ?: Repo(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    val client = TractiveClient(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var channelJob: Job? = null

    suspend fun doLogin(email: String, password: String) {
        de.jbecker.tractivewatch.data.AppLog.record(appContext, "Login E-Mail: gestartet (${email.length > 0} E-Mail)")
        val r = runCatching {
            client.login(email.trim(), password)
            loadPets()
        }
        de.jbecker.tractivewatch.data.AppLog.record(
            appContext,
            if (r.isSuccess) "Login E-Mail: ERFOLGREICH"
            else "Login E-Mail: FEHLGESCHLAGEN ${r.exceptionOrNull()!!.javaClass.simpleName}: ${r.exceptionOrNull()!!.message}"
        )
        r.getOrThrow()
    }

    suspend fun doLoginGoogle(authCode: String) {
        de.jbecker.tractivewatch.data.AppLog.record(appContext, "Login Google: Auth-Code an Tractive-API senden")
        val r = runCatching {
            client.loginGoogle(authCode)
            loadPets()
        }
        de.jbecker.tractivewatch.data.AppLog.record(
            appContext,
            if (r.isSuccess) "Login Google: ERFOLGREICH (Haustiere geladen)"
            else "Login Google: FEHLGESCHLAGEN ${r.exceptionOrNull()!!.javaClass.simpleName}: ${r.exceptionOrNull()!!.message}"
        )
        r.getOrThrow()
    }

    /** true, wenn beim Start direkt zur Home-Ansicht gesprungen werden kann. */
    val canRestoreSession: Boolean
        get() = client.hasStoredCredentials && !client.needsReauth

    suspend fun loadPets() {
        TrackerUi.loading.value = true
        TrackerUi.loadError.value = null
        try {
            val summaries = client.trackableObjects()
            val fresh = mutableListOf<TrackerUi.Pet>()
            for (s in summaries) {
                val petId = s.id ?: continue
                val details = runCatching { client.trackableObject(petId) }.getOrNull()
                val trackerId = details?.deviceId
                if (trackerId.isNullOrBlank()) continue
                if (fresh.any { it.trackerId == trackerId }) continue
                val name = details.details?.name?.takeIf { it.isNotBlank() } ?: "Haustier"
                fresh += TrackerUi.Pet(petId = petId, name = name, trackerId = trackerId)
                runCatching {
                    val hw = client.hwReport(trackerId)
                    if (hw.batteryLevel != null) TrackerUi.battery[trackerId] = hw.batteryLevel
                    TrackerUi.charging[trackerId] = hw.chargingState.equals("CHARGING", ignoreCase = true)
                }
                runCatching { refreshPosition(trackerId) }
                delay(300) // Tractive drosselt schnelle Folgeanfragen (429)
            }
            // Liste erst am Ende ersetzen: kein kurzzeitig leerer Zustand,
            // offene pet/{index}-Screens zeigen nicht plötzlich "Kein Tracker".
            if (TrackerUi.pets.toList() != fresh) {
                TrackerUi.pets.clear()
                TrackerUi.pets.addAll(fresh)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TrackerUi.loadError.value = e.friendlyMessage()
            de.jbecker.tractivewatch.data.AppLog.record(
                appContext, "loadPets: FEHLER ${e.javaClass.simpleName}: ${e.message?.take(160)}",
            )
        } finally {
            TrackerUi.loading.value = false
        }
    }

    /** Holt die letzte Position vom Server und übernimmt sie in den UI-State. */
    suspend fun refreshPosition(trackerId: String) {
        client.posReport(trackerId).toPosition()?.let { TrackerUi.position[trackerId] = it }
    }

    fun startChannel() {
        if (channelJob?.isActive == true) return
        channelJob = client.startChannel(
            scope = scope,
            onEvent = { ev -> applyEvent(ev) },
            onStatus = { TrackerUi.channelStatus.value = it },
        )
    }

    fun applyEvent(ev: ChannelEvent) {
        val id = ev.trackerId ?: return
        ev.position?.let { p ->
            val ll = p.latlong
            if (ll != null && ll.size >= 2) {
                TrackerUi.position[id] = Position(
                    lat = ll[0],
                    lon = ll[1],
                    accuracy = p.posUncertainty ?: p.accuracy,
                    sensor = p.sensorUsed,
                    time = p.time,
                )
            }
        }
        ev.hardware?.let { hw ->
            if (hw.batteryLevel != null) TrackerUi.battery[id] = hw.batteryLevel
        }
        applySwitch(id, ev.ledControl, TrackerUi.led)
        applySwitch(id, ev.buzzerControl, TrackerUi.buzzer)
        applySwitch(id, ev.liveTracking, TrackerUi.live)
    }

    private fun applySwitch(id: String, s: ChannelSwitch?, map: MutableMap<String, Boolean>) {
        if (s == null) return
        if (s.pending == true) return
        if (s.remaining == 0L) {
            map[id] = false
            return
        }
        s.active?.let { map[id] = it }
    }

    /**
     * Schaltet LED/Ton/Live um. Der UI-State wird NUR bei Erfolg geändert;
     * ein Fehler wird geworfen (früher: still verschluckt, UI zeigte trotzdem "an").
     */
    suspend fun toggle(trackerId: String, kind: String) {
        val target = !switchState(trackerId, kind)
        client.command(
            trackerId,
            when (kind) {
                "led" -> "led_control"
                "buzzer" -> "buzzer_control"
                else -> "live_tracking"
            },
            target,
        )
        setSwitchState(trackerId, kind, target)
    }

    private fun switchState(id: String, kind: String): Boolean = when (kind) {
        "led" -> TrackerUi.led[id] ?: false
        "buzzer" -> TrackerUi.buzzer[id] ?: false
        else -> TrackerUi.live[id] ?: false
    }

    private fun setSwitchState(id: String, kind: String, v: Boolean) {
        when (kind) {
            "led" -> TrackerUi.led[id] = v
            "buzzer" -> TrackerUi.buzzer[id] = v
            else -> TrackerUi.live[id] = v
        }
    }

    fun logout() {
        channelJob?.cancel()
        channelJob = null
        client.logout()
        TrackerUi.reset()
    }
}
