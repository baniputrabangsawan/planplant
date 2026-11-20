package com.jagadaun.app.domain

data class HealthGuideSource(val title: String, val url: String)

data class HealthGuide(
    val title: String,
    val symptoms: String,
    val firstSteps: List<String>,
    val prevention: List<String>,
    val sources: List<HealthGuideSource>,
)

/** Low-risk guidance for suspected causes, not a diagnosis or a pesticide prescription. */
object HealthGuides {
    private val houseplants = HealthGuideSource(
        title = "UC IPM — Houseplant Problems",
        url = "https://ipm.ucanr.edu/home-and-landscape/houseplant-problems/",
    )
    private const val escalation =
        "Jika gejala meluas, tanaman terus layu, atau banyak tanaman terdampak, minta pemeriksaan penyuluh pertanian atau ahli tanaman sebelum memilih pengobatan."

    val general = HealthGuide(
        title = "Amati dulu, jangan langsung mengobati",
        symptoms = "Bercak, daun menguning, dan layu dapat memiliki banyak penyebab, termasuk air, cahaya, hama, atau penyakit. Foto saja tidak memastikan penyebab maupun kesehatan tanaman.",
        firstSteps = listOf(
            "Periksa kedua sisi daun, pucuk, batang, dan kelembapan tanah.",
            "Jika ada hama, pisahkan pot dan cuci tangan setelah memegangnya.",
            "Periksa lubang drainase dan buang air yang menggenang di tatakan; jangan menambah air hanya karena daun layu jika media masih basah.",
            escalation,
        ),
        prevention = listOf(
            "Sesuaikan air dan cahaya dengan jenis tanaman, bukan satu jadwal untuk semua tanaman.",
            "Bersihkan daun gugur dan alat berkebun; periksa tanaman baru sebelum mendekatkannya ke koleksi.",
            "Pantau perubahan dengan foto berkala. Jangan memakai racikan pestisida atau obat hanya berdasarkan hasil foto.",
        ),
        sources = listOf(houseplants),
    )

    private val aphids = HealthGuide(
        title = "Dugaan kutu daun",
        symptoms = "Jika kutu daun penyebabnya, serangga kecil dapat bergerombol di pucuk atau bawah daun, disertai daun menggulung, cairan lengket, atau semut. Tanda ini juga dapat menyerupai masalah lain.",
        firstSteps = listOf(
            "Periksa bawah daun dan pucuk untuk memastikan ada kutu, bukan hanya bekas kerusakannya.",
            "Jika kutu terlihat pada tanaman yang cukup kokoh, bilas dengan air pada pagi hari tanpa merusak daun; biarkan daun cepat mengering.",
            "Jika serangan hanya pada sedikit pucuk, buang bagian yang terserang tanpa memangkas berlebihan.",
            escalation,
        ),
        prevention = listOf(
            "Periksa pucuk dan bawah daun secara rutin, terutama ketika banyak tunas baru muncul.",
            "Hindari pupuk nitrogen berlebihan dan periksa bibit sebelum ditanam.",
            "Lindungi pemangsa alami seperti kepik; hindari insektisida berspektrum luas yang dapat membunuhnya.",
        ),
        sources = listOf(HealthGuideSource("UC IPM — Aphids", "https://ipm.ucanr.edu/home-and-landscape/aphids/")),
    )

    private val mealybugs = HealthGuide(
        title = "Dugaan kutu putih",
        symptoms = "Jika kutu putih penyebabnya, serangga berlapis lilin atau gumpalan seperti kapas dapat terlihat di ketiak daun dan batang, kadang bersama cairan lengket. Lapisan putih saja belum memastikan kutu putih.",
        firstSteps = listOf(
            "Periksa celah batang, ketiak daun, pot, dan penyangga untuk menemukan serangga di balik lapisan putih.",
            "Jika terlihat kutu, pisahkan pot bila memungkinkan dan angkat kutu secara manual; buang bagian kecil yang sangat terserang.",
            "Jika tanaman cukup kokoh, bilas kutu yang terbuka dengan air tanpa melukai tanaman dan biarkan daun cepat kering.",
            escalation,
        ),
        prevention = listOf(
            "Periksa tanaman baru, pot, dan penyangga sebelum dipakai bersama tanaman lain.",
            "Pantau ulang celah tanaman karena kutu dapat bersembunyi; jangan berlebihan memberi pupuk nitrogen.",
            "Lindungi musuh alami dan hindari insektisida berspektrum luas sebagai tindakan otomatis.",
        ),
        sources = listOf(HealthGuideSource("UC IPM — Mealybugs", "https://ipm.ucanr.edu/home-and-landscape/mealybugs/")),
    )

