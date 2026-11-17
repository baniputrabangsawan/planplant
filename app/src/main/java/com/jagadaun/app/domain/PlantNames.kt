package com.jagadaun.app.domain

import java.util.Locale

/**
 * Familiar names for an intentionally small, species-level selection.
 * Sources document names only; their medicinal or care claims are not imported.
 * No genus, cultivar, or scientific-author stripping: an unknown name stays unknown.
 */
object PlantNames {
    private val names = mapOf(
        // CCRC, Universitas Gadjah Mada:
        // https://ccrc.farmasi.ugm.ac.id/ensiklopedia/ensiklopedia-tanaman-antikanker/k/kembang-sepatu/
        "hibiscus rosa-sinensis" to "Kembang sepatu",
        // Socfindo Conservation, local-name and species records:
        // https://www.socfindoconservation.co.id/plant/699
        "hibiscus schizopetalus" to "Kembang sepatu gantung",
        // https://www.socfindoconservation.co.id/plant/338
        "capsicum annuum" to "Cabai",
        // https://www.socfindoconservation.co.id/plant/319
        "solanum lycopersicum" to "Tomat",
        // https://www.socfindoconservation.co.id/plant/311
        "solanum melongena" to "Terong",
        // https://www.socfindoconservation.co.id/plant/475
        "carica papaya" to "Pepaya",
        // https://www.socfindoconservation.co.id/plant/451
        "psidium guajava" to "Jambu biji",
        // https://www.socfindoconservation.co.id/plant/203 (explicit hybrid spellings)
        "citrus x aurantiifolia" to "Jeruk nipis",
        "citrus × aurantiifolia" to "Jeruk nipis",
        "citrus aurantiifolia" to "Jeruk nipis",
        // https://www.socfindoconservation.co.id/plant/558
        "citrus maxima" to "Jeruk bali",
        // https://www.socfindoconservation.co.id/plant/616
        "jasminum sambac" to "Melati",
        // https://www.socfindoconservation.co.id/plant/617
        "codiaeum variegatum" to "Puring",
        // https://www.socfindoconservation.co.id/plant/329
        "aloe vera" to "Lidah buaya",
        // https://www.socfindoconservation.co.id/plant/804
        // Synonym: https://www.nparks.gov.sg/florafaunaweb/flora/2/4/2420
        "dracaena trifasciata" to "Lidah mertua",
        "sansevieria trifasciata" to "Lidah mertua",
    )

    /** A display alias only; never rewrites a saved or user-chosen plant name. */
    fun commonName(scientificName: String): String? =
        names[scientificName.trim().lowercase(Locale.ROOT)]
}
