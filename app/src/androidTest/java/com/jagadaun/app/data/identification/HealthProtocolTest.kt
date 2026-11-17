package com.jagadaun.app.data.identification

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthProtocolTest {
    private val attribution = """"attribution":{"name":"Pl@ntNet","url":"https://plantnet.org/"}"""

    @Test
    fun preservesProviderHypothesesAndRanksWithoutChangingLabels() {
        val result = parseHealthResponse(200, response("""
            {"code":"ELSIAM","label":"Elsinoë ampelina","score":0.02},
            {"code":"APHISP","label":"Aphis sp.","score":0.91}
        """))
        assertEquals(listOf("APHISP", "ELSIAM"), result.candidates.map { it.code })
        assertEquals("Aphis sp.", result.candidates.first().label)
        assertEquals(0.91, result.candidates.first().score, 0.0)
        assertFalse(result.uncertain)
    }

    @Test
    fun rejectsMalformedOrAmbiguousHypothesesInsteadOfShowingDiagnosis() {
        val invalidCandidates = listOf(
            "",
            """{"code":"APHISP","label":"Aphis sp.","score":0.91},{"code":"APHISP","label":"Other","score":0.1}""",
            """{"code":"aphisp","label":"Aphis sp.","score":0.91}""",
            """{"code":"APHISP","label":" ","score":0.91}""",
            """{"code":"APHISP","label":"Aphis\nsp.","score":0.91}""",
            """{"code":"APHISP","label":"Aphis sp.","score":1.1}""",
            """{"code":"APHISP","label":"Aphis sp.","score":"0.91"}""",
            """{"code":"APHISP","label":"Aphis sp.","score":1e999}""",
            """{"code":"APHISP","code":"ELSIAM","label":"Aphis sp.","score":0.91}""",
        )
        invalidCandidates.forEach { candidates -> assertInvalid(response(candidates)) }
        val candidate = """{"code":"APHISP","label":"Aphis sp.","score":0.91}"""
        assertInvalid(response(candidate).replace("\"uncertain\":false", "\"uncertain\":\"false\""))
        assertInvalid(response(candidate).replace("https://plantnet.org/", "https://example.com/"))
        assertInvalid(response(candidate) + " {}")
        assertInvalid(response(candidate).replace("\"uncertain\":false", "\"uncertain\":false,\"uncertain\":true"))
    }

    @Test
    fun rejectionAndQuotaStayDistinctFromSuccessfulDiagnosis() {
        val rejected = assertThrows(IdentificationException::class.java) {
            parseHealthResponse(422, """{"error":{"code":"NOT_RECOGNIZED"}}""")
        }
        assertEquals("NOT_RECOGNIZED", rejected.code)
        val limited = assertThrows(IdentificationException::class.java) {
            parseHealthResponse(429, """{"error":{"code":"DAILY_LIMIT"}}""")
        }
        assertEquals("DAILY_LIMIT", limited.code)
    }

    private fun response(candidates: String): String =
        """{"candidates":[$candidates],"uncertain":false,$attribution}"""

    private fun assertInvalid(body: String) {
        val error = assertThrows(IdentificationException::class.java) { parseHealthResponse(200, body) }
        assertEquals("INVALID_RESPONSE", error.code)
    }
}
