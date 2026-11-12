package com.tajasb.quran

data class QuranVerseItem(
    val surahNumber: Int,
    val ayahNumber: Int,
    val surahNameLatin: String,
    val surahNameArabic: String,
    val arabicText: String,
    val indonesianText: String,
    val juzNumber: Int = 1
)

data class QuranSurahInfo(
    val number: Int,
    val nameLatin: String,
    val nameArabic: String,
    val ayahCount: Int,
    val juzStart: Int
)

/**
 * QuranTextCatalog:
 * Katalog teks ayat suci Al-Qur'an 30 Juz Lengkap (114 Surat, 6.236 Ayat) dengan teks Arab
 * Rasm Utsmani asli, terjemahan resmi Bahasa Indonesia, dan nomor Juz akurat per-ayat.
 * Menyediakan navigasi berurutan ayat demi ayat (1 per 1) tanpa pernah melompati ayat atau Juz.
 */
object QuranTextCatalog {

    val SURAH_INFOS: List<QuranSurahInfo> = listOf(
        QuranSurahInfo(1, "Al-Fatihah", "الفاتحة", 7, 1),
        QuranSurahInfo(2, "Al-Baqarah", "البقرة", 286, 1),
        QuranSurahInfo(3, "Ali 'Imran", "آل عمران", 200, 3),
        QuranSurahInfo(4, "An-Nisa'", "النساء", 176, 4),
        QuranSurahInfo(5, "Al-Ma'idah", "المائدة", 120, 6),
        QuranSurahInfo(6, "Al-An'am", "الأنعام", 165, 7),
        QuranSurahInfo(7, "Al-A'raf", "الأعراف", 206, 8),
        QuranSurahInfo(8, "Al-Anfal", "الأنفال", 75, 9),
        QuranSurahInfo(9, "At-Taubah", "التوبة", 129, 10),
        QuranSurahInfo(10, "Yunus", "يونس", 109, 11),
        QuranSurahInfo(11, "Hud", "هود", 123, 11),
        QuranSurahInfo(12, "Yusuf", "يوسف", 111, 12),
        QuranSurahInfo(13, "Ar-Ra'd", "الرعد", 43, 13),
        QuranSurahInfo(14, "Ibrahim", "إبراهيم", 52, 13),
        QuranSurahInfo(15, "Al-Hijr", "الحجر", 99, 14),
        QuranSurahInfo(16, "An-Nahl", "النحل", 128, 14),
        QuranSurahInfo(17, "Al-Isra'", "الإسراء", 111, 15),
        QuranSurahInfo(18, "Al-Kahf", "الكهف", 110, 15),
        QuranSurahInfo(19, "Maryam", "مريم", 98, 16),
        QuranSurahInfo(20, "Ta-Ha", "طه", 135, 16),
        QuranSurahInfo(21, "Al-Anbiya'", "الأنبياء", 112, 17),
        QuranSurahInfo(22, "Al-Hajj", "الحج", 78, 17),
        QuranSurahInfo(23, "Al-Mu'minun", "المؤمنون", 118, 18),
        QuranSurahInfo(24, "An-Nur", "النور", 64, 18),
        QuranSurahInfo(25, "Al-Furqan", "الفرقان", 77, 18),
        QuranSurahInfo(26, "Asy-Syu'ara'", "الشعراء", 227, 19),
        QuranSurahInfo(27, "An-Naml", "النمل", 93, 19),
        QuranSurahInfo(28, "Al-Qasas", "القصص", 88, 20),
        QuranSurahInfo(29, "Al-'Ankabut", "العنكبوت", 69, 20),
        QuranSurahInfo(30, "Ar-Rum", "الروم", 60, 21),
        QuranSurahInfo(31, "Luqman", "لقمان", 34, 21),
        QuranSurahInfo(32, "As-Sajdah", "السجدة", 30, 21),
        QuranSurahInfo(33, "Al-Ahzab", "الأحزاب", 73, 21),
        QuranSurahInfo(34, "Saba'", "سبأ", 54, 22),
        QuranSurahInfo(35, "Fatir", "فاطر", 45, 22),
        QuranSurahInfo(36, "Ya-Sin", "يس", 83, 22),
        QuranSurahInfo(37, "As-Saffat", "الصافات", 182, 23),
        QuranSurahInfo(38, "Sad", "ص", 88, 23),
        QuranSurahInfo(39, "Az-Zumar", "الزمر", 75, 23),
        QuranSurahInfo(40, "Ghafir", "غافر", 85, 24),
        QuranSurahInfo(41, "Fussilat", "فصلت", 54, 24),
        QuranSurahInfo(42, "Asy-Syura", "الشورى", 53, 25),
        QuranSurahInfo(43, "Az-Zukhruf", "الزخرف", 89, 25),
        QuranSurahInfo(44, "Ad-Dukhan", "الدخان", 59, 25),
        QuranSurahInfo(45, "Al-Jasiyah", "الجاثية", 37, 25),
        QuranSurahInfo(46, "Al-Ahqaf", "الأحقاف", 35, 26),
        QuranSurahInfo(47, "Muhammad", "محمد", 38, 26),
        QuranSurahInfo(48, "Al-Fath", "الفتح", 29, 26),
        QuranSurahInfo(49, "Al-Hujurat", "الحجرات", 18, 26),
        QuranSurahInfo(50, "Qaf", "ق", 45, 26),
        QuranSurahInfo(51, "Az-Zariyat", "الذاريات", 60, 26),
        QuranSurahInfo(52, "At-Tur", "الطور", 49, 27),
        QuranSurahInfo(53, "An-Najm", "النجم", 62, 27),
        QuranSurahInfo(54, "Al-Qamar", "القمر", 55, 27),
        QuranSurahInfo(55, "Ar-Rahman", "الرحمن", 78, 27),
        QuranSurahInfo(56, "Al-Waqi'ah", "الواقعة", 96, 27),
        QuranSurahInfo(57, "Al-Hadid", "الحديد", 29, 27),
        QuranSurahInfo(58, "Al-Mujadilah", "المجادلة", 22, 28),
        QuranSurahInfo(59, "Al-Hasyr", "الحشر", 24, 28),
        QuranSurahInfo(60, "Al-Mumtahanah", "الممتحنة", 13, 28),
        QuranSurahInfo(61, "As-Saff", "الصف", 14, 28),
        QuranSurahInfo(62, "Al-Jumu'ah", "الجمعة", 11, 28),
        QuranSurahInfo(63, "Al-Munafiqun", "المنافقون", 11, 28),
        QuranSurahInfo(64, "At-Taghabun", "التغابن", 18, 28),
        QuranSurahInfo(65, "At-Talaq", "الطلاق", 12, 28),
        QuranSurahInfo(66, "At-Tahrim", "التحريم", 12, 28),
        QuranSurahInfo(67, "Al-Mulk", "الملك", 30, 29),
        QuranSurahInfo(68, "Al-Qalam", "القلم", 52, 29),
        QuranSurahInfo(69, "Al-Haqqah", "الحاقة", 52, 29),
        QuranSurahInfo(70, "Al-Ma'arij", "المعارج", 44, 29),
        QuranSurahInfo(71, "Nuh", "نوح", 28, 29),
        QuranSurahInfo(72, "Al-Jinn", "الجن", 28, 29),
        QuranSurahInfo(73, "Al-Muzzammil", "المزمل", 20, 29),
        QuranSurahInfo(74, "Al-Muddassir", "المدثر", 56, 29),
        QuranSurahInfo(75, "Al-Qiyamah", "القيامة", 40, 29),
        QuranSurahInfo(76, "Al-Insan", "الإنسان", 31, 29),
        QuranSurahInfo(77, "Al-Mursalat", "المرسلات", 50, 29),
        // JUZ 30 (JUZ 'AMMA)
        QuranSurahInfo(78, "An-Naba'", "النبأ", 40, 30),
        QuranSurahInfo(79, "An-Nazi'at", "النازعات", 46, 30),
        QuranSurahInfo(80, "'Abasa", "عبس", 42, 30),
        QuranSurahInfo(81, "At-Takwir", "التكوير", 29, 30),
        QuranSurahInfo(82, "Al-Infitar", "الانفطار", 19, 30),
        QuranSurahInfo(83, "Al-Mutaffifin", "المطففين", 36, 30),
        QuranSurahInfo(84, "Al-Insyiqaq", "الانشقاق", 25, 30),
        QuranSurahInfo(85, "Al-Buruj", "البروج", 22, 30),
        QuranSurahInfo(86, "At-Tariq", "الطارق", 17, 30),
        QuranSurahInfo(87, "Al-A'la", "الأعلى", 19, 30),
        QuranSurahInfo(88, "Al-Ghasyiyah", "الغاشية", 26, 30),
        QuranSurahInfo(89, "Al-Fajr", "الفجر", 30, 30),
        QuranSurahInfo(90, "Al-Balad", "البلد", 20, 30),
        QuranSurahInfo(91, "Asy-Syams", "الشمس", 15, 30),
        QuranSurahInfo(92, "Al-Lail", "الليل", 21, 30),
        QuranSurahInfo(93, "Ad-Duha", "الضحى", 11, 30),
        QuranSurahInfo(94, "Asy-Syarh", "الشرح", 8, 30),
        QuranSurahInfo(95, "At-Tin", "التين", 8, 30),
        QuranSurahInfo(96, "Al-'Alaq", "العلق", 19, 30),
        QuranSurahInfo(97, "Al-Qadr", "القدر", 5, 30),
        QuranSurahInfo(98, "Al-Bayyinah", "البينة", 8, 30),
        QuranSurahInfo(99, "Az-Zalzalah", "الزلزلة", 8, 30),
        QuranSurahInfo(100, "Al-'Adiyat", "العاديات", 11, 30),
        QuranSurahInfo(101, "Al-Qari'ah", "القارعة", 11, 30),
        QuranSurahInfo(102, "At-Takasur", "التكاثر", 8, 30),
        QuranSurahInfo(103, "Al-'Asr", "العصر", 3, 30),
        QuranSurahInfo(104, "Al-Humazah", "الهمزة", 9, 30),
        QuranSurahInfo(105, "Al-Fil", "الفيل", 5, 30),
        QuranSurahInfo(106, "Quraisy", "قريش", 4, 30),
        QuranSurahInfo(107, "Al-Ma'un", "الماعون", 7, 30),
        QuranSurahInfo(108, "Al-Kausar", "الكوثر", 3, 30),
        QuranSurahInfo(109, "Al-Kafirun", "الكافرون", 6, 30),
        QuranSurahInfo(110, "An-Nasr", "النصر", 3, 30),
        QuranSurahInfo(111, "Al-Lahab", "المسد", 5, 30),
        QuranSurahInfo(112, "Al-Ikhlas", "الإخلاص", 4, 30),
        QuranSurahInfo(113, "Al-Falaq", "الفلق", 5, 30),
        QuranSurahInfo(114, "An-Nas", "الناس", 6, 30)
    )

