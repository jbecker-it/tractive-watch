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

class Repo(context: Context) {

    val client = TractiveClient(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var channelJob: Job? = null

    suspend fun doLogin(email: String, password: String) {
        client.login(email.trim(), password)
        loadPets()
    }

    suspend fun doLoginGoogle(authCode: String) {
        client.loginGoogle(authCode)
        loadPets()
    }

    suspend fun loadPets() {
        TrackerUi.loading.value = true
        runCatching {
            val summaries = client.trackableObjects()
            TrackerUi.pets.clear()
            for (s in summaries) {
                val petId = s.id ?: continue
                val details = runCatching { client.trackableObject(petId) }.getOrNull()
                val trackerId = details?.deviceId
                if (trackerId.isNullOrBlank()) continue
                if (TrackerUi.pets.any { it.trackerId == trackerId }) continue
                val name = details?.details?.name?.takeIf { it.isNotBlank() } ?: "Haustier"
                TrackerUi.pets += TrackerUi.Pet(petId = petId, name = name, trackerId = trackerId)
                runCatching {
                    val hw = client.hwReport(trackerId)
                    if (hw.batteryLevel != null) TrackerUi.battery[trackerId] = hw.batteryLevel
                    TrackerUi.charging[trackerId] = hw.chargingState.equals("CHARGING", ignoreCase = true)
                }
                runCatching {
                    val pos = client.posReport(trackerId)
                    pos.toPosition()?.let { TrackerUi.position[trackerId] = it }
                }
                delay(500)
            }
        }
        TrackerUi.loading.value = false
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

    suspend fun toggle(trackerId: String, kind: String) {
        val target = !switchState(trackerId, kind)
        runCatching {
            client.command(
                trackerId,
                when (kind) {
                    "led" -> "led_control"
                    "buzzer" -> "buzzer_control"
                    else -> "live_tracking"
                },
                target,
            )
        }
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
