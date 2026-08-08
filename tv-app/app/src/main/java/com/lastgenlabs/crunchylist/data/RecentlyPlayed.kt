package com.lastgenlabs.crunchylist.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What has actually been watched, newest first.
 *
 * This exists because CrunchyList takes something away. Crunchyroll's own
 * "Continue watching" row is one of the things the guard bounces, and the app's
 * own list resets to the top of the alphabet every time you come back from an
 * episode — the activity is recreated, so focus lands on the first tile. With 29
 * shows in a three-column grid, that is a hunt after every single episode.
 *
 * No API and no permission is involved: every launch goes through one function,
 * so the app already knows. Deliberately not a favourites list — a shared list of
 * favourites between two kids with different taste needs upkeep and goes stale,
 * whereas this is right by construction.
 */
class RecentlyPlayed private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("recently_played", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(load())

    /**
     * Series IDs, most recently played first.
     *
     * May name shows the parent has since removed. That is deliberate rather than
     * sloppy: [Shelves] resolves these against the current whitelist and drops
     * anything not on it, so a removed show cannot appear on the kid's screen —
     * and if it is added back later it keeps its place. Pruning here would mean
     * the removal path had to remember to do it, and forgetting to is silent.
     */
    val ids: StateFlow<List<String>> = _ids.asStateFlow()

    fun record(seriesId: String) {
        val next = push(_ids.value, seriesId)
        if (next == _ids.value) return
        prefs.edit().putString(KEY, next.joinToString(SEP)).apply()
        _ids.value = next
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.split(SEP)
            ?.filter { it.isNotBlank() }
            ?.take(MAX)
            ?: emptyList()

    companion object {
        private const val KEY = "ids"
        private const val SEP = ","

        /**
         * Six is one row at the width the shelf has beside the detail panel, plus
         * a little to scroll into. Longer and it stops being "what we're watching"
         * and becomes a second copy of the whole list.
         */
        const val MAX = 6

        /** Pure so the ordering rule can be tested without a device. */
        fun push(current: List<String>, seriesId: String, max: Int = MAX): List<String> {
            if (seriesId.isBlank()) return current
            return (listOf(seriesId) + current.filterNot { it.equals(seriesId, ignoreCase = true) })
                .take(max)
        }

        @Volatile
        private var instance: RecentlyPlayed? = null

        /** Process-wide, for the same reason [WhitelistStore] is. */
        fun get(context: Context): RecentlyPlayed =
            instance ?: synchronized(this) {
                instance ?: RecentlyPlayed(context.applicationContext).also { instance = it }
            }
    }
}
