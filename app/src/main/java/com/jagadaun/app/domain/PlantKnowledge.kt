package com.jagadaun.app.domain

import java.util.Locale

data class PlantVariety(val name: String, val traits: String)
data class PlantKnowledgeReference(val title: String, val url: String)
data class PlantKnowledge(
    val name: String,
    val description: String,
    val observation: String,
    val varieties: List<PlantVariety>,
    val sources: List<PlantKnowledgeReference>,
)

/** Reading material, not a cultivar prediction. Never derived from a user's plant nickname. */
object PlantKnowledgeSource {
    private val entries = mapOf(
        "carica papaya" to PlantKnowledge(
            name = "Pepaya",
            description = "Pepaya memiliki daun menjari dan buah yang tumbuh dekat batang. Calina, Bangkok, dan Merah Delima termasuk jenis pepaya dengan ciri buah berbeda.",
            observation = "Cocokkan bentuk buah matang, warna daging, ukuran, dan label bibit. Foto daun saja tidak membedakan varietas ini. Bentuk buah juga dipengaruhi tipe bunga; gunakan asal bibit untuk memastikan namanya.",
            varieties = listOf(
                PlantVariety("Calina (sering dijual sebagai California)", "Buah relatif kecil, rata-rata sekitar 1,3 kg. Daging merah jingga, tebal, lembut, dan manis. Hasil pemuliaan IPB."),
                PlantVariety("Merah Delima", "Buah bulat lonjong. Daging oranye kemerahan, agak kenyal, dan manis. Varietas yang dilepas Kementerian Pertanian pada 2011."),
                PlantVariety("Bangkok", "Buah bulat agak panjang. Daging oranye kemerahan dengan rasa manis. Warnanya dapat menyerupai jenis pepaya lain."),
                PlantVariety("Cibinong", "Buah bulat panjang, agak ramping dan beralur, dengan tangkai panjang. Buah dapat berukuran besar; daging tebal dan merah."),
                PlantVariety("Jinggo", "Tangkai buah pendek; buah bulat panjang dengan posisi agak menungging. Daging tebal dan empuk, merah pucat, serta manis."),
            ),
            sources = listOf(
                PlantKnowledgeReference("IPB — Pepaya Calina", "https://stp.ipb.ac.id/pepaya-calina-imut-manis-dan-segar/"),
                PlantKnowledgeReference("Kementan — Merah Delima", "https://brmp.pertanian.go.id/informasi-publik/download/varietas/pepaya-merah-delima"),
                PlantKnowledgeReference("UAJY — Ciri pepaya (hlm. 4–5)", "https://repository.uajy.ac.id/id/eprint/4839/3/2BL01098.pdf"),
            ),
        ),
        "mangifera indica" to PlantKnowledge(
            name = "Mangga",
            description = "Mangga memiliki banyak kultivar. Arumanis, Manalagi, dan Gedong tetap satu spesies, tetapi bentuk dan warna buahnya berbeda.",
            observation = "Bandingkan buah yang sudah matang dan label bibit. Bentuk, warna, serta ukuran dapat berubah menurut tempat tumbuh dan kematangan; daun saja tidak menetapkan kultivar.",
            varieties = listOf(
                PlantVariety("Arumanis", "Buah cenderung memanjang dengan ujung tumpul. Dalam pengamatan di Tangerang, daging matang berwarna kuning oranye."),
                PlantVariety("Manalagi", "Buah lonjong dengan ujung tumpul. Dalam pengamatan yang sama, kulit matang tetap hijau dan dagingnya kuning keemasan."),
                PlantVariety("Gedong", "Buah lebih membulat dengan ujung membulat. Dalam pengamatan yang sama, kulit dan daging buah matang berwarna oranye."),
            ),
            sources = listOf(
                PlantKnowledgeReference("Unsoed — Kultivar mangga (2022, hlm. 95)", "https://jos.unsoed.ac.id/index.php/bioe/article/download/4720/3204"),
            ),
        ),
    )

    /** Exact species only: no genus, nickname, cultivar, or author-suffix guessing. */
    fun forScientificName(scientificName: String?): PlantKnowledge? =
        scientificName?.let { entries[it.trim().lowercase(Locale.ROOT)] }
}
