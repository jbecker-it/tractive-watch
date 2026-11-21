package de.jbecker.tractivewatch.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf

object TrackerUi {

    data class Pet(
        val petId: String,
        val name: String,
        val trackerId: String,
    )

    val pets = mutableStateListOf<Pet>()
    val battery = mutableStateMapOf<String, Int>()
    val charging = mutableStateMapOf<String, Boolean>()
    val led = mutableStateMapOf<String, Boolean>()
    val buzzer = mutableStateMapOf<String, Boolean>()
    val live = mutableStateMapOf<String, Boolean>()
    val position = mutableStateMapOf<String, Position>()

    /** null = verbindet, "live" = verbunden, "error" = getrennt */
    var channelStatus = mutableStateOf<String?>(null)
    var loading = mutableStateOf(false)

    fun reset() {
        pets.clear()
        battery.clear()
        charging.clear()
        led.clear()
        buzzer.clear()
        live.clear()
        position.clear()
        channelStatus.value = null
        loading.value = false
    }
}
