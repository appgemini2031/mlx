package com.tajasb.eventrelay

import android.content.Context
import android.util.Base64
import android.util.Log
import com.tajasb.notif.OverlayBannerBroadcaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class LiveEventReceiver private constructor(private val context: Context) {

    companion object {
        private const val TAG = "LiveEventReceiver"
        private const val EVENT_STATS_COALESCE_MS = 250L
        private const val LIKE_BANNER_THROTTLE_MS = 1600L
        private const val LIKE_AVATAR_BUBBLE_THROTTLE_MS = 450L

        private val SPREAD_LANES = floatArrayOf(0.16f, 0.33f, 0.50f, 0.67f, 0.84f)
        private val LEFT_LANES = floatArrayOf(0.14f, 0.25f, 0.36f)
        private val CENTER_LANES = floatArrayOf(0.36f, 0.50f, 0.64f)
        private val RIGHT_LANES = floatArrayOf(0.64f, 0.75f, 0.86f)

        fun isAvatarBubbleAllowedForEvent(cfg: LiveEventConfig, type: LiveEventType): Boolean {
            if (!cfg.enableAvatarBubble) return false
            return when (type) {
                LiveEventType.CHAT -> cfg.avatarOnChat
                LiveEventType.GIFT -> cfg.avatarOnGift
                LiveEventType.LIKE -> cfg.avatarOnLike
                LiveEventType.MEMBER -> cfg.avatarOnJoin
                LiveEventType.FOLLOW -> cfg.avatarOnFollow
                LiveEventType.SHARE -> cfg.avatarOnShare
                else -> false
            }
        }

        fun computeAvatarSpawnX(zone: String, seed: Int): Float {
            val lanes = when (zone.uppercase()) {
                "LEFT" -> LEFT_LANES
                "CENTER" -> CENTER_LANES
                "RIGHT" -> RIGHT_LANES
                else -> SPREAD_LANES
            }
            val laneIdx = (seed and 0x7FFFFFFF) % lanes.size
            val microJitter = (((seed ushr 8) and 0xFF) / 255f - 0.5f) * 0.03f
            return (lanes[laneIdx] + microJitter).coerceIn(0.12f, 0.88f)
        }

        fun computeNonOverlappingAvatarSpawnX(zone: String, sequenceIndex: Int, lastSpawnX: Float): Float {
            val lanes = when (zone.uppercase()) {
                "LEFT" -> LEFT_LANES
                "CENTER" -> CENTER_LANES
                "RIGHT" -> RIGHT_LANES
                else -> SPREAD_LANES
            }
            // Golden-ratio stepping across lanes so consecutive bubbles never land in the same or adjacent lane
            val step = if (lanes.size >= 5) 2 else 1
            var candidateIdx = ((sequenceIndex.coerceAtLeast(0)) * step) % lanes.size
            var candidateX = lanes[candidateIdx]
            if (kotlin.math.abs(candidateX - lastSpawnX) < 0.14f && lanes.size > 1) {
                candidateIdx = (candidateIdx + 1) % lanes.size
                candidateX = lanes[candidateIdx]
            }
            return candidateX.coerceIn(0.12f, 0.88f)
        }

        val TIKTOK_GIFT_COINS = mapOf(
            "paus" to 2150,
            "paus menyelam" to 2150,
            "diving whale" to 2150,
            "whale" to 2150,
            "paus terbang" to 2150,
            "flying whale" to 2150,
            "paus samudra" to 2150,
            "ocean whale" to 2150,
            "singa" to 29999,
            "lion" to 29999,
            "tiktok universe" to 34999,
            "universe" to 34999,
            "falcon" to 10999,
            "elang" to 10999,
            "garuda" to 9999,
            "kastil fantasi" to 20000,
            "fantasy castle" to 20000,
            "castle" to 20000,
            "pesawat jet" to 4888,
            "jet" to 4888,
            "private jet" to 4888,
            "pesawat" to 20000,
            "airplane" to 20000,
            "mobil balap" to 7000,
            "sports car" to 7000,
            "supercar" to 29999,
            "yacht" to 9888,
            "kapal pesiar" to 9888,
            "helikopter" to 4999,
            "helicopter" to 4999,
            "planet" to 15000,
            "interstellar" to 10000,
            "sun" to 10000,
            "matahari" to 10000,
            "moon" to 5000,
            "bulan" to 5000,
            "galaxy" to 1000,
            "bintang jatuh" to 1000,
            "shooting star" to 1000,
            "komet" to 1000,
            "kembang api" to 1088,
            "fireworks" to 1088,
            "kereta api" to 899,
            "kereta" to 899,
            "train" to 899,
            "motor balap" to 1000,
            "motorcycle" to 1000,
            "angsa" to 699,
            "swan" to 699,
            "gitar" to 500,
            "guitar" to 500,
            "mic" to 399,
            "microphone" to 399,
            "nasi tumpeng" to 300,
            "tumpeng" to 300,
            "corgi" to 299,
            "kacamata" to 199,
            "sunglasses" to 199,
            "hand heart" to 100,
            "hati tangan" to 100,
            "konfeti" to 100,
            "confetti" to 100,
            "topi dan kumis" to 99,
            "cap and mustache" to 99,
            "topi" to 99,
            "mahkota" to 99,
            "crown" to 99,
            "bintang" to 99,
            "star" to 99,
            "donat" to 30,
            "doughnut" to 30,
            "parfum" to 20,
            "perfume" to 20,
            "permen" to 10,
            "lollipop" to 10,
            "bunga kering" to 10,
            "origami" to 10,
            "jari hati" to 5,
            "finger heart" to 5,
            "panda" to 5,
            "kopi" to 1,
            "coffee" to 1,
            "es krim" to 1,
            "ice cream" to 1,
            "bebek" to 1,
            "duck" to 1,
            "mawar" to 1,
            "rose" to 1,
            "tiktok" to 1,
            "heart me" to 1,
            "heartme" to 1,
            "heart" to 1,
            "love" to 1,
            "hati" to 1,
            "cinta" to 1
        )

        private val sortedGiftEntries by lazy {
            TIKTOK_GIFT_COINS.entries.sortedByDescending { it.key.length }
        }

        fun resolveGiftDiamonds(giftName: String, rawCoins: Int): Int {
            // 1. Prioritaskan nilai diamond resmi dari TikTok jika valid
            if (rawCoins > 0) return rawCoins
            val clean = giftName.lowercase().trim()
            // 2. Pencocokan langsung kamus
            TIKTOK_GIFT_COINS[clean]?.let { return it }
            // 3. Pencocokan kata terpanjang
            for (entry in sortedGiftEntries) {
                if (clean.contains(entry.key)) return entry.value
            }
            com.tajasb.interaction.GiftCoinCatalog.resolveCoinCost(giftName, "TIKTOK")?.let { return it }
            return 1
        }

        val BIGO_GIFT_BEANS = mapOf(
            "supercar" to 3000,
            "sports car" to 3000,
            "super car" to 3000,
            "mobil balap" to 3000,
            "yacht" to 1000,
            "kapal pesiar" to 1000,
            "castle" to 10000,
            "kastil" to 10000,
            "dragon" to 9999,
            "naga" to 9999,
            "rocket" to 5000,
            "roket" to 5000,
            "pegasus" to 20000,
            "kuda terbang" to 20000,
            "helicopter" to 4999,
            "helikopter" to 4999,
            "motorcycle" to 500,
            "motor" to 500,
            "love boat" to 500,
            "perahu cinta" to 500,
            "guitar" to 200,
            "gitar" to 200,
            "fireworks" to 100,
            "kembang api" to 100,
            "diamond ring" to 100,
            "cincin" to 100,
            "ring" to 100,
            "mic" to 100,
            "microphone" to 100,
            "crown" to 99,
            "mahkota" to 99,
            "bell" to 10,
            "lonceng" to 10,
            "lucky star" to 10,
            "bintang" to 10,
            "lollipop" to 10,
            "permen" to 10,
            "coffee" to 5,
            "kopi" to 5,
            "kiss" to 1,
            "cium" to 1,
            "rose" to 1,
            "mawar" to 1,
            "heart" to 1,
            "hati" to 1,
            "cinta" to 1
        )

        private val sortedBigoGiftEntries by lazy {
            BIGO_GIFT_BEANS.entries.sortedByDescending { it.key.length }
        }

        fun resolveBigoGiftBeans(giftName: String, rawBeans: Int): Int {
            val clean = giftName.lowercase().trim()
            BIGO_GIFT_BEANS[clean]?.let { return it }
            for (entry in sortedBigoGiftEntries) {
                if (clean.contains(entry.key)) return entry.value
            }
            com.tajasb.interaction.GiftCoinCatalog.resolveCoinCost(giftName, "BIGO")?.let { return it }
            if (rawBeans > 1) return rawBeans
            return 1
        }

        fun optFirstPositiveInt(obj: JSONObject, vararg keys: String, defaultVal: Int = 0): Int {
            for (k in keys) {
                if (obj.has(k)) {
                    val v = obj.optInt(k, 0)
                    if (v > 0) return v
                }
            }
            return defaultVal
        }

        fun optFirstNonNegativeInt(obj: JSONObject, vararg keys: String, defaultVal: Int = 0): Int {
            for (k in keys) {
                if (obj.has(k)) {
                    val v = obj.optInt(k, -1)
                    if (v >= 0) return v
                }
            }
            return defaultVal
        }

        fun encodeMaskedClientFrame(opcode: Int, payload: ByteArray, maskKey: ByteArray? = null): ByteArray? {
            if (payload.size > 4 * 1024 * 1024) return null
            val key = maskKey ?: ByteArray(4).apply { SecureRandom().nextBytes(this) }
            val extraHeader = when {
                payload.size <= 125 -> 6
                payload.size <= 65535 -> 8
                else -> 14
            }
            val out = ByteArrayOutputStream(payload.size + extraHeader)
            out.write(0x80 or (opcode and 0x0F))
            when {
                payload.size <= 125 -> {
                    out.write(0x80 or payload.size)
                }
                payload.size <= 65535 -> {
                    out.write(0x80 or 126)
                    out.write((payload.size shr 8) and 0xFF)
                    out.write(payload.size and 0xFF)
                }
                else -> {
                    out.write(0x80 or 127)
                    val lenLong = payload.size.toLong()
                    for (shift in 56 downTo 0 step 8) {
                        out.write(((lenLong ushr shift) and 0xFFL).toInt())
                    }
                }
            }
            out.write(key)
            for (i in payload.indices) {
                out.write(payload[i].toInt() xor key[i % 4].toInt())
            }
            return out.toByteArray()
        }

        private fun readExactBytes(inStream: InputStream, count: Int): ByteArray? {
            if (count == 0) return ByteArray(0)
            val buf = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = inStream.read(buf, offset, count - offset)
                if (read == -1) return null
                offset += read
            }
            return buf
        }

        fun readCompleteWebSocketTextMessage(
            inStream: InputStream,
            onPing: (ByteArray) -> Unit = {}
        ): String? {
            val fragmentBuffer = ByteArrayOutputStream()
            var collectingFragments = false
            while (true) {
                val b0 = inStream.read()
                if (b0 == -1) return null
                val isFin = (b0 and 0x80) != 0
                val opcode = b0 and 0x0F

                val b1 = inStream.read()
                if (b1 == -1) return null
                val masked = (b1 and 0x80) != 0
                var payloadLen = (b1 and 0x7F).toLong()

                if (payloadLen == 126L) {
                    val ext = readExactBytes(inStream, 2) ?: return null
                    payloadLen = (((ext[0].toInt() and 0xFF) shl 8) or (ext[1].toInt() and 0xFF)).toLong()
                } else if (payloadLen == 127L) {
                    val ext = readExactBytes(inStream, 8) ?: return null
                    var len = 0L
                    for (i in 0 until 8) {
                        len = (len shl 8) or (ext[i].toLong() and 0xFF)
                    }
                    payloadLen = len
                }

                val maskKey = if (masked) {
                    readExactBytes(inStream, 4) ?: return null
                } else null

                if (payloadLen < 0L || payloadLen > 1024L * 1024L) {
                    return null
                }

                val payload = readExactBytes(inStream, payloadLen.toInt()) ?: return null
                if (masked && maskKey != null) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                    }
                }

                when (opcode) {
                    8 -> return null // Close frame
                    9 -> {
                        // RFC 6455 Ping -> echo Pong with identical payload (<=125 bytes)
                        onPing(if (payload.size <= 125) payload else payload.copyOf(125))
                        continue
                    }
                    10 -> continue // Unsolicited Pong -> ignore
                    1 -> {
                        fragmentBuffer.reset()
                        fragmentBuffer.write(payload)
                        if (isFin) {
                            return String(fragmentBuffer.toByteArray(), Charsets.UTF_8)
                        } else {
                            collectingFragments = true
                        }
                    }
                    0 -> {
                        if (!collectingFragments) continue
                        if (fragmentBuffer.size() + payload.size > 1024 * 1024) {
                            fragmentBuffer.reset()
                            collectingFragments = false
                            continue
                        }
                        fragmentBuffer.write(payload)
                        if (isFin) {
                            return String(fragmentBuffer.toByteArray(), Charsets.UTF_8)
                        }
                    }
                    else -> {
                        if (!collectingFragments) return ""
                    }
                }
            }
        }

        const val STREAK_TIMEOUT_MS = 3500L

        /**
         * Multi-Metode Resolver untuk menghitung delta gift secara presisi.
         * Mengatasi anomali combo drop, timeout kedaluwarsa, decrement count, dan protocol repeatEnd.
         */
        fun resolveStreakDelta(
            session: GiftStreakSession?,
            gCount: Int,
            groupId: String = "",
            repeatEnd: Boolean = false,
            nowMs: Long = System.currentTimeMillis(),
            timeoutMs: Long = STREAK_TIMEOUT_MS
        ): Pair<Int, GiftStreakSession?> {
            val isExpired = session != null && (nowMs - session.lastUpdateMs > timeoutMs)
            val isDifferentGroup = session != null && groupId.isNotBlank() && session.groupId.isNotBlank() && session.groupId != groupId
            val isDecreasingCount = session != null && (gCount < session.lastCount)
            val isPriorCompleted = session?.isCompleted == true

            val delta: Int = if (session == null) {
                // Streak pertama kali terlihat pada sesi ini:
                // Jika gCount > 1 dan masih berjalan (repeatEnd=false), ini combo yang sudah berjalan sebelum koneksi.
                // Hitung delta = 1 untuk ketukan saat ini; jika repeatEnd=true, hitung batch penuh.
                if (gCount > 1 && !repeatEnd) 1 else gCount
            } else if (isExpired || isDifferentGroup || isDecreasingCount || isPriorCompleted) {
                // Reset streak yang sah: penonton memulai combo baru
                gCount
            } else {
                if (gCount > session.lastCount) {
                    gCount - session.lastCount
                } else if (gCount == session.lastCount) {
                    0
                } else {
                    gCount
                }
            }

            val updatedSession = if (repeatEnd) {
                null
            } else {
                GiftStreakSession(
                    groupId = if (groupId.isNotBlank()) groupId else (session?.groupId ?: ""),
                    lastCount = gCount,
                    lastUpdateMs = nowMs,
                    isCompleted = false
                )
            }
            return Pair(delta.coerceAtLeast(0), updatedSession)
        }

        @Volatile
        private var instance: LiveEventReceiver? = null

        private val giftActiveUntilMs = AtomicLong(0L)

        fun peekInstance(): LiveEventReceiver? = instance

        fun isGiftActive(): Boolean = System.currentTimeMillis() < giftActiveUntilMs.get()

        fun getGiftRemainingMs(): Long = (giftActiveUntilMs.get() - System.currentTimeMillis()).coerceAtLeast(0L)

        fun markGiftActive(durationMs: Long) {
            val target = System.currentTimeMillis() + durationMs
            while (true) {
                val prev = giftActiveUntilMs.get()
                if (target <= prev) break
                if (giftActiveUntilMs.compareAndSet(prev, target)) break
            }
        }

        fun getInstance(context: Context): LiveEventReceiver {
            return instance ?: synchronized(this) {
                instance ?: LiveEventReceiver(context.applicationContext).also { instance = it }
            }
        }
    }

    data class GiftCoinEntry(
        val senderName: String,
        var totalCoins: Int,
        var lastUpdateMs: Long,
        var avatarUrl: String = "",
        var b64Avatar: String = ""
    )

    data class LikeTapEntry(
        val senderName: String,
        var totalTaps: Int,
        var avatarUrl: String = "",
        var b64Avatar: String = "",
        var lastUpdateMs: Long = 0L
    )

    private data class QueuedBannerNotification(
        val senderName: String,
        val content: String,
        val isGift: Boolean,
        val bannerType: String,
        val iconMode: String,
        val customPrefix: String,
        val durationMs: Long,
        val posXpct: Float,
        val posYpct: Float,
        val scale: Float,
        val fontSizeSp: Float,
        val minHoldMs: Long
    )

    private val prefs = LiveEventPreferences.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var eventStatsCoalesceJob: Job? = null
    @Volatile
    private var lastEventStatsSentAtMs: Long = 0L
    private var topLikersCoalesceJob: Job? = null
    @Volatile
    private var lastTopLikersSentAtMs: Long = 0L
    @Volatile
    private var lastLikeBannerSentAtMs: Long = 0L
    @Volatile
    private var lastLikeAvatarBubbleAtMs: Long = 0L
    private val avatarSpawnSequence = AtomicInteger(0)
    @Volatile
    private var lastAvatarSpawnX: Float = -1f

    private val giftCoinsBySender = java.util.concurrent.ConcurrentHashMap<String, GiftCoinEntry>()
    private val likeTapsBySender = java.util.concurrent.ConcurrentHashMap<String, LikeTapEntry>()
    private val localGiftStreaks = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val localGiftSessions = java.util.concurrent.ConcurrentHashMap<String, GiftStreakSession>()

    private val giftQueueLock = Any()
    private val giftBannerQueue = ArrayDeque<QueuedBannerNotification>()
    private var isGiftWorkerRunning = false

    private val interactionQueueLock = Any()
    private val interactionBannerQueue = ArrayDeque<QueuedBannerNotification>()
    private var isInteractionWorkerRunning = false

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _lastEvent = MutableStateFlow<LiveEventItem?>(null)
    val lastEvent: StateFlow<LiveEventItem?> = _lastEvent.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _totalReceivedCount = MutableStateFlow(0)
    val totalReceivedCount: StateFlow<Int> = _totalReceivedCount.asStateFlow()

    private val _totalLikesAccumulated = MutableStateFlow(0)
    val totalLikesAccumulated: StateFlow<Int> = _totalLikesAccumulated.asStateFlow()

    private val _totalGiftsAccumulated = MutableStateFlow(0)
    val totalGiftsAccumulated: StateFlow<Int> = _totalGiftsAccumulated.asStateFlow()

    private val _totalDiamondsAccumulated = MutableStateFlow(0)
    val totalDiamondsAccumulated: StateFlow<Int> = _totalDiamondsAccumulated.asStateFlow()

    private val _totalChatsAccumulated = MutableStateFlow(0)
    val totalChatsAccumulated: StateFlow<Int> = _totalChatsAccumulated.asStateFlow()

    private val _totalJoinsAccumulated = MutableStateFlow(0)
    val totalJoinsAccumulated: StateFlow<Int> = _totalJoinsAccumulated.asStateFlow()

    private val _totalFollowsAccumulated = MutableStateFlow(0)
    val totalFollowsAccumulated: StateFlow<Int> = _totalFollowsAccumulated.asStateFlow()

    private val _totalSharesAccumulated = MutableStateFlow(0)
    val totalSharesAccumulated: StateFlow<Int> = _totalSharesAccumulated.asStateFlow()

    private val _totalOrdersAccumulated = MutableStateFlow(0)
    val totalOrdersAccumulated: StateFlow<Int> = _totalOrdersAccumulated.asStateFlow()

    private val _totalPinProductsAccumulated = MutableStateFlow(0)
    val totalPinProductsAccumulated: StateFlow<Int> = _totalPinProductsAccumulated.asStateFlow()

    private val _connectionDetails = MutableStateFlow("Belum Terhubung")
    val connectionDetails: StateFlow<String> = _connectionDetails.asStateFlow()

    @Volatile
    var onBrokerConnected: (() -> Unit)? = null

    @Volatile
    var onMlbbControlReceived: ((JSONObject) -> Unit)? = null

    fun currentServerConfig(): LiveEventConfig = prefs.config.value

    private val _termuxLogs = MutableStateFlow<List<String>>(listOf("🟢 [Termux] Siap menerima event live..."))
    val termuxLogs: StateFlow<List<String>> = _termuxLogs.asStateFlow()

    fun clearTermuxLogs() {
        _termuxLogs.value = listOf("🧹 [Termux] Log dibersihkan")
    }

    fun addTermuxLog(msg: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeStr = sdf.format(java.util.Date())
        val formatted = "[$timeStr] $msg"
        _termuxLogs.update { prev ->
            val current = prev.toMutableList()
            while (current.size >= 80) {
                current.removeAt(0)
            }
            current.add(formatted)
            current
        }
        Log.i(TAG, formatted)
    }

    class LeaderboardTracker {
        private val giftCoinsBySender = java.util.concurrent.ConcurrentHashMap<String, GiftCoinEntry>()
        private val likeTapsBySender = java.util.concurrent.ConcurrentHashMap<String, LikeTapEntry>()

        fun recordGiftCoins(senderName: String, diamondCount: Int, avatarUrl: String = "", b64Avatar: String = "") {
            val cleanName = senderName.trim().ifBlank { "Penonton" }
            val key = cleanName.lowercase()
            val now = System.currentTimeMillis()
            giftCoinsBySender.compute(key) { _, existing ->
                if (existing == null) {
                    GiftCoinEntry(
                        senderName = cleanName,
                        totalCoins = diamondCount.coerceAtLeast(1),
                        lastUpdateMs = now,
                        avatarUrl = avatarUrl,
                        b64Avatar = b64Avatar
                    )
                } else {
                    val addCoins = diamondCount.coerceAtLeast(0)
                    if (addCoins > 0) {
                        existing.totalCoins += addCoins
                        existing.lastUpdateMs = now
                    }
                    if (avatarUrl.isNotBlank()) existing.avatarUrl = avatarUrl
                    if (b64Avatar.isNotBlank()) existing.b64Avatar = b64Avatar
                    existing
                }
            }
        }

        fun getTop3GiftCoinSenders(): List<GiftCoinEntry> {
            return giftCoinsBySender.values
                .sortedWith(compareByDescending<GiftCoinEntry> { it.totalCoins }.thenByDescending { it.lastUpdateMs })
                .take(3)
        }

        fun buildTop3GiftCoinsSerialized(): String {
            val top3 = getTop3GiftCoinSenders()
            if (top3.isEmpty()) return ""
            return top3.joinToString(";;") { entry ->
                val safeName = entry.senderName.replace("~", " ").replace(";", " ").replace("|", " ").trim()
                val safeB64 = if (entry.b64Avatar.length <= 4096) entry.b64Avatar else ""
                if (safeB64.isNotBlank()) {
                    "$safeName~${entry.totalCoins}~$safeB64"
                } else {
                    "$safeName~${entry.totalCoins}"
                }
            }
        }

        fun recordLikeTaps(senderName: String, likeCount: Int, avatarUrl: String = "", b64Avatar: String = "") {
            val cleanName = senderName.trim().ifBlank { "Penonton" }
            val key = cleanName.lowercase()
            val now = System.currentTimeMillis()
            likeTapsBySender.compute(key) { _, existing ->
                if (existing == null) {
                    LikeTapEntry(
                        senderName = cleanName,
                        totalTaps = likeCount.coerceAtLeast(1),
                        avatarUrl = avatarUrl,
                        b64Avatar = b64Avatar,
                        lastUpdateMs = now
                    )
                } else {
                    val addTaps = likeCount.coerceAtLeast(0)
                    if (addTaps > 0) {
                        existing.totalTaps += addTaps
                        existing.lastUpdateMs = now
                    }
                    if (avatarUrl.isNotBlank()) existing.avatarUrl = avatarUrl
                    if (b64Avatar.isNotBlank()) existing.b64Avatar = b64Avatar
                    existing
                }
            }
        }

        fun getTop10LikeTappers(): List<LikeTapEntry> {
            return likeTapsBySender.values
                .sortedWith(compareByDescending<LikeTapEntry> { it.totalTaps }.thenByDescending { it.lastUpdateMs })
                .take(10)
        }

        fun buildTop10LikersPayload(): String {
            val top10 = getTop10LikeTappers()
            if (top10.isEmpty()) return ""
            return top10.joinToString(";;") { entry ->
                val safeName = entry.senderName.replace("~", " ").replace(";", " ").replace("|", " ").trim()
                val safeB64 = if (entry.b64Avatar.length <= 4096) entry.b64Avatar else ""
                "$safeName~${entry.totalTaps}~$safeB64"
            }
        }

        fun clear() {
            giftCoinsBySender.clear()
            likeTapsBySender.clear()
        }
    }

    private val leaderboardTracker = LeaderboardTracker()

    fun resetSessionCoins() {
        _totalDiamondsAccumulated.value = 0
        _totalGiftsAccumulated.value = 0
        localGiftStreaks.clear()
        localGiftSessions.clear()
        leaderboardTracker.clear()
        scheduleRefreshTopLikers()
        scheduleRefreshEventStats()
    }

    fun resetAllSessionCounters() {
        _totalDiamondsAccumulated.value = 0
        _totalGiftsAccumulated.value = 0
        _totalLikesAccumulated.value = 0
        _totalChatsAccumulated.value = 0
        _totalJoinsAccumulated.value = 0
        _totalFollowsAccumulated.value = 0
        _totalSharesAccumulated.value = 0
        _totalReceivedCount.value = 0
        localGiftStreaks.clear()
        localGiftSessions.clear()
        leaderboardTracker.clear()
        scheduleRefreshTopLikers()
        scheduleRefreshEventStats()
    }

    fun recordGiftCoins(senderName: String, diamondCount: Int, avatarUrl: String = "", b64Avatar: String = "") =
        leaderboardTracker.recordGiftCoins(senderName, diamondCount, avatarUrl, b64Avatar)

    fun getTop3GiftCoinSenders(): List<GiftCoinEntry> =
        leaderboardTracker.getTop3GiftCoinSenders()

    fun buildTop3GiftCoinsSerialized(): String =
        leaderboardTracker.buildTop3GiftCoinsSerialized()

    fun recordLikeTaps(senderName: String, likeCount: Int, avatarUrl: String = "", b64Avatar: String = "") =
        leaderboardTracker.recordLikeTaps(senderName, likeCount, avatarUrl, b64Avatar)

    fun getTop10LikeTappers(): List<LikeTapEntry> =
        leaderboardTracker.getTop10LikeTappers()

    fun buildTop10LikersPayload(): String =
        leaderboardTracker.buildTop10LikersPayload()

    fun clearLeaderboards() {
        leaderboardTracker.clear()
        scheduleRefreshTopLikers()
    }

    fun scheduleRefreshTopLikers() {
        val cfg = prefs.config.value
        if (!cfg.enableTopLikersOverlay) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastTopLikersSentAtMs
        if (elapsed >= EVENT_STATS_COALESCE_MS) {
            topLikersCoalesceJob?.cancel()
            lastTopLikersSentAtMs = now
            refreshTopLikersIfEnabled()
        } else if (topLikersCoalesceJob?.isActive != true) {
            val waitMs = (EVENT_STATS_COALESCE_MS - elapsed).coerceAtLeast(40L)
            topLikersCoalesceJob = scope.launch {
                delay(waitMs)
                lastTopLikersSentAtMs = System.currentTimeMillis()
                refreshTopLikersIfEnabled()
            }
        }
    }

    fun refreshTopLikersIfEnabled() {
        val cfg = prefs.config.value
        if (!cfg.enableTopLikersOverlay) return
        val payload = leaderboardTracker.buildTop10LikersPayload().ifBlank { "QURAN:::|||SURAH:::|||AYAH:::|||HURUF:::" }
        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "TOP_LIKERS",
            content = payload,
            isGift = false,
            bannerType = "TOP_LIKERS",
            iconMode = "NONE",
            customPrefix = "${cfg.topLikersOrientation}:::${cfg.topGiftAvatarScale}:::${cfg.topGiftOrientation}:::${cfg.topLikersLatinFontSizeSp}",
            durationMs = 86400000L,
            posXpct = cfg.topLikersPosXpct,
            posYpct = cfg.topLikersPosYpct,
            scale = cfg.topLikersScale,
            fontSizeSp = cfg.topLikersFontSizeSp,
            capsulePaddingH = (cfg.topLikersWidthPct * 100).toInt(),
            capsulePaddingV = cfg.topLikersHeightPadV
        )
    }

    private fun enqueueGiftBanner(item: QueuedBannerNotification) {
        val shouldLaunchWorker: Boolean
        synchronized(giftQueueLock) {
            if (giftBannerQueue.size >= 50) {
                giftBannerQueue.removeFirst()
            }
            giftBannerQueue.addLast(item)
            shouldLaunchWorker = !isGiftWorkerRunning
            if (shouldLaunchWorker) {
                isGiftWorkerRunning = true
            }
        }
        if (shouldLaunchWorker) {
            scope.launch {
                try {
                    while (isActive) {
                        val next: QueuedBannerNotification?
                        val remaining: Int
                        synchronized(giftQueueLock) {
                            if (giftBannerQueue.isNotEmpty()) {
                                next = giftBannerQueue.removeFirst()
                                remaining = giftBannerQueue.size
                            } else {
                                next = null
                                remaining = 0
                                isGiftWorkerRunning = false
                            }
                        }
                        if (next == null) break
                        if (!prefs.config.value.enableGift) continue
                        val holdMs = when {
                            remaining > 8 -> (next.minHoldMs * 0.85f).toLong().coerceAtLeast(2400L)
                            remaining > 3 -> (next.minHoldMs * 0.92f).toLong().coerceAtLeast(2800L)
                            else -> next.minHoldMs.coerceAtLeast(3000L)
                        }
                        val displayDuration = holdMs + 350L
                        markGiftActive(displayDuration)

                        OverlayBannerBroadcaster.broadcastShow(
                            context = context,
                            senderName = next.senderName,
                            content = next.content,
                            isGift = next.isGift,
                            bannerType = next.bannerType,
                            iconMode = next.iconMode,
                            customPrefix = next.customPrefix,
                            durationMs = maxOf(next.durationMs, displayDuration),
                            posXpct = next.posXpct,
                            posYpct = next.posYpct,
                            scale = next.scale,
                            fontSizeSp = next.fontSizeSp
                        )
                        delay(displayDuration)
                    }
                } finally {
                    synchronized(giftQueueLock) {
                        isGiftWorkerRunning = false
                    }
                }
            }
        }
    }

    private fun enqueueInteractionBanner(item: QueuedBannerNotification, isHighPriority: Boolean) {
        // Proteksi Gift eksklusif: jika gift sedang aktif tampil di layar, Like langsung memantul (drop)
        if (!isHighPriority && isGiftActive()) {
            return
        }
        val shouldLaunchWorker: Boolean
        synchronized(interactionQueueLock) {
            if (!isHighPriority && interactionBannerQueue.isNotEmpty()) {
                return
            }
            if (interactionBannerQueue.size >= 25) {
                interactionBannerQueue.removeFirst()
            }
            interactionBannerQueue.addLast(item)
            shouldLaunchWorker = !isInteractionWorkerRunning
            if (shouldLaunchWorker) {
                isInteractionWorkerRunning = true
            }
        }
        if (shouldLaunchWorker) {
            scope.launch {
                try {
                    while (isActive) {
                        val giftRem = getGiftRemainingMs()
                        if (giftRem > 0L) {
                            delay(giftRem + 250L)
                        }
                        val next: QueuedBannerNotification?
                        val remaining: Int
                        synchronized(interactionQueueLock) {
                            if (interactionBannerQueue.isNotEmpty()) {
                                next = interactionBannerQueue.removeFirst()
                                remaining = interactionBannerQueue.size
                            } else {
                                next = null
                                remaining = 0
                                isInteractionWorkerRunning = false
                            }
                        }
                        if (next == null) break
                        if (isGiftActive()) {
                            if (next.bannerType != "EVENT_LIKE") {
                                synchronized(interactionQueueLock) {
                                    interactionBannerQueue.addFirst(next)
                                }
                            }
                            delay(getGiftRemainingMs() + 250L)
                            continue
                        }
                        val holdMs = when {
                            remaining > 5 -> (next.minHoldMs * 0.70f).toLong().coerceAtLeast(1400L)
                            remaining > 2 -> (next.minHoldMs * 0.85f).toLong().coerceAtLeast(1800L)
                            else -> next.minHoldMs
                        }
                        OverlayBannerBroadcaster.broadcastShow(
                            context = context,
                            senderName = next.senderName,
                            content = next.content,
                            isGift = next.isGift,
                            bannerType = next.bannerType,
                            iconMode = next.iconMode,
                            customPrefix = next.customPrefix,
                            durationMs = maxOf(next.durationMs, holdMs + 300L),
                            posXpct = next.posXpct,
                            posYpct = next.posYpct,
                            scale = next.scale,
                            fontSizeSp = next.fontSizeSp
                        )
                        delay(holdMs + 300L)
                    }
                } finally {
                    synchronized(interactionQueueLock) {
                        isInteractionWorkerRunning = false
                    }
                }
            }
        }
    }

    fun scheduleRefreshEventStats() {
        val cfg = prefs.config.value
        if (!cfg.enableEventStatsOverlay) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastEventStatsSentAtMs
        if (elapsed >= EVENT_STATS_COALESCE_MS) {
            eventStatsCoalesceJob?.cancel()
            lastEventStatsSentAtMs = now
            refreshEventStatsIfEnabled()
        } else if (eventStatsCoalesceJob?.isActive != true) {
            val waitMs = (EVENT_STATS_COALESCE_MS - elapsed).coerceAtLeast(35L)
            eventStatsCoalesceJob = scope.launch {
                delay(waitMs)
                lastEventStatsSentAtMs = System.currentTimeMillis()
                refreshEventStatsIfEnabled()
            }
        }
    }

    fun buildUnifiedDashboardPayload(): String {
        return buildString {
            append(_totalLikesAccumulated.value)
            append("|||")
            append(_totalGiftsAccumulated.value)
            append("|||")
            append(_totalDiamondsAccumulated.value)
            append("|||")
            append(_totalChatsAccumulated.value)
            append("|||")
            append(_viewerCount.value)
            append("|||")
            append(_totalJoinsAccumulated.value)
            append("|||")
            append(_totalFollowsAccumulated.value)
            append("|||")
            append(_totalSharesAccumulated.value)
            append("|||")
            append(buildTop3GiftCoinsSerialized())
            append("|||")
            append("TOTAL:::0|||SURAH:::0|||AYAH:::0|||HURUF:::0")
        }
    }

    fun refreshEventStatsIfEnabled() {
        val cfg = prefs.config.value
        if (!cfg.enableEventStatsOverlay) return
        val payload = buildUnifiedDashboardPayload()
        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = "STATS",
            content = payload,
            isGift = false,
            bannerType = "EVENT_STATS",
            iconMode = "NONE",
            customPrefix = if (cfg.showDiamondsInAnalytics) "SHOW_DIAMONDS" else "HIDE_DIAMONDS",
            durationMs = 86400000L,
            posXpct = cfg.eventStatsPosXpct,
            posYpct = cfg.eventStatsPosYpct,
            scale = cfg.eventStatsScale,
            fontSizeSp = cfg.eventStatsFontSizeSp,
            capsulePaddingH = (cfg.eventStatsWidthPct * 100).toInt()
        )
    }

    private var clientJob: Job? = null
    private var activeSocket: Socket? = null
    private var isUserInitiatedDisconnect = false

    init {
        // Auto-connect saat inisialisasi jika opsi diaktifkan
        if (prefs.config.value.autoConnect) {
            connect()
        }
    }

    fun connect() {
        isUserInitiatedDisconnect = false
        clientJob?.cancel()
        clientJob = scope.launch {
            runConnectionLoop()
        }
    }

    fun disconnect() {
        isUserInitiatedDisconnect = true
        clientJob?.cancel()
        clientJob = null
        closeSocket()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectionDetails.value = "Terputus oleh pengguna"
    }

    fun toggleConnection() {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED || _connectionStatus.value == ConnectionStatus.CONNECTING) {
            disconnect()
        } else {
            connect()
        }
    }

    private fun closeSocket() {
        try {
            activeSocket?.close()
        } catch (_: Throwable) {}
        activeSocket = null
    }

    private fun sendWebSocketRawFrame(opcode: Int, payload: ByteArray): Boolean {
        val sock = activeSocket ?: return false
        if (sock.isClosed || !sock.isConnected) return false
        return try {
            val frame = encodeMaskedClientFrame(opcode, payload) ?: return false
            val outStream = sock.getOutputStream()
            synchronized(outStream) {
                outStream.write(frame)
                outStream.flush()
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Gagal kirim frame WebSocket: ${e.message}")
            false
        }
    }

    fun sendWebSocketTextFrame(text: String): Boolean {
        return sendWebSocketRawFrame(0x01, text.toByteArray(Charsets.UTF_8))
    }

    private fun sendWebSocketTextFrameAsync(text: String): Boolean {
        val sock = activeSocket
        val connected = sock != null && !sock.isClosed && sock.isConnected
        scope.launch {
            sendWebSocketTextFrame(text)
        }
        return connected
    }

    fun switchTargetUsername(username: String): Boolean {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return false
        prefs.setTargetUsername(clean)
        prefs.addBackupUsername(clean)
        val jsonMsg = JSONObject().apply {
            put("action", "switch_target")
            put("platform", "TIKTOK")
            put("username", clean)
        }.toString()
        val sent = sendWebSocketTextFrameAsync(jsonMsg)
        _connectionDetails.value = "🟢 Target TikTok: @$clean"
        return sent
    }

    fun switchBigoTargetId(bigoId: String): Boolean {
        val clean = bigoId.trim().removePrefix("@")
        if (clean.isBlank()) return false
        prefs.setTargetBigoId(clean)
        prefs.addBackupBigoId(clean)
        val jsonMsg = JSONObject().apply {
            put("action", "switch_target")
            put("platform", "BIGO")
            put("username", clean)
            put("target_id", clean)
        }.toString()
        val sent = sendWebSocketTextFrameAsync(jsonMsg)
        _connectionDetails.value = "🟢 Target Bigo: @$clean"
        return sent
    }

    fun switchPlatform(platform: String): Boolean {
        val clean = if (platform.equals("BIGO", ignoreCase = true)) "BIGO" else "TIKTOK"
        prefs.setActivePlatform(clean)
        val isBigo = clean == "BIGO"
        val curTarget = if (isBigo) prefs.config.value.targetBigoId else prefs.config.value.targetUsername
        val platLabel = if (isBigo) "Bigo Live" else "TikTok"
        val jsonMsg = JSONObject().apply {
            put("action", "switch_platform")
            put("platform", clean)
            put("username", curTarget)
            put("target_id", curTarget)
        }.toString()
        val sent = sendWebSocketTextFrameAsync(jsonMsg)
        _connectionDetails.value = if (curTarget.isNotBlank()) "🟢 Terhubung ($platLabel) • @$curTarget" else "🟢 Terhubung ke Device 1 ($platLabel)"
        return sent
    }

    fun sendTargetGiftToBroker(targetName: String, targetCoins: Int): Boolean {
        val jsonMsg = JSONObject().apply {
            put("action", "set_target")
            put("target_gift_name", targetName)
            put("target_gift_coins", targetCoins)
        }.toString()
        return sendWebSocketTextFrameAsync(jsonMsg)
    }

    fun sendCoinRateToBroker(rateIdr: Int): Boolean {
        val jsonMsg = JSONObject().apply {
            put("action", "set_rate")
            put("coin_rate_idr", rateIdr)
        }.toString()
        return sendWebSocketTextFrameAsync(jsonMsg)
    }

    fun sendResetStatsToBroker(): Boolean {
        val jsonMsg = JSONObject().apply {
            put("action", "reset_stats")
        }.toString()
        return sendWebSocketTextFrameAsync(jsonMsg)
    }

    fun sendMlbbStateSync(syncPayload: JSONObject): Boolean {
        val jsonMsg = JSONObject().apply {
            put("action", "mlbb_state_sync")
            put("timestamp", System.currentTimeMillis())
            put("data", syncPayload)
        }.toString()
        return sendWebSocketTextFrameAsync(jsonMsg)
    }

    private suspend fun runConnectionLoop() {
        var retryDelayMs = 2000L
        while (scope.isActive && !isUserInitiatedDisconnect) {
            val cfg = prefs.config.value
            _connectionStatus.value = ConnectionStatus.CONNECTING
            _connectionDetails.value = "Menghubungkan ke ${cfg.serverHost}:${cfg.serverPort}..."

            var socket: Socket? = null
            try {
                socket = Socket()
                activeSocket = socket
                socket.connect(InetSocketAddress(cfg.serverHost, cfg.serverPort), 4500)
                socket.soTimeout = 25000 // 25s timeout deteksi koneksi zombie / putus (server ping tiap 10s)

                val outStream = socket.getOutputStream()
                val inStream = socket.getInputStream()

                // 1. Handshake WebSocket RFC 6455
                val randomKeyBytes = ByteArray(16).apply { SecureRandom().nextBytes(this) }
                val wsKey = Base64.encodeToString(randomKeyBytes, Base64.NO_WRAP)
                val handshakeReq = "GET / HTTP/1.1\r\n" +
                        "Host: ${cfg.serverHost}:${cfg.serverPort}\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: $wsKey\r\n" +
                        "Sec-WebSocket-Version: 13\r\n" +
                        "\r\n"

                outStream.write(handshakeReq.toByteArray(Charsets.UTF_8))
                outStream.flush()

                // Baca response header
                val responseHeader = readHttpHeader(inStream)
                if (!responseHeader.contains("101 Switching Protocols")) {
                    throw IllegalStateException("Server tidak merespons WebSocket Upgrade: $responseHeader")
                }

                _connectionStatus.value = ConnectionStatus.CONNECTED
                val isBigo = prefs.config.value.activePlatform == "BIGO"
                val curTarget = if (isBigo) prefs.config.value.targetBigoId else prefs.config.value.targetUsername
                val platLabel = if (isBigo) "Bigo Live" else "TikTok"
                _connectionDetails.value = if (curTarget.isNotBlank()) "🟢 Terhubung ($platLabel) • @$curTarget" else "🟢 Terhubung ke Device 1 ($platLabel)"
                retryDelayMs = 2000L // Reset delay jika sukses terhubung

                if (curTarget.isNotBlank()) {
                    val initMsg = JSONObject().apply {
                        put("action", "switch_target")
                        put("platform", if (isBigo) "BIGO" else "TIKTOK")
                        put("username", curTarget)
                        put("target_id", curTarget)
                    }.toString()
                    sendWebSocketTextFrame(initMsg)
                }
                onBrokerConnected?.invoke()

                // 2. Loop pembacaan frame WebSocket
                while (scope.isActive && !isUserInitiatedDisconnect) {
                    val frameText = readWebSocketTextFrame(inStream) ?: break
                    handleIncomingJson(frameText)
                }

            } catch (ce: CancellationException) {
                break
            } catch (t: Throwable) {
                Log.w(TAG, "Koneksi WebSocket terputus: ${t.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                _connectionDetails.value = "⚠️ Gagal terhubung: ${t.localizedMessage ?: "Timeout"}"
            } finally {
                closeSocket()
            }

            if (!isUserInitiatedDisconnect && scope.isActive) {
                _connectionDetails.value = "Mencoba menghubungkan ulang (${retryDelayMs / 1000}s)..."
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 1.5).toLong().coerceAtMost(15000L)
            }
        }
        if (isUserInitiatedDisconnect) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectionDetails.value = "Terputus"
        }
    }

    private fun readHttpHeader(inStream: InputStream): String {
        val sb = StringBuilder()
        var lastChar = 0
        var consecutiveNewlines = 0
        while (consecutiveNewlines < 4) {
            val b = inStream.read()
            if (b == -1) break
            val c = b.toChar()
            sb.append(c)
            if ((lastChar == '\r'.code && b == '\n'.code) || (lastChar == '\n'.code && b == '\n'.code)) {
                consecutiveNewlines += 2
            } else if (b != '\r'.code && b != '\n'.code) {
                consecutiveNewlines = 0
            }
            lastChar = b
        }
        return sb.toString()
    }

    private fun readWebSocketTextFrame(inStream: InputStream): String? {
        return readCompleteWebSocketTextMessage(inStream) { pingPayload ->
            sendWebSocketRawFrame(0x0A, pingPayload)
        }
    }

    fun handleIncomingJson(jsonStr: String) {
        if (jsonStr.isBlank()) return
        try {
            val json = JSONObject(jsonStr)
            val typeStr = json.optString("type", "").lowercase()
            val ts = json.optLong("timestamp", System.currentTimeMillis())
            val targetUser = json.optString("targetUser", "")
            val dataObj = json.optJSONObject("data") ?: json

            val cfg = prefs.config.value
            _totalReceivedCount.update { it + 1 }

            val rawPlatform = json.optString("platform", dataObj.optString("platform", cfg.activePlatform)).uppercase()
            val isBigo = rawPlatform == "BIGO" || (rawPlatform.isBlank() && cfg.activePlatform == "BIGO")
            val platformLabel = if (isBigo) "BIGO" else "TIKTOK"

            val avatarUrl = dataObj.optString(
                "avatarUrl",
                dataObj.optString("profilePictureUrl", "")
            ).trim()

            when (typeStr) {
                "broker_status" -> {
                    val tUser = json.optString("targetUser", "").ifBlank { dataObj.optString("targetUser", "") }
                    val msg = json.optString("message", "")
                    val plat = json.optString("platform", "").uppercase()
                    if (plat == "BIGO" || isBigo) {
                        if (tUser.isNotBlank()) {
                            prefs.setTargetBigoId(tUser)
                            prefs.addBackupBigoId(tUser)
                            _connectionDetails.value = "🟢 Target Bigo: @$tUser"
                        } else if (msg.isNotBlank()) {
                            _connectionDetails.value = "🟢 $msg"
                        }
                    } else {
                        if (tUser.isNotBlank()) {
                            prefs.setTargetUsername(tUser)
                            prefs.addBackupUsername(tUser)
                            _connectionDetails.value = "🟢 Target TikTok: @$tUser"
                        } else if (msg.isNotBlank()) {
                            _connectionDetails.value = "🟢 $msg"
                        }
                    }
                }
                "target_config" -> {
                    val tName = json.optString("target_gift_name", "").ifBlank { dataObj.optString("target_gift_name", "") }
                    val tCoins = optFirstPositiveInt(json, "target_gift_coins", "target_beans")
                        .takeIf { it > 0 } ?: optFirstPositiveInt(dataObj, "target_gift_coins", "target_beans")
                    val cRate = optFirstPositiveInt(json, "coin_rate_idr", "bean_rate_idr")
                        .takeIf { it > 0 } ?: optFirstPositiveInt(dataObj, "coin_rate_idr", "bean_rate_idr")
                    if (isBigo) {
                        if (tName.isNotBlank() && tCoins > 0) {
                            prefs.setTargetBigoGift(tName, tCoins)
                        }
                        if (cRate > 0) {
                            prefs.setBigoBeanRateIdr(cRate)
                        }
                    } else {
                        if (tName.isNotBlank() && tCoins > 0) {
                            prefs.setTargetGift(tName, tCoins)
                        }
                        if (cRate > 0) {
                            prefs.setCoinRateIdr(cRate)
                        }
                    }
                    if (json.has("diamonds") || json.has("beans")) {
                        val synced = optFirstNonNegativeInt(json, "diamonds", "beans", defaultVal = _totalDiamondsAccumulated.value)
                        _totalDiamondsAccumulated.value = synced
                    }
                }
                "reset_stats" -> {
                    resetSessionCoins()
                }
                "chat" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("nickname", "").ifBlank {
                            dataObj.optString("uniqueId", "Penonton")
                        }
                    }.trim().ifBlank { "Penonton" }
                    val msgText = dataObj.optString("text", "").ifBlank {
                        dataObj.optString("comment", "").ifBlank {
                            dataObj.optString("content", "")
                        }
                    }.trim()
                    val item = LiveEventItem(
                        type = LiveEventType.CHAT,
                        platform = platformLabel,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = sName,
                        text = msgText,
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item
                    _totalChatsAccumulated.update { it + 1 }
                    val tagPlat = if (isBigo) "[BIGO CHAT]" else "[CHAT]"
                    addTermuxLog("💬 $tagPlat $sName: $msgText")

                    if (cfg.enableComment && msgText.isNotBlank()) {
                        enqueueInteractionBanner(
                            QueuedBannerNotification(
                                senderName = sName,
                                content = msgText,
                                isGift = false,
                                bannerType = "COMMENT",
                                iconMode = "PRAYER",
                                customPrefix = cfg.customDoaPrefix,
                                durationMs = cfg.commentDurationMs,
                                posXpct = cfg.commentPosXpct,
                                posYpct = cfg.commentPosYpct,
                                scale = cfg.commentScale,
                                fontSizeSp = cfg.commentFontSizeSp,
                                minHoldMs = 1900L
                            ),
                            isHighPriority = true
                        )
                    }

                    triggerAvatarBubbleIfEnabled(item)
                    scheduleRefreshEventStats()
                }
                "gift" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("nickname", "").ifBlank {
                            dataObj.optString("uniqueId", "Penonton")
                        }
                    }.trim().ifBlank { "Penonton" }
                    val gName = dataObj.optString("giftName", "").ifBlank {
                        dataObj.optString("name", "Gift")
                    }.trim().ifBlank { "Gift" }
                    val gCount = optFirstPositiveInt(dataObj, "giftCount", "count", "repeatCount", defaultVal = 1).coerceAtLeast(1)
                    val groupId = dataObj.optString("groupId", "").ifBlank {
                        dataObj.optString("group_id", "").ifBlank {
                            dataObj.optString("comboId", "").ifBlank {
                                dataObj.optString("combo_id", "")
                            }
                        }
                    }.trim()
                    val repeatEnd = dataObj.optBoolean("repeatEnd", dataObj.optBoolean("repeat_end", false))
                    val rawDiamonds = optFirstPositiveInt(dataObj, "diamondCount", "diamonds", "diamond_count", "beans", "bean_count", defaultVal = 0)
                    val explicitUnit = optFirstPositiveInt(dataObj, "diamondUnit", "beanUnit", defaultVal = 0)
                    val dictLookup = if (isBigo) BIGO_GIFT_BEANS[gName.lowercase()] else TIKTOK_GIFT_COINS[gName.lowercase()]
                    val inferredUnit = when {
                        explicitUnit > 0 -> explicitUnit
                        rawDiamonds <= 0 -> 0
                        gCount > 1 && dictLookup != null && rawDiamonds == dictLookup -> rawDiamonds
                        gCount > 1 && rawDiamonds >= gCount && rawDiamonds % gCount == 0 -> rawDiamonds / gCount
                        else -> rawDiamonds
                    }
                    val unitCoins = if (isBigo) resolveBigoGiftBeans(gName, inferredUnit) else resolveGiftDiamonds(gName, inferredUnit)
                    val totalCoinsInEvent = unitCoins * gCount

                    val streakKey = "${platformLabel.lowercase()}_${sName.lowercase()}_${gName.lowercase()}"
                    val nowMs = System.currentTimeMillis()

                    val deltaGifts: Int = if (dataObj.has("deltaCount")) {
                        // METODE 3: Upstream Authority (Bridge / Relay sudah menghitung delta)
                        val upstreamDelta = dataObj.optInt("deltaCount", 0).coerceAtLeast(0)
                        if (repeatEnd) {
                            localGiftSessions.remove(streakKey)
                        } else {
                            localGiftSessions[streakKey] = GiftStreakSession(groupId, gCount, nowMs, false)
                        }
                        upstreamDelta
                    } else {
                        // METODE 1, 2 & 4: Multi-Layer Resolver (Protocol repeatEnd & groupId + Decrement check + Timeout)
                        val prevSession = localGiftSessions[streakKey]
                        val (calcDelta, newSession) = resolveStreakDelta(
                            session = prevSession,
                            gCount = gCount,
                            groupId = groupId,
                            repeatEnd = repeatEnd,
                            nowMs = nowMs
                        )
                        if (newSession == null) {
                            localGiftSessions.remove(streakKey)
                        } else {
                            localGiftSessions[streakKey] = newSession
                        }
                        calcDelta
                    }
                    localGiftStreaks[streakKey] = gCount

                    val deltaCoins = if (dataObj.has("deltaDiamonds") || dataObj.has("deltaBeans")) {
                        if (deltaGifts == 0 && !dataObj.has("deltaCount")) {
                            0
                        } else if (deltaGifts == 0 && dataObj.optInt("deltaCount", 0) == 0) {
                            0
                        } else {
                            optFirstPositiveInt(dataObj, "deltaDiamonds", "deltaBeans", defaultVal = unitCoins * deltaGifts).coerceAtLeast(0)
                        }
                    } else {
                        unitCoins * deltaGifts
                    }

                    // Jika ini paket penutup streak duplikat (repeatEnd=true dengan delta=0), cukup bersihkan session tanpa spam UI/stats/game
                    if (deltaGifts > 0 || deltaCoins > 0) {
                        val coinSymbol = if (isBigo) "🫘" else "💎"
                        val giftContent = "$gName x$gCount • $totalCoinsInEvent $coinSymbol"
                        val item = LiveEventItem(
                            type = LiveEventType.GIFT,
                            platform = platformLabel,
                            timestamp = ts,
                            targetUser = targetUser,
                            senderName = sName,
                            giftName = gName,
                            giftCount = gCount,
                            diamondCount = totalCoinsInEvent,
                            text = giftContent,
                            avatarUrl = avatarUrl
                        )
                        _lastEvent.value = item
                        val tagPlat = if (isBigo) "[BIGO GIFT]" else "[GIFT]"
                        addTermuxLog("🎁 $tagPlat $sName kirim $giftContent")
                        if (deltaGifts > 0) {
                            _totalGiftsAccumulated.update { it + deltaGifts }
                        }
                        if (deltaCoins > 0) {
                            _totalDiamondsAccumulated.update { it + deltaCoins }
                            recordGiftCoins(sName, deltaCoins, avatarUrl)
                        }
                        scheduleRefreshTopLikers()
                        if (avatarUrl.isNotBlank() && deltaCoins > 0) {
                            scope.launch {
                                val b64 = ViewerAvatarCache.getOrFetchCompactBase64(avatarUrl)
                                if (b64.isNotBlank()) {
                                    recordGiftCoins(sName, 0, avatarUrl, b64)
                                    scheduleRefreshEventStats()
                                    scheduleRefreshTopLikers()
                                }
                            }
                        }

                        // Saklar Gift & Saweran (Antrean FIFO; jika separateOverlays=false gabung ke banner COMMENT)
                        if (cfg.enableGift && deltaGifts > 0) {
                            enqueueGiftBanner(
                                QueuedBannerNotification(
                                    senderName = sName,
                                    content = giftContent,
                                    isGift = true,
                                    bannerType = if (cfg.separateOverlays) "EVENT_GIFT" else "COMMENT",
                                    iconMode = "SHOPPING",
                                    customPrefix = cfg.customGiftPrefix,
                                    durationMs = cfg.giftDurationMs,
                                    posXpct = cfg.giftPosXpct,
                                    posYpct = cfg.giftPosYpct,
                                    scale = cfg.giftScale,
                                    fontSizeSp = cfg.giftFontSizeSp,
                                    minHoldMs = 2600L
                                )
                            )
                        }

                        triggerAvatarBubbleIfEnabled(item)
                        scheduleRefreshEventStats()
                    }
                }
                "like" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("nickname", "").ifBlank {
                            dataObj.optString("uniqueId", "Penonton")
                        }
                    }.trim().ifBlank { "Penonton" }
                    val lCount = optFirstPositiveInt(dataObj, "likeCount", "count", defaultVal = 1).coerceAtLeast(1)
                    val totalL = optFirstNonNegativeInt(dataObj, "totalLikes", "total", "totalLikeCount", defaultVal = 0)
                    val likeContent = "+$lCount Like"
                    val item = LiveEventItem(
                        type = LiveEventType.LIKE,
                        platform = platformLabel,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = sName,
                        likeCount = lCount,
                        totalLikes = totalL,
                        text = likeContent,
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item
                    _totalLikesAccumulated.update { it + lCount }
                    val tagLikePlat = if (isBigo) "[BIGO LIKE]" else "[LIKE]"
                    addTermuxLog("❤️ $tagLikePlat $sName: +$lCount Like (Total: $totalL)")
                    recordLikeTaps(sName, lCount, avatarUrl)
                    if (avatarUrl.isNotBlank()) {
                        scope.launch {
                            val b64 = ViewerAvatarCache.getOrFetchCompactBase64(avatarUrl)
                            if (b64.isNotBlank()) {
                                recordLikeTaps(sName, 0, avatarUrl, b64)
                                scheduleRefreshTopLikers()
                            }
                        }
                    }

                    // Saklar Tap Like (Antrean tidak menimpa Join/Follow/Share & hanya diproses saat saklar ON)
                    if (cfg.enableLike) {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastLikeBannerSentAtMs >= LIKE_BANNER_THROTTLE_MS) {
                            lastLikeBannerSentAtMs = nowMs
                            enqueueInteractionBanner(
                                QueuedBannerNotification(
                                    senderName = sName,
                                    content = likeContent,
                                    isGift = false,
                                    bannerType = if (cfg.separateOverlays) "EVENT_LIKE" else "COMMENT",
                                    iconMode = "LIKE",
                                    customPrefix = "Like",
                                    durationMs = cfg.likeDurationMs,
                                    posXpct = cfg.likePosXpct,
                                    posYpct = cfg.likePosYpct,
                                    scale = cfg.likeScale,
                                    fontSizeSp = cfg.likeFontSizeSp,
                                    minHoldMs = 1700L
                                ),
                                isHighPriority = false
                            )
                        }
                    }

                    triggerAvatarBubbleIfEnabled(item)
                    scheduleRefreshEventStats()
                    scheduleRefreshTopLikers()
                }
                "member", "join", "member_enter" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("nickname", "").ifBlank {
                            dataObj.optString("uniqueId", "Penonton")
                        }
                    }.trim().ifBlank { "Penonton" }
                    val item = LiveEventItem(
                        type = LiveEventType.MEMBER,
                        platform = platformLabel,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = sName,
                        text = "Masuk Siaran",
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item
                    _totalJoinsAccumulated.update { it + 1 }
                    val tagJoinPlat = if (isBigo) "[BIGO JOIN]" else "[JOIN]"
                    addTermuxLog("👋 $tagJoinPlat $sName masuk siaran")

                    // Saklar Sambutan Join (Antrean terurut tanpa menimpa event sebelumnya)
                    if (cfg.enableJoin) {
                        enqueueInteractionBanner(
                            QueuedBannerNotification(
                                senderName = sName,
                                content = "Masuk Siaran",
                                isGift = false,
                                bannerType = if (cfg.separateOverlays) "EVENT_LIKE" else "COMMENT",
                                iconMode = "MEMBER",
                                customPrefix = cfg.customJoinPrefix.ifBlank { "Join" },
                                durationMs = 3000L,
                                posXpct = cfg.likePosXpct,
                                posYpct = cfg.likePosYpct,
                                scale = cfg.likeScale,
                                fontSizeSp = cfg.likeFontSizeSp,
                                minHoldMs = 2100L
                            ),
                            isHighPriority = true
                        )
                    }
                    triggerAvatarBubbleIfEnabled(item)
                    scheduleRefreshEventStats()
                }
                "follow" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("nickname", "").ifBlank {
                            dataObj.optString("uniqueId", "Penonton")
                        }
                    }.trim().ifBlank { "Penonton" }
                    val item = LiveEventItem(
                        type = LiveEventType.FOLLOW,
                        platform = platformLabel,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = sName,
                        text = "Mengikuti Siaran",
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item
                    _totalFollowsAccumulated.update { it + 1 }
                    val tagFollowPlat = if (isBigo) "[BIGO FOLLOW]" else "[FOLLOW]"
                    addTermuxLog("➕ $tagFollowPlat $sName mengikuti")

                    // Saklar Follow Baru (Antrean terurut tanpa menimpa event sebelumnya)
                    if (cfg.enableFollow) {
                        enqueueInteractionBanner(
                            QueuedBannerNotification(
                                senderName = sName,
                                content = "Mengikuti Siaran",
                                isGift = false,
                                bannerType = if (cfg.separateOverlays) "EVENT_LIKE" else "COMMENT",
                                iconMode = "FOLLOW",
                                customPrefix = cfg.customFollowPrefix.ifBlank { "Follow" },
                                durationMs = 3200L,
                                posXpct = cfg.likePosXpct,
                                posYpct = cfg.likePosYpct,
                                scale = cfg.likeScale,
                                fontSizeSp = cfg.likeFontSizeSp,
                                minHoldMs = 2300L
                            ),
                            isHighPriority = true
                        )
                    }
                    triggerAvatarBubbleIfEnabled(item)
                    scheduleRefreshEventStats()
                }
                "share" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("nickname", "").ifBlank {
                            dataObj.optString("uniqueId", "Penonton")
                        }
                    }.trim().ifBlank { "Penonton" }
                    val item = LiveEventItem(
                        type = LiveEventType.SHARE,
                        platform = platformLabel,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = sName,
                        text = "Membagikan Siaran",
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item
                    _totalSharesAccumulated.update { it + 1 }
                    val tagSharePlat = if (isBigo) "[BIGO SHARE]" else "[SHARE]"
                    addTermuxLog("🔄 $tagSharePlat $sName membagikan siaran")

                    // Saklar Share Live (Antrean terurut tanpa menimpa event sebelumnya)
                    if (cfg.enableShare) {
                        enqueueInteractionBanner(
                            QueuedBannerNotification(
                                senderName = sName,
                                content = "Membagikan Siaran",
                                isGift = false,
                                bannerType = if (cfg.separateOverlays) "EVENT_LIKE" else "COMMENT",
                                iconMode = "SHARE",
                                customPrefix = cfg.customSharePrefix.ifBlank { "Share" },
                                durationMs = 3000L,
                                posXpct = cfg.likePosXpct,
                                posYpct = cfg.likePosYpct,
                                scale = cfg.likeScale,
                                fontSizeSp = cfg.likeFontSizeSp,
                                minHoldMs = 2200L
                            ),
                            isHighPriority = true
                        )
                    }
                    triggerAvatarBubbleIfEnabled(item)
                    scheduleRefreshEventStats()
                }
                "room_stats" -> {
                    val vCount = optFirstNonNegativeInt(dataObj, "viewerCount", "memberCount", "total", defaultVal = 0)
                    if (vCount > 0 || _viewerCount.value == 0) {
                        _viewerCount.value = vCount
                    }
                    val displayCount = _viewerCount.value
                    val item = LiveEventItem(
                        type = LiveEventType.ROOM_STATS,
                        timestamp = ts,
                        targetUser = targetUser,
                        viewerCount = displayCount,
                        text = "$displayCount Penonton"
                    )
                    _lastEvent.value = item
                    addTermuxLog("👥 [ROOM] $displayCount penonton aktif")
                    scheduleRefreshEventStats()
                }
                "order", "shop_order", "shoporder", "buyer" -> {
                    val sName = dataObj.optString("senderName", "").ifBlank {
                        dataObj.optString("user", "").ifBlank {
                            dataObj.optString("nickname", "").ifBlank {
                                dataObj.optString("uniqueId", "Pembeli")
                            }
                        }
                    }.trim().ifBlank { "Pembeli" }
                    val pName = dataObj.optString("productName", "").ifBlank {
                        dataObj.optString("productTitle", "").ifBlank {
                            dataObj.optString("title", "").ifBlank {
                                dataObj.optString("product", "Keranjang Kuning")
                            }
                        }
                    }.trim().ifBlank { "Keranjang Kuning" }
                    val pPrice = dataObj.optString("price", "").ifBlank { dataObj.optString("productPrice", "") }.trim()
                    val pQty = optFirstPositiveInt(dataObj, "quantity", "count", defaultVal = 1).coerceAtLeast(1)
                    val pId = dataObj.optString("productId", "").ifBlank { dataObj.optString("id", "") }.trim()
                    val orderDesc = if (pPrice.isNotBlank()) "Checkout $pName ($pPrice)" else "Checkout $pName"

                    _totalOrdersAccumulated.update { it + 1 }
                    addTermuxLog("🛍️ [JUALAN/ORDER] $sName -> $orderDesc (x$pQty)")

                    val item = LiveEventItem(
                        type = LiveEventType.SHOP_ORDER,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = sName,
                        text = orderDesc,
                        productName = pName,
                        productPrice = pPrice,
                        productQuantity = pQty,
                        productId = pId,
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item

                    // Saklar Jualan: Pembeli Checkout
                    if (cfg.enableShopOrder) {
                        enqueueInteractionBanner(
                            QueuedBannerNotification(
                                senderName = sName,
                                content = if (pQty > 1) "$orderDesc x$pQty" else orderDesc,
                                isGift = false,
                                bannerType = "BUYER",
                                iconMode = "SHOPPING",
                                customPrefix = "Order",
                                durationMs = cfg.bannerDurationMs,
                                posXpct = cfg.commentPosXpct,
                                posYpct = cfg.commentPosYpct,
                                scale = cfg.commentScale,
                                fontSizeSp = cfg.commentFontSizeSp,
                                minHoldMs = 2400L
                            ),
                            isHighPriority = true
                        )
                    }
                    triggerAvatarBubbleIfEnabled(item)
                }
                "pin_product", "pinproduct", "shop_product", "shopproduct" -> {
                    val pName = dataObj.optString("productName", "").ifBlank {
                        dataObj.optString("productTitle", "").ifBlank {
                            dataObj.optString("title", "Etalase Produk")
                        }
                    }.trim().ifBlank { "Etalase Produk" }
                    val pPrice = dataObj.optString("price", "").ifBlank { dataObj.optString("productPrice", "") }.trim()
                    val pId = dataObj.optString("productId", "").ifBlank { dataObj.optString("id", "") }.trim()
                    val pinDesc = if (pPrice.isNotBlank()) "Pin: $pName ($pPrice)" else "Pin: $pName"

                    _totalPinProductsAccumulated.update { it + 1 }
                    addTermuxLog("📌 [JUALAN/PIN] Host Pin $pinDesc")

                    val item = LiveEventItem(
                        type = LiveEventType.PIN_PRODUCT,
                        timestamp = ts,
                        targetUser = targetUser,
                        senderName = "Host",
                        text = pinDesc,
                        productName = pName,
                        productPrice = pPrice,
                        productId = pId,
                        avatarUrl = avatarUrl
                    )
                    _lastEvent.value = item
                    if (cfg.enablePinProduct) {
                        enqueueInteractionBanner(
                            QueuedBannerNotification(
                                senderName = "Host",
                                content = pinDesc,
                                isGift = false,
                                bannerType = "BUYER",
                                iconMode = "PIN",
                                customPrefix = "Etalase",
                                durationMs = cfg.bannerDurationMs,
                                posXpct = cfg.commentPosXpct,
                                posYpct = cfg.commentPosYpct,
                                scale = cfg.commentScale,
                                fontSizeSp = cfg.commentFontSizeSp,
                                minHoldMs = 2400L
                            ),
                            isHighPriority = true
                        )
                    }
                }
                "flash_sale", "shop_flash_sale", "shopflashsale" -> {
                    addTermuxLog("⚡ [JUALAN/FLASH_SALE] Flash sale toko dimulai")
                }
                "mlbb_control" -> {
                    val ctrlPayload = if (dataObj.has("cmd")) dataObj else json
                    onMlbbControlReceived?.invoke(ctrlPayload)
                }
                "mlbb_request_sync" -> {
                    onBrokerConnected?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengurai event JSON: ${e.message}")
        }
    }

    /**
     * Memicu animasi gelembung foto profil penonton bulat melayang naik bergelombang di kamera virtual
     * sesuai saklar mandiri per-event dan ukuran per-event yang dipilih pengguna, dengan jalur anti-tumpuk.
     */
    fun triggerAvatarBubbleIfEnabled(item: LiveEventItem) {
        val cfg = prefs.config.value
        if (!isAvatarBubbleAllowedForEvent(cfg, item.type)) return

        if (item.type == LiveEventType.LIKE) {
            val now = System.currentTimeMillis()
            if (now - lastLikeAvatarBubbleAtMs < LIKE_AVATAR_BUBBLE_THROTTLE_MS) {
                return
            }
            lastLikeAvatarBubbleAtMs = now
        }

        val seq = avatarSpawnSequence.incrementAndGet()
        val spawnX = computeNonOverlappingAvatarSpawnX(cfg.avatarSpawnZone, seq, lastAvatarSpawnX)
        lastAvatarSpawnX = spawnX
        val duration = cfg.getAvatarDurationForEvent(item.type)
        val scale = cfg.getAvatarScaleForEvent(item.type)

        scope.launch {
            val b64Avatar = if (item.avatarUrl.isNotBlank()) {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(650L) {
                        ViewerAvatarCache.getOrFetchBase64(item.avatarUrl)
                    } ?: ""
                } catch (_: Throwable) {
                    ""
                }
            } else {
                ""
            }
            val giftMeta = if (item.type == LiveEventType.GIFT) {
                "${item.giftName.ifBlank { "Gift" }} x${item.giftCount.coerceAtLeast(1)}"
            } else ""
            val payload = "${item.type.name}|||${item.senderName}|||$b64Avatar|||$seq|||$giftMeta"
            OverlayBannerBroadcaster.broadcastShow(
                context = context,
                senderName = item.senderName,
                content = payload,
                isGift = (item.type == LiveEventType.GIFT),
                bannerType = "AVATAR_BUBBLE",
                iconMode = item.type.name,
                customPrefix = "",
                durationMs = duration,
                posXpct = spawnX,
                posYpct = 0.88f,
                scale = scale
            )
        }
    }

    /**
     * Memungkinkan pengujian langsung gelembung foto profil bulat melayang di kamera virtual.
     */
    fun injectTestAvatarBubble(preferredType: LiveEventType? = null) {
        val sampleSenders = listOf(
            Pair(LiveEventType.GIFT, "Sultan Makkah"),
            Pair(LiveEventType.CHAT, "Hamba Allah"),
            Pair(LiveEventType.LIKE, "Sahabat Live"),
            Pair(LiveEventType.MEMBER, "Kang Santri"),
            Pair(LiveEventType.FOLLOW, "Ukh Fatimah"),
            Pair(LiveEventType.SHARE, "Bang Ahmad")
        )
        val seq = avatarSpawnSequence.incrementAndGet()
        val chosen = if (preferredType != null) {
            sampleSenders.firstOrNull { it.first == preferredType } ?: Pair(preferredType, "Penonton")
        } else {
            sampleSenders[(seq - 1) % sampleSenders.size]
        }
        val cfg = prefs.config.value
        val spawnX = computeNonOverlappingAvatarSpawnX(cfg.avatarSpawnZone, seq, lastAvatarSpawnX)
        lastAvatarSpawnX = spawnX
        val scale = cfg.getAvatarScaleForEvent(chosen.first)
        val duration = cfg.getAvatarDurationForEvent(chosen.first)
        val giftMeta = if (chosen.first == LiveEventType.GIFT) "Paus x1" else ""
        val payload = "${chosen.first.name}|||${chosen.second}||||||$seq|||$giftMeta"
        OverlayBannerBroadcaster.broadcastShow(
            context = context,
            senderName = chosen.second,
            content = payload,
            isGift = (chosen.first == LiveEventType.GIFT),
            bannerType = "AVATAR_BUBBLE",
            iconMode = chosen.first.name,
            customPrefix = "",
            durationMs = duration,
            posXpct = spawnX,
            posYpct = 0.88f,
            scale = scale
        )
    }

    /**
     * Simulasi lonjakan Tap-Tap Love dinamis pada 10 penonton & Top 3 Gift Koin agar peringkat saling menyalip.
     */
    fun injectTestTopLikersActivity() {
        val sampleLikers = listOf(
            "Ukh Fatimah", "Kang Santri", "Sultan Makkah", "Hamba Allah", "Bang Ahmad",
            "Siti Aisyah", "Rizky Pratama", "Bunda Zahra", "Mas Fajar", "Neng Nisa",
            "Pak Haji Umar", "Dinda Syari"
        )
        val rnd = java.util.Random()

        // 1. Kategori Huruf & Like Taps
        for (i in 0 until 4) {
            val pick = sampleLikers[rnd.nextInt(sampleLikers.size)]
            val taps = 15 + rnd.nextInt(90)
            recordLikeTaps(pick, taps)
            _totalLikesAccumulated.update { it + taps }
        }

        // 2. Kategori Gift
        val giftSenders = listOf("Sultan Makkah", "Pak Haji Umar", "Bunda Zahra")
        val pickGifter = giftSenders[rnd.nextInt(giftSenders.size)]
        val coins = 10 + rnd.nextInt(60)
        recordGiftCoins(pickGifter, coins)
        _totalGiftsAccumulated.update { it + 1 }
        _totalDiamondsAccumulated.update { it + coins }

        refreshTopLikersIfEnabled()
        refreshEventStatsIfEnabled()
    }

    /**
     * Memungkinkan pengujian instan event di Device 2 tanpa harus menunggu live asli.
     */
    fun injectTestEvent(type: LiveEventType) {
        val isBigo = prefs.config.value.activePlatform == "BIGO"
        val plat = if (isBigo) "BIGO" else "TIKTOK"
        val json = when (type) {
            LiveEventType.CHAT -> """{"type":"chat","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Hamba Allah","text":"Semoga sehat sekeluarga, lancar rezeki dan berkah selalu 🤲"}}"""
            LiveEventType.GIFT -> if (isBigo) {
                """{"type":"gift","platform":"BIGO","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Sultan Bigo","giftName":"Supercar","giftCount":1,"beans":3000,"deltaBeans":3000}}"""
            } else {
                """{"type":"gift","platform":"TIKTOK","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Sultan Makkah","giftName":"Mawar","giftCount":10,"diamondCount":10,"deltaDiamonds":10}}"""
            }
            LiveEventType.LIKE -> """{"type":"like","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Sahabat Live","likeCount":20,"totalLikes":1200}}"""
            LiveEventType.MEMBER -> """{"type":"member","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Kang Santri"}}"""
            LiveEventType.FOLLOW -> """{"type":"follow","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Ukh_Fatimah"}}"""
            LiveEventType.SHARE -> """{"type":"share","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Bang Ahmad"}}"""
            LiveEventType.ROOM_STATS -> """{"type":"room_stats","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"viewerCount":850}}"""
            LiveEventType.SHOP_ORDER -> """{"type":"order","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Bunda Rahma","productName":"Gamis Abaya Turkey","price":"Rp 185.000","quantity":1,"text":"Baru saja checkout Gamis Abaya Turkey"}}"""
            LiveEventType.PIN_PRODUCT -> """{"type":"pin_product","platform":"$plat","timestamp":${System.currentTimeMillis()},"data":{"productName":"Hijab Pashmina Silk","price":"Rp 49.000","productId":"102","text":"Host Pin Produk: Hijab Pashmina Silk"}}"""
            else -> return
        }
        handleIncomingJson(json)
    }

    /**
     * Simulasi instan penonton kirim Paus (2.150 💎).
     */
    fun injectTestPausGift() {
        val json = """{"type":"gift","platform":"TIKTOK","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Sultan Makkah","giftName":"Paus","giftCount":1,"diamondCount":2150,"deltaDiamonds":2150}}"""
        handleIncomingJson(json)
    }

    /**
     * Simulasi instan penonton Bigo Live kirim Supercar (3.000 🫘 Beans).
     */
    fun injectTestBigoGift(giftName: String = "Supercar", beans: Int = 3000) {
        val json = """{"type":"gift","platform":"BIGO","timestamp":${System.currentTimeMillis()},"data":{"senderName":"Sultan Bigo","giftName":"$giftName","giftCount":1,"beans":$beans,"deltaBeans":$beans}}"""
        handleIncomingJson(json)
    }
}
