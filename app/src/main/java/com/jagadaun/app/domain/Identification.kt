package com.jagadaun.app.domain

/** Provider ranking score, not a probability or an identification guarantee. */
data class PlantCandidate(
    val scientificName: String,
    val commonName: String?,
    val score: Double,
) {
    val displayName: String
        get() = PlantNames.commonName(scientificName) ?: commonName ?: scientificName
}

data class IdentificationResult(
    val candidates: List<PlantCandidate>,
    val uncertain: Boolean,
)
