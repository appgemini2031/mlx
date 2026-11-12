package com.tajasb.game

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tajasb.eventrelay.LiveEventReceiver
import com.tajasb.eventrelay.ViewerAvatarCache
import com.tajasb.notif.OverlayBannerBroadcaster
import com.tajasb.quran.InteractiveQuranKhatamEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * InteractiveKingOfThroneEngine:
 * Mesin permainan interaktif "👑 Raja Singgasana (Rebutan Kursi Sultan)" untuk TikTok Live.
 *
 * Mekanisme Permainan:
 * - Jika Singgasana kosong, penonton pertama yang Tap Like, Komentar, atau Kirim Gift langsung naik takhta menjadi Raja.
 * - Jika Raja yang sedang bertahta berinteraksi (Tap Like / Chat / Gift), poinnya menambah tebal Perisai Takhta (DEFEND).
 * - Jika penonton lain (Penantang) berinteraksi, poin mereka menggempur/mengikis Perisai Takhta (ATTACK).
 * - Saat Perisai Takhta habis (0 HP), terjadi KUDETA (USURP): Penantang menggulingkan Raja lama dan menduduki Singgasana Emas!
 * - Durasi kekuasaan Raja dicatat secara real-time ke Rekor Penguasa Terlama (Hall of Fame).
 */
class InteractiveKingOfThroneEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KingOfThroneEngine"
        private const val PREFS_NAME = "king_of_throne_storage_v1"
        private const val BROADCAST_THROTTLE_MS = 120L
        private const val USURP_CELEBRATION_DURATION_MS = 5000L

        @Volatile
        private var instance: InteractiveKingOfThroneEngine? = null

        fun peekInstance(): InteractiveKingOfThroneEngine? = instance

        fun getInstance(context: Context): InteractiveKingOfThroneEngine {
            return instance ?: synchronized(this) {
                instance ?: InteractiveKingOfThroneEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(KingOfThroneState())
    val state: StateFlow<KingOfThroneState> = _state.asStateFlow()

    // Poin akumulasi per penonton pada ronde berjalan
    private val userTotalPoints = ConcurrentHashMap<String, Long>()
    private val userAvatarBase64Map = ConcurrentHashMap<String, String>()

    private var broadcastJob: Job? = null
    @Volatile private var isPendingBroadcast = false
    private var lastBroadcastTime = 0L

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        // Pastikan default OFF saat pertama kali diinisialisasi
        if (!prefs.getBoolean("key_default_kot_off_v1", false)) {
            prefs.edit()
                .putBoolean("is_enabled", false)
                .putBoolean("key_default_kot_off_v1", true)
                .apply()
        }
        val enabled = prefs.getBoolean("is_enabled", false)
        val title = prefs.getString("throne_title", "RAJA SINGGASANA") ?: "RAJA SINGGASANA"
        val baseShield = prefs.getFloat("base_shield_on_usurp", 250.0f).coerceIn(50.0f, 10000.0f)
        val round = prefs.getInt("current_round", 1).coerceAtLeast(1)
        val longestName = prefs.getString("longest_reign_name", "-") ?: "-"
        val longestSec = prefs.getLong("longest_reign_sec", 0L)
        val highestName = prefs.getString("highest_king_name", "-") ?: "-"
        val highestScore = prefs.getLong("highest_king_score", 0L)
        val likeW = prefs.getFloat("like_weight", 1.0f).coerceIn(0.1f, 50.0f)
        val chatW = prefs.getFloat("chat_weight", 15.0f).coerceIn(1.0f, 200.0f)
        val giftMult = prefs.getFloat("gift_multiplier", 25.0f).coerceIn(1.0f, 500.0f)
        val posX = prefs.getFloat("pos_x_pct", 0.50f).coerceIn(0.05f, 0.95f)
        val posY = prefs.getFloat("pos_y_pct", 0.28f).coerceIn(0.05f, 0.95f)
        val scale = prefs.getFloat("overlay_scale", 1.0f).coerceIn(0.5f, 2.5f)
        val rot = prefs.getInt("overlay_rotation", 0)
        val mir = prefs.getBoolean("is_mirrored", false)

        _state.value = KingOfThroneState(
            isEnabled = enabled,
            isArenaDisplayActive = false,
            throneTitle = title,
            baseShieldOnUsurp = baseShield,
            maxThroneShieldHp = baseShield,
            currentRound = round,
            longestReignName = longestName,
            longestReignSec = longestSec,
            highestKingName = highestName,
            highestKingScore = highestScore,
            likeWeight = likeW,
            chatWeight = chatW,
            giftMultiplier = giftMult,
            posXpct = posX,
            posYpct = posY,
            overlayScale = scale,
            overlayRotation = rot,
            isMirrored = mir
        )
    }

    private fun persistState() {
        val s = _state.value
        prefs.edit()
            .putBoolean("is_enabled", s.isEnabled)
            .putString("throne_title", s.throneTitle)
            .putFloat("base_shield_on_usurp", s.baseShieldOnUsurp)
            .putInt("current_round", s.currentRound)
            .putString("longest_reign_name", s.longestReignName)
            .putLong("longest_reign_sec", s.longestReignSec)
            .putString("highest_king_name", s.highestKingName)
            .putLong("highest_king_score", s.highestKingScore)
            .putFloat("like_weight", s.likeWeight)
            .putFloat("chat_weight", s.chatWeight)
            .putFloat("gift_multiplier", s.giftMultiplier)
            .putFloat("pos_x_pct", s.posXpct)
            .putFloat("pos_y_pct", s.posYpct)
            .putFloat("overlay_scale", s.overlayScale)
            .putInt("overlay_rotation", s.overlayRotation)
            .putBoolean("is_mirrored", s.isMirrored)
            .apply()
    }

    // =========================================================================
    // KONTROL PUBLIK & SINKRONISASI REMOTE 2-HP (DEVICE 1 <-> DEVICE 2)
    // =========================================================================

    @Synchronized
    fun setEnabled(enabled: Boolean, propagateRemote: Boolean = true) {
        if (_state.value.isEnabled == enabled) return
        val nextStillActive = enabled || _state.value.isArenaDisplayActive
        if (!nextStillActive) {
            autoTrafficJob?.cancel()
            autoTrafficJob = null
        }
        _state.value = _state.value.copy(
            isEnabled = enabled,
            isAutoTraffic = if (nextStillActive) _state.value.isAutoTraffic else false
        )
        persistState()

        if (enabled) {
            // Matikan overlay game lain agar kamera virtual tidak bertumpuk
            InteractiveQuranKhatamEngine.peekInstance()?.setEnabled(false)
            InteractiveTugOfWarEngine.peekInstance()?.setEnabled(false, propagateRemote = false)
            InteractiveBeyBattleEngine.peekInstance()?.setEnabled(false)
            InteractiveTrooperRoyaleEngine.peekInstance()?.setEnabled(false)
            scheduleBroadcastToOverlay(immediate = true)
        } else {
            hideFromOverlay()
        }
        if (propagateRemote) {
            sendRemoteCommand("set_enabled", mapOf("enabled" to enabled))
        }
    }

    @Synchronized
    fun toggleEnabled(): Boolean {
        val next = !_state.value.isEnabled
        setEnabled(next)
        return next
    }

    /**
     * Mengaktifkan atau menonaktifkan Layar Penuh Arena Raja Singgasana (Device 2 - Mode Game Seluler).
     */
    @Synchronized
    fun setArenaDisplayActive(active: Boolean) {
        if (_state.value.isArenaDisplayActive == active) return
        if (active) {
            InteractiveQuranKhatamEngine.peekInstance()?.setEnabled(false)
            InteractiveTugOfWarEngine.peekInstance()?.setArenaDisplayActive(false)
            InteractiveBeyBattleEngine.peekInstance()?.setArenaDisplayActive(false)
            InteractiveTrooperRoyaleEngine.peekInstance()?.setArenaDisplayActive(false)
        }
        val nextStillActive = active || _state.value.isEnabled
        if (!nextStillActive) {
            autoTrafficJob?.cancel()
            autoTrafficJob = null
        }
        _state.value = _state.value.copy(
            isArenaDisplayActive = active,
            isAutoTraffic = if (nextStillActive) _state.value.isAutoTraffic else false,
            lastActionText = if (active && !_state.value.hasKing) {
                "Arena Raja Singgasana Aktif! Tap Like, Komen, atau Gift untuk menduduki takhta!"
            } else {
                _state.value.lastActionText
            }
        )
        broadcastStateSyncToBroker()
    }

    private var autoTrafficJob: Job? = null

    @Synchronized
    fun setHubActive(active: Boolean) {
        if (_state.value.isHubActive == active) return
        _state.value = _state.value.copy(isHubActive = active)
    }

    @Synchronized
    fun setAutoTraffic(enabled: Boolean) {
        if (_state.value.isAutoTraffic == enabled) return
        _state.value = _state.value.copy(isAutoTraffic = enabled)
        autoTrafficJob?.cancel()
        if (enabled) {
            autoTrafficJob = scope.launch(Dispatchers.Default) {
                val sampleNames = listOf("Sultan_Bigo", "BigoQueen99", "Aisyah_Live", "Raja_Sawer", "BigoGamer_ID", "Ksatria_Bigo")
                while (isActive && _state.value.isAutoTraffic) {
                    delay((2200L..4200L).random())
                    if (!_state.value.isGameActive) continue
                    val sender = sampleNames.random()
                    val roll = kotlin.random.Random.nextFloat()
                    when {
                        roll < 0.50f -> onChatEvent(sender, "Kudeta Takhta!")
                        roll < 0.85f -> onLikeEvent(sender, (5..15).random())
                        else -> onGiftEvent(sender, "Rose", 1, 1)
                    }
                }
            }
        }
    }

    @Synchronized
    fun toggleAutoTraffic(): Boolean {
        val next = !_state.value.isAutoTraffic
        setAutoTraffic(next)
        return next
    }

    @Synchronized
    fun setThroneTitle(title: String, propagateRemote: Boolean = true) {
        val safeTitle = title.trim().ifBlank { "RAJA SINGGASANA" }.uppercase()
        _state.value = _state.value.copy(
            throneTitle = safeTitle,
            lastActionText = "Gelar Takhta diatur menjadi: 👑 $safeTitle"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("set_title", mapOf("title" to safeTitle))
        }
    }

    @Synchronized
    fun setBaseShield(shieldPoints: Float, propagateRemote: Boolean = true) {
        val safe = shieldPoints.coerceIn(50.0f, 10000.0f)
        val cur = _state.value
        val updatedShield = if (!cur.hasKing) 0f else cur.throneShieldHp.coerceAtLeast(safe * 0.5f)
        val updatedMax = if (!cur.hasKing) safe else maxOf(cur.maxThroneShieldHp, safe)
        _state.value = cur.copy(
            baseShieldOnUsurp = safe,
            throneShieldHp = updatedShield,
            maxThroneShieldHp = updatedMax,
            lastActionText = "Perisai Awal Takhta diatur ke ${safe.toInt()} HP"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("set_base_shield", mapOf("baseShield" to safe.toDouble()))
        }
    }

    @Synchronized
    fun setWeights(like: Float, chat: Float, giftMultiplier: Float, propagateRemote: Boolean = true) {
        val safeLike = like.coerceIn(0.1f, 50.0f)
        val safeChat = chat.coerceIn(1.0f, 200.0f)
        val safeGift = giftMultiplier.coerceIn(1.0f, 500.0f)
        _state.value = _state.value.copy(
            likeWeight = safeLike,
            chatWeight = safeChat,
            giftMultiplier = safeGift,
            lastActionText = "Bobot diatur: Like=+${safeLike.toInt()}, Chat=+${safeChat.toInt()}, Koin=+${safeGift.toInt()}"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand(
                "set_weights",
                mapOf(
                    "likeWeight" to safeLike.toDouble(),
                    "chatWeight" to safeChat.toDouble(),
                    "giftMultiplier" to safeGift.toDouble()
                )
            )
        }
    }

    @Synchronized
    fun setTransform(posX: Float, posY: Float, scale: Float, rotation: Int = 0, mirrored: Boolean = false) {
        _state.value = _state.value.copy(
            posXpct = posX.coerceIn(0.02f, 0.98f),
            posYpct = posY.coerceIn(0.02f, 0.98f),
            overlayScale = scale.coerceIn(0.5f, 2.5f),
            overlayRotation = rotation,
            isMirrored = mirrored
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
    }

    @Synchronized
    fun startNewRound(propagateRemote: Boolean = true) {
        val cur = _state.value
        updateLongestReignIfNeeded(cur)
        val nextR = cur.currentRound + 1
        userTotalPoints.clear()
        _state.value = cur.copy(
            currentRound = nextR,
            currentKingName = "",
            currentKingAvatarBase64 = "",
            currentKingScore = 0L,
            reignStartedAtMs = 0L,
            throneShieldHp = 0.0f,
            maxThroneShieldHp = cur.baseShieldOnUsurp,
            topChallengers = emptyList(),
            totalUsurpCount = 0,
            usurpCelebrationUntilMs = 0L,
            lastUsurperName = "",
            lastDethronedName = "",
            lastActionType = "IDLE",
            lastActionText = "Ronde $nextR dimulai! Singgasana terbuka untuk diperebutkan!"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("next_round")
        }
    }

    @Synchronized
    fun resetThroneAndRecords(propagateRemote: Boolean = true) {
        userTotalPoints.clear()
        userAvatarBase64Map.clear()
        _state.value = _state.value.copy(
            currentRound = 1,
            currentKingName = "",
            currentKingAvatarBase64 = "",
            currentKingScore = 0L,
            reignStartedAtMs = 0L,
            throneShieldHp = 0.0f,
            maxThroneShieldHp = _state.value.baseShieldOnUsurp,
            topChallengers = emptyList(),
            longestReignName = "-",
            longestReignSec = 0L,
            highestKingName = "-",
            highestKingScore = 0L,
            totalUsurpCount = 0,
            usurpCelebrationUntilMs = 0L,
            lastUsurperName = "",
            lastDethronedName = "",
            lastActionType = "IDLE",
            lastActionText = "Singgasana & seluruh rekor telah direset!"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("reset_all")
        }
    }

    @Synchronized
    fun manualAction(
        senderName: String,
        points: Float,
        actionLabel: String = "Aksi Host",
        propagateRemote: Boolean = true
    ) {
        processInteractionInternal(senderName = senderName, points = points, actionLabel = actionLabel)
        if (propagateRemote) {
            sendRemoteCommand(
                "manual_action",
                mapOf(
                    "senderName" to senderName,
                    "points" to points.toDouble(),
                    "actionLabel" to actionLabel
                )
            )
        }
    }

    @Synchronized
    fun boostKingShield(extraShield: Float, propagateRemote: Boolean = true) {
        val cur = _state.value
        if (!cur.hasKing) {
            processInteractionInternal("Sultan Host", extraShield, "🛡️ Perisai Awal")
        } else {
            val newShield = cur.throneShieldHp + extraShield.coerceAtLeast(10f)
            val newMax = maxOf(cur.maxThroneShieldHp, newShield)
            _state.value = cur.copy(
                throneShieldHp = newShield,
                maxThroneShieldHp = newMax,
                lastActionType = "DEFEND",
                lastActionText = "🛡️ Perisai Raja ${cur.currentKingName} ditambah +${extraShield.toInt()} HP!"
            )
            scheduleBroadcastToOverlay(immediate = true)
        }
        if (propagateRemote) {
            sendRemoteCommand("boost_shield", mapOf("extraShield" to extraShield.toDouble()))
        }
    }

    // =========================================================================
    // EVENT INGESTION (CHAT, LIKE, GIFT) DARI BROKER TIKTOK LIVE
    // =========================================================================

    fun onChatEvent(senderName: String, message: String, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        cacheAvatarAsync(senderName, avatarUrl)
        val s = _state.value
        val cleanMsg = message.trim()
        val bonusMultiplier = if (
            cleanMsg.contains("raja", ignoreCase = true) ||
            cleanMsg.contains("sultan", ignoreCase = true) ||
            cleanMsg.contains("kudeta", ignoreCase = true) ||
            cleanMsg.contains("takhta", ignoreCase = true) ||
            cleanMsg.contains("tahta", ignoreCase = true) ||
            cleanMsg.contains("ambil", ignoreCase = true) ||
            cleanMsg == "1"
        ) 1.25f else 1.0f

        val pts = s.chatWeight * bonusMultiplier
        processInteractionInternal(
            senderName = senderName,
            points = pts,
            actionLabel = "💬 \"$cleanMsg\""
        )
    }

    fun onLikeEvent(senderName: String, count: Int, avatarUrl: String = "") {
        if (!_state.value.isGameActive || count <= 0) return
        cacheAvatarAsync(senderName, avatarUrl)
        val s = _state.value
        val pts = count * s.likeWeight
        processInteractionInternal(
            senderName = senderName,
            points = pts,
            actionLabel = if (count > 1) "❤️ +$count Like" else "❤️ Tap Like"
        )
    }

    fun onGiftEvent(senderName: String, giftName: String, count: Int, coins: Int, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        cacheAvatarAsync(senderName, avatarUrl)
        val s = _state.value
        val safeCoins = if (coins > 0) coins else 1
        val pts = safeCoins * count.coerceAtLeast(1) * s.giftMultiplier
        processInteractionInternal(
            senderName = senderName,
            points = pts,
            actionLabel = "🎁 $giftName ($safeCoins 💎)"
        )
    }

    fun onJoinEvent(senderName: String, avatarUrl: String = "") {
        if (!_state.value.isGameActive) return
        cacheAvatarAsync(senderName, avatarUrl)
        val s = _state.value
        if (!s.hasKing) {
            processInteractionInternal(senderName, 10f, "👋 Bergabung & Duduk")
        } else {
            // Berikan sedikit guncangan perisai dari penantang baru
            processInteractionInternal(senderName, (s.chatWeight * 0.5f).coerceAtLeast(1f), "👋 Masuk Menantang")
        }
    }

    private fun cacheAvatarAsync(senderName: String, avatarUrl: String) {
        if (avatarUrl.isBlank() || senderName.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val b64 = ViewerAvatarCache.getOrFetchCompactBase64(avatarUrl)
                if (b64.isNotBlank()) {
                    userAvatarBase64Map[senderName] = b64
                    synchronized(this@InteractiveKingOfThroneEngine) {
                        val cur = _state.value
                        if (cur.currentKingName.equals(senderName, ignoreCase = true) && cur.currentKingAvatarBase64 != b64) {
                            _state.value = cur.copy(currentKingAvatarBase64 = b64)
                        }
                        refreshChallengersLeaderboardLocked()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    // =========================================================================
    // LOGIKA INTI PERTAHANAN TAKHTA & KUDETA (USURP MECHANIC)
    // =========================================================================

    @Synchronized
    private fun processInteractionInternal(senderName: String, points: Float, actionLabel: String) {
        val safeSender = senderName.trim().ifBlank { "Penonton" }
        val safePts = points.coerceAtLeast(1.0f)
        val now = System.currentTimeMillis()
        val s = _state.value

        val newTotalForSender = (userTotalPoints[safeSender] ?: 0L) + safePts.toLong()
        userTotalPoints[safeSender] = newTotalForSender
        val senderAvatar = userAvatarBase64Map[safeSender] ?: ""

        // 1. Jika Singgasana masih kosong -> Langsung naik takhta!
        if (!s.hasKing) {
            val initShield = s.baseShieldOnUsurp + safePts
            val highKingName = if (newTotalForSender > s.highestKingScore) safeSender else s.highestKingName
            val highKingScore = maxOf(s.highestKingScore, newTotalForSender)

            _state.value = s.copy(
                currentKingName = safeSender,
                currentKingAvatarBase64 = senderAvatar,
                currentKingScore = newTotalForSender,
                reignStartedAtMs = now,
                throneShieldHp = initShield,
                maxThroneShieldHp = initShield,
                highestKingName = highKingName,
                highestKingScore = highKingScore,
                lastActionType = "USURP",
                lastUsurperName = safeSender,
                lastDethronedName = "",
                usurpCelebrationUntilMs = now + USURP_CELEBRATION_DURATION_MS,
                lastActionText = "👑 $safeSender menduduki Singgasana Pertama! ($actionLabel)"
            )
            refreshChallengersLeaderboardLocked()
            persistState()
            scheduleBroadcastToOverlay(immediate = true)
            return
        }

        // 2. Jika yang berinteraksi adalah Raja yang sedang bertahta -> Perkuat Perisai (DEFEND)!
        if (s.currentKingName.equals(safeSender, ignoreCase = true)) {
            val newShield = (s.throneShieldHp + safePts).coerceAtMost(50000f)
            val newMax = maxOf(s.maxThroneShieldHp, newShield)
            val currentReignSec = if (s.reignStartedAtMs > 0L) ((now - s.reignStartedAtMs) / 1000L).coerceAtLeast(0L) else 0L
            val isNewLongest = currentReignSec > s.longestReignSec
            val highKingName = if (newTotalForSender > s.highestKingScore) safeSender else s.highestKingName
            val highKingScore = maxOf(s.highestKingScore, newTotalForSender)

            _state.value = s.copy(
                currentKingScore = newTotalForSender,
                currentKingAvatarBase64 = senderAvatar.ifBlank { s.currentKingAvatarBase64 },
                throneShieldHp = newShield,
                maxThroneShieldHp = newMax,
                longestReignName = if (isNewLongest) safeSender else s.longestReignName,
                longestReignSec = if (isNewLongest) currentReignSec else s.longestReignSec,
                highestKingName = highKingName,
                highestKingScore = highKingScore,
                lastActionType = "DEFEND",
                lastActionText = "🛡️ Raja $safeSender menambah Perisai +${safePts.toInt()} HP ($actionLabel)"
            )
            refreshChallengersLeaderboardLocked()
            scheduleBroadcastToOverlay(immediate = safePts >= 100f)
            return
        }

        // 3. Jika yang berinteraksi adalah Penantang -> Serang Perisai Singgasana (ATTACK / USURP)!
        val remainingShield = s.throneShieldHp - safePts
        if (remainingShield > 0.0f) {
            // Raja masih bertahan, tapi perisainya terkikis
            val currentReignSec = if (s.reignStartedAtMs > 0L) ((now - s.reignStartedAtMs) / 1000L).coerceAtLeast(0L) else 0L
            val isNewLongest = currentReignSec > s.longestReignSec

            _state.value = s.copy(
                throneShieldHp = remainingShield,
                longestReignName = if (isNewLongest) s.currentKingName else s.longestReignName,
                longestReignSec = if (isNewLongest) currentReignSec else s.longestReignSec,
                lastActionType = "ATTACK",
                lastActionText = "⚔️ $safeSender menggempur Singgasana -${safePts.toInt()} HP! ($actionLabel)"
            )
            refreshChallengersLeaderboardLocked()
            scheduleBroadcastToOverlay(immediate = safePts >= 100f)
        } else {
            // KUDETA BERHASIL! Perisai Raja hancur (<= 0) -> Penantang naik takhta!
            val oldKing = s.currentKingName
            val oldKingReignSec = if (s.reignStartedAtMs > 0L) ((now - s.reignStartedAtMs) / 1000L).coerceAtLeast(1L) else 1L
            val bestReignName = if (oldKingReignSec >= s.longestReignSec) oldKing else s.longestReignName
            val bestReignSec = maxOf(s.longestReignSec, oldKingReignSec)

            val overflowPts = (-remainingShield).coerceAtLeast(0.0f)
            val newKingShield = (s.baseShieldOnUsurp + overflowPts).coerceAtMost(50000f)
            val highKingName = if (newTotalForSender > s.highestKingScore) safeSender else s.highestKingName
            val highKingScore = maxOf(s.highestKingScore, newTotalForSender)

            Log.i(TAG, "King of Throne USURP: $safeSender dethroned $oldKing (reigned ${oldKingReignSec}s)")

            _state.value = s.copy(
                currentKingName = safeSender,
                currentKingAvatarBase64 = senderAvatar,
                currentKingScore = newTotalForSender,
                reignStartedAtMs = now,
                throneShieldHp = newKingShield,
                maxThroneShieldHp = newKingShield,
                longestReignName = bestReignName,
                longestReignSec = bestReignSec,
                highestKingName = highKingName,
                highestKingScore = highKingScore,
                totalUsurpCount = s.totalUsurpCount + 1,
                lastUsurperName = safeSender,
                lastDethronedName = oldKing,
                usurpCelebrationUntilMs = now + USURP_CELEBRATION_DURATION_MS,
                lastActionType = "USURP",
                lastActionText = "👑 KUDETA! $safeSender menggulingkan $oldKing & merebut Singgasana! ($actionLabel)"
            )
            refreshChallengersLeaderboardLocked()
            persistState()
            scheduleBroadcastToOverlay(immediate = true)
        }
    }

    private fun updateLongestReignIfNeeded(s: KingOfThroneState) {
        if (!s.hasKing || s.reignStartedAtMs <= 0L) return
        val reignSec = ((System.currentTimeMillis() - s.reignStartedAtMs) / 1000L).coerceAtLeast(1L)
        if (reignSec > s.longestReignSec) {
            _state.value = s.copy(
                longestReignName = s.currentKingName,
                longestReignSec = reignSec
            )
        }
    }

    private fun refreshChallengersLeaderboardLocked() {
        val cur = _state.value
        val kingName = cur.currentKingName
        val topList = userTotalPoints.entries
            .filter { !it.key.equals(kingName, ignoreCase = true) }
            .sortedByDescending { it.value }
            .take(4)
            .map { entry ->
                ThroneChallengerEntry(
                    name = entry.key,
                    points = entry.value,
                    avatarBase64 = userAvatarBase64Map[entry.key] ?: ""
                )
            }
        _state.value = cur.copy(topChallengers = topList)
    }

    // =========================================================================
    // PROTOKOL SINKRONISASI 2-HP (DEVICE 1 KONTROLER <-> DEVICE 2 ARENA)
    // =========================================================================

    private fun sendRemoteCommand(cmdName: String, params: Map<String, Any?> = emptyMap()) {
        scope.launch {
            try {
                val receiver = LiveEventReceiver.peekInstance() ?: return@launch
                val json = JSONObject().apply {
                    put("action", "kot_cmd")
                    put("cmd", cmdName)
                    for ((k, v) in params) {
                        if (v != null) put(k, v)
                    }
                }
                receiver.sendWebSocketTextFrame(json.toString())
            } catch (_: Throwable) {}
        }
    }

    private fun broadcastStateSyncToBroker() {
        scope.launch {
            try {
                val receiver = LiveEventReceiver.peekInstance() ?: return@launch
                val s = _state.value
                val challengersArr = JSONArray()
                s.topChallengers.forEach { ch ->
                    challengersArr.put(
                        JSONObject().apply {
                            put("name", ch.name)
                            put("points", ch.points)
                        }
                    )
                }
                val json = JSONObject().apply {
                    put("action", "kot_state_sync")
                    put("throneTitle", s.throneTitle)
                    put("currentKingName", s.currentKingName)
                    put("currentKingScore", s.currentKingScore)
                    put("reignStartedAtMs", s.reignStartedAtMs)
                    put("throneShieldHp", s.throneShieldHp.toDouble())
                    put("maxThroneShieldHp", s.maxThroneShieldHp.toDouble())
                    put("baseShieldOnUsurp", s.baseShieldOnUsurp.toDouble())
                    put("longestReignName", s.longestReignName)
                    put("longestReignSec", s.longestReignSec)
                    put("highestKingName", s.highestKingName)
                    put("highestKingScore", s.highestKingScore)
                    put("totalUsurpCount", s.totalUsurpCount)
                    put("currentRound", s.currentRound)
                    put("likeWeight", s.likeWeight.toDouble())
                    put("chatWeight", s.chatWeight.toDouble())
                    put("giftMultiplier", s.giftMultiplier.toDouble())
                    put("lastActionText", s.lastActionText)
                    put("lastActionType", s.lastActionType)
                    put("lastUsurperName", s.lastUsurperName)
                    put("lastDethronedName", s.lastDethronedName)
                    put("usurpCelebrationUntilMs", s.usurpCelebrationUntilMs)
                    put("topChallengers", challengersArr)
                }
                receiver.sendWebSocketTextFrame(json.toString())
            } catch (_: Throwable) {}
        }
    }

    @Synchronized
    fun applyRemoteCommand(dataObj: JSONObject) {
        val cmd = dataObj.optString("cmd", "").lowercase()
        when (cmd) {
            "set_title" -> {
                val t = dataObj.optString("title", _state.value.throneTitle)
                setThroneTitle(t, propagateRemote = false)
            }
            "set_base_shield" -> {
                val bs = dataObj.optDouble("baseShield", _state.value.baseShieldOnUsurp.toDouble()).toFloat()
                setBaseShield(bs, propagateRemote = false)
            }
            "set_weights" -> {
                val lW = dataObj.optDouble("likeWeight", _state.value.likeWeight.toDouble()).toFloat()
                val cW = dataObj.optDouble("chatWeight", _state.value.chatWeight.toDouble()).toFloat()
                val gM = dataObj.optDouble("giftMultiplier", _state.value.giftMultiplier.toDouble()).toFloat()
                setWeights(lW, cW, gM, propagateRemote = false)
            }
            "next_round" -> startNewRound(propagateRemote = false)
            "reset_all" -> resetThroneAndRecords(propagateRemote = false)
            "boost_shield" -> {
                val extra = dataObj.optDouble("extraShield", 100.0).toFloat()
                boostKingShield(extra, propagateRemote = false)
            }
            "manual_action" -> {
                val sender = dataObj.optString("senderName", "Penantang")
                val pts = dataObj.optDouble("points", 50.0).toFloat()
                val label = dataObj.optString("actionLabel", "Aksi Host")
                manualAction(sender, pts, label, propagateRemote = false)
            }
            "set_enabled" -> {
                val en = dataObj.optBoolean("enabled", _state.value.isEnabled)
                setEnabled(en, propagateRemote = false)
            }
        }
    }

    @Synchronized
    fun applyRemoteStateSync(dataObj: JSONObject) {
        if (_state.value.isArenaDisplayActive) return
        val cur = _state.value
        val parsedChallengers = mutableListOf<ThroneChallengerEntry>()
        val arr = dataObj.optJSONArray("topChallengers")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val n = item.optString("name", "")
                val p = item.optLong("points", 0L)
                if (n.isNotBlank()) {
                    parsedChallengers.add(
                        ThroneChallengerEntry(
                            name = n,
                            points = p,
                            avatarBase64 = userAvatarBase64Map[n] ?: ""
                        )
                    )
                }
            }
        }
        _state.value = cur.copy(
            throneTitle = dataObj.optString("throneTitle", cur.throneTitle),
            currentKingName = dataObj.optString("currentKingName", cur.currentKingName),
            currentKingScore = dataObj.optLong("currentKingScore", cur.currentKingScore),
            reignStartedAtMs = dataObj.optLong("reignStartedAtMs", cur.reignStartedAtMs),
            throneShieldHp = dataObj.optDouble("throneShieldHp", cur.throneShieldHp.toDouble()).toFloat(),
            maxThroneShieldHp = dataObj.optDouble("maxThroneShieldHp", cur.maxThroneShieldHp.toDouble()).toFloat(),
            baseShieldOnUsurp = dataObj.optDouble("baseShieldOnUsurp", cur.baseShieldOnUsurp.toDouble()).toFloat(),
            longestReignName = dataObj.optString("longestReignName", cur.longestReignName),
            longestReignSec = dataObj.optLong("longestReignSec", cur.longestReignSec),
            highestKingName = dataObj.optString("highestKingName", cur.highestKingName),
            highestKingScore = dataObj.optLong("highestKingScore", cur.highestKingScore),
            totalUsurpCount = dataObj.optInt("totalUsurpCount", cur.totalUsurpCount),
            currentRound = dataObj.optInt("currentRound", cur.currentRound),
            likeWeight = dataObj.optDouble("likeWeight", cur.likeWeight.toDouble()).toFloat(),
            chatWeight = dataObj.optDouble("chatWeight", cur.chatWeight.toDouble()).toFloat(),
            giftMultiplier = dataObj.optDouble("giftMultiplier", cur.giftMultiplier.toDouble()).toFloat(),
            lastActionText = dataObj.optString("lastActionText", cur.lastActionText),
            lastActionType = dataObj.optString("lastActionType", cur.lastActionType),
            lastUsurperName = dataObj.optString("lastUsurperName", cur.lastUsurperName),
            lastDethronedName = dataObj.optString("lastDethronedName", cur.lastDethronedName),
            usurpCelebrationUntilMs = dataObj.optLong("usurpCelebrationUntilMs", cur.usurpCelebrationUntilMs),
            topChallengers = if (parsedChallengers.isNotEmpty()) parsedChallengers else cur.topChallengers
        )
    }

    // =========================================================================
    // BROADCAST KE VIRTUAL CAMERA (VCAM OVERLAY)
    // =========================================================================

    private fun scheduleBroadcastToOverlay(immediate: Boolean = false) {
        val s = _state.value
        if (!s.isGameActive) return

        val now = System.currentTimeMillis()
        if (immediate || (now - lastBroadcastTime) >= BROADCAST_THROTTLE_MS) {
            lastBroadcastTime = now
            isPendingBroadcast = false
            broadcastJob?.cancel()
            if (s.isEnabled) {
                broadcastToOverlayNow()
            }
            broadcastStateSyncToBroker()
        } else if (!isPendingBroadcast) {
            isPendingBroadcast = true
            broadcastJob = scope.launch {
                delay(BROADCAST_THROTTLE_MS)
                lastBroadcastTime = System.currentTimeMillis()
                isPendingBroadcast = false
                if (_state.value.isEnabled) {
                    broadcastToOverlayNow()
                }
                broadcastStateSyncToBroker()
            }
        }
    }

    private fun broadcastToOverlayNow() {
        val s = _state.value
        if (!s.isEnabled) return

        try {
            val topCh = s.topChallengers.firstOrNull()
            val contentPayload = buildString {
                append("KING_OF_THRONE|")
                append("${s.throneTitle}|")
                append("${s.currentKingName.ifBlank { "KOSONG" }}|")
                append("${s.currentKingScore}|")
                append("${s.throneShieldHp.toInt()}|")
                append("${s.maxThroneShieldHp.toInt()}|")
                append("${s.currentRound}|")
                append("${s.totalUsurpCount}|")
                append("${topCh?.name ?: "-"}:${topCh?.points ?: 0L}|")
                append("${s.longestReignName}:${s.longestReignSec}|")
                append("${s.lastActionText}|")
                append("${s.lastUsurperName}|")
                append("${s.usurpCelebrationUntilMs}")
            }

            // Menggunakan kanal overlay game TUG_OF_WAR dengan prefix KING_OF_THRONE agar renderer otomatis menggambar kartu Raja Singgasana
            OverlayBannerBroadcaster.broadcastShow(
                context = context,
                senderName = "TUG_OF_WAR",
                content = contentPayload,
                isGift = false,
                bannerType = "TUG_OF_WAR",
                iconMode = "NONE",
                customPrefix = "",
                formatMode = "PREFIX_AND_COMMENT",
                durationMs = 86400000L,
                posXpct = s.posXpct,
                posYpct = s.posYpct,
                rotation = s.overlayRotation,
                scale = s.overlayScale,
                isMirrored = s.isMirrored,
                entryDirection = "FADE",
                capsuleAlpha = 1.0f
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error broadcasting King of Throne overlay: ${t.message}")
        }
    }

    private fun hideFromOverlay() {
        try {
            OverlayBannerBroadcaster.broadcastHide(context, bannerType = "TUG_OF_WAR")
        } catch (_: Throwable) {}
    }
}
