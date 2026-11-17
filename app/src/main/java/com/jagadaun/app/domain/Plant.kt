package com.jagadaun.app.domain

enum class Origin { MANUAL, IDENTIFIED }

/** Tanaman di Kebun Saya. */
data class Plant(
    val id: String,
    val displayName: String,
    val scientificName: String? = null,
    val notes: String = "",
    val photoRef: String? = null,
    val origin: Origin = Origin.MANUAL,
    val createdAt: Long,
    val guide: CareGuide? = null,
)
