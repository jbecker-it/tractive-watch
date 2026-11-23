package de.jbecker.tractivewatch

import com.squareup.moshi.JsonEncodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.UnknownHostException

class FriendlyMessageTest {
    @Test
    fun jsonParseErrorIsNotReportedAsNetworkError() {
        // v1.14: Nicht-JSON-Antwort von Tractive erschien als "Netzwerkfehler"
        val msg = JsonEncodingException("Use JsonReader.setLenient(true)").friendlyMessage()
        assertNotEquals("Netzwerkfehler", msg)
        assertEquals("Unerwartete Antwort von Tractive", msg)
    }

    @Test
    fun realNetworkErrorStaysNetworkError() {
        assertEquals("Keine Netzverbindung", UnknownHostException("x").friendlyMessage())
    }
}
