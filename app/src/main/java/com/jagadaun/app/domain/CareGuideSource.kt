package com.jagadaun.app.domain

/**
 * Panduan perawatan terkurasi (JD-05). Konten statis dikunci oleh nama ilmiah,
 * bukan data yang diedit pengguna. Ditambah seiring panduan terkurasi tersedia.
 */
object CareGuideSource {
    private val guides = mapOf(
        "Monstera deliciosa" to CareGuide(
            light = "Cahaya terang tidak langsung",
            water = "Siram saat 5 cm atas tanah kering",
            soil = "Campuran porous dengan arang sekam",
            source = "RHS Plant Finder",
        ),
    )

    fun forScientificName(scientificName: String?): CareGuide? =
        scientificName?.let { guides[it] }
}