    private val spiderMites = HealthGuide(
        title = "Dugaan tungau laba-laba",
        symptoms = "Jika tungau laba-laba penyebabnya, daun dapat berbintik pucat, menguning atau berwarna perunggu, kadang disertai jaring halus. Tungau sangat kecil; bintik daun saja tidak cukup untuk memastikan.",
        firstSteps = listOf(
            "Periksa bawah daun dengan kaca pembesar, atau ketuk daun di atas kertas putih untuk mencari titik kecil yang bergerak.",
            "Jika tungau terlihat dan daun cukup kokoh, bilas bagian bawah daun dengan air tanpa merusaknya, lalu biarkan cepat kering.",
            "Periksa kelembapan media dan cegah kekeringan sesuai kebutuhan jenis tanaman, tanpa membuat media tergenang.",
            escalation,
        ),
        prevention = listOf(
            "Kurangi debu pada tanaman dan area sekitarnya; pantau bawah daun secara rutin.",
            "Hindari tanaman kekurangan air serta lindungi tungau pemangsa dan serangga bermanfaat.",
            "Jangan memakai insektisida umum secara otomatis: sebagian dapat memicu peningkatan tungau dengan membunuh pemangsanya.",
        ),
        sources = listOf(HealthGuideSource("UC IPM — Spider Mites", "https://ipm.ucanr.edu/home-and-landscape/spider-mites/")),
    )

    private val powderyMildew = HealthGuide(
        title = "Dugaan embun tepung",
        symptoms = "Jika embun tepung penyebabnya, bercak seperti tepung putih dapat melebar pada daun atau pucuk, kadang disertai daun berubah bentuk. Beberapa tanaman menunjukkan gejala berbeda; lapisan putih belum membuktikan penyebabnya.",
        firstSteps = listOf(
            "Amati kedua sisi daun dan pucuk; bandingkan perkembangan bercak sebelum memilih penanganan.",
            "Jika memungkinkan, renggangkan tanaman untuk aliran udara dan sesuaikan cahaya dengan toleransi jenisnya; jangan langsung menjemur tanaman penyuka naungan.",
            "Jika bagian yang diduga terserang masih sedikit, buang bagian tersebut tanpa memangkas berlebihan.",
            escalation,
        ),
        prevention = listOf(
            "Pilih varietas tahan embun tepung bila tersedia dan beri jarak tanam yang cukup.",
            "Hindari pupuk nitrogen berlebihan yang memicu pertumbuhan daun terlalu rimbun.",
            "Jangan menganggap daun harus basah agar embun tepung terjadi; penyakit ini dapat berkembang tanpa air di permukaan daun. Hindari menyemprot air sebagai obat umum karena dapat memicu penyakit lain.",
        ),
        sources = listOf(
            HealthGuideSource("UC IPM — Powdery Mildew on Ornamentals", "https://ipm.ucanr.edu/home-and-landscape/powdery-mildew-on-ornamentals/"),
            HealthGuideSource("UC IPM — Powdery Mildew on Vegetables", "https://ipm.ucanr.edu/home-and-landscape/powdery-mildew-on-vegetables/"),
        ),
    )

    private val anthracnose = HealthGuide(
        title = "Dugaan antraknosa",
        symptoms = "Jika antraknosa penyebabnya, bercak gelap atau bagian jaringan yang cekung dapat muncul pada daun, batang, atau buah. Gejalanya berbeda menurut tanaman dan dapat menyerupai penyakit lain.",
        firstSteps = listOf(
            "Catat bagian yang berbercak dan apakah bercak meluas; hasil foto belum memastikan antraknosa.",
            "Kumpulkan daun dan bagian yang gugur. Jika perlu membuang bagian terserang, hindari pemangkasan berat dan bersihkan alat setelahnya.",
            "Siram media tanpa memercikkan air ke daun; beri jarak agar daun lebih cepat kering.",
            escalation,
        ),
        prevention = listOf(
            "Kurangi percikan air dan lamanya daun basah; perbaiki sirkulasi udara sesuai jenis tanaman.",
            "Bersihkan sisa tanaman yang diduga terserang dan pilih varietas tahan bila tersedia.",
            "Jangan menganggap semua bercak adalah antraknosa atau mengandalkan fungisida untuk memulihkan jaringan yang sudah rusak.",
        ),
        sources = listOf(
            HealthGuideSource("UC IPM — Anthracnose", "https://ipm.ucanr.edu/home-and-landscape/anthracnose/"),
            houseplants,
        ),
    )

