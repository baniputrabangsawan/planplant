package com.jagadaun.app.data.identification

import android.util.JsonReader
import android.util.JsonToken
import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantCandidate
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.StringReader

/** Only stable codes cross into UI; provider response bodies are never displayed or logged. */
class IdentificationException(val code: String) : Exception(code)

internal const val MAX_RESPONSE_BYTES = 64 * 1024

internal fun readBoundedResponse(input: InputStream, checkActive: () -> Unit = {}): String {
    val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
    var count = 0
    while (count < bytes.size) {
        checkActive()
        val read = input.read(bytes, count, bytes.size - count)
        if (read == -1) break
        count += read
    }
    if (count > MAX_RESPONSE_BYTES) throw IdentificationException("INVALID_RESPONSE")
    return String(bytes, 0, count, Charsets.UTF_8)
}

internal fun resolveHttpError(status: Int, serverCode: String?): String {
    val expectedStatus = when (serverCode) {
        "UNAUTHORIZED" -> status == 401
        "INVALID_IMAGE" -> status == 400
        "IMAGE_TOO_LARGE" -> status == 413
        "NOT_RECOGNIZED" -> status == 422
        "DAILY_LIMIT", "PROVIDER_QUOTA" -> status == 429
        "PROVIDER_AUTH", "NOT_CONFIGURED" -> status == 503
        "PROVIDER_UNAVAILABLE" -> status == 502 || status == 503
        "TIMEOUT" -> status == 504
        else -> false
    }
    if (expectedStatus) return requireNotNull(serverCode)
    return when (status) {
        401, 403 -> "UNAUTHORIZED"
        413 -> "IMAGE_TOO_LARGE"
        422 -> "NOT_RECOGNIZED"
        429 -> "DAILY_LIMIT"
        504 -> "TIMEOUT"
        else -> "PROVIDER_UNAVAILABLE"
    }
}

internal fun throwHttpError(status: Int, body: String): Nothing {
    val code = try {
        JSONObject(body).optJSONObject("error")?.opt("code") as? String
    } catch (_: JSONException) {
        null
    }
    throw IdentificationException(resolveHttpError(status, code))
}

internal fun parseIdentificationResponse(status: Int, body: String): IdentificationResult {
    if (status != 200) throwHttpError(status, body)
    try {
        JsonReader(StringReader(body)).use { reader ->
            reader.isLenient = false
            val result = readIdentificationResult(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) invalidResponse()
            return result
        }
    } catch (_: IOException) {
        invalidResponse()
    } catch (_: IllegalStateException) {
        invalidResponse()
    }
}

internal fun readIdentificationResult(reader: JsonReader): IdentificationResult {
    var candidates: ArrayList<PlantCandidate>? = null
    var uncertain: Boolean? = null
    var attributed = false
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "candidates" -> {
                if (candidates != null) invalidResponse()
                val rows = ArrayList<PlantCandidate>(3)
                reader.beginArray()
                while (reader.hasNext()) {
                    if (rows.size == 3) invalidResponse()
                    rows += readPlantCandidate(reader)
                }
                reader.endArray()
                candidates = rows
            }
            "uncertain" -> {
                if (uncertain != null || reader.peek() != JsonToken.BOOLEAN) invalidResponse()
                uncertain = reader.nextBoolean()
            }
            "attribution" -> {
                if (attributed) invalidResponse()
                readPlantnetAttribution(reader)
                attributed = true
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    if (!attributed) invalidResponse()
    val ranked = candidates ?: invalidResponse()
    ranked.sortByDescending { it.score }
    return IdentificationResult(ranked, uncertain ?: invalidResponse())
}

private fun readPlantCandidate(reader: JsonReader): PlantCandidate {
    var scientificName: String? = null
    var commonName: String? = null
    var commonNameRead = false
    var score: Double? = null
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "scientificName" -> {
                if (scientificName != null || reader.peek() != JsonToken.STRING) invalidResponse()
                scientificName = validName(reader.nextString())
            }
            "commonName" -> {
                if (commonNameRead) invalidResponse()
                commonNameRead = true
                commonName = when (reader.peek()) {
                    JsonToken.NULL -> { reader.nextNull(); null }
                    JsonToken.STRING -> validName(reader.nextString())
                    else -> invalidResponse()
                }
            }
            "score" -> {
                if (score != null || reader.peek() != JsonToken.NUMBER) invalidResponse()
                score = reader.nextString().toDoubleOrNull() ?: invalidResponse()
                if (!score.isFinite() || score !in 0.0..1.0) invalidResponse()
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    if (!commonNameRead) invalidResponse()
    return PlantCandidate(scientificName ?: invalidResponse(), commonName, score ?: invalidResponse())
}

private fun validName(name: String): String {
    if (name.length !in 1..200 || name.isBlank() || name.any { it.isISOControl() }) invalidResponse()
    return name.trim()
}

private fun invalidResponse(): Nothing = throw IdentificationException("INVALID_RESPONSE")
