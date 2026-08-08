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
        syncWithBundle(context)
    }

    /**
     * Reconciles the list on disk with the one bundled in this APK.
     *
     * Entering ~30 series IDs with a TV remote is miserable, so the curated list
     * ships with the app — but it is a starting point, not an authority. See
     * [SeedMerge] for the split between what the parent owns (which shows) and
     * what the APK owns (the write-ups).
     *
     * Membership is tracked by "have we ever offered this ID" rather than "is the
     * list empty". Those differ in a way that matters: the Chrome extension
     * re-seeded whenever its list hit zero, so a parent who deliberately removed
     * every show found them all back on the next launch (audit §3.9).
     */
    private fun syncWithBundle(context: Context) {
        val raw = try {
            context.applicationContext.assets.open(SEED_ASSET)
                .bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return   // no bundled list is a perfectly fine state
        }
        val bundled = try {
            decode(raw)
        } catch (_: Exception) {
            emptyList()
        }
        if (bundled.isEmpty()) return

        val everSeeded: Set<String> = when {
            // First ever launch — nothing has been offered, so all of it is new.
            !prefs.getBoolean(KEY_SEEDED, false) -> emptySet()
            // Upgraded from a build that recorded only *that* it seeded, not what.
            // The current list is the only evidence there is, so treat it as the
            // record: a show removed under the old build returns once, and never
            // again after that.
            else -> prefs.getStringSet(KEY_SEEN_IDS, null)?.toSet()
                ?: _shows.value.mapTo(mutableSetOf()) { it.seriesId.uppercase() }
        }

        val result = SeedMerge.merge(_shows.value, bundled, everSeeded)
        prefs.edit()
            .putBoolean(KEY_SEEDED, true)
            .putStringSet(KEY_SEEN_IDS, result.seenIds)
            .apply()
        if (result.shows != _shows.value) persist(result.shows)
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
                    put("category", s.category)
                    put("hook", s.hook)
                    put("description", s.description)
                    put("about", s.about)
                    put("meta", s.meta)
                    put("facts", s.facts)
                    put("rating", s.rating)
                    put("advisories", s.advisories)
                    put("cast", JSONArray().apply {
                        s.cast.forEach { c ->
                            put(JSONObject().apply {
                                put("name", c.name)
                                put("role", c.role)
                                put("bio", c.bio)
                                put("image", c.image ?: JSONObject.NULL)
                            })
                        }
                    })
                }
            )
        }
        return arr.toString()
    }

    /**
     * All text fields default to empty, so a whitelist written by an older build —
     * or a show the parent added by pasting a URL — decodes fine and simply has no
     * blurb to show.
     */
    private fun decode(raw: String): List<Show> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("seriesId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Show(
                seriesId = id,
                title = o.optString("title", id),
                imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                dateAdded = o.optString("dateAdded", ""),
                category = o.optString("category", ""),
                hook = o.optString("hook", ""),
                description = o.optString("description", ""),
                about = o.optString("about", ""),
                meta = o.optString("meta", ""),
                facts = o.optString("facts", ""),
                rating = o.optString("rating", ""),
                advisories = o.optString("advisories", ""),
                cast = o.optJSONArray("cast")?.let { arr2 ->
                    (0 until arr2.length()).mapNotNull { k ->
                        val c = arr2.optJSONObject(k) ?: return@mapNotNull null
                        val n = c.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        CastMember(
                            name = n,
                            role = c.optString("role", ""),
                            image = c.optString("image").takeIf { it.isNotBlank() && it != "null" },
                            bio = c.optString("bio", "")
                        )
                    }
                } ?: emptyList()
            )
        }
    }

    companion object {
        private const val KEY = "shows_json"
        private const val KEY_SEEDED = "seeded_from_assets"
        private const val KEY_SEEN_IDS = "seeded_ids"
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
