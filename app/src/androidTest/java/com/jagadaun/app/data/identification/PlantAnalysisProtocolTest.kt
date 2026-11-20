package com.jagadaun.app.data.identification

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlantAnalysisProtocolTest {
    private val attribution = """"attribution":{"name":"Pl@ntNet","url":"https://plantnet.org/"}"""
    private val identification = """{
        "candidates":[
            {"scientificName":"Monstera deliciosa","commonName":null,"score":0.1},
            {"scientificName":"Epipremnum aureum","commonName":"Sirih gading","score":0.8}
        ],"uncertain":false,$attribution
    }"""
    private val health = """{
        "candidates":[
            {"code":"APHISP","label":"Aphis sp.","score":0.2},
            {"code":"ELSIAM","label":"Elsinoë ampelina","score":0.8}
        ],"uncertain":false,$attribution
    }"""

    @Test
    fun readsBothAttributedResultsWithoutMixingTheirRankings() {
        val result = parsePlantAnalysisResponse(200, response())
        val species = requireNotNull(result.identification)
        val hypotheses = requireNotNull(result.health)
        assertEquals(listOf("Epipremnum aureum", "Monstera deliciosa"), species.candidates.map { it.scientificName })
        assertEquals("Sirih gading", species.candidates.first().commonName)
        assertNull(species.candidates.last().commonName)
        assertEquals(listOf("ELSIAM", "APHISP"), hypotheses.candidates.map { it.code })
        assertEquals("Elsinoë ampelina", hypotheses.candidates.first().label)
        assertFalse(species.uncertain)
        assertFalse(hypotheses.uncertain)
        assertNull(result.identificationErrorCode)
        assertNull(result.healthErrorCode)
    }

    @Test
    fun preservesEitherSuccessWithEverySupportedIndependentError() {
        for (code in listOf("NOT_RECOGNIZED", "PROVIDER_AUTH", "PROVIDER_QUOTA", "PROVIDER_UNAVAILABLE", "TIMEOUT")) {
            val speciesOnly = parsePlantAnalysisResponse(200, response(health = "null", healthError = "\"$code\""))
            assertNotNull(speciesOnly.identification)
            assertNull(speciesOnly.identificationErrorCode)
            assertNull(speciesOnly.health)
            assertEquals(code, speciesOnly.healthErrorCode)
            val healthOnly = parsePlantAnalysisResponse(200, response(identification = "null", identificationError = "\"$code\""))
            assertNull(healthOnly.identification)
            assertEquals(code, healthOnly.identificationErrorCode)
            assertNotNull(healthOnly.health)
            assertNull(healthOnly.healthErrorCode)
        }
    }

    @Test
    fun requiresExactlyFourUniqueFieldsAndConsistentNullErrorPairs() {
        val invalid = listOf(
            response(identificationError = "\"TIMEOUT\""),
            response(healthError = "\"TIMEOUT\""),
            response(identification = "null"),
            response(health = "null"),
            response(identification = "null", identificationError = "true"),
            response(health = "null", healthError = "42"),
            response(health = "null", healthError = "\"DAILY_LIMIT\""),
            response(health = "null", healthError = "\"UNAUTHORIZED\""),
            response(health = "null", healthError = "\"provider private detail\""),
            response(identification = "null", identificationError = "\"NOT_RECOGNIZED\"", health = "null", healthError = "\"TIMEOUT\""),
            response().replace(",\"healthErrorCode\":null", ""),
            response().replace("\"healthErrorCode\":null", "\"healthErrorCode\":null,\"healthErrorCode\":null"),
            response().replace("\"identificationErrorCode\":null", "\"identificationErrorCode\":null,\"identificationErrorCode\":null"),
            response().replace("\"healthErrorCode\":null", "\"healthErrorCode\":null,\"extra\":true"),
            response() + " {}",
            "[]",
            "null",
        )
        invalid.forEach(::assertInvalid)
    }

    @Test
    fun validatesBothNestedAttributionsAndCandidateShapes() {
        for (badIdentification in listOf(
            identification.replace("https://plantnet.org/", "https://example.com/"),
            identification.replace("Pl@ntNet", "Other"),
            identification.replace(",$attribution", ""),
            identification.replace("\"score\":0.8", "\"score\":\"0.8\""),
            identification.replace("\"commonName\":null,", ""),
            identification.replace("\"uncertain\":false", "\"uncertain\":false,\"uncertain\":true"),
            """{"candidates":[],"uncertain":true,$attribution}""",
        )) {
            assertInvalid(response(identification = badIdentification))
        }
        for (badHealth in listOf(
            health.replace("https://plantnet.org/", "https://example.com/"),
            health.replace("Pl@ntNet", "Other"),
            health.replace(",$attribution", ""),
            health.replace("\"score\":0.8", "\"score\":1e999"),
            health.replace("\"code\":\"ELSIAM\"", "\"code\":\"APHISP\""),
            health.replace("\"uncertain\":false", "\"uncertain\":\"false\""),
            """{"candidates":[],"uncertain":true,$attribution}""",
        )) {
            assertInvalid(response(health = badHealth))
        }
    }

    @Test
    fun keepsBothFailureHttpErrorsOnTheExistingSafeContract() {
        for ((status, code) in listOf(
            422 to "NOT_RECOGNIZED", 429 to "PROVIDER_QUOTA", 503 to "PROVIDER_AUTH",
            502 to "PROVIDER_UNAVAILABLE", 504 to "TIMEOUT",
        )) {
            val error = assertThrows(IdentificationException::class.java) {
                parsePlantAnalysisResponse(status, """{"error":{"code":"$code"}}""")
            }
            assertEquals(code, error.code)
        }
        val sanitized = assertThrows(IdentificationException::class.java) {
            parsePlantAnalysisResponse(502, """{"error":{"code":"private provider error"}}""")
        }
        assertEquals("PROVIDER_UNAVAILABLE", sanitized.code)
    }

    private fun response(
        identification: String = this.identification,
        identificationError: String = "null",
        health: String = this.health,
        healthError: String = "null",
    ): String = """{"identification":$identification,"identificationErrorCode":$identificationError,"health":$health,"healthErrorCode":$healthError}"""

    private fun assertInvalid(body: String) {
        val error = assertThrows(IdentificationException::class.java) { parsePlantAnalysisResponse(200, body) }
        assertEquals("INVALID_RESPONSE", error.code)
    }
}
