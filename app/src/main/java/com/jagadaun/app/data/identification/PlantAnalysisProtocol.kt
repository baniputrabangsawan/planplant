package com.jagadaun.app.data.identification

import android.util.JsonReader
import android.util.JsonToken
import com.jagadaun.app.domain.HealthResult
import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantAnalysisResult
import java.io.IOException
import java.io.StringReader

internal fun parsePlantAnalysisResponse(status: Int, body: String): PlantAnalysisResult {
    if (status != 200) throwHttpError(status, body)
    try {
        JsonReader(StringReader(body)).use { reader ->
            reader.isLenient = false
            var identification: IdentificationResult? = null
            var identificationErrorCode: String? = null
            var health: HealthResult? = null
            var healthErrorCode: String? = null
            var seen = 0
            reader.beginObject()
            while (reader.hasNext()) {
                val field = when (reader.nextName()) {
                    "identification" -> 1
                    "identificationErrorCode" -> 2
                    "health" -> 4
                    "healthErrorCode" -> 8
                    else -> invalidAnalysisResponse()
                }
                if (seen and field != 0) invalidAnalysisResponse()
                seen = seen or field
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    continue
                }
                when (field) {
                    1 -> {
                        identification = readIdentificationResult(reader)
                        if (identification.candidates.isEmpty()) invalidAnalysisResponse()
                    }
                    2 -> identificationErrorCode = readAnalysisErrorCode(reader)
                    4 -> health = readHealthResult(reader)
                    8 -> healthErrorCode = readAnalysisErrorCode(reader)
                }
            }
            reader.endObject()
            if (seen != 15 || reader.peek() != JsonToken.END_DOCUMENT ||
                (identification == null) != (identificationErrorCode != null) ||
                (health == null) != (healthErrorCode != null) ||
                (identification == null && health == null)
            ) {
                invalidAnalysisResponse()
            }
            return PlantAnalysisResult(identification, identificationErrorCode, health, healthErrorCode)
        }
    } catch (_: IOException) {
        invalidAnalysisResponse()
    } catch (_: IllegalStateException) {
        invalidAnalysisResponse()
    }
}

private fun readAnalysisErrorCode(reader: JsonReader): String {
    if (reader.peek() != JsonToken.STRING) invalidAnalysisResponse()
    return when (val code = reader.nextString()) {
        "NOT_RECOGNIZED", "PROVIDER_AUTH", "PROVIDER_QUOTA", "PROVIDER_UNAVAILABLE", "TIMEOUT" -> code
        else -> invalidAnalysisResponse()
    }
}

private fun invalidAnalysisResponse(): Nothing = throw IdentificationException("INVALID_RESPONSE")
