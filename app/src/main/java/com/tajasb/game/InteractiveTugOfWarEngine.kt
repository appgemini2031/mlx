package com.tajasb.game

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tajasb.eventrelay.LiveEventReceiver
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
 * InteractiveTugOfWarEngine:
 * Mesin permainan interaktif Tarik Tambang / Duel Kubu untuk TikTok Live.
 * Mendukung penuh:
 * 1. Mode Game Seluler 2-HP (Device 1 Remote Kontroler + Server Termux ➔ Device 2 Layar Penuh Arena 60 FPS)
 * 2. Mode Kamera Virtual (VCam Overlay)
 */
class InteractiveTugOfWarEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TugOfWarEngine"
        private const val PREFS_NAME = "tug_of_war_storage_v1"
        private const val BROADCAST_THROTTLE_MS = 120L
        private const val CELEBRATION_DURATION_MS = 6000L

        @Volatile
        private var instance: InteractiveTugOfWarEngine? = null

        fun peekInstance(): InteractiveTugOfWarEngine? = instance

        fun getInstance(context: Context): InteractiveTugOfWarEngine {
            return instance ?: synchronized(this) {
                instance ?: InteractiveTugOfWarEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TugOfWarState())
    val state: StateFlow<TugOfWarState> = _state.asStateFlow()

    // Memetakan username penonton ke tim yang didukungnya ("A" atau "B")
    private val senderTeamAffiliation = ConcurrentHashMap<String, String>()

    // Total poin kontribusi per user untuk penentuan Top Contributor / MVP
    private val userPointsTeamA = ConcurrentHashMap<String, Long>()
    private val userPointsTeamB = ConcurrentHashMap<String, Long>()

    private var broadcastJob: Job? = null
    @Volatile private var isPendingBroadcast = false
    private var lastBroadcastTime = 0L

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        if (!prefs.getBoolean("key_default_all_games_off_v1", false)) {
            prefs.edit()
                .putBoolean("is_enabled", false)
                .putBoolean("key_default_all_games_off_v1", true)
                .apply()
        }
        val enabled = prefs.getBoolean("is_enabled", false)
        val teamA = prefs.getString("team_a_name", "COWOK") ?: "COWOK"
        val teamB = prefs.getString("team_b_name", "CEWEK") ?: "CEWEK"
        val colorA = prefs.getString("team_a_color", "#0284C7") ?: "#0284C7"
        val colorB = prefs.getString("team_b_color", "#E11D48") ?: "#E11D48"
        val pointsToWin = prefs.getFloat("points_to_win", 500.0f).coerceIn(50.0f, 10000.0f)
        val winsA = prefs.getInt("team_a_wins", 0)
        val winsB = prefs.getInt("team_b_wins", 0)
        val round = prefs.getInt("current_round", 1)
        val likeW = prefs.getFloat("like_weight", 1.0f).coerceIn(0.1f, 50.0f)
        val chatW = prefs.getFloat("chat_weight", 10.0f).coerceIn(1.0f, 200.0f)
        val giftMult = prefs.getFloat("gift_multiplier", 10.0f).coerceIn(1.0f, 500.0f)
        val posX = prefs.getFloat("pos_x_pct", 0.50f).coerceIn(0.05f, 0.95f)
        val posY = prefs.getFloat("pos_y_pct", 0.28f).coerceIn(0.05f, 0.95f)
        val scale = prefs.getFloat("overlay_scale", 1.0f).coerceIn(0.5f, 2.5f)
        val rot = prefs.getInt("overlay_rotation", 0)
        val mir = prefs.getBoolean("is_mirrored", false)

        _state.value = TugOfWarState(
            isEnabled = enabled,
            isArenaDisplayActive = false,
            teamAName = teamA,
            teamBName = teamB,
            teamAColorHex = colorA,
            teamBColorHex = colorB,
            pointsToWin = pointsToWin,
            teamAWins = winsA,
            teamBWins = winsB,
            currentRound = round,
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
            .putString("team_a_name", s.teamAName)
            .putString("team_b_name", s.teamBName)
            .putString("team_a_color", s.teamAColorHex)
            .putString("team_b_color", s.teamBColorHex)
            .putFloat("points_to_win", s.pointsToWin)
            .putInt("team_a_wins", s.teamAWins)
            .putInt("team_b_wins", s.teamBWins)
            .putInt("current_round", s.currentRound)
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
            // Matikan sementara Al-Qur'an Khatam & Raja Singgasana agar tidak bentrok
            InteractiveQuranKhatamEngine.peekInstance()?.setEnabled(false)
            InteractiveKingOfThroneEngine.peekInstance()?.setEnabled(false, propagateRemote = false)
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
     * Mengaktifkan atau menonaktifkan Mode Layar Penuh Arena Game (Device 2 - Game Seluler TikTok).
     * Saat aktif, seluruh event Like, Chat, dan Gift langsung menggerakkan tali di layar penuh
     * meskipun saklar VCam Overlay dalam posisi OFF.
     */
    @Synchronized
    fun setArenaDisplayActive(active: Boolean) {
        if (_state.value.isArenaDisplayActive == active) return
        if (active) {
            InteractiveQuranKhatamEngine.peekInstance()?.setEnabled(false)
            InteractiveKingOfThroneEngine.peekInstance()?.setArenaDisplayActive(false)
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
            lastActionText = if (active) "Arena Layar Penuh Aktif! Menunggu tarikan penonton..." else _state.value.lastActionText
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
                val sampleNames = listOf("BigoQueen99", "Sultan_Bigo", "Aisyah_Live", "Donatur_Makkah", "Barokah_Stream", "Raja_Sawer", "BigoGamer_ID", "Ksatria_Bigo")
                val votes = listOf("1", "2", "merah", "biru", "gasss 1", "gasss 2", "kiri", "kanan")
                while (isActive && _state.value.isAutoTraffic) {
                    delay((2200L..4200L).random())
                    if (!_state.value.isGameActive) continue
                    val sender = sampleNames.random()
                    val roll = kotlin.random.Random.nextFloat()
                    when {
                        roll < 0.45f -> onChatEvent(sender, votes.random())
                        roll < 0.80f -> onLikeEvent(sender, (3..12).random())
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
    fun setTeamNames(
        teamA: String,
        teamB: String,
        colorA: String? = null,
        colorB: String? = null,
        propagateRemote: Boolean = true
    ) {
        val safeA = teamA.trim().ifBlank { "TIM A" }
        val safeB = teamB.trim().ifBlank { "TIM B" }
        val cA = colorA ?: _state.value.teamAColorHex
        val cB = colorB ?: _state.value.teamBColorHex
        _state.value = _state.value.copy(
            teamAName = safeA,
            teamBName = safeB,
            teamAColorHex = cA,
            teamBColorHex = cB,
            lastActionText = "Kubu diatur: $safeA vs $safeB"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand(
                "set_teams",
                mapOf(
                    "teamA" to safeA,
                    "teamB" to safeB,
                    "colorA" to cA,
                    "colorB" to cB
                )
            )
        }
    }

    @Synchronized
    fun setPointsToWin(points: Float, propagateRemote: Boolean = true) {
        val safe = points.coerceIn(50.0f, 10000.0f)
        _state.value = _state.value.copy(
            pointsToWin = safe,
            ropePosition = _state.value.ropePosition.coerceIn(-safe, safe),
            lastActionText = "Target kemenangan diatur ke ${safe.toInt()} poin"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("set_points_to_win", mapOf("pointsToWin" to safe.toDouble()))
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
            lastActionText = "Bobot diatur: Like=${safeLike.toInt()}, Chat=${safeChat.toInt()}, Koin=${safeGift.toInt()}x"
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
    fun resetRope(propagateRemote: Boolean = true) {
        _state.value = _state.value.copy(
            ropePosition = 0.0f,
            winnerCelebrationUntilMs = 0L,
            lastActionText = "Tali direset ke tengah!"
        )
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("reset_rope")
        }
    }

    @Synchronized
    fun resetScores(propagateRemote: Boolean = true) {
        _state.value = _state.value.copy(
            teamAWins = 0,
            teamBWins = 0,
            currentRound = 1,
            ropePosition = 0.0f,
            winnerCelebrationUntilMs = 0L,
            lastWinnerTeam = "",
            topPullerTeamA = "-",
            topPullerTeamB = "-",
            topPullerPointsA = 0L,
            topPullerPointsB = 0L,
            recentFeed = emptyList(),
            lastActionText = "Skor dan ronde direset ke 0-0!"
        )
        userPointsTeamA.clear()
        userPointsTeamB.clear()
        senderTeamAffiliation.clear()
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("reset_scores")
        }
    }

    @Synchronized
    fun startNewRound(propagateRemote: Boolean = true) {
        val nextR = _state.value.currentRound + 1
        _state.value = _state.value.copy(
            currentRound = nextR,
            ropePosition = 0.0f,
            winnerCelebrationUntilMs = 0L,
            lastWinnerTeam = "",
            lastActionText = "Ronde $nextR dimulai!"
        )
        persistState()
        scheduleBroadcastToOverlay(immediate = true)
        if (propagateRemote) {
            sendRemoteCommand("next_round")
        }
    }

    @Synchronized
    fun manualPull(
        team: String,
        points: Float,
        sender: String = "Host",
        action: String = "Manual",
        propagateRemote: Boolean = true
    ) {
        pullInternal(team = team, points = points, sender = sender, action = action)
        if (propagateRemote) {
            sendRemoteCommand(
                "manual_pull",
                mapOf(
                    "team" to team,
                    "points" to points.toDouble(),
                    "sender" to sender,
                    "actionLabel" to action
                )
            )
        }
    }

    private fun sendRemoteCommand(cmdName: String, params: Map<String, Any?> = emptyMap()) {
        scope.launch {
            try {
                val receiver = LiveEventReceiver.peekInstance() ?: return@launch
                val json = JSONObject().apply {
                    put("action", "tow_cmd")
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
                val json = JSONObject().apply {
                    put("action", "tow_state_sync")
                    put("teamAName", s.teamAName)
                    put("teamBName", s.teamBName)
                    put("teamAColorHex", s.teamAColorHex)
                    put("teamBColorHex", s.teamBColorHex)
                    put("ropePosition", s.ropePosition.toDouble())
                    put("pointsToWin", s.pointsToWin.toDouble())
                    put("teamAWins", s.teamAWins)
                    put("teamBWins", s.teamBWins)
                    put("currentRound", s.currentRound)
                    put("likeWeight", s.likeWeight.toDouble())
                    put("chatWeight", s.chatWeight.toDouble())
                    put("giftMultiplier", s.giftMultiplier.toDouble())
                    put("lastActionText", s.lastActionText)
                    put("lastWinnerTeam", s.lastWinnerTeam)
                    put("winnerCelebrationUntilMs", s.winnerCelebrationUntilMs)
                    put("topPullerTeamA", s.topPullerTeamA)
                    put("topPullerPointsA", s.topPullerPointsA)
                    put("topPullerTeamB", s.topPullerTeamB)
                    put("topPullerPointsB", s.topPullerPointsB)
                }
                receiver.sendWebSocketTextFrame(json.toString())
            } catch (_: Throwable) {}
        }
    }

    /**
     * Menerapkan instruksi kendali jarak jauh dari Device 1 (Kontroler) ke Device 2 (Arena Game).
     */
    @Synchronized
    fun applyRemoteCommand(dataObj: JSONObject) {
        val cmd = dataObj.optString("cmd", "").lowercase()
        when (cmd) {
            "set_teams" -> {
                val tA = dataObj.optString("teamA", _state.value.teamAName)
                val tB = dataObj.optString("teamB", _state.value.teamBName)
                val cA = dataObj.optString("colorA", _state.value.teamAColorHex)
                val cB = dataObj.optString("colorB", _state.value.teamBColorHex)
                setTeamNames(tA, tB, cA, cB, propagateRemote = false)
            }
            "reset_rope" -> resetRope(propagateRemote = false)
            "reset_scores" -> resetScores(propagateRemote = false)
            "next_round" -> startNewRound(propagateRemote = false)
            "set_points_to_win" -> {
                val pts = dataObj.optDouble("pointsToWin", _state.value.pointsToWin.toDouble()).toFloat()
                setPointsToWin(pts, propagateRemote = false)
            }
            "set_weights" -> {
                val lW = dataObj.optDouble("likeWeight", _state.value.likeWeight.toDouble()).toFloat()
                val cW = dataObj.optDouble("chatWeight", _state.value.chatWeight.toDouble()).toFloat()
                val gM = dataObj.optDouble("giftMultiplier", _state.value.giftMultiplier.toDouble()).toFloat()
                setWeights(lW, cW, gM, propagateRemote = false)
            }
            "manual_pull" -> {
                val team = dataObj.optString("team", "A")
                val pts = dataObj.optDouble("points", 10.0).toFloat()
                val sender = dataObj.optString("sender", "Host")
                val act = dataObj.optString("actionLabel", "Boost")
                manualPull(team, pts, sender, act, propagateRemote = false)
            }
            "set_enabled" -> {
                val en = dataObj.optBoolean("enabled", _state.value.isEnabled)
                setEnabled(en, propagateRemote = false)
            }
        }
    }

    /**
     * Menerima sinkronisasi state real-time dari Device 2 ke Device 1 (saat Device 1 menjadi Kontroler).
     */
    @Synchronized
    fun applyRemoteStateSync(dataObj: JSONObject) {
        // Jika Device 2 sedang membuka Arena Layar Penuh, Device 2 adalah sumber kebenaran utama (master)
        if (_state.value.isArenaDisplayActive) return
        val cur = _state.value
        _state.value = cur.copy(
            teamAName = dataObj.optString("teamAName", cur.teamAName),
            teamBName = dataObj.optString("teamBName", cur.teamBName),
            teamAColorHex = dataObj.optString("teamAColorHex", cur.teamAColorHex),
            teamBColorHex = dataObj.optString("teamBColorHex", cur.teamBColorHex),
            ropePosition = dataObj.optDouble("ropePosition", cur.ropePosition.toDouble()).toFloat(),
            pointsToWin = dataObj.optDouble("pointsToWin", cur.pointsToWin.toDouble()).toFloat(),
            teamAWins = dataObj.optInt("teamAWins", cur.teamAWins),
            teamBWins = dataObj.optInt("teamBWins", cur.teamBWins),
            currentRound = dataObj.optInt("currentRound", cur.currentRound),
            likeWeight = dataObj.optDouble("likeWeight", cur.likeWeight.toDouble()).toFloat(),
            chatWeight = dataObj.optDouble("chatWeight", cur.chatWeight.toDouble()).toFloat(),
            giftMultiplier = dataObj.optDouble("giftMultiplier", cur.giftMultiplier.toDouble()).toFloat(),
            lastActionText = dataObj.optString("lastActionText", cur.lastActionText),
            lastWinnerTeam = dataObj.optString("lastWinnerTeam", cur.lastWinnerTeam),
            winnerCelebrationUntilMs = dataObj.optLong("winnerCelebrationUntilMs", cur.winnerCelebrationUntilMs),
            topPullerTeamA = dataObj.optString("topPullerTeamA", cur.topPullerTeamA),
            topPullerPointsA = dataObj.optLong("topPullerPointsA", cur.topPullerPointsA),
            topPullerTeamB = dataObj.optString("topPullerTeamB", cur.topPullerTeamB),
            topPullerPointsB = dataObj.optLong("topPullerPointsB", cur.topPullerPointsB)
        )
    }

    // =========================================================================
    // EVENT INGESTION (CHAT, LIKE, GIFT)
    // =========================================================================

    fun onChatEvent(senderName: String, message: String) {
        if (!_state.value.isGameActive) return
        val msg = message.trim().lowercase()
        val s = _state.value
        val nameA = s.teamAName.lowercase()
        val nameB = s.teamBName.lowercase()

        val voteA = msg == "1" || msg == "a" || msg.contains("1") || msg.contains("kiri") || msg.contains("left") || msg.contains(nameA)
        val voteB = msg == "2" || msg == "b" || msg.contains("2") || msg.contains("kanan") || msg.contains("right") || msg.contains(nameB)

        val targetTeam = when {
            voteA && !voteB -> {
                senderTeamAffiliation[senderName] = "A"
                "A"
            }
            voteB && !voteA -> {
                senderTeamAffiliation[senderName] = "B"
                "B"
            }
            else -> {
                // Gunakan afiliasi tim user sebelumnya jika ada, atau tarik tim yang tertinggal agar game selalu bergerak
                senderTeamAffiliation[senderName] ?: if (s.ropePosition > 0) "A" else "B"
            }
        }

        if (targetTeam != null) {
            pullInternal(
                team = targetTeam,
                points = s.chatWeight,
                sender = senderName,
                action = "Chat \"$message\""
            )
        }
    }

    fun onLikeEvent(senderName: String, count: Int) {
        if (!_state.value.isGameActive || count <= 0) return
        val s = _state.value

        // Jika user sudah berafiliasi ke suatu tim, tarik ke timnya
        val affiliated = senderTeamAffiliation[senderName]
        val targetTeam = affiliated ?: if (s.ropePosition > 0) "A" else "B" // Penyeimbang dinamis (Comeback Mechanic)

        val points = count * s.likeWeight
        pullInternal(
            team = targetTeam,
            points = points,
            sender = senderName,
            action = if (count > 1) "$count Likes ❤️" else "Tap Like ❤️"
        )
    }

    fun onGiftEvent(senderName: String, giftName: String, count: Int, coins: Int) {
        if (!_state.value.isGameActive) return
        val s = _state.value
        val safeCoins = if (coins > 0) coins else 1
        val points = safeCoins * count.coerceAtLeast(1) * s.giftMultiplier

        // Tentukan tim: Utamakan afiliasi chat user, jika belum ada, tarik tim yang tertinggal
        val affiliated = senderTeamAffiliation[senderName]
        val targetTeam = affiliated ?: if (s.ropePosition > 0) "A" else "B"

        pullInternal(
            team = targetTeam,
            points = points,
            sender = senderName,
            action = "🎁 $giftName ($safeCoins 💎)"
        )
    }

    fun onJoinEvent(senderName: String) {
        if (!_state.value.isGameActive) return
        val s = _state.value
        // Sambut penonton baru yang bergabung di ticker aksi, dan beri dorongan kecil untuk tim yang tertinggal
        val targetTeam = senderTeamAffiliation[senderName] ?: if (s.ropePosition > 0) "A" else "B"
        pullInternal(
            team = targetTeam,
            points = (s.chatWeight * 0.5f).coerceAtLeast(1f),
            sender = senderName,
            action = "👋 Masuk Siaran"
        )
    }

    // =========================================================================
    // LOGIKA TARIKAN & BATAS KEMENANGAN
    // =========================================================================

    @Synchronized
    private fun pullInternal(team: String, points: Float, sender: String, action: String) {
        val s = _state.value
        val now = System.currentTimeMillis()

        // Catat poin Top Puller
        if (team == "A") {
            val total = (userPointsTeamA[sender] ?: 0L) + points.toLong().coerceAtLeast(1L)
            userPointsTeamA[sender] = total
        } else {
            val total = (userPointsTeamB[sender] ?: 0L) + points.toLong().coerceAtLeast(1L)
            userPointsTeamB[sender] = total
        }

        val topA = userPointsTeamA.maxByOrNull { it.value }
        val topB = userPointsTeamB.maxByOrNull { it.value }

        val delta = if (team == "A") -points else points
        val newPos = (s.ropePosition + delta).coerceIn(-s.pointsToWin, s.pointsToWin)

        val teamLabel = if (team == "A") s.teamAName else s.teamBName
        val ptsInt = points.toInt().coerceAtLeast(1)
        val actionDesc = "$sender (+$ptsInt) ➔ $teamLabel ($action)"

        // Cek Kemenangan Ronde
        var newWinsA = s.teamAWins
        var newWinsB = s.teamBWins
        var celebrationUntil = s.winnerCelebrationUntilMs
        var winnerTeam = s.lastWinnerTeam
        var finalPos = newPos

        if (newPos <= -s.pointsToWin) {
            // Tim A Menang Ronde Ini!
            newWinsA++
            winnerTeam = s.teamAName
            celebrationUntil = now + CELEBRATION_DURATION_MS
            finalPos = 0.0f // Reset tali untuk ronde berikutnya
            Log.i(TAG, "Tug of War: Team A ($winnerTeam) WON round ${s.currentRound}!")
        } else if (newPos >= s.pointsToWin) {
            // Tim B Menang Ronde Ini!
            newWinsB++
            winnerTeam = s.teamBName
            celebrationUntil = now + CELEBRATION_DURATION_MS
            finalPos = 0.0f // Reset tali untuk ronde berikutnya
            Log.i(TAG, "Tug of War: Team B ($winnerTeam) WON round ${s.currentRound}!")
        }

        val updatedFeed = (listOf(actionDesc) + s.recentFeed).take(5)

        _state.value = s.copy(
            ropePosition = finalPos,
            teamAWins = newWinsA,
            teamBWins = newWinsB,
            currentRound = if (winnerTeam.isNotEmpty() && finalPos == 0.0f && celebrationUntil == now + CELEBRATION_DURATION_MS) s.currentRound + 1 else s.currentRound,
            lastWinnerTeam = winnerTeam,
            winnerCelebrationUntilMs = celebrationUntil,
            lastActionText = actionDesc,
            lastActionTeam = team,
            recentFeed = updatedFeed,
            topPullerTeamA = topA?.key ?: s.topPullerTeamA,
            topPullerPointsA = topA?.value ?: s.topPullerPointsA,
            topPullerTeamB = topB?.key ?: s.topPullerTeamB,
            topPullerPointsB = topB?.value ?: s.topPullerPointsB
        )

        if (newWinsA != s.teamAWins || newWinsB != s.teamBWins) {
            persistState()
        }

        scheduleBroadcastToOverlay(immediate = (points >= 100.0f || celebrationUntil > now))
    }

    // =========================================================================
    // BROADCAST KE VIRTUAL CAMERA & SINKRONISASI BROKER
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
            // Serialisasikan state permainan ke dalam format konten string terstruktur
            val contentPayload = buildString {
                append("${s.teamAName}|${s.teamBName}|")
                append("${s.teamAColorHex}|${s.teamBColorHex}|")
                append("${s.ropePosition}|${s.pointsToWin}|")
                append("${s.teamAWins}|${s.teamBWins}|${s.currentRound}|")
                append("${s.lastActionText}|${s.lastWinnerTeam}|${s.winnerCelebrationUntilMs}|")
                append("${s.topPullerTeamA}:${s.topPullerPointsA}|${s.topPullerTeamB}:${s.topPullerPointsB}")
            }

            OverlayBannerBroadcaster.broadcastShow(
                context = context,
                senderName = "TUG_OF_WAR",
                content = contentPayload,
                isGift = false,
                bannerType = "TUG_OF_WAR",
                iconMode = "NONE",
                customPrefix = "",
                formatMode = "PREFIX_AND_COMMENT",
                durationMs = 86400000L, // Persistent hingga di-OFF-kan
                posXpct = s.posXpct,
                posYpct = s.posYpct,
                rotation = s.overlayRotation,
                scale = s.overlayScale,
                isMirrored = s.isMirrored,
                entryDirection = "FADE",
                capsuleAlpha = 1.0f
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error broadcasting Tug of War overlay: ${t.message}")
        }
    }

    private fun hideFromOverlay() {
        try {
            OverlayBannerBroadcaster.broadcastHide(context, bannerType = "TUG_OF_WAR")
        } catch (_: Throwable) {}
    }
}
