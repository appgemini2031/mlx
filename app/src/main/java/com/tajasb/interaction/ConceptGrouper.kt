package com.tajasb.interaction

enum class ConceptGroupingMode(val label: String) {
    BY_EVENT("📂 Event"),
    BY_ACTION("👆 Aksi"),
    ALL("📋 Semua")
}

data class ConceptGroupBucket(
    val key: String,
    val title: String,
    val emoji: String,
    val rules: List<IndexedRule>
)

data class IndexedRule(
    val globalIndex: Int,
    val rule: UniversalTriggerRule
)

/**
 * Mengelompokkan daftar Konsep berdasarkan Sumber Event (Gift, Like, Chat, Follow, dll.)
 * atau berdasarkan Kategori Aksi Utama (Klik ML, Geser ML, Tahan+Geser, Home->Game, Overlay/Meme, Sound, Kosong)
 * agar manajemen banyak konsep tetap rapi dan mudah ditemukan.
 */
object ConceptGrouper {

    fun classifyActionBucket(rule: UniversalTriggerRule): Triple<String, String, String> {
        val active = rule.actionSequence.filter { it.enabled }
        if (active.isEmpty()) {
            return Triple("ACT_EMPTY", "Kosong (Belum Diisi)", "📭")
        }
        val firstTouch = active.firstOrNull {
            it.actionType == ActionType.SIMULATED_CLICK ||
                it.actionType == ActionType.SIMULATED_SWIPE ||
                it.actionType == ActionType.HOME_BACK_GAME
        }
        if (firstTouch != null) {
            return when (firstTouch.actionType) {
                ActionType.HOME_BACK_GAME -> Triple("ACT_HOME", "Home → Game", "🏠")
                ActionType.SIMULATED_SWIPE -> Triple("ACT_SWIPE", "Geser / Swipe ML", "👉")
                ActionType.SIMULATED_CLICK -> {
                    if (firstTouch.holdAndDrag) {
                        Triple("ACT_CLICK", "Klik / Tahan+Geser ML", "👆")
                    } else {
                        Triple("ACT_CLICK", "Klik ML", "👆")
                    }
                }
                else -> Triple("ACT_CLICK", "Klik ML", "👆")
            }
        }
        val firstOverlay = active.firstOrNull {
            it.actionType == ActionType.MEME_OVERLAY ||
                it.actionType == ActionType.PROFILE_OVERLAY ||
                it.actionType == ActionType.CUSTOM_IMAGE ||
                it.actionType == ActionType.TEXT_OVERLAY ||
                it.actionType == ActionType.SHOW_OVERLAY ||
                it.actionType == ActionType.ANIMATION ||
                it.actionType == ActionType.TIER_OVERLAY
        }
        if (firstOverlay != null) {
            return Triple("ACT_OVERLAY", "Meme / Profil / Overlay", "🎭")
        }
        val firstSound = active.firstOrNull {
            it.actionType == ActionType.PLAY_SOUND ||
                it.actionType == ActionType.CUSTOM_BACKSOUND ||
                it.actionType == ActionType.AUDIO_SEQUENCE
        }
        if (firstSound != null) {
            return Triple("ACT_SOUND", "Sound / SFX", "🔊")
        }
        return Triple("ACT_OTHER", "Timing / Logika", "⚙️")
    }

    private fun eventEmoji(source: UniversalTriggerSource): String = when (source) {
        UniversalTriggerSource.GIFT -> "🎁"
        UniversalTriggerSource.GIFT_QUANTITY -> "💎"
        UniversalTriggerSource.LIKE -> "❤️"
        UniversalTriggerSource.CHAT -> "💬"
        UniversalTriggerSource.FOLLOW -> "👤"
        UniversalTriggerSource.SHARE -> "🔗"
        UniversalTriggerSource.MEMBER_JOIN -> "👋"
        UniversalTriggerSource.SHOP_ORDER -> "🛒"
        UniversalTriggerSource.CUSTOM_EVENT -> "⚡"
    }

    fun groupRules(
        rules: List<UniversalTriggerRule>,
        mode: ConceptGroupingMode
    ): List<ConceptGroupBucket> {
        if (rules.isEmpty()) return emptyList()
        val indexed = rules.mapIndexed { idx, r -> IndexedRule(idx, r) }
        return when (mode) {
            ConceptGroupingMode.ALL -> listOf(
                ConceptGroupBucket(
                    key = "ALL",
                    title = "Semua Konsep",
                    emoji = "📋",
                    rules = indexed
                )
            )
            ConceptGroupingMode.BY_EVENT -> {
                val grouped = linkedMapOf<String, MutableList<IndexedRule>>()
                val meta = mutableMapOf<String, Pair<String, String>>()
                for (item in indexed) {
                    val src = item.rule.triggerSource
                    val key = "EVT_${src.name}"
                    meta[key] = src.displayName to eventEmoji(src)
                    grouped.getOrPut(key) { mutableListOf() }.add(item)
                }
                val order = listOf(
                    UniversalTriggerSource.GIFT,
                    UniversalTriggerSource.GIFT_QUANTITY,
                    UniversalTriggerSource.LIKE,
                    UniversalTriggerSource.CHAT,
                    UniversalTriggerSource.FOLLOW,
                    UniversalTriggerSource.SHARE,
                    UniversalTriggerSource.MEMBER_JOIN,
                    UniversalTriggerSource.SHOP_ORDER,
                    UniversalTriggerSource.CUSTOM_EVENT
                ).map { "EVT_${it.name}" }
                order.mapNotNull { key ->
                    val list = grouped[key] ?: return@mapNotNull null
                    val (title, emoji) = meta[key] ?: (key to "🎯")
                    ConceptGroupBucket(key = key, title = title, emoji = emoji, rules = list)
                }
            }
            ConceptGroupingMode.BY_ACTION -> {
                val grouped = linkedMapOf<String, MutableList<IndexedRule>>()
                val meta = mutableMapOf<String, Pair<String, String>>()
                for (item in indexed) {
                    val (key, title, emoji) = classifyActionBucket(item.rule)
                    meta[key] = title to emoji
                    grouped.getOrPut(key) { mutableListOf() }.add(item)
                }
                val order = listOf("ACT_CLICK", "ACT_SWIPE", "ACT_HOME", "ACT_OVERLAY", "ACT_SOUND", "ACT_OTHER", "ACT_EMPTY")
                order.mapNotNull { key ->
                    val list = grouped[key] ?: return@mapNotNull null
                    val (title, emoji) = meta[key] ?: (key to "⚙️")
                    ConceptGroupBucket(key = key, title = title, emoji = emoji, rules = list)
                }
            }
        }
    }
}
