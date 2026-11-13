package com.tajasb.quran

import android.content.Context
import android.content.SharedPreferences
import com.tajasb.notif.OverlayBannerBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuranKhatamState(
    val isEnabled: Boolean = false,
    val currentSurahNumber: Int = 1,
    val currentAyahNumber: Int = 1,
    val revealedCharCount: Int = 0,
    val totalCharsInAyah: Int = 0,
    val currentVerse: QuranVerseItem = QuranTextCatalog.DEFAULT_FATIHAH_VERSE,
    val revealedArabicText: String = "",
    val unrevealedArabicText: String = "",
    val lastContributorName: String = "Menunggu Penonton",
    val lastActionDescription: String = "Tap Like / Chat Doa / Gift untuk Menulis",
    val totalLettersWrittenSession: Long = 0L,
    val totalAyahsCompletedSession: Int = 0,
    val isAyahCompletedFlash: Boolean = false,
    val likesPerLetter: Int = 1,
    val chatRewardLetters: Int = 4,
    val keywordBonusLetters: Int = 10,
    val memberRewardLetters: Int = 1,
    val followRewardLetters: Int = 5,
    val shareRewardLetters: Int = 5,
    val coinsPerLetter: Int = 2,
    val customKeywords: String = "aamiin, amin, khatam, alhamdulillah, masyaallah, barakallah, bismillah, subhanallah",
    val giftCompletesAyah: Boolean = false,
    val giftTieringEnabled: Boolean = true,
    val celebrationDurationMs: Long = 3000L,
    // Posisi & Transformasi Mandiri Kartu Khatam Al-Qur'an (Default: Tengah Frame & Rata Kiri-Kanan Proporsional)
    val posXpct: Float = 0.50f,
    val posYpct: Float = 0.50f,
    val overlayRotation: Int = 0,
    val overlayScale: Float = 1.0f,
    val overlayMirrored: Boolean = false,
    // Penyesuaian Tampilan Ayat & Font & Garis (Default: Rata Full Kiri-Kanan Frame & Memanjang ke Bawah)
    val arabicFontSizeSp: Float = 28.0f,
    val latinFontSizeSp: Float = 12.0f,
    val maxArabicLines: Int = 0, // 0 = Auto (Fleksibel Alami Sesuai Panjang Ayat, Tidak Kaku di 4 Baris)
    val cardWidthPct: Float = 0.98f,
    val cardPaddingV: Int = 16,
    val showTranslation: Boolean = true,
    val showNotifUnderAyah: Boolean = true,
    // Prioritas Isi Doa di Susunan Paling Atas Bawah Kartu Khatam
    val lastPrayerSender: String = "",
    val lastPrayerText: String = "",
    val lastPrayerAtMs: Long = 0L,
    // Overlay Sisa Huruf, Ayat, Surat, Juz (Default: Tengah Atas & Rata Kiri-Kanan Proporsional)
    val enableRemainingStatsOverlay: Boolean = false,
    val remainingStatsPosXpct: Float = 0.50f,
    val remainingStatsPosYpct: Float = 0.12f,
    val remainingStatsScale: Float = 1.0f,
    val remainingStatsFontSizeSp: Float = 14.0f,
    // Mode Per-Ayat 3-Zona Aman (Atas Redup N-1, Tengah Fokus Aman N, Bawah Antre N+1) & Auto-Play Suara Imam
    val perAyahThreeZoneMode: Boolean = true,
    val imamAutoPlayEnabled: Boolean = false,
    val selectedImamId: String = "alafasy",
    val scrollTransitionPhase: Float = 0f
)

/**
 * InteractiveQuranKhatamEngine:
 * Mesin utama Khatam Al-Qur'an 30 Juz Interaktif yang dioptimalkan dengan:
 * - Mode Per-Ayat 3-Zona Aman (Atas Ayat N-1 redup, Tengah Ayat N fokus emas, Bawah Ayat N+1 antre)
 * - Auto-Play Suara Imam Per-Ayat (SSSAAA.mp3) + Transisi Gulir Naik Halus (Cubic Ease-In-Out)
 * - Pre-indexed Non-Space Grapheme Clusters (O(1) substring slicing, bebas buang Like di spasi/waqaf)
 * - Integrasi Kontribusi Penonton Termux (Doa, Saweran Gift, Tap Like, Leaderboard 4 Kategori)
 */
class InteractiveQuranKhatamEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "QuranKhatamEngine"
        private const val PREFS_NAME = "quran_khatam_storage_v1"
        private const val BROADCAST_THROTTLE_MS = 150L
        private const val FAST_ANIM_THROTTLE_MS = 28L
        private const val PERSIST_DEBOUNCE_MS = 2500L

        /**
         * Menghitung kurva ease-in-out kubik (0.0f..1.0f) agar pergerakan ayat naik dari Zona Bawah
         * ke Zona Aman Tengah dan Zona Atas mengalir mulus tanpa patah-patah.
         */
        fun computeSmoothScrollEasing(t: Float): Float {
            val clamped = t.coerceIn(0f, 1f)
            return if (clamped < 0.5f) {
                4f * clamped * clamped * clamped
            } else {
                val f = -2f * clamped + 2f
                1f - (f * f * f) / 2f
            }
        }

        @Volatile
        private var instance: InteractiveQuranKhatamEngine? = null

        fun peekInstance(): InteractiveQuranKhatamEngine? = instance

        fun getInstance(context: Context): InteractiveQuranKhatamEngine {
            return instance ?: synchronized(this) {
                instance ?: InteractiveQuranKhatamEngine(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Memecah teks Arab menjadi daftar grapheme cluster huruf nyata yang aman dari harakat terpotong
         * dan menggabungkan spasi/tanda waqaf ke dalam cluster huruf di depannya.
         * Dengan demikian, setiap 1 ketukan Like selalu menyalakan 1 huruf Hijaiyah nyata di layar
         * (tidak ada ketukan yang terbuang sia-sia pada karakter spasi kosong).
         */
        fun splitArabicGraphemes(text: String): List<String> {
            val sanitized = QuranTextCatalog.sanitizeArabicText(text)
            if (sanitized.isEmpty()) return emptyList()

            val list = mutableListOf<String>()
            val sb = java.lang.StringBuilder()
            var hasBaseLetterInCurrent = false

            for (i in sanitized.indices) {
                val c = sanitized[i]
                val code = c.code
                val type = Character.getType(c)
                val isCombiningOrHarakat = (type == Character.NON_SPACING_MARK.toInt() ||
                        type == Character.COMBINING_SPACING_MARK.toInt() ||
                        (code in 0x064B..0x065F) ||
                        code == 0x0670 ||
                        code == 0x0640) // Tatweel
                val isSpaceOrWaqafOrOrnament = (c == ' ' ||
                        (code in 0x06D6..0x06ED) ||
                        (code in 0x0660..0x0669) ||
                        c == '﴿' || c == '﴾' || c == '۝')

                if (!hasBaseLetterInCurrent) {
                    sb.append(c)
                    if (!isCombiningOrHarakat && !isSpaceOrWaqafOrOrnament) {
                        hasBaseLetterInCurrent = true
                    }
                } else if (isCombiningOrHarakat || isSpaceOrWaqafOrOrnament) {
                    // Gabungkan harakat, spasi, dan tanda waqaf ke huruf dasar sebelumnya
                    sb.append(c)
                } else {
                    // Huruf konsonan baru dimulai -> simpan cluster sebelumnya
                    list.add(sb.toString())
                    sb.setLength(0)
                    sb.append(c)
                    hasBaseLetterInCurrent = true
                }
            }
            if (sb.isNotEmpty()) {
                if (hasBaseLetterInCurrent || list.isEmpty()) {
                    list.add(sb.toString())
                } else {
                    val lastIdx = list.lastIndex
                    list[lastIdx] = list[lastIdx] + sb.toString()
                }
            }
            return list
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val perAyahAudioPlayer = QuranPerAyahAudioPlayer(context)

    private val _state: MutableStateFlow<QuranKhatamState>
    val state: StateFlow<QuranKhatamState>

    private var currentGraphemes: List<String> = emptyList()
    private var currentFullText: String = ""
    private var currentPrefixOffsets: IntArray = IntArray(1)

    @Volatile
    private var lastBroadcastTimeMs: Long = 0L
    @Volatile
    private var lastAyahCompletedAtMs: Long = 0L
    @Volatile
    private var pendingLikeAccumulator: Int = 0
    @Volatile
    private var retainedArabicText: String = ""
    @Volatile
    private var hasCompletedAnyAyahInSession: Boolean = false
    private var pendingBroadcastJob: Job? = null
    private var pendingPersistJob: Job? = null
    private var transitionAnimJob: Job? = null

    init {
        _state = MutableStateFlow(QuranKhatamState())
        state = _state.asStateFlow()
        loadPersistedState()
        QuranTextCatalog.ensureLoadedAsync(context) {
            refreshCurrentVerseFromCatalog()
        }
    }

    @Synchronized
    private fun refreshCurrentVerseFromCatalog() {
        val s = _state.value
        val updatedVerse = QuranTextCatalog.getVerse(s.currentSurahNumber, s.currentAyahNumber)
        if (updatedVerse.arabicText != s.currentVerse.arabicText || updatedVerse.juzNumber != s.currentVerse.juzNumber) {
            indexVerseGraphemes(updatedVerse.arabicText)
            val safeRevealed = s.revealedCharCount.coerceIn(0, currentGraphemes.size)
            val (rev, unrev) = sliceRevealedAndUnrevealed(safeRevealed)
            _state.value = s.copy(
                currentVerse = updatedVerse,
                revealedCharCount = safeRevealed,
                totalCharsInAyah = currentGraphemes.size,
                revealedArabicText = rev,
                unrevealedArabicText = unrev
            )
            if (s.isEnabled) {
                scheduleBroadcastToOverlay(immediate = true)
            }
        }
    }

    private fun indexVerseGraphemes(arabicText: String) {
        val graphemes = splitArabicGraphemes(arabicText)
        currentGraphemes = graphemes
        val full = graphemes.joinToString("")
        currentFullText = full
        val offsets = IntArray(graphemes.size + 1)
        var acc = 0
        for (i in graphemes.indices) {
            acc += graphemes[i].length
            offsets[i + 1] = acc
        }
        currentPrefixOffsets = offsets
    }

    private fun sliceRevealedAndUnrevealed(revealedCount: Int): Pair<String, String> {
        val safeCount = revealedCount.coerceIn(0, currentGraphemes.size)
        val splitIdx = currentPrefixOffsets.getOrElse(safeCount) { currentFullText.length }
            .coerceIn(0, currentFullText.length)
        return Pair(
            currentFullText.substring(0, splitIdx),
            currentFullText.substring(splitIdx)
        )
    }

    private fun loadPersistedState() {
        if (!prefs.getBoolean("key_default_all_games_off_v1", false)) {
            prefs.edit()
                .putBoolean("is_enabled", false)
                .putBoolean("key_default_all_games_off_v1", true)
                .apply()
        }

        if (!prefs.getBoolean("key_quran_frame_proportional_v2", false)) {
            prefs.edit()
                .putFloat("pos_x_pct", 0.50f)
                .putFloat("pos_y_pct", 0.50f)
                .putInt("overlay_rotation", 0)
                .putFloat("overlay_scale", 1.0f)
                .putBoolean("overlay_mirrored", false)
                .putFloat("arabic_font_size_sp", 28.0f)
                .putFloat("card_width_pct", 0.98f)
                .putInt("card_padding_v", 16)
                .putFloat("rem_stats_pos_x", 0.50f)
                .putFloat("rem_stats_pos_y", 0.12f)
                .putFloat("rem_stats_scale", 1.0f)
                .putFloat("rem_stats_font_size", 14.0f)
                .putBoolean("key_quran_frame_proportional_v2", true)
                .apply()
        }

        if (!prefs.getBoolean("key_quran_30juz_tsv_uncompressed_fix_v2", false)) {
            val savedSurah = prefs.getInt("current_surah", 1)
            val savedAyah = prefs.getInt("current_ayah", 1)
            val edit = prefs.edit()
                .putInt("max_arabic_lines", 0)
                .putBoolean("gift_completes_ayah", false)
                .putBoolean("key_quran_30juz_sequential_fix_v1", true)
                .putBoolean("key_quran_30juz_tsv_uncompressed_fix_v2", true)
            // Kembalikan progres yang sebelumnya sempat melompat akibat katalog parsial lama
            if ((savedSurah == 2 && savedAyah >= 255) || savedSurah >= 18) {
                edit.putInt("current_surah", 1)
                edit.putInt("current_ayah", 1)
                edit.putInt("revealed_chars", 0)
            }
            edit.apply()
        }

        if (!prefs.getBoolean("key_quran_consistent_4lines_v1", false)) {
            val curLines = prefs.getInt("max_arabic_lines", 0)
            val edit = prefs.edit().putBoolean("key_quran_consistent_4lines_v1", true)
            if (curLines <= 0) {
                edit.putInt("max_arabic_lines", 4)
            }
            edit.apply()
        }

        val enabled = prefs.getBoolean("is_enabled", false)
        val rawSurah = prefs.getInt("current_surah", 1).coerceIn(1, 114)
        val rawAyah = prefs.getInt("current_ayah", 1)
        val (surah, ayah) = QuranTextCatalog.resolveNearestCuratedCoord(rawSurah, rawAyah)
        val revealed = prefs.getInt("revealed_chars", 0)
        val totalLetters = prefs.getLong("total_letters", 0L)
        val totalAyahs = prefs.getInt("total_ayahs", 0)
        val likesPerLetter = prefs.getInt("likes_per_letter", 1).coerceIn(1, 1000)
        val chatReward = prefs.getInt("chat_reward", 4).coerceIn(0, 500)
        val keywordBonus = prefs.getInt("keyword_bonus", 10).coerceIn(0, 500)
        val memberReward = prefs.getInt("member_reward", 1).coerceIn(0, 200)
        val followReward = prefs.getInt("follow_reward", 5).coerceIn(0, 200)
        val shareReward = prefs.getInt("share_reward", 5).coerceIn(0, 200)
        val coinsPerLetter = prefs.getInt("coins_per_letter", 2).coerceIn(1, 100)
        val customKeywords = prefs.getString("custom_keywords", "aamiin, amin, khatam, alhamdulillah, masyaallah, barakallah, bismillah, subhanallah")
            ?: "aamiin, amin, khatam, alhamdulillah, masyaallah, barakallah, bismillah, subhanallah"
        val giftCompletes = prefs.getBoolean("gift_completes_ayah", false)
        val giftTiering = prefs.getBoolean("gift_tiering_enabled", true)
        val posX = prefs.getFloat("pos_x_pct", 0.50f).coerceIn(0.02f, 0.98f)
        val posY = prefs.getFloat("pos_y_pct", 0.50f).coerceIn(0.02f, 0.98f)
        val rot = prefs.getInt("overlay_rotation", 0)
        val scale = prefs.getFloat("overlay_scale", 1.0f).coerceIn(0.5f, 2.2f)
        val mirrored = prefs.getBoolean("overlay_mirrored", false)
        val fontSize = prefs.getFloat("arabic_font_size_sp", 28.0f)
        val latinFontSize = prefs.getFloat("latin_font_size_sp", 12.0f).coerceIn(5.0f, 32.0f)
        val maxLines = prefs.getInt("max_arabic_lines", 4)
        val cardWidthPct = prefs.getFloat("card_width_pct", 0.98f)
        val cardPadV = prefs.getInt("card_padding_v", 16)
        val showTrans = prefs.getBoolean("show_translation", true)
        val showNotifUnderAyah = prefs.getBoolean("show_notif_under_ayah", true)
        val remStatsEnabled = false
        val remStatsPosX = prefs.getFloat("rem_stats_pos_x", 0.50f)
        val remStatsPosY = prefs.getFloat("rem_stats_pos_y", 0.12f)
        val remStatsScale = prefs.getFloat("rem_stats_scale", 1.0f)
        val remStatsFontSize = prefs.getFloat("rem_stats_font_size", 14.0f)
        val perAyahThreeZone = prefs.getBoolean("per_ayah_three_zone_mode", true)
        val imamAutoPlay = prefs.getBoolean("imam_auto_play_enabled", false)
        val selectedImamId = prefs.getString("selected_imam_id", "alafasy") ?: "alafasy"

        val verse = QuranTextCatalog.getVerse(surah, ayah)
        indexVerseGraphemes(verse.arabicText)
        val safeRevealed = revealed.coerceIn(0, currentGraphemes.size)
        val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(safeRevealed)

        _state.value = QuranKhatamState(
            isEnabled = enabled,
            currentSurahNumber = verse.surahNumber,
            currentAyahNumber = verse.ayahNumber,
            revealedCharCount = safeRevealed,
            totalCharsInAyah = currentGraphemes.size,
            currentVerse = verse,
            revealedArabicText = revealedText,
            unrevealedArabicText = unrevealedText,
            totalLettersWrittenSession = totalLetters,
            totalAyahsCompletedSession = totalAyahs,
            likesPerLetter = likesPerLetter,
            chatRewardLetters = chatReward,
            keywordBonusLetters = keywordBonus,
            memberRewardLetters = memberReward,
            followRewardLetters = followReward,
            shareRewardLetters = shareReward,
            coinsPerLetter = coinsPerLetter,
            customKeywords = customKeywords,
            giftCompletesAyah = giftCompletes,
            giftTieringEnabled = giftTiering,
            posXpct = posX,
            posYpct = posY,
            overlayRotation = rot,
            overlayScale = scale,
            overlayMirrored = mirrored,
            arabicFontSizeSp = fontSize,
            latinFontSizeSp = latinFontSize,
            maxArabicLines = maxLines,
            cardWidthPct = cardWidthPct,
            cardPaddingV = cardPadV,
            showTranslation = showTrans,
            showNotifUnderAyah = showNotifUnderAyah,
            enableRemainingStatsOverlay = remStatsEnabled,
            remainingStatsPosXpct = remStatsPosX,
            remainingStatsPosYpct = remStatsPosY,
            remainingStatsScale = remStatsScale,
            remainingStatsFontSizeSp = remStatsFontSize,
            perAyahThreeZoneMode = perAyahThreeZone,
            imamAutoPlayEnabled = imamAutoPlay,
            selectedImamId = selectedImamId,
            scrollTransitionPhase = 0f
        )

        if (enabled) {
            scope.launch {
                delay(60L)
                scheduleBroadcastToOverlay(immediate = true)
                if (imamAutoPlay) {
                    syncImamAutoPlayForCurrentAyah()
                }
            }
        }
    }

    private fun persistStateNow() {
        pendingPersistJob?.cancel()
        pendingPersistJob = null
        val s = _state.value
        prefs.edit().apply {
            putBoolean("is_enabled", s.isEnabled)
            putInt("current_surah", s.currentSurahNumber)
            putInt("current_ayah", s.currentAyahNumber)
            putInt("revealed_chars", s.revealedCharCount)
            putLong("total_letters", s.totalLettersWrittenSession)
            putInt("total_ayahs", s.totalAyahsCompletedSession)
            putInt("likes_per_letter", s.likesPerLetter)
            putInt("chat_reward", s.chatRewardLetters)
            putInt("keyword_bonus", s.keywordBonusLetters)
            putInt("member_reward", s.memberRewardLetters)
            putInt("follow_reward", s.followRewardLetters)
            putInt("share_reward", s.shareRewardLetters)
            putInt("coins_per_letter", s.coinsPerLetter)
            putString("custom_keywords", s.customKeywords)
            putBoolean("gift_completes_ayah", s.giftCompletesAyah)
            putBoolean("gift_tiering_enabled", s.giftTieringEnabled)
            putFloat("pos_x_pct", s.posXpct)
            putFloat("pos_y_pct", s.posYpct)
            putInt("overlay_rotation", s.overlayRotation)
            putFloat("overlay_scale", s.overlayScale)
            putBoolean("overlay_mirrored", s.overlayMirrored)
            putFloat("arabic_font_size_sp", s.arabicFontSizeSp)
            putFloat("latin_font_size_sp", s.latinFontSizeSp)
            putInt("max_arabic_lines", s.maxArabicLines)
            putFloat("card_width_pct", s.cardWidthPct)
            putInt("card_padding_v", s.cardPaddingV)
            putBoolean("show_translation", s.showTranslation)
            putBoolean("show_notif_under_ayah", s.showNotifUnderAyah)
            putBoolean("enable_rem_stats", s.enableRemainingStatsOverlay)
            putFloat("rem_stats_pos_x", s.remainingStatsPosXpct)
            putFloat("rem_stats_pos_y", s.remainingStatsPosYpct)
            putFloat("rem_stats_scale", s.remainingStatsScale)
            putFloat("rem_stats_font_size", s.remainingStatsFontSizeSp)
            putBoolean("per_ayah_three_zone_mode", s.perAyahThreeZoneMode)
            putBoolean("imam_auto_play_enabled", s.imamAutoPlayEnabled)
            putString("selected_imam_id", s.selectedImamId)
            apply()
        }
    }

    private fun schedulePersistState(immediate: Boolean = false) {
        if (immediate) {
            persistStateNow()
            return
        }
        if (pendingPersistJob?.isActive == true) return
        pendingPersistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistStateNow()
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (_state.value.isEnabled == enabled) return
        _state.value = _state.value.copy(isEnabled = enabled, scrollTransitionPhase = 0f)
        schedulePersistState(immediate = true)
        if (enabled) {
            // Matikan sementara Tarik Tambang, Raja Singgasana, BeyBattle, & TrooperRoyale agar tidak bertabrakan di layar
            com.tajasb.game.InteractiveTugOfWarEngine.peekInstance()?.setEnabled(false)
            com.tajasb.game.InteractiveKingOfThroneEngine.peekInstance()?.setEnabled(false, propagateRemote = false)
            com.tajasb.game.InteractiveBeyBattleEngine.peekInstance()?.setEnabled(false)
            com.tajasb.game.InteractiveTrooperRoyaleEngine.peekInstance()?.setEnabled(false)
            scheduleBroadcastToOverlay(immediate = true)
            if (_state.value.imamAutoPlayEnabled) {
                syncImamAutoPlayForCurrentAyah()
            }
        } else {
            transitionAnimJob?.cancel()
            transitionAnimJob = null
            perAyahAudioPlayer.stopPlayback()
            hideFromOverlay()
        }
    }

    @Synchronized
    fun toggleEnabled(): Boolean {
        val next = !_state.value.isEnabled
        setEnabled(next)
        return next
    }

    // =========================================================================
    // KONTROL MODE PER-AYAT 3-ZONA AMAN & AUTO-PLAY SUARA IMAM
    // =========================================================================
    @Synchronized
    fun setPerAyahThreeZoneMode(enabled: Boolean) {
        if (_state.value.perAyahThreeZoneMode == enabled) return
        transitionAnimJob?.cancel()
        transitionAnimJob = null
        _state.value = _state.value.copy(
            perAyahThreeZoneMode = enabled,
            scrollTransitionPhase = 0f
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun togglePerAyahThreeZoneMode(): Boolean {
        val next = !_state.value.perAyahThreeZoneMode
        setPerAyahThreeZoneMode(next)
        return next
    }

    @Synchronized
    fun setImamAutoPlayEnabled(enabled: Boolean) {
        if (_state.value.imamAutoPlayEnabled == enabled) return
        val nextThreeZone = if (enabled) true else _state.value.perAyahThreeZoneMode
        _state.value = _state.value.copy(
            imamAutoPlayEnabled = enabled,
            perAyahThreeZoneMode = nextThreeZone
        )
        schedulePersistState(immediate = true)
        if (enabled && _state.value.isEnabled) {
            syncImamAutoPlayForCurrentAyah()
        } else {
            perAyahAudioPlayer.stopPlayback()
        }
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun toggleImamAutoPlay(): Boolean {
        val next = !_state.value.imamAutoPlayEnabled
        setImamAutoPlayEnabled(next)
        return next
    }

    @Synchronized
    fun selectImamForPerAyah(imamId: String) {
        val imam = QuranCatalog.findImamById(imamId)
        if (_state.value.selectedImamId == imam.id) return
        _state.value = _state.value.copy(selectedImamId = imam.id)
        schedulePersistState(immediate = true)
        if (_state.value.isEnabled && _state.value.imamAutoPlayEnabled) {
            syncImamAutoPlayForCurrentAyah()
        }
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun cycleImamForPerAyah(): QuranImam {
        val curId = _state.value.selectedImamId
        val idx = QuranCatalog.IMAMS.indexOfFirst { it.id.equals(curId, ignoreCase = true) }
        val nextIdx = if (idx >= 0) (idx + 1) % QuranCatalog.IMAMS.size else 0
        val nextImam = QuranCatalog.IMAMS[nextIdx]
        selectImamForPerAyah(nextImam.id)
        return nextImam
    }

    private fun syncImamAutoPlayForCurrentAyah() {
        val s = _state.value
        if (!s.isEnabled || !s.imamAutoPlayEnabled) {
            perAyahAudioPlayer.stopPlayback()
            return
        }
        val imam = QuranCatalog.findImamById(s.selectedImamId)
        val totalChars = currentGraphemes.size.coerceAtLeast(1)
        val initialRatio = (s.revealedCharCount.toFloat() / totalChars.toFloat()).coerceIn(0f, 0.90f)
        perAyahAudioPlayer.playAyah(
            imam = imam,
            surahNumber = s.currentSurahNumber,
            ayahNumber = s.currentAyahNumber,
            arabicText = s.currentVerse.arabicText,
            graphemeCount = totalChars,
            initialRatio = initialRatio,
            onProgressRatio = { ratio ->
                onImamAudioProgressRatio(ratio)
            },
            onAyahCompleted = {
                onImamAudioAyahCompleted()
            }
        )
    }

    @Synchronized
    private fun onImamAudioProgressRatio(ratio: Float) {
        val s = _state.value
        if (!s.isEnabled || !s.imamAutoPlayEnabled) return
        val totalChars = currentGraphemes.size.coerceAtLeast(1)
        val targetRevealed = (ratio.coerceIn(0f, 1f) * totalChars).toInt().coerceIn(0, totalChars)
        if (targetRevealed <= s.revealedCharCount) return

        val delta = targetRevealed - s.revealedCharCount
        val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(targetRevealed)
        _state.value = s.copy(
            revealedCharCount = targetRevealed,
            revealedArabicText = revealedText,
            unrevealedArabicText = unrevealedText,
            totalLettersWrittenSession = s.totalLettersWrittenSession + delta.toLong()
        )
        schedulePersistState(immediate = false)
        scheduleBroadcastToOverlay(immediate = false, fastAnim = true)
    }

    @Synchronized
    private fun onImamAudioAyahCompleted() {
        val s = _state.value
        if (!s.isEnabled || !s.imamAutoPlayEnabled) return
        val totalChars = currentGraphemes.size.coerceAtLeast(1)
        val remaining = (totalChars - s.revealedCharCount).coerceAtLeast(0)
        val newTotalWritten = s.totalLettersWrittenSession + remaining.toLong()
        val newTotalAyahs = s.totalAyahsCompletedSession + 1
        val imam = QuranCatalog.findImamById(s.selectedImamId)

        val contributor = if (s.lastContributorName.isNotBlank() && s.lastContributorName != "Menunggu Penonton") {
            s.lastContributorName
        } else {
            "🎙️ ${imam.displayName}"
        }
        val desc = if (s.lastActionDescription.isNotBlank() && !s.lastActionDescription.startsWith("Tap Like")) {
            s.lastActionDescription
        } else {
            "Tilawah Ayat ${s.currentSurahNumber}:${s.currentAyahNumber} Selesai ✨"
        }

        lastAyahCompletedAtMs = System.currentTimeMillis()
        advanceToNextAyahInternal(
            contributorName = contributor,
            actionDesc = desc,
            totalWritten = newTotalWritten,
            totalAyahs = newTotalAyahs,
            isFlash = false,
            initialCarryOverLetters = 0,
            animateThreeZoneScroll = true
        )
    }

    /**
     * Memulai animasi gulir naik 3-Zona Aman yang halus (scrollTransitionPhase: 1.0f -> 0.0f)
     * selama ~450ms (13 frame @ ~30 FPS) menggunakan kurva Cubic Ease-In-Out.
     * Pada saat scrollTransitionPhase = 1.0f, ayat yang baru selesai (Ayat N-1) masih berada tepat di
     * Zona Aman Tengah dan ayat baru (Ayat N) berada di Zona Bawah, lalu keduanya mengalir naik ke atas.
     */
    private fun startSmoothUpwardRollAnimation() {
        transitionAnimJob?.cancel()
        if (!_state.value.isEnabled || !_state.value.perAyahThreeZoneMode) {
            if (_state.value.scrollTransitionPhase != 0f) {
                _state.value = _state.value.copy(scrollTransitionPhase = 0f)
            }
            return
        }
        transitionAnimJob = scope.launch {
            val totalSteps = 13
            for (step in 1..totalSteps) {
                delay(32L)
                val progress = step.toFloat() / totalSteps.toFloat()
                val eased = computeSmoothScrollEasing(progress)
                val remainingPhase = (1f - eased).coerceIn(0f, 1f)
                synchronized(this@InteractiveQuranKhatamEngine) {
                    if (!_state.value.isEnabled || !_state.value.perAyahThreeZoneMode) return@launch
                    _state.value = _state.value.copy(
                        scrollTransitionPhase = if (step == totalSteps) 0f else remainingPhase
                    )
                    scheduleBroadcastToOverlay(immediate = (step == totalSteps), fastAnim = true)
                }
            }
        }
    }

    /**
     * Memajukan sejumlah huruf Arab nyata pada ayat saat ini.
     * Jika jumlah huruf yang ditambahkan menyelesaikan ayat saat ini, ayat diselesaikan dan
     * sisa huruf (carry-over) langsung diterapkan ke ayat berikutnya tanpa hangus.
     */
    @Synchronized
    fun advanceLetters(lettersToAdd: Int, contributorName: String, actionDesc: String) {
        if (!_state.value.isEnabled || lettersToAdd <= 0) return

        val s = _state.value
        val actualName = contributorName.trim().ifBlank { "Penonton" }

        // Jika mode Auto-Play Suara Imam aktif, bacaan & perpindahan ayat dipimpin oleh durasi suara Imam
        // sedangkan setiap event penonton dari Termux tetap tercatat penuh pada statistik, leaderboard, dan dock kontributor!
        if (s.imamAutoPlayEnabled) {
            val newTotalWritten = s.totalLettersWrittenSession + lettersToAdd.toLong()
            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(actualName, lettersToAdd)
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
            _state.value = s.copy(
                lastContributorName = contributorName,
                lastActionDescription = actionDesc,
                totalLettersWrittenSession = newTotalWritten
            )
            schedulePersistState(immediate = false)
            scheduleBroadcastToOverlay(immediate = true)
            return
        }

        val totalChars = currentGraphemes.size.coerceAtLeast(1)
        val remainingInCurrent = (totalChars - s.revealedCharCount).coerceAtLeast(0)

        if (lettersToAdd >= remainingInCurrent) {
            // Ayat selesai! Mulai ayat berikutnya dari huruf ke-0 (tanpa lompat lintas banyak ayat sekaligus)
            lastAyahCompletedAtMs = System.currentTimeMillis()
            val actualAdded = remainingInCurrent.coerceAtLeast(1)
            val newTotalWritten = s.totalLettersWrittenSession + actualAdded.toLong()
            val newTotalAyahs = s.totalAyahsCompletedSession + 1

            val tracker = QuranKhatamLeaderboardTracker.getInstance(context)
            tracker.recordLetters(actualName, actualAdded)
            tracker.recordAyahCompleted(actualName)

            advanceToNextAyahInternal(
                contributorName = contributorName,
                actionDesc = "$actionDesc (Ayat Selesai ✨)",
                totalWritten = newTotalWritten,
                totalAyahs = newTotalAyahs,
                isFlash = false,
                initialCarryOverLetters = 0,
                animateThreeZoneScroll = s.perAyahThreeZoneMode
            )
        } else {
            val newRevealed = (s.revealedCharCount + lettersToAdd).coerceIn(0, totalChars)
            val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(newRevealed)
            val newTotalWritten = s.totalLettersWrittenSession + lettersToAdd.toLong()

            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(actualName, lettersToAdd)
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()

            _state.value = s.copy(
                revealedCharCount = newRevealed,
                revealedArabicText = revealedText,
                unrevealedArabicText = unrevealedText,
                lastContributorName = contributorName,
                lastActionDescription = actionDesc,
                totalLettersWrittenSession = newTotalWritten,
                isAyahCompletedFlash = false
            )
            schedulePersistState(immediate = false)
            scheduleBroadcastToOverlay(immediate = false)
        }
    }

    @Synchronized
    fun completeCurrentAyah(contributorName: String, actionDesc: String) {
        if (!_state.value.isEnabled) {
            setEnabled(true)
        }
        val s = _state.value
        val remaining = (currentGraphemes.size - s.revealedCharCount).coerceAtLeast(1)
        if (s.imamAutoPlayEnabled) {
            val actualName = contributorName.trim().ifBlank { "Penonton" }
            val tracker = QuranKhatamLeaderboardTracker.getInstance(context)
            tracker.recordLetters(actualName, remaining)
            tracker.recordAyahCompleted(actualName)
            advanceToNextAyahInternal(
                contributorName = contributorName,
                actionDesc = "$actionDesc (Ayat Selesai ✨)",
                totalWritten = s.totalLettersWrittenSession + remaining.toLong(),
                totalAyahs = s.totalAyahsCompletedSession + 1,
                isFlash = false,
                initialCarryOverLetters = 0,
                animateThreeZoneScroll = s.perAyahThreeZoneMode
            )
        } else {
            advanceLetters(remaining, contributorName, actionDesc)
        }
    }

    private fun advanceToNextAyahInternal(
        contributorName: String,
        actionDesc: String,
        totalWritten: Long,
        totalAyahs: Int,
        isFlash: Boolean,
        initialCarryOverLetters: Int = 0,
        animateThreeZoneScroll: Boolean = _state.value.perAyahThreeZoneMode
    ) {
        val s = _state.value
        val (nextSurah, nextAyah) = QuranTextCatalog.getNextCuratedCoord(
            s.currentSurahNumber,
            s.currentAyahNumber
        )

        hasCompletedAnyAyahInSession = true
        // Simpan ayat yang baru saja tuntas agar transisi mengalir mulus tanpa fliker/kedip
        retainedArabicText = if (s.perAyahThreeZoneMode) {
            ""
        } else {
            s.currentVerse.arabicText + " ﴿" + QuranTextCatalog.toArabicDigits(s.currentAyahNumber) + "﴾ "
        }

        val nextVerse = QuranTextCatalog.getVerse(nextSurah, nextAyah)
        indexVerseGraphemes(nextVerse.arabicText)

        val maxCarry = (currentGraphemes.size - 1).coerceAtLeast(0)
        val safeCarry = initialCarryOverLetters.coerceIn(0, maxCarry)
        val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(safeCarry)

        val actualName = contributorName.trim().ifBlank { "Penonton" }
        val tracker = QuranKhatamLeaderboardTracker.getInstance(context)
        if (nextVerse.surahNumber != s.currentSurahNumber) {
            tracker.recordSurahCompleted(actualName)
        }
        if (nextVerse.juzNumber != s.currentVerse.juzNumber) {
            tracker.recordJuzCompleted(actualName)
        }
        com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()

        val initialScrollPhase = if (animateThreeZoneScroll && s.perAyahThreeZoneMode) 1.0f else 0f
        _state.value = s.copy(
            currentSurahNumber = nextVerse.surahNumber,
            currentAyahNumber = nextVerse.ayahNumber,
            revealedCharCount = safeCarry,
            totalCharsInAyah = currentGraphemes.size,
            currentVerse = nextVerse,
            revealedArabicText = revealedText,
            unrevealedArabicText = unrevealedText,
            lastContributorName = contributorName,
            lastActionDescription = actionDesc,
            totalLettersWrittenSession = totalWritten,
            totalAyahsCompletedSession = totalAyahs,
            isAyahCompletedFlash = false,
            scrollTransitionPhase = initialScrollPhase
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true, fastAnim = initialScrollPhase > 0f)
        if (initialScrollPhase > 0f) {
            startSmoothUpwardRollAnimation()
        }
        if (s.isEnabled && s.imamAutoPlayEnabled) {
            syncImamAutoPlayForCurrentAyah()
        }
    }

    @Synchronized
    fun nextAyah() {
        retainedArabicText = ""
        advanceToNextAyahInternal(
            contributorName = "Host Siaran",
            actionDesc = "Lompat Ayat Berikutnya",
            totalWritten = _state.value.totalLettersWrittenSession,
            totalAyahs = _state.value.totalAyahsCompletedSession,
            isFlash = false,
            initialCarryOverLetters = 0,
            animateThreeZoneScroll = _state.value.perAyahThreeZoneMode
        )
    }

    @Synchronized
    fun prevAyah() {
        retainedArabicText = ""
        transitionAnimJob?.cancel()
        transitionAnimJob = null
        val s = _state.value
        val (prevSurah, prevAyah) = QuranTextCatalog.getPrevCuratedCoord(
            s.currentSurahNumber,
            s.currentAyahNumber
        )

        val prevVerse = QuranTextCatalog.getVerse(prevSurah, prevAyah)
        indexVerseGraphemes(prevVerse.arabicText)
        val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(0)

        _state.value = s.copy(
            currentSurahNumber = prevVerse.surahNumber,
            currentAyahNumber = prevVerse.ayahNumber,
            revealedCharCount = 0,
            totalCharsInAyah = currentGraphemes.size,
            currentVerse = prevVerse,
            revealedArabicText = revealedText,
            unrevealedArabicText = unrevealedText,
            lastContributorName = "Host Siaran",
            lastActionDescription = "Kembali ke Ayat Sebelumnya",
            isAyahCompletedFlash = false,
            scrollTransitionPhase = 0f
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
        if (s.isEnabled && s.imamAutoPlayEnabled) {
            syncImamAutoPlayForCurrentAyah()
        }
    }

    @Synchronized
    fun jumpToSurah(surahNumber: Int, ayahNumber: Int = 1) {
        retainedArabicText = ""
        transitionAnimJob?.cancel()
        transitionAnimJob = null
        val validSurah = surahNumber.coerceIn(1, 114)
        val sInfo = QuranTextCatalog.getSurahInfo(validSurah)
        val validAyah = ayahNumber.coerceIn(1, sInfo.ayahCount)
        val (targetSurah, targetAyah) = QuranTextCatalog.resolveNearestCuratedCoord(validSurah, validAyah)

        val verse = QuranTextCatalog.getVerse(targetSurah, targetAyah)
        indexVerseGraphemes(verse.arabicText)
        val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(0)

        _state.value = _state.value.copy(
            currentSurahNumber = verse.surahNumber,
            currentAyahNumber = verse.ayahNumber,
            revealedCharCount = 0,
            totalCharsInAyah = currentGraphemes.size,
            currentVerse = verse,
            revealedArabicText = revealedText,
            unrevealedArabicText = unrevealedText,
            lastContributorName = "Host Siaran",
            lastActionDescription = "Memilih QS. ${verse.surahNameLatin} [${verse.surahNumber}:${verse.ayahNumber}] (Juz ${verse.juzNumber})",
            isAyahCompletedFlash = false,
            scrollTransitionPhase = 0f
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
        if (_state.value.isEnabled && _state.value.imamAutoPlayEnabled) {
            syncImamAutoPlayForCurrentAyah()
        }
    }

    @Synchronized
    fun jumpToJuz(juzNumber: Int) {
        val validJuz = juzNumber.coerceIn(1, 30)
        val (surah, ayah) = QuranTextCatalog.getFirstSurahAndAyahOfJuz(validJuz)
        jumpToSurah(surah, ayah)
    }

    @Synchronized
    fun nextJuz() {
        val curJuz = _state.value.currentVerse.juzNumber.coerceIn(1, 30)
        val next = if (curJuz < 30) curJuz + 1 else 1
        jumpToJuz(next)
    }

    @Synchronized
    fun prevJuz() {
        val curJuz = _state.value.currentVerse.juzNumber.coerceIn(1, 30)
        val prev = if (curJuz > 1) curJuz - 1 else 30
        jumpToJuz(prev)
    }

    @Synchronized
    fun nextSurah() {
        val curSurah = _state.value.currentSurahNumber.coerceIn(1, 114)
        val next = if (curSurah < 114) curSurah + 1 else 1
        jumpToSurah(next, 1)
    }

    @Synchronized
    fun prevSurah() {
        val curSurah = _state.value.currentSurahNumber.coerceIn(1, 114)
        val prev = if (curSurah > 1) curSurah - 1 else 114
        jumpToSurah(prev, 1)
    }

    @Synchronized
    fun toggleGiftCompletesAyah(): Boolean {
        val next = !_state.value.giftCompletesAyah
        _state.value = _state.value.copy(giftCompletesAyah = next)
        schedulePersistState(immediate = true)
        return next
    }

    @Synchronized
    fun setLikesPerLetter(count: Int) {
        val safe = count.coerceIn(1, 1000)
        _state.value = _state.value.copy(likesPerLetter = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustLikesPerLetter(delta: Int) {
        setLikesPerLetter(_state.value.likesPerLetter + delta)
    }

    @Synchronized
    fun setChatRewardLetters(letters: Int) {
        val safe = letters.coerceIn(0, 500)
        _state.value = _state.value.copy(chatRewardLetters = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustChatRewardLetters(delta: Int) {
        setChatRewardLetters(_state.value.chatRewardLetters + delta)
    }

    @Synchronized
    fun setFollowRewardLetters(letters: Int) {
        val safe = letters.coerceIn(0, 200)
        _state.value = _state.value.copy(followRewardLetters = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustFollowRewardLetters(delta: Int) {
        setFollowRewardLetters(_state.value.followRewardLetters + delta)
    }

    @Synchronized
    fun setShareRewardLetters(letters: Int) {
        val safe = letters.coerceIn(0, 200)
        _state.value = _state.value.copy(shareRewardLetters = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustShareRewardLetters(delta: Int) {
        setShareRewardLetters(_state.value.shareRewardLetters + delta)
    }

    @Synchronized
    fun setMemberRewardLetters(letters: Int) {
        val safe = letters.coerceIn(0, 200)
        _state.value = _state.value.copy(memberRewardLetters = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustMemberRewardLetters(delta: Int) {
        setMemberRewardLetters(_state.value.memberRewardLetters + delta)
    }

    @Synchronized
    fun setKeywordBonusLetters(letters: Int) {
        val safe = letters.coerceIn(0, 500)
        _state.value = _state.value.copy(keywordBonusLetters = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustKeywordBonusLetters(delta: Int) {
        setKeywordBonusLetters(_state.value.keywordBonusLetters + delta)
    }

    @Synchronized
    fun setCoinsPerLetter(coins: Int) {
        val safe = coins.coerceIn(1, 100)
        _state.value = _state.value.copy(coinsPerLetter = safe)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun adjustCoinsPerLetter(delta: Int) {
        setCoinsPerLetter(_state.value.coinsPerLetter + delta)
    }

    @Synchronized
    fun setCustomKeywords(keywords: String) {
        val clean = keywords.trim()
        _state.value = _state.value.copy(customKeywords = clean)
        schedulePersistState(immediate = true)
    }

    @Synchronized
    fun applyPresetMode(preset: String) {
        when (preset.uppercase()) {
            "FAST" -> {
                // Mode Cepat / Santai: 1 Like = 1 H, Chat +5H, Bonus Doa +10H, Join +1H, Follow +5H, Share +5H
                setLikesPerLetter(1)
                setChatRewardLetters(5)
                setKeywordBonusLetters(10)
                setMemberRewardLetters(1)
                setFollowRewardLetters(5)
                setShareRewardLetters(5)
                setCoinsPerLetter(2)
            }
            "CROWD" -> {
                // Mode Ramai / Banjir Like: 10 Like = 1 H, Chat +4H, Bonus Doa +15H, Join OFF, Follow +5H, Share +10H
                setLikesPerLetter(10)
                setChatRewardLetters(4)
                setKeywordBonusLetters(15)
                setMemberRewardLetters(0)
                setFollowRewardLetters(5)
                setShareRewardLetters(10)
                setCoinsPerLetter(2)
            }
            "PRAYER" -> {
                // Mode Fokus Doa & Saweran: 5 Like = 1 H, Chat +8H, Bonus Doa +20H, Join +2H, Follow +10H, Share +10H
                setLikesPerLetter(5)
                setChatRewardLetters(8)
                setKeywordBonusLetters(20)
                setMemberRewardLetters(2)
                setFollowRewardLetters(10)
                setShareRewardLetters(10)
                setCoinsPerLetter(3)
            }
            "MARATHON" -> {
                // Mode Maraton Khatam: 25 Like = 1 H, Chat +10H, Bonus Doa +30H, Join OFF, Follow +10H, Share +20H
                setLikesPerLetter(25)
                setChatRewardLetters(10)
                setKeywordBonusLetters(30)
                setMemberRewardLetters(0)
                setFollowRewardLetters(10)
                setShareRewardLetters(20)
                setCoinsPerLetter(5)
            }
        }
    }

    @Synchronized
    fun toggleGiftTiering(): Boolean {
        val next = !_state.value.giftTieringEnabled
        _state.value = _state.value.copy(giftTieringEnabled = next)
        schedulePersistState(immediate = true)
        return next
    }

    @Synchronized
    fun setActivePrayer(senderName: String, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        _state.value = _state.value.copy(
            lastPrayerSender = senderName.trim().ifBlank { "Penonton" },
            lastPrayerText = cleanText,
            lastPrayerAtMs = System.currentTimeMillis()
        )
        if (_state.value.isEnabled) {
            scheduleBroadcastToOverlay(immediate = true)
        }
    }

    /**
     * Mengulang progres pengetikan huruf pada ayat yang sedang aktif saja (0%)
     * tanpa menghapus total statistik sesi atau kembali ke Al-Fatihah.
     */
    @Synchronized
    fun resetCurrentAyah() {
        transitionAnimJob?.cancel()
        transitionAnimJob = null
        val s = _state.value
        val (revealedText, unrevealedText) = sliceRevealedAndUnrevealed(0)
        _state.value = s.copy(
            revealedCharCount = 0,
            revealedArabicText = revealedText,
            unrevealedArabicText = unrevealedText,
            lastContributorName = "Host Siaran",
            lastActionDescription = "Ulang Ayat Aktif",
            isAyahCompletedFlash = false,
            scrollTransitionPhase = 0f
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
        if (s.isEnabled && s.imamAutoPlayEnabled) {
            syncImamAutoPlayForCurrentAyah()
        }
    }

    @Synchronized
    fun resetProgress() {
        hasCompletedAnyAyahInSession = false
        jumpToSurah(1, 1)
        _state.value = _state.value.copy(
            totalLettersWrittenSession = 0L,
            totalAyahsCompletedSession = 0,
            scrollTransitionPhase = 0f
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    // =========================================================================
    // EVENT RELAY DISPATCHERS
    // =========================================================================
    @Synchronized
    fun onLikeEvent(senderName: String, likeCount: Int = 1) {
        val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(context).config.value
        if (!_state.value.isEnabled && !ep.enableTopLikersOverlay) return
        val count = likeCount.coerceAtLeast(1)
        val ratio = _state.value.likesPerLetter.coerceAtLeast(1)

        pendingLikeAccumulator += count
        val letters = pendingLikeAccumulator / ratio
        val actualName = senderName.trim().ifBlank { "Penonton" }

        if (letters > 0) {
            pendingLikeAccumulator %= ratio
            if (_state.value.isEnabled) {
                val now = System.currentTimeMillis()
                if (now - lastAyahCompletedAtMs < 350L && _state.value.revealedCharCount == 0) {
                    return
                }
                advanceLetters(letters, actualName, "+$count Like ($letters H)")
            } else {
                QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(actualName, letters)
                com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
            }
        } else {
            // Belum mencapai ambang 1 huruf tapi kontribusi tetap tercatat
            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(actualName, 0)
        }
    }

    fun onChatEvent(senderName: String, text: String) {
        val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(context).config.value
        if (!_state.value.isEnabled && !ep.enableTopLikersOverlay) return
        val cleanName = senderName.trim().ifBlank { "Penonton" }
        val cleanText = text.trim()
        val now = System.currentTimeMillis()
        if (cleanText.isNotEmpty()) {
            _state.value = _state.value.copy(
                lastPrayerSender = cleanName,
                lastPrayerText = cleanText,
                lastPrayerAtMs = now
            )
        }

        // Filter Kata Kunci Doa & Khatam (Daftar Kustom + Bawaan)
        val lowerText = cleanText.lowercase()
        val customList = _state.value.customKeywords
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val isSpecialKeyword = customList.any { kw -> lowerText.contains(kw) } ||
                lowerText.contains("aamiin") || lowerText.contains("amin") ||
                lowerText.contains("khatam") || lowerText.contains("alhamdulillah") ||
                lowerText.contains("masyaallah") || lowerText.contains("masya allah") ||
                lowerText.contains("barakallah") || lowerText.contains("bismillah") ||
                lowerText.contains("subhanallah") || lowerText.contains("allahu akbar")

        val baseReward = _state.value.chatRewardLetters.coerceIn(0, 500)
        val keywordBonus = _state.value.keywordBonusLetters.coerceIn(0, 500)
        val reward = if (isSpecialKeyword) (baseReward + keywordBonus) else baseReward
        if (reward <= 0) return

        val actionDesc = if (isSpecialKeyword) "Chat Doa Spesial (+$reward Huruf)" else "Doa/Komentar (+$reward Huruf)"

        if (_state.value.isEnabled) {
            advanceLetters(reward, cleanName, actionDesc)
        } else {
            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(cleanName, reward)
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
        }
    }

    fun onGiftEvent(senderName: String, giftName: String, count: Int = 1, diamondCount: Int = count) {
        val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(context).config.value
        if (!_state.value.isEnabled && !ep.enableTopLikersOverlay) return
        val safeDiamonds = maxOf(diamondCount, count)
        val actualName = senderName.trim().ifBlank { "Penonton" }
        val gLower = giftName.lowercase().trim()

        // 🎁 Tiering Gift Khusus:
        // Tier 4 (Mega Sultan): Paus (Whale), Singa (Lion), Planet, Falcon, Universe (>= 10,000 diamond) -> Khatam 1 Surat & Buka Surat Berikutnya!
        val isMegaSultanGift = _state.value.giftTieringEnabled && (
                safeDiamonds >= 10000 ||
                gLower.contains("lion") || gLower.contains("singa") ||
                gLower.contains("whale") || gLower.contains("paus") ||
                gLower.contains("universe") || gLower.contains("falcon")
        )
        // Tier 3 (Super Gift): Mobil Sport, Pesawat, Kapal Pesiar, Castle (1000 - 9999 diamond) -> Selesaikan 3 Ayat berturut-turut
        val isSuperGift = _state.value.giftTieringEnabled && (safeDiamonds in 1000..9999 || gLower.contains("sportscar") || gLower.contains("castle"))

        // Tier 2 (Medium Gift): Topi, Kacamata, Parfum, Kembang Api (100 - 999 diamond) atau mode giftCompletesAyah -> Selesaikan 1 Ayat penuh
        val isAyahCompletingGift = _state.value.giftCompletesAyah || safeDiamonds >= 500 || (_state.value.giftTieringEnabled && safeDiamonds >= 100)

        val coinsRate = _state.value.coinsPerLetter.coerceAtLeast(1)

        if (_state.value.isEnabled) {
            val now = System.currentTimeMillis()
            if (now - lastAyahCompletedAtMs < 400L && _state.value.revealedCharCount == 0) {
                return
            }
            when {
                isMegaSultanGift -> {
                    // Selesaikan surat saat ini dan buka awal surat berikutnya
                    nextSurah()
                    _state.value = _state.value.copy(
                        lastContributorName = actualName,
                        lastActionDescription = "🎁 MEGA SULTAN $giftName x$count ($safeDiamonds 💎) ➔ AUTO KHATAM SURAT!"
                    )
                    val tracker = QuranKhatamLeaderboardTracker.getInstance(context)
                    tracker.recordSurahCompleted(actualName)
                    tracker.recordAyahCompleted(actualName)
                    tracker.recordLetters(actualName, 100)
                    schedulePersistState(immediate = true)
                    scheduleBroadcastToOverlay(immediate = true)
                    com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
                }
                isSuperGift -> {
                    // Selesaikan 1 ayat saat ini dengan bonus statistik masif
                    completeCurrentAyah(actualName, "🎁 SUPER GIFT $giftName x$count ($safeDiamonds 💎) ➔ Buka 3 Ayat!")
                    val tracker = QuranKhatamLeaderboardTracker.getInstance(context)
                    tracker.recordAyahCompleted(actualName)
                    tracker.recordLetters(actualName, 60)
                }
                isAyahCompletingGift -> {
                    completeCurrentAyah(actualName, "Gift $giftName x$count ($safeDiamonds 💎) ➔ Buka 1 Ayat")
                }
                else -> {
                    // Tier 1 (Kecil/Mawar/koin): Gift dihitung berdasarkan coinsPerLetter
                    val addLetters = (safeDiamonds.coerceAtLeast(1) * coinsRate).coerceIn(1, 500)
                    advanceLetters(addLetters, actualName, "Gift $giftName x$count (+$addLetters Huruf)")
                }
            }
        } else {
            val tracker = QuranKhatamLeaderboardTracker.getInstance(context)
            when {
                isMegaSultanGift -> {
                    tracker.recordSurahCompleted(actualName)
                    tracker.recordAyahCompleted(actualName)
                    tracker.recordLetters(actualName, 100)
                }
                isSuperGift || isAyahCompletingGift -> {
                    tracker.recordAyahCompleted(actualName)
                    tracker.recordLetters(actualName, 50)
                }
                else -> {
                    val addLetters = (safeDiamonds.coerceAtLeast(1) * coinsRate).coerceIn(1, 500)
                    tracker.recordLetters(actualName, addLetters)
                }
            }
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
        }
    }

    fun onMemberJoinEvent(senderName: String) {
        val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(context).config.value
        if (!_state.value.isEnabled && !ep.enableTopLikersOverlay) return
        val cleanName = senderName.trim().ifBlank { "Penonton" }
        val reward = _state.value.memberRewardLetters.coerceIn(0, 200)
        if (reward <= 0) return // Dinonaktifkan jika 0

        if (_state.value.isEnabled) {
            advanceLetters(reward, cleanName, "Masuk Siaran (+$reward Huruf)")
        } else {
            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(cleanName, reward)
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
        }
    }

    fun onFollowEvent(senderName: String) {
        val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(context).config.value
        if (!_state.value.isEnabled && !ep.enableTopLikersOverlay) return
        val cleanName = senderName.trim().ifBlank { "Penonton" }
        val reward = _state.value.followRewardLetters.coerceIn(0, 200)
        if (reward <= 0) return

        if (_state.value.isEnabled) {
            advanceLetters(reward, cleanName, "Follow Baru (+$reward Huruf)")
        } else {
            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(cleanName, reward)
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
        }
    }

    fun onShareEvent(senderName: String) {
        val ep = com.tajasb.eventrelay.LiveEventPreferences.getInstance(context).config.value
        if (!_state.value.isEnabled && !ep.enableTopLikersOverlay) return
        val cleanName = senderName.trim().ifBlank { "Penonton" }
        val reward = _state.value.shareRewardLetters.coerceIn(0, 200)
        if (reward <= 0) return

        if (_state.value.isEnabled) {
            advanceLetters(reward, cleanName, "Share Siaran (+$reward Huruf)")
        } else {
            QuranKhatamLeaderboardTracker.getInstance(context).recordLetters(cleanName, reward)
            com.tajasb.eventrelay.LiveEventReceiver.getInstance(context).scheduleRefreshTopLikers()
        }
    }

    // Testing simulation
    fun testAddLetter() {
        if (!_state.value.isEnabled) {
            setEnabled(true)
        }
        advanceLetters(1, "Penonton Uji", "+1 Like")
    }

    fun testAddWord() {
        if (!_state.value.isEnabled) {
            setEnabled(true)
        }
        _state.value = _state.value.copy(
            lastPrayerSender = "Sahabat Doa",
            lastPrayerText = "Semoga sehat sekeluarga, lancar rezeki dan berkah selalu 🤲",
            lastPrayerAtMs = System.currentTimeMillis()
        )
        advanceLetters(4, "Sahabat Doa", "Chat Doa")
    }

    fun testCompleteAyah() {
        if (!_state.value.isEnabled) {
            setEnabled(true)
        }
        completeCurrentAyah("Sultan Makkah", "Gift Mawar x10")
    }

    // =========================================================================
    // POSITION & TRANSFORM CONTROLS (MANDIRI UNTUK KARTU KHATAM AL-QUR'AN)
    // =========================================================================
    @Synchronized
    fun setOverlayPosition(xPct: Float, yPct: Float) {
        val sx = xPct.coerceIn(-1.50f, 2.50f)
        val sy = yPct.coerceIn(-1.50f, 2.50f)
        _state.value = _state.value.copy(posXpct = sx, posYpct = sy)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun moveOverlay(dxPct: Float, dyPct: Float) {
        val s = _state.value
        setOverlayPosition(s.posXpct + dxPct, s.posYpct + dyPct)
    }

    @Synchronized
    fun setOverlayRotation(angle: Int) {
        val clean = ((angle % 360) + 360) % 360
        _state.value = _state.value.copy(overlayRotation = clean)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun cycleOverlayRotation(): Int {
        val next = (_state.value.overlayRotation + 90) % 360
        setOverlayRotation(next)
        return next
    }

    @Synchronized
    fun toggleOverlayMirror(): Boolean {
        val next = !_state.value.overlayMirrored
        _state.value = _state.value.copy(overlayMirrored = next)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
        return next
    }

    @Synchronized
    fun setOverlayScale(scale: Float) {
        val sc = scale.coerceAtLeast(0.05f)
        _state.value = _state.value.copy(overlayScale = sc)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun adjustOverlayScale(delta: Float): Float {
        val next = (_state.value.overlayScale + delta).coerceAtLeast(0.05f)
        setOverlayScale(next)
        return next
    }

    @Synchronized
    fun resetOverlayTransform() {
        _state.value = _state.value.copy(
            posXpct = 0.50f,
            posYpct = 0.50f,
            overlayRotation = 0,
            overlayScale = 1.0f,
            overlayMirrored = false,
            cardWidthPct = 0.98f,
            arabicFontSizeSp = 28.0f,
            latinFontSizeSp = 12.0f,
            cardPaddingV = 16
        )
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    // =========================================================================
    // TYPOGRAPHY & CARD CUSTOMIZATION CONTROLS
    // =========================================================================
    fun setArabicFontSize(sp: Float) {
        val s = sp.coerceAtLeast(4f)
        _state.value = _state.value.copy(arabicFontSizeSp = s)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun adjustArabicFontSize(delta: Float) {
        setArabicFontSize(_state.value.arabicFontSizeSp + delta)
    }

    fun setLatinFontSize(sp: Float) {
        val s = sp.coerceIn(5.0f, 32.0f)
        _state.value = _state.value.copy(latinFontSizeSp = s)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun adjustLatinFontSize(delta: Float) {
        setLatinFontSize(_state.value.latinFontSizeSp + delta)
    }

    fun setMaxArabicLines(lines: Int) {
        val l = lines.coerceIn(0, 8)
        _state.value = _state.value.copy(maxArabicLines = l)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun adjustMaxArabicLines(delta: Int) {
        val cur = _state.value.maxArabicLines
        val next = (cur + delta).coerceIn(0, 8)
        setMaxArabicLines(next)
    }

    fun cycleMaxArabicLines(): Int {
        val cur = _state.value.maxArabicLines
        val next = when (cur) {
            0 -> 1
            1 -> 2
            2 -> 3
            3 -> 4
            4 -> 5
            5 -> 6
            else -> 0 // 0 = Auto (Fleksibel Alami)
        }
        setMaxArabicLines(next)
        return next
    }

    fun setCardWidthPct(pct: Float) {
        val p = pct.coerceAtLeast(0.10f)
        _state.value = _state.value.copy(cardWidthPct = p)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun adjustCardWidthPct(delta: Float) {
        setCardWidthPct(_state.value.cardWidthPct + delta)
    }

    fun setCardPaddingV(pad: Int) {
        val p = pad.coerceAtLeast(0)
        _state.value = _state.value.copy(cardPaddingV = p)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun adjustCardPaddingV(delta: Int) {
        setCardPaddingV(_state.value.cardPaddingV + delta)
    }

    fun toggleTranslation(): Boolean {
        val next = !_state.value.showTranslation
        _state.value = _state.value.copy(showTranslation = next)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
        return next
    }

    fun toggleNotifUnderAyah(): Boolean {
        val next = !_state.value.showNotifUnderAyah
        _state.value = _state.value.copy(showNotifUnderAyah = next)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
        return next
    }

    fun setNotifUnderAyah(show: Boolean) {
        _state.value = _state.value.copy(showNotifUnderAyah = show)
        schedulePersistState(immediate = true)
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun toggleRemainingStatsOverlay(): Boolean {
        val next = !_state.value.enableRemainingStatsOverlay
        _state.value = _state.value.copy(enableRemainingStatsOverlay = next)
        schedulePersistState(immediate = true)
        if (next) {
            broadcastRemainingStatsOverlay()
        } else {
            OverlayBannerBroadcaster.broadcastHide(context, "QURAN_STATS")
        }
        return next
    }

    fun setRemainingStatsPos(xPct: Float, yPct: Float) {
        val sx = xPct.coerceIn(0.02f, 0.98f)
        val sy = yPct.coerceIn(0.02f, 0.98f)
        _state.value = _state.value.copy(remainingStatsPosXpct = sx, remainingStatsPosYpct = sy)
        schedulePersistState(immediate = true)
        broadcastRemainingStatsOverlay()
    }

    fun adjustRemainingStatsPos(dx: Float, dy: Float) {
        val s = _state.value
        setRemainingStatsPos(s.remainingStatsPosXpct + dx, s.remainingStatsPosYpct + dy)
    }

    fun adjustRemainingStatsScale(delta: Float) {
        val s = (_state.value.remainingStatsScale + delta).coerceIn(0.35f, 2.20f)
        _state.value = _state.value.copy(remainingStatsScale = s)
        schedulePersistState(immediate = true)
        broadcastRemainingStatsOverlay()
        scheduleBroadcastToOverlay(immediate = true)
    }

    fun adjustRemainingStatsFontSize(delta: Float) {
        val s = (_state.value.remainingStatsFontSizeSp + delta).coerceIn(8f, 24f)
        _state.value = _state.value.copy(remainingStatsFontSizeSp = s)
        schedulePersistState(immediate = true)
        broadcastRemainingStatsOverlay()
        scheduleBroadcastToOverlay(immediate = true)
    }

    // =========================================================================
    // COALESCED OVERLAY SYNCHRONIZATION (ANTI-SPAM BROADCAST)
    // =========================================================================
    private fun scheduleBroadcastToOverlay(immediate: Boolean = false, fastAnim: Boolean = false) {
        if (!_state.value.isEnabled) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastBroadcastTimeMs
        val throttleMs = if (fastAnim) FAST_ANIM_THROTTLE_MS else BROADCAST_THROTTLE_MS

        if (immediate || elapsed >= throttleMs) {
            pendingBroadcastJob?.cancel()
            pendingBroadcastJob = null
            lastBroadcastTimeMs = now
            broadcastToOverlayNow()
        } else if (pendingBroadcastJob?.isActive != true) {
            val waitMs = (throttleMs - elapsed).coerceIn(16L, throttleMs)
            pendingBroadcastJob = scope.launch {
                delay(waitMs)
                lastBroadcastTimeMs = System.currentTimeMillis()
                broadcastToOverlayNow()
            }
        }
    }

    private fun buildConsistentMultiLineUnrevealedText(s: QuranKhatamState): String {
        if (s.perAyahThreeZoneMode) {
            // Pada Mode 3-Zona Aman Per-Ayat, Zona Tengah HANYA menampilkan 1 ayat fokus aktif
            return (s.unrevealedArabicText + " ﴿" + QuranTextCatalog.toArabicDigits(s.currentAyahNumber) + "﴾").trim()
        }
        val targetLines = s.maxArabicLines
        if (targetLines <= 0) {
            return s.unrevealedArabicText
        }
        val minCharsNeeded = targetLines * 145
        val sb = StringBuilder(minCharsNeeded + 128)
        sb.append(s.unrevealedArabicText)
        sb.append(" ﴿").append(QuranTextCatalog.toArabicDigits(s.currentAyahNumber)).append("﴾ ")

        var curSurah = s.currentSurahNumber
        var curAyah = s.currentAyahNumber
        var appendedCount = 0
        while ((s.revealedArabicText.length + sb.length) < minCharsNeeded && appendedCount < 10) {
            val (nextS, nextA) = QuranTextCatalog.getNextCuratedCoord(curSurah, curAyah)
            val nextVerse = QuranTextCatalog.getVerse(nextS, nextA)
            sb.append(nextVerse.arabicText)
            sb.append(" ﴿").append(QuranTextCatalog.toArabicDigits(nextA)).append("﴾ ")
            curSurah = nextS
            curAyah = nextA
            appendedCount++
        }
        return sb.toString().trim()
    }

    fun buildOverlayPayload(): Pair<String, String> {
        val s = _state.value
        val headerText = "QS. ${s.currentVerse.surahNameLatin} [${s.currentSurahNumber}:${s.currentAyahNumber}]"
        val isPrayerFresh = s.lastPrayerText.isNotBlank() && (System.currentTimeMillis() - s.lastPrayerAtMs < 15000L)
        val prayerSender = if (isPrayerFresh) s.lastPrayerSender else ""
        val prayerText = if (isPrayerFresh) s.lastPrayerText else ""
        val displayUnrevealed = buildConsistentMultiLineUnrevealedText(s)
        val remStats = buildRemainingStatsPayload()
        val fullRevealedArabic = if (!s.perAyahThreeZoneMode && retainedArabicText.isNotBlank()) {
            retainedArabicText + s.revealedArabicText
        } else {
            s.revealedArabicText
        }

        // Data 3-Zona Aman (Atas: Ayat N-1 redup, Tengah: Ayat N fokus aman, Bawah: Ayat N+1 antre)
        val isInitialFirstAyah = (s.currentSurahNumber == 1 && s.currentAyahNumber == 1 && !hasCompletedAnyAyahInSession && s.totalAyahsCompletedSession == 0)
        val (prevS, prevA) = QuranTextCatalog.getPrevCuratedCoord(s.currentSurahNumber, s.currentAyahNumber)
        val prevVerse = QuranTextCatalog.getVerse(prevS, prevA)
        val prevVerseLabel = if (isInitialFirstAyah) "🕌 Pembuka Tilawah" else "QS. ${prevVerse.surahNameLatin} [$prevS:$prevA]"
        val prevVerseArabic = if (isInitialFirstAyah) {
            "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ"
        } else {
            "${prevVerse.arabicText} ﴿${QuranTextCatalog.toArabicDigits(prevA)}﴾"
        }

        val (prevPrevS, prevPrevA) = QuranTextCatalog.getPrevCuratedCoord(prevS, prevA)
        val prevPrevVerse = QuranTextCatalog.getVerse(prevPrevS, prevPrevA)
        val prevPrevVerseArabic = if (s.currentSurahNumber == 1 && s.currentAyahNumber <= 2 && s.totalAyahsCompletedSession <= 1) {
            "أَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ"
        } else {
            "${prevPrevVerse.arabicText} ﴿${QuranTextCatalog.toArabicDigits(prevPrevA)}﴾"
        }

        val (nextS, nextA) = QuranTextCatalog.getNextCuratedCoord(s.currentSurahNumber, s.currentAyahNumber)
        val nextVerse = QuranTextCatalog.getVerse(nextS, nextA)
        val nextVerseLabel = "QS. ${nextVerse.surahNameLatin} [$nextS:$nextA]"
        val nextVerseArabic = "${nextVerse.arabicText} ﴿${QuranTextCatalog.toArabicDigits(nextA)}﴾"

        val imam = QuranCatalog.findImamById(s.selectedImamId)
        val imamBadge = if (s.imamAutoPlayEnabled) {
            "🎙️ ${imam.displayName}"
        } else {
            "🕌 3-Zona Aman"
        }

        val payloadContent = buildString(768) {
            append(headerText)
            append("|||")
            append(fullRevealedArabic)
            append("|||")
            append(displayUnrevealed)
            append("|||")
            append(if (s.showTranslation) s.currentVerse.indonesianText else "")
            append("|||")
            append(s.lastContributorName)
            append("|||")
            append(s.lastActionDescription)
            append("|||")
            append("${s.revealedCharCount}/${s.totalCharsInAyah}")
            append("|||")
            append("${s.totalLettersWrittenSession}")
            append("|||")
            append(prayerSender)
            append("|||")
            append(prayerText)
            append("|||")
            append(remStats)
            append("|||")
            append(prevVerseLabel)
            append("|||")
            append(prevVerseArabic)
            append("|||")
            append(nextVerseLabel)
            append("|||")
            append(nextVerseArabic)
            append("|||")
            append(prevPrevVerseArabic)
            append("|||")
            append(imamBadge)
        }
        return Pair(headerText, payloadContent)
    }

    fun buildRemainingStatsPayload(): String {
        val s = _state.value
        val sInfo = QuranTextCatalog.getSurahInfo(s.currentSurahNumber)
        val currentJuz = s.currentVerse.juzNumber.coerceIn(1, 30)
        val remLetters = (s.totalCharsInAyah - s.revealedCharCount).coerceAtLeast(0)
        val remAyahsInSurah = (sInfo.ayahCount - s.currentAyahNumber).coerceAtLeast(0)
        val remSurahs = (114 - s.currentSurahNumber).coerceAtLeast(0)
        val remJuz = (30 - currentJuz).coerceAtLeast(0)
        return "QS. ${sInfo.nameLatin} ${s.currentSurahNumber}:${s.currentAyahNumber}/${sInfo.ayahCount} • Juz $currentJuz|||$remLetters|||$remAyahsInSurah|||$remSurahs|||$remJuz"
    }

    private fun broadcastToOverlayNow() {
        val s = _state.value
        if (!s.isEnabled) return

        val (headerText, payloadContent) = buildOverlayPayload()
        val formatModeStr = if (s.perAyahThreeZoneMode) {
            String.format(java.util.Locale.US, "THREE_ZONE_%.3f_%d", s.scrollTransitionPhase.coerceIn(0f, 1f), s.maxArabicLines)
        } else {
            "MAX_LINES_${s.maxArabicLines}"
        }

        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = headerText,
            content = payloadContent,
            isGift = false,
            bannerType = "QURAN_KHATAM",
            iconMode = "PRAYER",
            customPrefix = "${if (s.showTranslation) "SHOW_TRANS" else "HIDE_TRANS"}:::${s.latinFontSizeSp}:::${if (s.showNotifUnderAyah) "SHOW_NOTIF" else "HIDE_NOTIF"}",
            formatMode = formatModeStr,
            durationMs = 86400000L, // Tetap tampil selama live aktif
            posXpct = s.posXpct,
            posYpct = s.posYpct,
            rotation = s.overlayRotation,
            scale = s.overlayScale,
            isMirrored = s.overlayMirrored,
            fontSizeSp = s.arabicFontSizeSp,
            capsulePaddingH = (s.cardWidthPct * 100).toInt(),
            capsulePaddingV = s.cardPaddingV
        )

        if (s.enableRemainingStatsOverlay) {
            broadcastRemainingStatsOverlay()
        } else {
            // Sinkronkan progres ke overlay analitik terpadu jika aktif (tanpa memicu rekursi singleton)
            com.tajasb.eventrelay.LiveEventReceiver.peekInstance()?.scheduleRefreshEventStats()
        }
    }

    fun broadcastRemainingStatsOverlay() {
        val s = _state.value
        if (!s.enableRemainingStatsOverlay || !s.isEnabled) return

        val payload = buildRemainingStatsPayload()

        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "SISA_KHATAM",
            content = payload,
            isGift = false,
            bannerType = "QURAN_STATS",
            iconMode = "NONE",
            durationMs = 86400000L,
            posXpct = s.remainingStatsPosXpct,
            posYpct = s.remainingStatsPosYpct,
            scale = s.remainingStatsScale,
            fontSizeSp = s.remainingStatsFontSizeSp
        )
        com.tajasb.eventrelay.LiveEventReceiver.peekInstance()?.scheduleRefreshEventStats()
    }

    fun refreshOverlay() {
        if (_state.value.isEnabled) {
            scheduleBroadcastToOverlay(immediate = true)
        }
    }

    private fun hideFromOverlay() {
        pendingBroadcastJob?.cancel()
        pendingBroadcastJob = null
        OverlayBannerBroadcaster.broadcastHide(context, "QURAN_KHATAM")
        OverlayBannerBroadcaster.broadcastHide(context, "QURAN_STATS")
    }
}
