package com.jagadaun.app.data.identification

import com.jagadaun.app.domain.IdentificationResult
import com.jagadaun.app.domain.PlantCandidate
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream

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
        val tokener = JSONTokener(body)
        val root = tokener.nextValue() as? JSONObject ?: invalidResponse()
        if (tokener.nextClean() != '\u0000') invalidResponse()
        val uncertain = root.get("uncertain") as? Boolean ?: invalidResponse()
        val attribution = root.getJSONObject("attribution")
        if (attribution.get("name") != "Pl@ntNet" || attribution.get("url") != "https://plantnet.org/") {
            invalidResponse()
        }
        val rows = root.getJSONArray("candidates")
        if (rows.length() > 3) invalidResponse()
        val candidates = ArrayList<PlantCandidate>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val scientificName = validName(row.get("scientificName"))
            val commonName = when (val value = row.get("commonName")) {
                JSONObject.NULL -> null
                else -> validName(value)
            }
            val score = (row.get("score") as? Number)?.toDouble() ?: invalidResponse()
            if (!score.isFinite() || score !in 0.0..1.0) invalidResponse()
            candidates += PlantCandidate(scientificName, commonName, score)
        }
        return IdentificationResult(candidates.sortedByDescending { it.score }, uncertain)
    } catch (_: JSONException) {
        invalidResponse()
    }
}

private fun validName(value: Any): String {
    val name = value as? String ?: invalidResponse()
    if (name.length !in 1..200 || name.isBlank() || name.any { it.isISOControl() }) invalidResponse()
    return name.trim()
}

private fun invalidResponse(): Nothing = throw IdentificationException("INVALID_RESPONSE")
