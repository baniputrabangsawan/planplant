package com.jagadaun.app.domain

/** License and credit travel with the photo and must be shown wherever it appears. */
data class NearbyPhoto(
    val thumbUrl: String,
    val url: String,
    val license: String,
    val credit: String,
)

/** Public record frequency, not plant abundance or native status. */
data class NearbySpecies(
    val scientificName: String,
    val commonName: String?,
    val observations: Int,
    val latitude: Double,
    val longitude: Double,
    val description: String? = null,
    val wikiUrl: String? = null,
    val photo: NearbyPhoto? = null,
)

data class NearbyPlants(
    val species: List<NearbySpecies>,
    val radiusKm: Int,
    val totalRecords: Int,
)
