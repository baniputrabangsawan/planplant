package com.jagadaun.app.data.identification

import android.util.JsonReader
import android.util.JsonToken
import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthResult
import java.io.IOException
import java.io.StringReader

internal fun parseHealthResponse(status: Int, body: String): HealthResult {
    if (status != 200) throwHttpError(status, body)
    try {
        JsonReader(StringReader(body)).use { reader ->
            reader.isLenient = false
            var candidates: List<HealthCandidate>? = null
            var uncertain: Boolean? = null
            var attributed = false
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "candidates" -> {
                        if (candidates != null) invalidHealthResponse()
                        candidates = readHealthCandidates(reader)
                    }
                    "uncertain" -> {
                        if (uncertain != null || reader.peek() != JsonToken.BOOLEAN) invalidHealthResponse()
                        uncertain = reader.nextBoolean()
                    }
                    "attribution" -> {
                        if (attributed) invalidHealthResponse()
                        readHealthAttribution(reader)
                        attributed = true
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (!attributed || reader.peek() != JsonToken.END_DOCUMENT) invalidHealthResponse()
            return HealthResult(
                candidates ?: invalidHealthResponse(),
                uncertain ?: invalidHealthResponse(),
            )
        }
    } catch (_: IOException) {
        invalidHealthResponse()
    } catch (_: IllegalStateException) {
        invalidHealthResponse()
    }
}

private fun readHealthCandidates(reader: JsonReader): List<HealthCandidate> {
    val candidates = ArrayList<HealthCandidate>(3)
    reader.beginArray()
    while (reader.hasNext()) {
        if (candidates.size == 3) invalidHealthResponse()
        val candidate = readHealthCandidate(reader)
        if (candidates.any { it.code == candidate.code }) invalidHealthResponse()
        candidates += candidate
    }
    reader.endArray()
    if (candidates.isEmpty()) invalidHealthResponse()
    candidates.sortByDescending { it.score }
    return candidates
}

private fun readHealthCandidate(reader: JsonReader): HealthCandidate {
    var code: String? = null
    var label: String? = null
    var score: Double? = null
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "code" -> {
                if (code != null) invalidHealthResponse()
                code = readHealthString(reader)
                if (code.length !in 1..20 || code.any { it !in 'A'..'Z' && it !in '0'..'9' }) {
                    invalidHealthResponse()
                }
            }
            "label" -> {
                if (label != null) invalidHealthResponse()
                val value = readHealthString(reader)
                if (value.length !in 1..200 || value.isBlank() || value.any { it.isISOControl() }) {
                    invalidHealthResponse()
                }
                label = value.trim()
            }
            "score" -> {
                if (score != null || reader.peek() != JsonToken.NUMBER) invalidHealthResponse()
                score = reader.nextString().toDoubleOrNull() ?: invalidHealthResponse()
                if (!score.isFinite() || score !in 0.0..1.0) invalidHealthResponse()
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return HealthCandidate(
        code ?: invalidHealthResponse(),
        label ?: invalidHealthResponse(),
        score ?: invalidHealthResponse(),
    )
}

private fun readHealthAttribution(reader: JsonReader) {
    var name: String? = null
    var url: String? = null
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "name" -> {
                if (name != null) invalidHealthResponse()
                name = readHealthString(reader)
            }
            "url" -> {
                if (url != null) invalidHealthResponse()
                url = readHealthString(reader)
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    if (name != "Pl@ntNet" || url != "https://plantnet.org/") invalidHealthResponse()
}

private fun readHealthString(reader: JsonReader): String {
    if (reader.peek() != JsonToken.STRING) invalidHealthResponse()
    return reader.nextString()
}

private fun invalidHealthResponse(): Nothing = throw IdentificationException("INVALID_RESPONSE")
