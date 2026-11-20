package com.jagadaun.app.domain

/** Independent species and disease hypotheses from one explicitly uploaded photo. */
data class PlantAnalysisResult(
    val identification: IdentificationResult?,
    val identificationErrorCode: String?,
    val health: HealthResult?,
    val healthErrorCode: String?,
)
