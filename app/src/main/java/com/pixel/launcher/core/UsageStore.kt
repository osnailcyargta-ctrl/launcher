package com.pixel.launcher.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks how often each app is opened so the dock can surface the top three.
 *
 * The launcher never queries [android.app.usage.UsageStatsManager]: that needs a
 * special permission and wakes up a system service. Counting our own launches is
 * free, private, and enough to rank apps.
 *
 * Counts decay: every [DECAY_AFTER] launches the whole table is halved, so a
 * burst of usage months ago cannot pin an app to the dock forever.
 */
class UsageStore(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val counts = HashMap<String, Int>()
    private val lastUsed = HashMap<String, Long>()

    private val _ranking = MutableStateFlow<List<String>>(emptyList())

    /** App keys ordered by score, best first. */
    val ranking: StateFlow<List<String>> = _ranking.asStateFlow()

    init {
        for ((k, v) in sp.all) {
            when {
                k.startsWith(PREFIX_COUNT) -> counts[k.removePrefix(PREFIX_COUNT)] = v as? Int ?: 0
                k.startsWith(PREFIX_TIME) -> lastUsed[k.removePrefix(PREFIX_TIME)] = v as? Long ?: 0L
            }
        }
        recompute()
    }

    fun record(key: String) {
        val next = (counts[key] ?: 0) + 1
        counts[key] = next
        lastUsed[key] = System.currentTimeMillis()

        val total = sp.getInt(KEY_TOTAL, 0) + 1
        val editor = sp.edit()
        if (total >= DECAY_AFTER) {
            for (k in counts.keys.toList()) {
                val halved = counts.getValue(k) / 2
                if (halved <= 0) {
                    counts.remove(k)
                    editor.remove(PREFIX_COUNT + k)
                } else {
                    counts[k] = halved
                    editor.putInt(PREFIX_COUNT + k, halved)
                }
            }
            editor.putInt(KEY_TOTAL, 0)
        } else {
            editor.putInt(PREFIX_COUNT + key, next)
            editor.putInt(KEY_TOTAL, total)
        }
        editor.putLong(PREFIX_TIME + key, lastUsed.getValue(key)).apply()

        recompute()
    }

    fun reset() {
        counts.clear()
        lastUsed.clear()
        sp.edit().clear().apply()
        recompute()
    }

    /** Drops entries for apps that are no longer installed. */
    fun prune(validKeys: Set<String>) {
        val stale = counts.keys.filterNot { it in validKeys }
        if (stale.isEmpty()) return
        val editor = sp.edit()
        for (k in stale) {
            counts.remove(k)
            lastUsed.remove(k)
            editor.remove(PREFIX_COUNT + k).remove(PREFIX_TIME + k)
        }
        editor.apply()
        recompute()
    }

    private fun recompute() {
        _ranking.value = counts.keys
            .sortedWith(
                compareByDescending<String> { counts[it] ?: 0 }
                    .thenByDescending { lastUsed[it] ?: 0L },
            )
            .take(RANK_SIZE)
    }

    private companion object {
        const val FILE = "pixel_launcher_usage"
        const val PREFIX_COUNT = "c:"
        const val PREFIX_TIME = "t:"
        const val KEY_TOTAL = "total_since_decay"
        const val DECAY_AFTER = 200
        const val RANK_SIZE = 16
    }
}