    private val leafSpots = HealthGuide(
        title = "Dugaan bercak daun akibat cendawan",
        symptoms = "Jika cendawan bercak daun penyebabnya, bercak cokelat hingga kehitaman dapat membesar atau menyatu. Luka semprotan, sinar matahari, dan penyebab lain dapat tampak serupa, sehingga perlu pengamatan lanjutan.",
        firstSteps = listOf(
            "Periksa apakah bercak bertambah dan apakah ada perubahan cahaya, penyiraman, atau penyemprotan sebelum gejala muncul.",
            "Jika memungkinkan, pisahkan pot dan buang sedikit daun yang jelas rusak; bersihkan alat dan tangan setelah menanganinya.",
            "Hindari percikan air ke daun dan siram pagi hari agar bagian yang basah cepat mengering.",
            escalation,
        ),
        prevention = listOf(
            "Beri ruang antar tanaman dan hindari daun terus-menerus basah.",
            "Periksa tanaman baru dan bersihkan sisa daun; jangan memakai alat kotor antar tanaman.",
            "Pastikan penyebab dengan bantuan ahli sebelum memilih pengendalian khusus untuk tanaman tersebut.",
        ),
        sources = listOf(houseplants),
    )

    private val rootRot = HealthGuide(
        title = "Dugaan busuk akar atau pangkal Phytophthora",
        symptoms = "Jika busuk akar atau pangkal Phytophthora penyebabnya, tanaman dapat layu meski media basah, daun menguning, dan akar atau pangkal menggelap. Gejala tersebut tidak spesifik; foto daun saja tidak memastikan busuk akar.",
        firstSteps = listOf(
            "Periksa kelembapan media dan aliran air. Jangan terus menambah air pada media yang sudah basah hanya karena tanaman layu.",
            "Hilangkan genangan dan pastikan air dapat keluar; sesuaikan penyiraman tanpa membuat tanaman kekeringan.",
            "Hindari memindahkan tanah, air limpasan, atau alat kotor dari tanaman yang diduga terserang ke tanaman lain; jangan mengupas batang hanya berdasarkan hasil foto.",
            escalation,
        ),
        prevention = listOf(
            "Gunakan media berdrainase baik dan hindari pangkal tanaman terus-menerus terendam.",
            "Jangan menanam lebih dalam daripada posisi semula atau menumpuk tanah dan mulsa pada pangkal batang.",
            "Pilih bibit yang tampak sehat; perbaiki drainase sebelum menanam kembali. Penyakit Phytophthora pada daun atau buah memerlukan panduan berbeda.",
        ),
        sources = listOf(
            HealthGuideSource("UC IPM — Phytophthora Root and Crown Rot", "https://ipm.ucanr.edu/home-and-landscape/phytophthora-root-and-crown-rot/"),
            houseplants,
        ),
    )

    /**
     * Exact EPPO codes from Pl@ntNet's /v2/diseases catalogue (2026-11-16).
     * Deliberately not exhaustive: no label matching, prefixes, or inferred diagnosis.
     * Generic Phytophthora codes are excluded because they also cover foliar diseases.
     */
    fun forCode(code: String): HealthGuide? = when (code) {
        "1APHIF", "1APHIG", "APHISP", "APHIGO", "APHICR", "APHIFA", "BRVCBR",
        "MYZUPE", "MACSEU", "APHINE", "PENLNI" -> aphids
        "1PSEUF", "PSECSP", "PSECAD", "PSECCI", "PSECGA", "PSECMA", "PSECOB",
        "PSECEL", "PLANMI", "PHENMD", "PHENSL", "PHENDE" -> mealybugs
        "1TETRF", "TETRSP", "TETRUR", "TETREV" -> spiderMites
        "1OIDIG", "OIDISP", "OIDINL", "OIDIMA", "OIDICP", "ERYSSP", "ERYSBE",
        "ERYSCI", "ERYSHE", "ERYSCR", "ERYSLA", "ERYSPI", "ERYSPP", "GOLOSP",
        "PODOSP", "PODOAP", "PODOXA", "PODOLE", "PODOCL", "SPHRFU", "SPHRPA",
        "SPHRMA", "UNCINE", "LEVETA" -> powderyMildew
        "COLLSP", "COLLGL", "COLLAC", "COLLCC", "COLLDS", "COLLLA", "COLLCI",
        "COLLDE", "COLLDU", "COLLFR", "COLLLD", "COLLTR", "COLLOH", "GLOMCI" -> anthracnose
        "1CERCG", "CERCSP", "CERCBE", "CERCCA", "CERCLS", "CERCNI", "CERCAP",
        "ALTECC", "ALTEPN", "ALTEMA", "SEPTSP", "SEPTAP", "SEPTLY" -> leafSpots
        "PHYTCC", "PHYTCM", "PHYTFR", "PHYTFU", "PHYTME", "PHYTMS",
        "PHYTAL", "PHYTCR" -> rootRot
        else -> null
    }
}
