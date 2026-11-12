package com.tajasb.quran

import java.util.Locale

data class QuranImam(
    val index: Int,
    val id: String,
    val displayName: String,
    val styleBadge: String,
    val primaryServerBaseUrl: String,
    val fallbackServerBaseUrl: String
) {
    fun buildSurahAudioUrl(surahNumber: Int, useFallback: Boolean = false): String {
        val code3 = String.format(Locale.US, "%03d", surahNumber.coerceIn(1, 114))
        val base = (if (useFallback) fallbackServerBaseUrl else primaryServerBaseUrl).trimEnd('/')
        return "$base/$code3.mp3"
    }
}

data class QuranSurah(
    val number: Int,
    val nameLatin: String,
    val meaningId: String,
    val ayahCount: Int
) {
    val code3: String
        get() = String.format(Locale.US, "%03d", number)

    val safeFileSlug: String
        get() = nameLatin.replace(Regex("[^A-Za-z0-9\\-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

    fun buildFileName(imamId: String): String {
        return "${code3}_${safeFileSlug}_${imamId}.mp3"
    }
}

enum class QuranAudioRoleOption(
    val id: String,
    val label: String,
    val shortBadge: String,
    val description: String
) {
    AUTO(
        id = "AUTO",
        label = "AUTO (Standar + SR)",
        shortBadge = "AUTO",
        description = "Masuk Playlist Audio & otomatis terdaftar sebagai Musik Latar SR"
    ),
    SV1(
        id = "SV1",
        label = "SV1 (Audio SR & AX VT)",
        shortBadge = "SV1",
        description = "Siap untuk System Video SV1 (Musik Latar SR & Tautan VT)"
    ),
    SV2(
        id = "SV2",
        label = "SV2 (Audio PM & SR)",
        shortBadge = "SV2",
        description = "Siap untuk System Video SV2 (Paket Master PM1..n & SR)"
    ),
    SV3(
        id = "SV3",
        label = "SV3 (Audio Loop VL/VB)",
        shortBadge = "SV3",
        description = "Siap untuk System Video SV3 (Musik pendamping siklus VL ↔ VB)"
    ),
    SV4(
        id = "SV4",
        label = "SV4 (Variasi Susunan LA1..n)",
        shortBadge = "SV4",
        description = "Otomatis masuk ke daftar Variasi Susunan Audio SV4 (LA1..LAn)"
    );

    companion object {
        fun fromId(id: String): QuranAudioRoleOption {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
        }
    }
}

enum class QuranTabMode(val label: String, val icon: String) {
    SAVED("Tersimpan di HP", "💾"),
    DOWNLOAD("Unduh Baru (20 Qari)", "🌐")
}

data class SavedQuranAudioItem(
    val imamId: String,
    val imamName: String,
    val surahNumber: Int,
    val surahCode: String,
    val surahName: String,
    val surahMeaning: String,
    val filePath: String,
    val fileSizeBytes: Long
)

data class QuranDownloadState(
    val isExpanded: Boolean = false,
    val activeTab: QuranTabMode = QuranTabMode.SAVED,
    val selectedImam: QuranImam = QuranCatalog.IMAMS.first(),
    val selectedRole: QuranAudioRoleOption = QuranAudioRoleOption.AUTO,
    val searchQuery: String = "",
    val savedSurahFilterImamId: String? = null,
    val selectedSurahNumbers: Set<Int> = setOf(1, 18, 36, 55, 56, 67),
    val downloadedSurahKeys: Set<String> = emptySet(), // "${imam.id}_${surahNumber}"
    val isDownloading: Boolean = false,
    val currentDownloadingSurah: QuranSurah? = null,
    val completedCount: Int = 0,
    val totalQueueCount: Int = 0,
    val currentSurahProgressPercent: Int = 0,
    val isTestingVoice: Boolean = false,
    val isLoadingVoiceTest: Boolean = false,
    val testingSurahNumber: Int? = null,
    val testingImamId: String? = null,
    val voiceTestStatusText: String = "",
    val statusMessage: String = "Pilih Imam & Surat Al-Quran untuk didownload berurutan dan masuk otomatis ke Playlist Audio."
)

object QuranCatalog {

    val PREFERRED_SURAH_NUMBERS: Set<Int> = setOf(1, 18, 36, 55, 56, 67)
    val JUZ_30_SURAH_NUMBERS: Set<Int> = (78..114).toSet()
    val ALL_114_SURAH_NUMBERS: Set<Int> = (1..114).toSet()

    val IMAMS: List<QuranImam> = listOf(
        QuranImam(
            index = 1,
            id = "alafasy",
            displayName = "Mishary Rashid Alafasy",
            styleBadge = "Murattal Merdu",
            primaryServerBaseUrl = "https://server8.mp3quran.net/afs",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaasee"
        ),
        QuranImam(
            index = 2,
            id = "sudais",
            displayName = "Abdurrahman As-Sudais",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server11.mp3quran.net/sds",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/abdurrahmaan_as-sudays"
        ),
        QuranImam(
            index = 3,
            id = "maher",
            displayName = "Maher Al-Muaiqly",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server12.mp3quran.net/maher",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/maher_256"
        ),
        QuranImam(
            index = 4,
            id = "shuraim",
            displayName = "Saud Ash-Shuraim",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server7.mp3quran.net/shur",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/sa3ood_ash-shuraym"
        ),
        QuranImam(
            index = 5,
            id = "ghamdi",
            displayName = "Saad Al-Ghamdi",
            styleBadge = "Tartil Syahdu",
            primaryServerBaseUrl = "https://server7.mp3quran.net/s_gmd",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/sa3d_al-ghaamidi/complete"
        ),
        QuranImam(
            index = 6,
            id = "abdulbasit",
            displayName = "Abdul Basit Abdus Samad (Murattal)",
            styleBadge = "Legendaris Mesir",
            primaryServerBaseUrl = "https://server7.mp3quran.net/basit",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/abdul_basit_murattal"
        ),
        QuranImam(
            index = 7,
            id = "dossari",
            displayName = "Yasser Ad-Dossari",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server11.mp3quran.net/yasser",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/yasser_ad-dussary"
        ),
        QuranImam(
            index = 8,
            id = "qatami",
            displayName = "Nasser Al-Qatami",
            styleBadge = "Khusyuk & Menyentuh",
            primaryServerBaseUrl = "https://server6.mp3quran.net/qtm",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/nasser_bin_ali_alqatami"
        ),
        QuranImam(
            index = 9,
            id = "rifai",
            displayName = "Hani Ar-Rifai",
            styleBadge = "Tartil Lembut",
            primaryServerBaseUrl = "https://server8.mp3quran.net/hani",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/hani_ar-rifai"
        ),
        QuranImam(
            index = 10,
            id = "hussary",
            displayName = "Mahmoud Khalil Al-Hussary",
            styleBadge = "Tajwid Klasik",
            primaryServerBaseUrl = "https://server13.mp3quran.net/husr",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/mahmood_khaleel_al-husaree"
        ),
        QuranImam(
            index = 11,
            id = "minshawi",
            displayName = "Mohamed Siddiq Al-Minshawi",
            styleBadge = "Murattal Klasik",
            primaryServerBaseUrl = "https://server10.mp3quran.net/minsh",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee"
        ),
        QuranImam(
            index = 12,
            id = "shatri",
            displayName = "Abu Bakr Ash-Shatri",
            styleBadge = "Suara Jernih",
            primaryServerBaseUrl = "https://server11.mp3quran.net/shatri",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/abu_bakr_ash-shaatree"
        ),
        QuranImam(
            index = 13,
            id = "ajmi",
            displayName = "Ahmad Al-Ajmi",
            styleBadge = "Lantunan Kuat",
            primaryServerBaseUrl = "https://server10.mp3quran.net/ajm",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/ahmed_ibn_3ali_al-3ajamy"
        ),
        QuranImam(
            index = 14,
            id = "alijaber",
            displayName = "Syaikh Ali Jaber",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server11.mp3quran.net/a_jbr",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/ali_jaber"
        ),
        QuranImam(
            index = 15,
            id = "baleelah",
            displayName = "Bandar Baleelah",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server6.mp3quran.net/balilah",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/bandar_baleela"
        ),
        QuranImam(
            index = 16,
            id = "juhany",
            displayName = "Abdullah Awad Al-Juhany",
            styleBadge = "Imam Masjidil Haram",
            primaryServerBaseUrl = "https://server11.mp3quran.net/jhn",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/abdullaah_3awwaad_al-juhaynee"
        ),
        QuranImam(
            index = 17,
            id = "kurdi",
            displayName = "Raad Muhammad Al-Kurdi",
            styleBadge = "Irama Tenang",
            primaryServerBaseUrl = "https://server6.mp3quran.net/kurdi",
            fallbackServerBaseUrl = "https://server6.mp3quran.net/kurdi"
        ),
        QuranImam(
            index = 18,
            id = "abkar",
            displayName = "Idris Abkar",
            styleBadge = "Maqam Syahdu",
            primaryServerBaseUrl = "https://server6.mp3quran.net/abkr",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/idrees_abkar"
        ),
        QuranImam(
            index = 19,
            id = "fares",
            displayName = "Fares Abbad",
            styleBadge = "Tartil Jernih",
            primaryServerBaseUrl = "https://server8.mp3quran.net/frs_a",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/fares"
        ),
        QuranImam(
            index = 20,
            id = "budair",
            displayName = "Salah Al-Budair",
            styleBadge = "Imam Masjid Nabawi",
            primaryServerBaseUrl = "https://server6.mp3quran.net/s_bud",
            fallbackServerBaseUrl = "https://download.quranicaudio.com/quran/salaah_bukhaatir"
        )
    )

    val SURAHS: List<QuranSurah> = listOf(
        QuranSurah(1, "Al-Fatihah", "Pembukaan", 7),
        QuranSurah(2, "Al-Baqarah", "Sapi Betina", 286),
        QuranSurah(3, "Ali 'Imran", "Keluarga Imran", 200),
        QuranSurah(4, "An-Nisa'", "Wanita", 176),
        QuranSurah(5, "Al-Ma'idah", "Hidangan", 120),
        QuranSurah(6, "Al-An'am", "Binatang Ternak", 165),
        QuranSurah(7, "Al-A'raf", "Tempat Tertinggi", 206),
        QuranSurah(8, "Al-Anfal", "Harta Rampasan", 75),
        QuranSurah(9, "At-Taubah", "Pengampunan", 129),
        QuranSurah(10, "Yunus", "Nabi Yunus", 109),
        QuranSurah(11, "Hud", "Nabi Hud", 123),
        QuranSurah(12, "Yusuf", "Nabi Yusuf", 111),
        QuranSurah(13, "Ar-Ra'd", "Guruh", 43),
        QuranSurah(14, "Ibrahim", "Nabi Ibrahim", 52),
        QuranSurah(15, "Al-Hijr", "Bukit Batu", 99),
        QuranSurah(16, "An-Nahl", "Lebah", 128),
        QuranSurah(17, "Al-Isra'", "Perjalanan Malam", 111),
        QuranSurah(18, "Al-Kahfi", "Penghuni Gua", 110),
        QuranSurah(19, "Maryam", "Maryam", 98),
        QuranSurah(20, "Taha", "Taha", 135),
        QuranSurah(21, "Al-Anbiya'", "Para Nabi", 112),
        QuranSurah(22, "Al-Hajj", "Ibadah Haji", 78),
        QuranSurah(23, "Al-Mu'minun", "Orang-Orang Mukmin", 118),
        QuranSurah(24, "An-Nur", "Cahaya", 64),
        QuranSurah(25, "Al-Furqan", "Pembeda", 77),
        QuranSurah(26, "Asy-Syu'ara'", "Para Penyair", 227),
        QuranSurah(27, "An-Naml", "Semut", 93),
        QuranSurah(28, "Al-Qasas", "Kisah-Kisah", 88),
        QuranSurah(29, "Al-'Ankabut", "Laba-Laba", 69),
        QuranSurah(30, "Ar-Rum", "Bangsa Romawi", 60),
        QuranSurah(31, "Luqman", "Keluarga Luqman", 34),
        QuranSurah(32, "As-Sajdah", "Sujud", 30),
        QuranSurah(33, "Al-Ahzab", "Golongan yang Bersekutu", 73),
        QuranSurah(34, "Saba'", "Kaum Saba'", 54),
        QuranSurah(35, "Fatir", "Pencipta", 45),
        QuranSurah(36, "Yasin", "Yasin", 83),
        QuranSurah(37, "As-Saffat", "Barisan-Barisan", 182),
        QuranSurah(38, "Sad", "Sad", 88),
        QuranSurah(39, "Az-Zumar", "Rombongan", 75),
        QuranSurah(40, "Ghafir", "Yang Mengampuni", 85),
        QuranSurah(41, "Fussilat", "Yang Dijelaskan", 54),
        QuranSurah(42, "Asy-Syura", "Musyawarah", 53),
        QuranSurah(43, "Az-Zukhruf", "Perhiasan", 89),
        QuranSurah(44, "Ad-Dukhan", "Kabut", 59),
        QuranSurah(45, "Al-Jasiyah", "Yang Berlutut", 37),
        QuranSurah(46, "Al-Ahqaf", "Bukit-Bukit Pasir", 35),
        QuranSurah(47, "Muhammad", "Nabi Muhammad", 38),
        QuranSurah(48, "Al-Fath", "Kemenangan", 29),
        QuranSurah(49, "Al-Hujurat", "Kamar-Kamar", 18),
        QuranSurah(50, "Qaf", "Qaf", 45),
        QuranSurah(51, "Az-Zariyat", "Angin yang Menerbangkan", 60),
        QuranSurah(52, "At-Tur", "Bukit Tursina", 49),
        QuranSurah(53, "An-Najm", "Bintang", 62),
        QuranSurah(54, "Al-Qamar", "Bulan", 55),
        QuranSurah(55, "Ar-Rahman", "Yang Maha Pemurah", 78),
        QuranSurah(56, "Al-Waqi'ah", "Hari Kiamat", 96),
        QuranSurah(57, "Al-Hadid", "Besi", 29),
        QuranSurah(58, "Al-Mujadilah", "Wanita yang Mengajukan Gugatan", 22),
        QuranSurah(59, "Al-Hasyr", "Pengusiran", 24),
        QuranSurah(60, "Al-Mumtahanah", "Wanita yang Diuji", 13),
        QuranSurah(61, "As-Saff", "Barisan", 14),
        QuranSurah(62, "Al-Jumu'ah", "Hari Jumat", 11),
        QuranSurah(63, "Al-Munafiqun", "Orang-Orang Munafik", 11),
        QuranSurah(64, "At-Taghabun", "Hari Dinampakkan Kesalahan", 18),
        QuranSurah(65, "At-Talaq", "Talak", 12),
        QuranSurah(66, "At-Tahrim", "Mengharamkan", 12),
        QuranSurah(67, "Al-Mulk", "Kerajaan", 30),
        QuranSurah(68, "Al-Qalam", "Pena", 52),
        QuranSurah(69, "Al-Haqqah", "Hari Kiamat yang Pasti", 52),
        QuranSurah(70, "Al-Ma'arij", "Tempat-Tempat Naik", 44),
        QuranSurah(71, "Nuh", "Nabi Nuh", 28),
        QuranSurah(72, "Al-Jinn", "Jin", 28),
        QuranSurah(73, "Al-Muzzammil", "Orang yang Berselimut", 20),
        QuranSurah(74, "Al-Muddassir", "Orang yang Berkemul", 56),
        QuranSurah(75, "Al-Qiyamah", "Hari Kebangkitan", 40),
        QuranSurah(76, "Al-Insan", "Manusia", 31),
        QuranSurah(77, "Al-Mursalat", "Malaikat yang Diutus", 50),
        QuranSurah(78, "An-Naba'", "Berita Besar", 40),
        QuranSurah(79, "An-Nazi'at", "Malaikat yang Mencabut", 46),
        QuranSurah(80, "'Abasa", "Ia Bermuka Masam", 42),
        QuranSurah(81, "At-Takwir", "Menggulung", 29),
        QuranSurah(82, "Al-Infitar", "Terbelah", 19),
        QuranSurah(83, "Al-Mutaffifin", "Orang-Orang yang Curang", 36),
        QuranSurah(84, "Al-Insyiqaq", "Terbelah", 25),
        QuranSurah(85, "Al-Buruj", "Gugusan Bintang", 22),
        QuranSurah(86, "At-Tariq", "Yang Datang di Malam Hari", 17),
        QuranSurah(87, "Al-A'la", "Yang Paling Tinggi", 19),
        QuranSurah(88, "Al-Ghasyiyah", "Hari Pembalasan", 26),
        QuranSurah(89, "Al-Fajr", "Fajar", 30),
        QuranSurah(90, "Al-Balad", "Negeri", 20),
        QuranSurah(91, "Asy-Syams", "Matahari", 15),
        QuranSurah(92, "Al-Lail", "Malam", 21),
        QuranSurah(93, "Ad-Duha", "Waktu Duha", 11),
        QuranSurah(94, "Al-Insyirah", "Melapangkan", 8),
        QuranSurah(95, "At-Tin", "Buah Tin", 8),
        QuranSurah(96, "Al-'Alaq", "Segumpal Darah", 19),
        QuranSurah(97, "Al-Qadr", "Kemuliaan", 5),
        QuranSurah(98, "Al-Bayyinah", "Bukti yang Nyata", 8),
        QuranSurah(99, "Az-Zalzalah", "Kegoncangan", 8),
        QuranSurah(100, "Al-'Adiyat", "Kuda Perang yang Berlari Kencang", 11),
        QuranSurah(101, "Al-Qari'ah", "Hari Kiamat yang Menggetarkan", 11),
        QuranSurah(102, "At-Takasur", "Bermegah-Megahan", 8),
        QuranSurah(103, "Al-'Asr", "Masa / Waktu", 3),
        QuranSurah(104, "Al-Humazah", "Pengumpat", 9),
        QuranSurah(105, "Al-Fil", "Gajah", 5),
        QuranSurah(106, "Quraisy", "Suku Quraisy", 4),
        QuranSurah(107, "Al-Ma'un", "Barang-Barang yang Berguna", 7),
        QuranSurah(108, "Al-Kausar", "Nikmat yang Berlimpah", 3),
        QuranSurah(109, "Al-Kafirun", "Orang-Orang Kafir", 6),
        QuranSurah(110, "An-Nasr", "Pertolongan", 3),
        QuranSurah(111, "Al-Lahab", "Gejolak Api", 5),
        QuranSurah(112, "Al-Ikhlas", "Ikhlas / Kemurnian Keesaan Allah", 4),
        QuranSurah(113, "Al-Falaq", "Waktu Subuh", 5),
        QuranSurah(114, "An-Nas", "Umat Manusia", 6)
    )

    fun findImamById(id: String): QuranImam {
        return IMAMS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: IMAMS.first()
    }

    fun findSurahByNumber(number: Int): QuranSurah? {
        return SURAHS.getOrNull(number - 1)
    }
}
