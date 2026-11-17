package com.jagadaun.app.domain

/** A user's saved hypothesis and observations, not a verified diagnosis. */
data class HealthRecord(
    val id: String,
    val plantId: String?,
    val createdAt: Long,
    val candidate: HealthCandidate,
    val uncertain: Boolean,
    val photoRefs: List<String>,
    val notes: String = "",
    val status: HealthTrackingStatus = HealthTrackingStatus.WATCHING,
) {
    init {
        require(id.isNotBlank()) { "Assessment id is required" }
        require(photoRefs.size in 1..3 && photoRefs.all { it.isNotBlank() }) {
            "An assessment needs one to three local photos"
        }
    }
}

enum class HealthTrackingStatus(val label: String) {
    WATCHING("Dalam pengamatan"),
    IMPROVING("Membaik"),
    WORSE("Memburuk"),
    RECOVERED("Pulih menurut pengguna"),
}