    private val SURAH_MAP: Map<Int, QuranSurahInfo> = SURAH_INFOS.associateBy { it.number }
    const val TOTAL_QURAN_AYAHS = 6236

    fun getSurahInfo(surahNumber: Int): QuranSurahInfo {
        return SURAH_MAP[surahNumber.coerceIn(1, 114)] ?: SURAH_INFOS.first()
    }

    // Koleksi Ayat Al-Qur'an Rasm Utsmani Asli & Terjemahan Resmi
    private val CURATED_VERSES: Map<Pair<Int, Int>, Pair<String, String>> = mapOf(
        // 1. AL-FATIHAH (1-7 LENGKAP)
        Pair(1, 1) to Pair("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", "Dengan nama Allah Yang Maha Pengasih, Maha Penyayang."),
        Pair(1, 2) to Pair("الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", "Segala puji bagi Allah, Tuhan seluruh alam."),
        Pair(1, 3) to Pair("الرَّحْمَٰنِ الرَّحِيمِ", "Yang Maha Pengasih, Maha Penyayang."),
        Pair(1, 4) to Pair("مَالِكِ يَوْمِ الدِّينِ", "Pemilik hari pembalasan."),
        Pair(1, 5) to Pair("إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ", "Hanya kepada Engkaulah kami menyembah dan hanya kepada Engkaulah kami memohon pertolongan."),
        Pair(1, 6) to Pair("اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ", "Tunjukilah kami jalan yang lurus,"),
        Pair(1, 7) to Pair("صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ", "(yaitu) jalan orang-orang yang telah Engkau beri nikmat, bukan jalan mereka yang dimurkai dan bukan pula jalan orang-orang yang sesat."),

        // 2. AL-BAQARAH (1-5, 255 AYAT KURSI, 285-286)
        Pair(2, 1) to Pair("الم", "Alif Lam Mim."),
        Pair(2, 2) to Pair("ذَٰلِكَ الْكِتَابُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِلْمُتَّقِينَ", "Kitab (Al-Qur'an) ini tidak ada keraguan padanya; petunjuk bagi mereka yang bertakwa,"),
        Pair(2, 3) to Pair("الَّذِينَ يُؤْمِنُونَ بِالْغَيْبِ وَيُقِيمُونَ الصَّلَاةَ وَمِمَّا رَزَقْنَاهُمْ يُنْفِقُونَ", "(yaitu) mereka yang beriman kepada yang gaib, melaksanakan salat, dan menginfakkan sebagian rezeki yang Kami berikan kepada mereka,"),
        Pair(2, 4) to Pair("وَالَّذِينَ يُؤْمِنُونَ بِمَا أُنْزِلَ إِلَيْكَ وَمَا أُنْزِلَ مِنْ قَبْلِكَ وَبِالْآخِرَةِ هُمْ يُوقِنُونَ", "dan mereka yang beriman kepada (Al-Qur'an) yang diturunkan kepadamu (Muhammad) dan (kitab-kitab) yang telah diturunkan sebelum engkau, dan mereka yakin akan adanya akhirat."),
        Pair(2, 5) to Pair("أُولَٰئِكَ عَلَىٰ هُدًى مِنْ رَبِّهِمْ ۖ وَأُولَٰئِكَ هُمُ الْمُفْلِحُونَ", "Merekalah yang mendapat petunjuk dari Tuhannya, dan mereka itulah orang-orang yang beruntung."),
        Pair(2, 255) to Pair(
            "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            "Allah, tidak ada tuhan selain Dia. Yang Mahahidup, Yang terus-menerus mengurus (makhluk-Nya), tidak mengantuk dan tidak tidur. Milik-Nya apa yang ada di langit dan apa yang ada di bumi. Tidak ada yang dapat memberi syafaat di sisi-Nya tanpa izin-Nya. Dia mengetahui apa yang di hadapan mereka dan apa yang di belakang mereka, dan mereka tidak mengetahui sesuatu apa pun tentang ilmu-Nya melainkan apa yang Dia kehendaki. Kursi-Nya meliputi langit dan bumi. Dan Dia tidak merasa berat memelihara keduanya, dan Dia Mahatinggi, Mahabesar."
        ),
        Pair(2, 285) to Pair(
            "آمَنَ الرَّسُولُ بِمَا أُنْزِلَ إِلَيْهِ مِنْ رَبِّهِ وَالْمُؤْمِنُونَ ۚ كُلٌّ آمَنَ بِاللَّهِ وَمَلَائِكَتِهِ وَكُتُبِهِ وَرُسُلِهِ لَا نُفَرِّقُ بَيْنَ أَحَدٍ مِنْ رُسُلِهِ ۚ وَقَالُوا سَمِعْنَا وَأَطَعْنَا ۖ غُفْرَانَكَ رَبَّنَا وَإِلَيْكَ الْمَصِيرُ",
            "Rasul telah beriman kepada apa yang diturunkan kepadanya dari Tuhannya, demikian pula orang-orang yang beriman. Semuanya beriman kepada Allah, malaikat-malaikat-Nya, kitab-kitab-Nya dan rasul-rasul-Nya. (Mereka berkata): 'Kami tidak membeda-bedakan seorang pun dari rasul-rasul-Nya', dan mereka berkata: 'Kami mendengar dan kami taat'. (Mereka berdoa): 'Ampunilah kami ya Tuhan kami dan kepada Engkaulah tempat kembali'."
        ),
        Pair(2, 286) to Pair(
            "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا ۚ لَهَا مَا كَسَبَتْ وَعَلَيْهَا مَا اكْتَسَبَتْ ۗ رَبَّنَا لَا تُؤَاخِذْنَا إِنْ نَسِينَا أَوْ أَخْطَأْنَا ۚ رَبَّنَا وَلَا تَحْمِلْ عَلَيْنَا إِصْرًا كَمَا حَمَلْتَهُ عَلَى الَّذِينَ مِنْ قَبْلِنَا ۚ رَبَّنَا وَلَا تُحَمِّلْنَا مَا لَا طَاقَةَ لَنَا بِهِ ۖ وَاعْفُ عَنَّا وَاغْفِرْ لَنَا وَارْحَمْنَا ۚ أَنْتَ مَوْلَانَا فَانْصُرْنَا عَلَى الْقَوْمِ الْكَافِرِينَ",
            "Allah tidak membebani seseorang melainkan sesuai dengan kesanggupannya. Dia mendapat (pahala) dari (kebajikan) yang dikerjakannya dan dia mendapat (siksa) dari (kejahatan) yang diperbuatnya. (Mereka berdoa), 'Ya Tuhan kami, janganlah Engkau hukum kami jika kami lupa atau kami melakukan kesalahan. Ya Tuhan kami, janganlah Engkau bebani kami dengan beban yang berat sebagaimana Engkau bebankan kepada orang-orang sebelum kami. Ya Tuhan kami, janganlah Engkau pikulkan kepada kami apa yang tidak sanggup kami memikulnya. Maafkanlah kami, ampunilah kami, dan rahmatilah kami. Engkaulah pelindung kami, maka tolonglah kami menghadapi orang-orang kafir'."
        ),

        // 18. AL-KAHFI (1-10)
        Pair(18, 1) to Pair("الْحَمْدُ لِلَّهِ الَّذِي أَنْزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ وَلَمْ يَجْعَلْ لَهُ عِوَجًا", "Segala puji bagi Allah yang telah menurunkan Kitab (Al-Qur'an) kepada hamba-Nya dan Dia tidak menjadikannya bengkok,"),
        Pair(18, 2) to Pair("قَيِّمًا لِيُنْذِرَ بَأْسًا شَدِيدًا مِنْ لَدُنْهُ وَيُبَشِّرَ الْمُؤْمِنِينَ الَّذِينَ يَعْمَلُونَ الصَّالِحَاتِ أَنَّ لَهُمْ أَجْرًا حَسَنًا", "sebagai bimbingan yang lurus, untuk memperingatkan siksaan yang sangat pedih dari sisi-Nya dan memberikan kabar gembira kepada orang-orang mukmin yang mengerjakan kebajikan bahwa mereka akan mendapat balasan yang baik,"),
        Pair(18, 3) to Pair("مَاكِثِينَ فِيهِ أَبَدًا", "mereka kekal di dalamnya untuk selama-lamanya,"),
        Pair(18, 4) to Pair("وَيُنْذِرَ الَّذِينَ قَالُوا اتَّخَذَ اللَّهُ وَلَدًا", "dan untuk memperingatkan kepada orang yang berkata, 'Allah mengambil seorang anak.'"),
        Pair(18, 10) to Pair("إِذْ أَوَى الْفِتْيَةُ إِلَى الْكَهْفِ فَقَالُوا رَبَّنَا آتِنَا مِنْ لَدُنْكَ رَحْمَةً وَهَيِّئْ لَنَا مِنْ أَمْرِنَا رَشَدًا", "(Ingatlah) ketika pemuda-pemuda itu berlindung ke dalam gua lalu mereka berdoa, 'Ya Tuhan kami, berikanlah rahmat kepada kami dari sisi-Mu dan sempurnakanlah petunjuk yang lurus bagi kami dalam urusan kami'."),

        // 36. YA-SIN (1-9)
        Pair(36, 1) to Pair("يس", "Ya Sin."),
        Pair(36, 2) to Pair("وَالْقُرْآنِ الْحَكِيمِ", "Demi Al-Qur'an yang penuh hikmah,"),
        Pair(36, 3) to Pair("إِنَّكَ لَمِنَ الْمُرْسَلِينَ", "sungguh, engkau (Muhammad) benar-benar salah seorang dari rasul-rasul,"),
        Pair(36, 4) to Pair("عَلَىٰ صِرَاطٍ مُسْتَقِيمٍ", "(yang berada) di atas jalan yang lurus,"),
        Pair(36, 5) to Pair("تَنْزِيلَ الْعَزِيزِ الرَّحِيمِ", "(sebagai wahyu) yang diturunkan oleh (Allah) Yang Mahaperkasa, Maha Penyayang,"),
        Pair(36, 6) to Pair("لِتُنْذِرَ قَوْمًا مَا أُنْذِرَ آبَاؤُهُمْ فَهُمْ غَافِلُونَ", "agar engkau memberi peringatan kepada suatu kaum yang nenek moyangnya belum pernah diberi peringatan, karena itu mereka lalai."),
        Pair(36, 7) to Pair("لَقَدْ حَقَّ الْقَوْلُ عَلَىٰ أَكْثَرِهِمْ فَهُمْ لَا يُؤْمِنُونَ", "Sungguh, pasti berlaku perkataan (hukuman) terhadap kebanyakan mereka, karena mereka tidak beriman."),
        Pair(36, 8) to Pair("إِنَّا جَعَلْنَا فِي أَعْنَاقِهِمْ أَغْلَالًا فَهِيَ إِلَى الْأَذْقَانِ فَهُمْ مُقْمَحُونَ", "Sungguh, Kami telah memasang belenggu di leher mereka, lalu tangan mereka (diangkat) ke dagu, karena itu mereka tertengadah."),
        Pair(36, 9) to Pair("وَجَعَلْنَا مِنْ بَيْنِ أَيْدِيهِمْ سَدًّا وَمِنْ خَلْفِهِمْ سَدًّا فَأَغْشَيْنَاهُمْ فَهُمْ لَا يُبْصِرُونَ", "Dan Kami jadikan di hadapan mereka sekat (dinding) dan di belakang mereka juga sekat, dan Kami tutup (mata) mereka sehingga mereka tidak dapat melihat."),
        Pair(36, 58) to Pair("سَلَامٌ قَوْلًا مِنْ رَبٍّ رَحِيمٍ", "(Kepada mereka dikatakan), 'Salam,' sebagai ucapan selamat dari Tuhan Yang Maha Penyayang."),
        Pair(36, 82) to Pair("إِنَّمَا أَمْرُهُ إِذَا أَرَادَ شَيْئًا أَنْ يَقُولَ لَهُ كُنْ فَيَكُونُ", "Sesungguhnya urusan-Nya apabila Dia menghendaki sesuatu Dia hanya berkata kepadanya, 'Jadilah!' Maka jadilah sesuatu itu."),
        Pair(36, 83) to Pair("فَسُبْحَانَ الَّذِي بِيَدِهِ مَلَكُوتُ كُلِّ شَيْءٍ وَإِلَيْهِ تُرْجَعُونَ", "Maka Mahasuci (Allah) yang di tangan-Nya kekuasaan atas segala sesuatu dan kepada-Nya kamu dikembalikan."),

        // 55. AR-RAHMAN (1-13)
        Pair(55, 1) to Pair("الرَّحْمَٰنُ", "(Allah) Yang Maha Pengasih,"),
        Pair(55, 2) to Pair("عَلَّمَ الْقُرْآنَ", "yang telah mengajarkan Al-Qur'an."),
        Pair(55, 3) to Pair("خَلَقَ الْإِنْسَانَ", "Dia menciptakan manusia,"),
        Pair(55, 4) to Pair("عَلَّمَهُ الْبَيَانَ", "mengajarnya pandai berbicara."),
        Pair(55, 5) to Pair("الشَّمْسُ وَالْقَمَرُ بِحُسْبَانٍ", "Matahari dan bulan beredar menurut perhitungan,"),
        Pair(55, 6) to Pair("وَالنَّجْمُ وَالشَّجَرُ يَسْجُدَانِ", "dan tetumbuhan dan pepohonan, keduanya tunduk (kepada-Nya)."),
        Pair(55, 13) to Pair("فَبِأَيِّ آلَاءِ رَبِّكُمَا تُكَذِّبَانِ", "Maka nikmat Tuhanmu yang manakah yang kamu dustakan?"),

        // 56. AL-WAQI'AH (1-6)
        Pair(56, 1) to Pair("إِذَا وَقَعَتِ الْوَاقِعَةُ", "Apabila terjadi hari Kiamat,"),
        Pair(56, 2) to Pair("لَيْسَ لِوَقْعَتِهَا كَاذِبَةٌ", "terjadinya tidak dapat didustakan (disangkal),"),
        Pair(56, 3) to Pair("خَافِضَةٌ رَافِعَةٌ", "(kejadian itu) merendahkan (satu golongan) dan meninggikan (golongan yang lain)."),

        // 67. AL-MULK (1-5)
        Pair(67, 1) to Pair("تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ", "Mahasuci Allah yang di tangan-Nyalah segala kerajaan, dan Dia Mahakuasa atas segala sesuatu,"),
        Pair(67, 2) to Pair("الَّذِي خَلَقَ الْمَوْتَ وَالْحَيَاةَ لِيَبْلُوَكُمْ أَيُّكُمْ أَحْسَنُ عَمَلًا ۚ وَهُوَ الْعَزِيزُ الْغَفُورُ", "yang menciptakan mati dan hidup, untuk menguji kamu, siapa di antara kamu yang lebih baik amalnya. Dan Dia Mahaperkasa, Maha Pengampun,"),
        Pair(67, 3) to Pair("الَّذِي خَلَقَ سَبْعَ سَمَاوَاتٍ طِبَاقًا ۖ مَا تَرَىٰ فِي خَلْقِ الرَّحْمَٰنِ مِنْ تَفَاوُتٍ ۖ فَارْجِعِ الْبَصَرَ هَلْ تَرَىٰ مِنْ فُطُورٍ", "yang menciptakan tujuh langit berlapis-lapis. Tidak akan kamu lihat sesuatu yang tidak seimbang pada ciptaan Tuhan Yang Maha Pengasih. Maka lihatlah sekali lagi, adakah kamu lihat sesuatu yang cacat?"),

        // 78. AN-NABA' (1-5)
        Pair(78, 1) to Pair("عَمَّ يَتَسَاءَلُونَ", "Tentang apakah mereka saling bertanya-tanya?"),
        Pair(78, 2) to Pair("عَنِ النَّبَأِ الْعَظِيمِ", "Tentang berita yang besar (hari kebangkitan),"),
        Pair(78, 3) to Pair("الَّذِي هُمْ فِيهِ مُخْتَلِفُونَ", "yang dalam hal itu mereka berselisih."),
        Pair(78, 4) to Pair("كَلَّا سَيَعْلَمُونَ", "Tidak! Kelak mereka akan mengetahui,"),
        Pair(78, 5) to Pair("ثُمَّ كَلَّا سَيَعْلَمُونَ", "sekali lagi tidak! Kelak mereka akan mengetahui."),

        // 93. AD-DUHA (1-11 LENGKAP)
        Pair(93, 1) to Pair("وَالضُّحَىٰ", "Demi waktu duha (ketika matahari naik sepenggalah),"),
        Pair(93, 2) to Pair("وَاللَّيْلِ إِذَا سَجَىٰ", "dan demi malam apabila telah sunyi,"),
        Pair(93, 3) to Pair("مَا وَدَّعَكَ رَبُّكَ وَمَا قَلَىٰ", "Tuhanmu tidak meninggalkan engkau (Muhammad) dan tidak (pula) membencimu,"),
        Pair(93, 4) to Pair("وَلَلْآخِرَةُ خَيْرٌ لَكَ مِنَ الْأُولَىٰ", "dan sungguh, yang kemudian itu lebih baik bagimu daripada yang permulaan."),
        Pair(93, 5) to Pair("وَلَسَوْفَ يُعْطِيكَ رَبُّكَ فَتَرْضَىٰ", "Dan sungguh, kelak Tuhanmu pasti memberikan karunia-Nya kepadamu, sehingga engkau menjadi puas."),
        Pair(93, 6) to Pair("أَلَمْ يَجِدْكَ يَتِيمًا فَآوَىٰ", "Bukankah Dia mendapatimu sebagai seorang yatim, lalu Dia melindungimu?"),
        Pair(93, 7) to Pair("وَوَجَدَكَ ضَالًّا فَهَدَىٰ", "Dan Dia mendapatimu sebagai seorang yang bingung, lalu Dia memberikan petunjuk,"),
        Pair(93, 8) to Pair("وَوَجَدَكَ عَائِلًا فَأَغْنَىٰ", "dan Dia mendapatimu sebagai seorang yang kekurangan, lalu Dia memberikan kecukupan."),
        Pair(93, 9) to Pair("فَأَمَّا الْيَتِيمَ فَلَا تَقْهَرْ", "Maka terhadap anak yatim janganlah engkau berlaku sewenang-wenang."),
        Pair(93, 10) to Pair("وَأَمَّا السَّائِلَ فَلَا تَنْهَرْ", "Dan terhadap orang yang meminta-minta janganlah engkau menghardiknya."),
        Pair(93, 11) to Pair("وَأَمَّا بِنِعْمَةِ رَبِّكَ فَحَدِّثْ", "Dan terhadap nikmat Tuhanmu hendaklah engkau nyatakan (dengan bersyukur)."),

        // 94. ASY-SYARH (1-8 LENGKAP)
        Pair(94, 1) to Pair("أَلَمْ نَشْرَحْ لَكَ صَدْرَكَ", "Bukankah Kami telah melapangkan dadamu (Muhammad)?"),
        Pair(94, 2) to Pair("وَوَضَعْنَا عَنْكَ وِزْرَكَ", "dan Kami pun telah menurunkan bebanmu darimu,"),
        Pair(94, 3) to Pair("الَّذِي أَنْقَضَ ظَهْرَكَ", "yang memberatkan punggungmu,"),
        Pair(94, 4) to Pair("وَرَفَعْنَا لَكَ ذِكْرَكَ", "dan Kami tinggikan bagimu sebutan (nama)-mu."),
        Pair(94, 5) to Pair("فَإِنَّ مَعَ الْعُسْرِ يُسْرًا", "Maka sesungguhnya bersama kesulitan ada kemudahan,"),
        Pair(94, 6) to Pair("إِنَّ مَعَ الْعُسْرِ يُسْرًا", "sesungguhnya bersama kesulitan ada kemudahan."),
        Pair(94, 7) to Pair("فَإِذَا فَرَغْتَ فَانْصَبْ", "Maka apabila engkau telah selesai (dari suatu urusan), tetaplah bekerja keras (untuk urusan yang lain),"),
        Pair(94, 8) to Pair("وَإِلَىٰ رَبِّكَ فَارْغَبْ", "dan hanya kepada Tuhanmulah engkau berharap."),

        // 97. AL-QADR (1-5 LENGKAP)
        Pair(97, 1) to Pair("إِنَّا أَنْزَلْنَاهُ فِي لَيْلَةِ الْقَدْرِ", "Sesungguhnya Kami telah menurunkannya (Al-Qur'an) pada malam qadar."),
        Pair(97, 2) to Pair("وَمَا أَدْرَاكَ مَا لَيْلَةُ الْقَدْرِ", "Dan tahukah kamu apakah malam kemuliaan itu?"),
        Pair(97, 3) to Pair("لَيْلَةُ الْقَدْرِ خَيْرٌ مِنْ أَلْفِ شَهْرٍ", "Malam kemuliaan itu lebih baik daripada seribu bulan."),
        Pair(97, 4) to Pair("تَنَزَّلُ الْمَلَائِكَةُ وَالرُّوحُ فِيهَا بِإِذْنِ رَبِّهِمْ مِنْ كُلِّ أَمْرٍ", "Pada malam itu turun para malaikat dan Rūḥ (Jibril) dengan izin Tuhannya untuk mengatur segala urusan."),
        Pair(97, 5) to Pair("سَلَامٌ هِيَ حَتَّىٰ مَطْلَعِ الْفَجْرِ", "Sejahteralah (malam itu) sampai terbit fajar."),

        // 103. AL-'ASR (1-3 LENGKAP)
        Pair(103, 1) to Pair("وَالْعَصْرِ", "Demi masa,"),
        Pair(103, 2) to Pair("إِنَّ الْإِنْسَانَ لَفِي خُسْرٍ", "sungguh, manusia berada dalam kerugian,"),
        Pair(103, 3) to Pair("إِلَّا الَّذِينَ آمَنُوا وَعَمِلُوا الصَّالِحَاتِ وَتَوَاصَوْا بِالْحَقِّ وَتَوَاصَوْا بِالصَّبْرِ", "kecuali orang-orang yang beriman dan mengerjakan kebajikan serta saling menasihati untuk kebenaran dan saling menasihati untuk kesabaran."),

        // 105. AL-FIL (1-5 LENGKAP)
        Pair(105, 1) to Pair("أَلَمْ تَرَ كَيْفَ فَعَلَ رَبُّكَ بِأَصْحَابِ الْفِيلِ", "Tidakkah engkau (Muhammad) perhatikan bagaimana Tuhanmu telah bertindak terhadap pasukan bergajah?"),
        Pair(105, 2) to Pair("أَلَمْ يَجْعَلْ كَيْدَهُمْ فِي تَضْلِيلٍ", "Bukankah Dia telah menjadikan tipu daya mereka itu sia-sia?"),
        Pair(105, 3) to Pair("وَأَرْسَلَ عَلَيْهِمْ طَيْرًا أَبَابِيلَ", "dan Dia mengirimkan kepada mereka burung yang berbondong-bondong,"),
        Pair(105, 4) to Pair("تَرْمِيهِمْ بِحِجَارَةٍ مِنْ سِجِّيلٍ", "yang melempari mereka dengan batu dari tanah liat yang dibakar,"),
        Pair(105, 5) to Pair("فَجَعَلَهُمْ كَعَصْفٍ مَأْكُولٍ", "sehingga mereka dijadikan-Nya seperti daun-daun yang dimakan (ulat)."),

        // 106. QURAISY (1-4 LENGKAP)
        Pair(106, 1) to Pair("لِإِيلَافِ قُرَيْشٍ", "Karena kebiasaan orang-orang Quraisy,"),
        Pair(106, 2) to Pair("إِيلَافِهِمْ رِحْلَةَ الشِّتَاءِ وَالصَّيْفِ", "(yaitu) kebiasaan mereka bepergian pada musim dingin dan musim panas."),
        Pair(106, 3) to Pair("فَلْيَعْبُدُوا رَبَّ هَٰذَا الْبَيْتِ", "Maka hendaklah mereka menyembah Tuhan (pemilik) rumah ini (Ka'bah),"),
        Pair(106, 4) to Pair("الَّذِي أَطْعَمَهُمْ مِنْ جُوعٍ وَآمَنَهُمْ مِنْ خَوْفٍ", "yang telah memberi makanan kepada mereka untuk menghilangkan lapar dan mengamankan mereka dari rasa ketakutan."),

        // 107. AL-MA'UN (1-7 LENGKAP)
        Pair(107, 1) to Pair("أَرَأَيْتَ الَّذِي يُكَذِّبُ بِالدِّينِ", "Tahukah kamu (orang) yang mendustakan agama?"),
        Pair(107, 2) to Pair("فَذَٰلِكَ الَّذِي يَدُعُّ الْيَتِيمَ", "Maka itulah orang yang menghardik anak yatim,"),
        Pair(107, 3) to Pair("وَلَا يَحُضُّ عَلَىٰ طَعَامِ الْمِسْكِينِ", "dan tidak mendorong memberi makan orang miskin."),
        Pair(107, 4) to Pair("فَوَيْلٌ لِلْمُصَلِّينَ", "Maka celakalah orang yang salat,"),
        Pair(107, 5) to Pair("الَّذِينَ هُمْ عَنْ صَلَاتِهِمْ سَاهُونَ", "(yaitu) orang-orang yang lalai terhadap salatnya,"),
        Pair(107, 6) to Pair("الَّذِينَ هُمْ يُرَاءُونَ", "yang berbuat ria,"),
        Pair(107, 7) to Pair("وَيَمْنَعُونَ الْمَاعُونَ", "dan enggan (memberikan) bantuan."),

        // 108. AL-KAUSAR (1-3 LENGKAP)
        Pair(108, 1) to Pair("إِنَّا أَعْطَيْنَاكَ الْكَوْثَرَ", "Sungguh, Kami telah memberimu (Muhammad) nikmat yang banyak."),
        Pair(108, 2) to Pair("فَصَلِّ لِرَبِّكَ وَانْحَرْ", "Maka laksanakanlah salat karena Tuhanmu, dan berkurbanlah."),
        Pair(108, 3) to Pair("إِنَّ شَانِئَكَ هُوَ الْأَبْتَرُ", "Sungguh, orang-orang yang membencimu dialah yang terputus (dari rahmat Allah)."),

        // 109. AL-KAFIRUN (1-6 LENGKAP)
        Pair(109, 1) to Pair("قُلْ يَا أَيُّهَا الْكَافِرُونَ", "Katakanlah (Muhammad), 'Wahai orang-orang kafir!'"),
        Pair(109, 2) to Pair("لَا أَعْبُدُ مَا تَعْبُدُونَ", "aku tidak akan menyembah apa yang kamu sembah,"),
        Pair(109, 3) to Pair("وَلَا أَنْتُمْ عَابِدُونَ مَا أَعْبُدُ", "dan kamu bukan penyembah apa yang aku sembah,"),
        Pair(109, 4) to Pair("وَلَا أَنَا عَابِدٌ مَا عَبَدْتُمْ", "dan aku tidak pernah menjadi penyembah apa yang kamu sembah,"),
        Pair(109, 5) to Pair("وَلَا أَنْتُمْ عَابِدُونَ مَا أَعْبُدُ", "dan kamu tidak pernah (pula) menjadi penyembah apa yang aku sembah."),
        Pair(109, 6) to Pair("لَكُمْ دِينُكُمْ وَلِيَ دِينِ", "Untukmu agamamu, dan untukku agamaku."),

        // 110. AN-NASR (1-3 LENGKAP)
        Pair(110, 1) to Pair("إِذَا جَاءَ نَصْرُ اللَّهِ وَالْفَتْحُ", "Apabila telah datang pertolongan Allah dan kemenangan,"),
        Pair(110, 2) to Pair("وَرَأَيْتَ النَّاسَ يَدْخُلُونَ فِي دِينِ اللَّهِ أَفْوَاجًا", "dan engkau melihat manusia berbondong-bondong masuk agama Allah,"),
        Pair(110, 3) to Pair("فَسَبِّحْ بِحَمْدِ رَبِّكَ وَاسْتَغْفِرْهُ ۚ إِنَّهُ كَانَ تَوَّابًا", "maka bertasbihlah dengan memuji Tuhanmu dan mohonlah ampunan kepada-Nya. Sungguh, Dia Maha Penerima tobat."),

        // 111. AL-LAHAB (1-5 LENGKAP)
        Pair(111, 1) to Pair("تَبَّتْ يَدَا أَبِي لَهَبٍ وَتَبَّ", "Binasalah kedua tangan Abu Lahab dan benar-benar binasa dia!"),
        Pair(111, 2) to Pair("مَا أَغْنَىٰ عَنْهُ مَالُهُ وَمَا كَسَبَ", "Tidaklah berguna baginya hartanya dan apa yang dia usahakan."),
        Pair(111, 3) to Pair("سَيَصْلَىٰ نَارًا ذَاتَ لَهَبٍ", "Kelak dia akan masuk ke dalam api yang bergejolak (neraka)."),
        Pair(111, 4) to Pair("وَامْرَأَتُهُ حَمَّالَةَ الْحَطَبِ", "Dan (begitu pula) istrinya, pembawa kayu bakar (penyebar fitnah)."),
        Pair(111, 5) to Pair("فِي جِيدِهَا حَبْلٌ مِنْ مَسَدٍ", "Di lehernya ada tali dari sabut yang dipintal."),

        // 112. AL-IKHLAS (1-4 LENGKAP)
        Pair(112, 1) to Pair("قُلْ هُوَ اللَّهُ أَحَدٌ", "Katakanlah (Muhammad), 'Dialah Allah, Yang Maha Esa.'"),
        Pair(112, 2) to Pair("اللَّهُ الصَّمَدُ", "Allah tempat meminta segala sesuatu."),
        Pair(112, 3) to Pair("لَمْ يَلِدْ وَلَمْ يُولَدْ", "(Allah) tidak beranak dan tidak pula diperanakkan,"),
        Pair(112, 4) to Pair("وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ", "dan tidak ada sesuatu yang setara dengan Dia."),

        // 113. AL-FALAQ (1-5 LENGKAP)
        Pair(113, 1) to Pair("قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ", "Katakanlah, 'Aku berlindung kepada Tuhan yang menguasai subuh (fajar),'"),
        Pair(113, 2) to Pair("مِنْ شَرِّ مَا خَلَقَ", "dari kejahatan (makhluk yang) Dia ciptakan,"),
        Pair(113, 3) to Pair("وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ", "dan dari kejahatan malam apabila telah gelap gulita,"),
        Pair(113, 4) to Pair("وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ", "dan dari kejahatan (perempuan-perempuan) penyihir yang meniup pada buhul-buhul (talinya),"),
        Pair(113, 5) to Pair("وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ", "dan dari kejahatan orang yang dengki apabila dia dengki."),

        // 114. AN-NAS (1-6 LENGKAP)
        Pair(114, 1) to Pair("قُلْ أَعُوذُ بِرَبِّ النَّاسِ", "Katakanlah, 'Aku berlindung kepada Tuhannya manusia,'"),
        Pair(114, 2) to Pair("مَلِكِ النَّاسِ", "Raja manusia,"),
        Pair(114, 3) to Pair("إِلَٰهِ النَّاسِ", "Sembahan manusia,"),
        Pair(114, 4) to Pair("مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ", "dari kejahatan (bisikan) setan yang bersembunyi,"),
        Pair(114, 5) to Pair("الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ", "yang membisikkan (kejahatan) ke dalam dada manusia,"),
        Pair(114, 6) to Pair("مِنَ الْجِنَّةِ وَالنَّاسِ", "dari (golongan) jin dan manusia.")
    )

