package de.jbecker.tractivewatch.data

import com.squareup.moshi.Json

data class AuthToken(
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "expires_at") val expiresAt: Long? = null,
)

data class ApiErrorBody(
    val code: Int? = null,
    val message: String? = null,
)

data class LoginRequest(
    @Json(name = "platform_email") val platformEmail: String,
    @Json(name = "platform_token") val platformToken: String,
    @Json(name = "grant_type") val grantType: String = "tractive",
)

data class TrackableObjectSummary(
    @Json(name = "_id") val id: String? = null,
)

data class TrackableDetails(
    val name: String? = null,
)

data class TrackableObject(
    @Json(name = "_id") val id: String? = null,
    @Json(name = "device_id") val deviceId: String? = null,
    val details: TrackableDetails? = null,
)

data class HwReport(
    @Json(name = "battery_level") val batteryLevel: Int? = null,
    @Json(name = "charging_state") val chargingState: String? = null,
    @Json(name = "firmware_version") val firmwareVersion: String? = null,
)

data class PosReport(
    @Json(name = "latlong") val latlong: List<Double>? = null,
    @Json(name = "pos_uncertainty") val posUncertainty: Double? = null,
    @Json(name = "sensor_used") val sensorUsed: String? = null,
    @Json(name = "speed") val speed: Double? = null,
    val time: String? = null,
) {
    fun toPosition(): Position? {
        val ll = latlong ?: return null
        if (ll.size < 2) return null
        return Position(
            lat = ll[0],
            lon = ll[1],
            accuracy = posUncertainty,
            sensor = sensorUsed,
            time = time,
        )
    }
}

data class ChannelPosition(
    @Json(name = "latlong") val latlong: List<Double>? = null,
    @Json(name = "pos_uncertainty") val posUncertainty: Double? = null,
    val accuracy: Double? = null,
    @Json(name = "sensor_used") val sensorUsed: String? = null,
    val time: String? = null,
)

data class ChannelHardware(
    @Json(name = "battery_level") val batteryLevel: Int? = null,
)

data class ChannelSwitch(
    val active: Boolean? = null,
    val pending: Boolean? = null,
    val remaining: Long? = null,
)

data class ChannelEvent(
    val message: String? = null,
    @Json(name = "tracker_id") val trackerId: String? = null,
    val position: ChannelPosition? = null,
    val hardware: ChannelHardware? = null,
    @Json(name = "led_control") val ledControl: ChannelSwitch? = null,
    @Json(name = "buzzer_control") val buzzerControl: ChannelSwitch? = null,
    @Json(name = "live_tracking") val liveTracking: ChannelSwitch? = null,
)

data class Position(
    val lat: Double,
    val lon: Double,
    val accuracy: Double? = null,
    val sensor: String? = null,
    val time: String? = null,
)
