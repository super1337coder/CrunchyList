package com.lastgenlabs.crunchylist.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The parent-curated list of allowed shows.
 *
 * Deliberately a plain JSON blob in SharedPreferences: the list is a handful of
 * entries, read on every launch, and written rarely. A database would be more
 * machinery than the problem deserves.
 */
class WhitelistStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("whitelist", Context.MODE_PRIVATE)

    private val _shows = MutableStateFlow(load())
    val shows: StateFlow<List<Show>> = _shows.asStateFlow()

    fun add(show: Show): Boolean {
        val current = _shows.value
        if (current.any { it.seriesId.equals(show.seriesId, ignoreCase = true) }) return false
        persist(current + show)
        return true
    }

    fun remove(seriesId: String) {
        persist(_shows.value.filterNot { it.seriesId.equals(seriesId, ignoreCase = true) })
    }

    fun update(show: Show) {
        persist(_shows.value.map { if (it.seriesId == show.seriesId) show else it })
    }

    /** A reference series for guard calibration — any entitled show will do. */
    fun anySeriesId(): String? = _shows.value.firstOrNull()?.seriesId

    private fun persist(list: List<Show>) {
        val sorted = list.sortedBy { it.title.lowercase() }
        prefs.edit().putString(KEY, encode(sorted)).apply()
        _shows.value = sorted
    }

    private fun load(): List<Show> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            decode(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encode(list: List<Show>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("seriesId", s.seriesId)
                    put("title", s.title)
                    put("imageUrl", s.imageUrl ?: JSONObject.NULL)
                    put("dateAdded", s.dateAdded)
                }
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<Show> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("seriesId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Show(
                seriesId = id,
                title = o.optString("title", id),
                imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                dateAdded = o.optString("dateAdded", "")
            )
        }
    }

    private companion object {
        const val KEY = "shows_json"
    }
}