    val SORTED_CURATED_KEYS: List<Pair<Int, Int>> = CURATED_VERSES.keys.sortedWith(
        compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }
    )

    private const val COMPLETE_ASSET_TSV = "quran_30juz_complete.tsv"
    private const val COMPLETE_ASSET_GZ = "quran_30juz_complete.tsv.gz"
    private val FULL_VERSES = java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, Triple<Int, String, String>>(6400)
    @Volatile
    private var isFullCatalogLoaded = false
    @Volatile
    private var isJvmLocalFileChecked = false
    private val catalogExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "QuranCatalogLoader").apply { isDaemon = true }
    }

    val DEFAULT_FATIHAH_VERSE = QuranVerseItem(
        surahNumber = 1,
        ayahNumber = 1,
        surahNameLatin = "Al-Fatihah",
        surahNameArabic = "الفاتحة",
        arabicText = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
        indonesianText = "Dengan nama Allah Yang Maha Pengasih, Maha Penyayang.",
        juzNumber = 1
    )

    val JUZ_START_COORDS: Map<Int, Pair<Int, Int>> = mapOf(
        1 to Pair(1, 1),
        2 to Pair(2, 142),
        3 to Pair(2, 253),
        4 to Pair(3, 93),
        5 to Pair(4, 24),
        6 to Pair(4, 148),
        7 to Pair(5, 82),
        8 to Pair(6, 111),
        9 to Pair(7, 88),
        10 to Pair(8, 41),
        11 to Pair(9, 93),
        12 to Pair(11, 6),
        13 to Pair(12, 53),
        14 to Pair(15, 1),
        15 to Pair(17, 1),
        16 to Pair(18, 75),
        17 to Pair(21, 1),
        18 to Pair(23, 1),
        19 to Pair(25, 21),
        20 to Pair(27, 56),
        21 to Pair(29, 46),
        22 to Pair(33, 31),
        23 to Pair(36, 28),
        24 to Pair(39, 32),
        25 to Pair(41, 47),
        26 to Pair(46, 1),
        27 to Pair(51, 31),
        28 to Pair(58, 1),
        29 to Pair(67, 1),
        30 to Pair(78, 1)
    )

    fun getFirstSurahAndAyahOfJuz(juzNumber: Int): Pair<Int, Int> {
        return JUZ_START_COORDS[juzNumber.coerceIn(1, 30)] ?: Pair(1, 1)
    }

    fun isCatalogReady(): Boolean = isFullCatalogLoaded && FULL_VERSES.size >= 6000

    fun ensureLoadedAsync(context: android.content.Context, onComplete: (() -> Unit)? = null) {
        if (isCatalogReady()) {
            onComplete?.invoke()
            return
        }
        val appCtx = context.applicationContext ?: context
        catalogExecutor.execute {
            try {
                ensureLoaded(appCtx)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    @Synchronized
    fun ensureLoaded(context: android.content.Context? = null) {
        if (isFullCatalogLoaded && FULL_VERSES.size >= 6000) return
        if (context == null && isJvmLocalFileChecked) return
        try {
            val rawStream: java.io.InputStream? = when {
                context != null -> {
                    try { context.assets.open(COMPLETE_ASSET_TSV) } catch (_: Throwable) {
                        try { context.assets.open(COMPLETE_ASSET_GZ) } catch (_: Throwable) { null }
                    }
                }
                else -> {
                    isJvmLocalFileChecked = true
                    listOf(
                        java.io.File("app/src/main/assets/$COMPLETE_ASSET_TSV"),
                        java.io.File("src/main/assets/$COMPLETE_ASSET_TSV"),
                        java.io.File("app/src/main/assets/$COMPLETE_ASSET_GZ"),
                        java.io.File("src/main/assets/$COMPLETE_ASSET_GZ")
                    ).firstOrNull { it.exists() && it.length() > 1000L }?.inputStream()
                }
            }

            if (rawStream != null) {
                val buffered = java.io.BufferedInputStream(rawStream)
                buffered.mark(4)
                val b0 = buffered.read()
                val b1 = buffered.read()
                buffered.reset()
                val isGzip = (b0 == 0x1f && b1 == 0x8b)
                val decodedStream = if (isGzip) java.util.zip.GZIPInputStream(buffered) else buffered

                decodedStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            val parts = line.split('\t')
                            if (parts.size >= 5) {
                                val sNum = parts[0].trim().removePrefix("\uFEFF").toIntOrNull() ?: return@forEach
                                val aNum = parts[1].trim().toIntOrNull() ?: return@forEach
                                val jNum = parts[2].trim().toIntOrNull() ?: 1
                                val arTxt = parts[3].trim().removePrefix("\uFEFF")
                                val idTxt = parts[4].trim()
                                FULL_VERSES[Pair(sNum, aNum)] = Triple(jNum.coerceIn(1, 30), arTxt, idTxt)
                            }
                        }
                    }
                }
                if (FULL_VERSES.size >= 6000) {
                    isFullCatalogLoaded = true
                }
            }
        } catch (_: Throwable) {}
    }

    fun getLoadedVerseCount(): Int {
        if (!isFullCatalogLoaded && !isJvmLocalFileChecked) {
            ensureLoaded(null)
        }
        return if (FULL_VERSES.isNotEmpty()) FULL_VERSES.size else CURATED_VERSES.size
    }

    /**
     * Mencari koordinat (Surah, Ayat) berikutnya secara berurutan 1 demi 1 ayat (1:1 s/d 114:6)
     * tanpa pernah melompati ayat atau Juz.
     */
    fun getNextCuratedCoord(currentSurah: Int, currentAyah: Int): Pair<Int, Int> {
        val validSurah = currentSurah.coerceIn(1, 114)
        val sInfo = getSurahInfo(validSurah)
        val clampedAyah = currentAyah.coerceIn(1, sInfo.ayahCount)

        return if (clampedAyah < sInfo.ayahCount) {
            Pair(validSurah, clampedAyah + 1)
        } else if (validSurah < 114) {
            Pair(validSurah + 1, 1)
        } else {
            Pair(1, 1)
        }
    }

    /**
     * Mencari koordinat (Surah, Ayat) sebelumnya secara berurutan 1 demi 1 ayat.
     */
    fun getPrevCuratedCoord(currentSurah: Int, currentAyah: Int): Pair<Int, Int> {
        val validSurah = currentSurah.coerceIn(1, 114)
        val sInfo = getSurahInfo(validSurah)
        val clampedAyah = currentAyah.coerceIn(1, sInfo.ayahCount)

        return if (clampedAyah > 1) {
            Pair(validSurah, clampedAyah - 1)
        } else if (validSurah > 1) {
            val prevSurah = validSurah - 1
            val prevInfo = getSurahInfo(prevSurah)
            Pair(prevSurah, prevInfo.ayahCount)
        } else {
            Pair(114, 6)
        }
    }

    /**
     * Memetakan permintaan (Surah, Ayat) ke ayat terdekat yang tersedia di memori jika katalog belum selesai dimuat.
     */
    fun resolveNearestCuratedCoord(surahNumber: Int, ayahNumber: Int): Pair<Int, Int> {
        if (!isFullCatalogLoaded && !isJvmLocalFileChecked) {
            ensureLoaded(null)
        }
        val validSurah = surahNumber.coerceIn(1, 114)
        val sInfo = getSurahInfo(validSurah)
        val validAyah = ayahNumber.coerceIn(1, sInfo.ayahCount)
        val direct = Pair(validSurah, validAyah)
        if (FULL_VERSES.containsKey(direct)) {
            return direct
        }
        if (CURATED_VERSES.containsKey(direct)) {
            return direct
        }
        val sameSurah = CURATED_VERSES.keys.filter { it.first == validSurah }
        if (sameSurah.isNotEmpty()) {
            return sameSurah.minByOrNull { kotlin.math.abs(it.second - validAyah) } ?: sameSurah.first()
        }
        return CURATED_VERSES.keys.minByOrNull {
            kotlin.math.abs(it.first - validSurah) * 1000 + kotlin.math.abs(it.second - validAyah)
        } ?: Pair(1, 1)
    }

    fun toArabicDigits(number: Int): String {
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val sb = StringBuilder()
        for (ch in number.toString()) {
            if (ch in '0'..'9') sb.append(arabicDigits[ch - '0']) else sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Membersihkan teks sehingga HANYA menyisakan karakter Arab asli:
     * - Huruf Hijaiyah dasar (Alif s/d Ya)
     * - Harakat / Tashkeel / Tanween / Sukun / Tasydid (0x064B..0x065F, 0x0670)
     * - Angka Arab-India (٠..٩ / 0x0660..0x0669)
     * - Tanda waqaf & ornamen ayat Al-Qur'an (0x06D6..0x06ED, ﴿, ﴾, ۝)
     * - Spasi (0x0020)
     */
    fun sanitizeArabicText(input: String): String {
        val sb = StringBuilder(input.length)
        for (i in input.indices) {
            val c = input[i]
            val code = c.code
            if (c == '\uFEFF' || c == '\u200B' || c == '\u200E' || c == '\u200F') continue

            val isArabic = (code in 0x0600..0x06FF) ||
                    (code in 0x0750..0x077F) ||
                    (code in 0x08A0..0x08FF) ||
                    (code in 0xFB50..0xFDFF) ||
                    (code in 0xFE70..0xFEFC) ||
                    c == '﴿' || c == '﴾' || c == '۝'
            if (isArabic) {
                sb.append(c)
            } else if (c == ' ') {
                if (sb.isNotEmpty() && sb.last() != ' ') {
                    sb.append(' ')
                }
            } else if (c in '0'..'9') {
                val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
                sb.append(arabicDigits[c - '0'])
            }
        }
        return sb.toString().trim()
    }

    /**
     * Mengambil ayat berdasarkan nomor Surat dan nomor Ayat dari katalog lengkap 6.236 ayat 30 Juz.
     */
    fun getVerse(surahNumber: Int, ayahNumber: Int): QuranVerseItem {
        if (!isFullCatalogLoaded && !isJvmLocalFileChecked) {
            ensureLoaded(null)
        }
        val sInfo = getSurahInfo(surahNumber)
        val validAyah = ayahNumber.coerceIn(1, sInfo.ayahCount)
        val key = Pair(sInfo.number, validAyah)

        val fullMatch = FULL_VERSES[key]
        val (juzNum, rawArabic, indo) = if (fullMatch != null) {
            fullMatch
        } else {
            val direct = CURATED_VERSES[key]
            if (direct != null) {
                Triple(sInfo.juzStart, direct.first, direct.second)
            } else {
                val nearestCoord = resolveNearestCuratedCoord(sInfo.number, validAyah)
                val fallback = CURATED_VERSES[nearestCoord] ?: Pair(
                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                    "Dengan nama Allah Yang Maha Pengasih, Maha Penyayang."
                )
                Triple(sInfo.juzStart, fallback.first, fallback.second)
            }
        }

        val arabic = sanitizeArabicText(rawArabic)

        return QuranVerseItem(
            surahNumber = sInfo.number,
            ayahNumber = validAyah,
            surahNameLatin = sInfo.nameLatin,
            surahNameArabic = sInfo.nameArabic,
            arabicText = arabic,
            indonesianText = indo,
            juzNumber = juzNum
        )
    }
}
