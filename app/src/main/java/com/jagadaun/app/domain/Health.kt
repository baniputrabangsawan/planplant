package com.jagadaun.app.domain

/** A disease hypothesis with a provider ranking score, never a diagnosis or health guarantee. */
data class HealthCandidate(
    val code: String,
    val label: String,
    val score: Double,
)

data class HealthResult(
    val candidates: List<HealthCandidate>,
    val uncertain: Boolean,
)
