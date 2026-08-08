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
class WhitelistStore private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("whitelist", Context.MODE_PRIVATE)

    private val _shows = MutableStateFlow(load())
    val shows: StateFlow<List<Show>> = _shows.asStateFlow()

    init {
        seedFromAssetsIfFirstRun(context)
    }

    /**
     * Loads the bundled starter list the first time the app runs.
     *
     * Entering ~30 series IDs with a TV remote is miserable, so the curated list
     * ships with the app.
     *
     * Guarded by a "have we ever seeded" flag rather than "is the list empty".
     * Those differ in a way that matters: the Chrome extension re-seeded whenever
     * its list hit zero, so a parent who deliberately removed every show found
     * them all back on the next launch (audit §3.9). Removing everything is a
     * legitimate thing to want.
     */
    private fun seedFromAssetsIfFirstRun(context: Context) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()

        if (_shows.value.isNotEmpty()) return   // already curated; don't touch it

        val raw = try {
            context.applicationContext.assets.open(SEED_ASSET)
                .bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return   // no bundled list is a perfectly fine state
        }
        val seeded = try {
            decode(raw)
        } catch (_: Exception) {
            emptyList()
        }
        if (seeded.isNotEmpty()) persist(seeded)
    }

    fun add(show: Show): Boolean {
        val current = _shows.value
        if (current.any { it.seriesId.equals(show.seriesId, ignoreCase = true) }) return false
        persist(current + show)
        return true
    }

    fun remove(seriesId: String) {
        persist(_shows.value.filterNot { it.seriesId.equals(seriesId, ignoreCase = true) })
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

    companion object {
        private const val KEY = "shows_json"
        private const val KEY_SEEDED = "seeded_from_assets"
        private const val SEED_ASSET = "default_whitelist.json"

        @Volatile
        private var instance: WhitelistStore? = null

        /**
         * Process-wide singleton — this MUST be shared.
         *
         * Each instance owns a StateFlow seeded once at construction, so two
         * instances silently diverge: adding a show in Settings updated its own
         * copy while the home screen, holding a different instance, kept showing
         * "No shows yet". The data was on disk the whole time; only the in-memory
         * flow was stale.
         */
        fun get(context: Context): WhitelistStore =
            instance ?: synchronized(this) {
                instance ?: WhitelistStore(context.applicationContext).also { instance = it }
            }
    }
}
