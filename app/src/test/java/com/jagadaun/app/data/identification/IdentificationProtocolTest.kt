package com.jagadaun.app.data.identification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CancellationException

class IdentificationProtocolTest {
    @Test
    fun `preserves supported status and error pairs`() {
        val cases = listOf(
            Triple(401, "UNAUTHORIZED", "UNAUTHORIZED"),
            Triple(400, "INVALID_IMAGE", "INVALID_IMAGE"),
            Triple(413, "IMAGE_TOO_LARGE", "IMAGE_TOO_LARGE"),
            Triple(422, "NOT_RECOGNIZED", "NOT_RECOGNIZED"),
            Triple(429, "DAILY_LIMIT", "DAILY_LIMIT"),
            Triple(429, "PROVIDER_QUOTA", "PROVIDER_QUOTA"),
            Triple(503, "PROVIDER_AUTH", "PROVIDER_AUTH"),
            Triple(502, "PROVIDER_UNAVAILABLE", "PROVIDER_UNAVAILABLE"),
            Triple(503, "PROVIDER_UNAVAILABLE", "PROVIDER_UNAVAILABLE"),
            Triple(503, "NOT_CONFIGURED", "NOT_CONFIGURED"),
            Triple(504, "TIMEOUT", "TIMEOUT"),
        )
        cases.forEach { (status, code, expected) ->
            assertEquals(expected, resolveHttpError(status, code))
        }
    }

    @Test
    fun `does not trust arbitrary or mismatched server errors`() {
        assertEquals("UNAUTHORIZED", resolveHttpError(401, "PROVIDER_QUOTA"))
        assertEquals("PROVIDER_UNAVAILABLE", resolveHttpError(500, "UNAUTHORIZED"))
        assertEquals("PROVIDER_UNAVAILABLE", resolveHttpError(302, "NOT_RECOGNIZED"))
        assertEquals("DAILY_LIMIT", resolveHttpError(429, "upstream detail must not reach UI"))
        assertEquals("TIMEOUT", resolveHttpError(504, null))
    }

    @Test
    fun `accepts response at exact byte limit`() {
        val text = "x".repeat(MAX_RESPONSE_BYTES)
        assertEquals(text, readBoundedResponse(ByteArrayInputStream(text.toByteArray())))
    }

    @Test
    fun `rejects oversized response after bounded read`() {
        var bytesRead = 0
        val input = object : InputStream() {
            override fun read(): Int {
                bytesRead++
                return 'x'.code
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill('x'.code.toByte(), offset, offset + length)
                bytesRead += length
                return length
            }
        }
        val error = assertThrows(IdentificationException::class.java) { readBoundedResponse(input) }
        assertEquals("INVALID_RESPONSE", error.code)
        assertEquals(MAX_RESPONSE_BYTES + 1, bytesRead)
    }

    @Test
    fun `propagates cancellation before response read`() {
        assertThrows(CancellationException::class.java) {
            readBoundedResponse(ByteArrayInputStream("{}".toByteArray())) { throw CancellationException() }
        }
    }

    @Test
    fun `decodes UTF8 plant names without changing bytes`() {
        val text = "Épipremnum — sirih gading"
        assertEquals(text, readBoundedResponse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))))
    }
}
