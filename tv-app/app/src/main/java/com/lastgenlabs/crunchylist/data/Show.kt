package com.lastgenlabs.crunchylist.data

/**
 * One main character, for the More Info screen.
 *
 * [role] is a one-line "who they are and what they do" — the thing a kid who
 * likes knowing a show before starting it actually wants. Written by hand rather
 * than scraped: the source data was inconsistent in voice, leaked wiki markup,
 * and occasionally gave away plot.
 */
data class CastMember(
    val name: String,
    val role: String = "",
    val image: String? = null
)

/**
 * One parent-approved series.
 *
 * [seriesId] is Crunchyroll's catalogue ID (e.g. "G4PH0WXVJ"). These are content
 * identifiers, not app internals — they survive Crunchyroll app updates, which is
 * why the whitelist itself carries no rename risk (audit §4.2.4).
 *
 * Everything past [dateAdded] is optional. A show added by pasting a URL has none
 * of it and still works; the UI just shows less.
 */
data class Show(
    val seriesId: String,
    val title: String,
    val imageUrl: String? = null,
    val dateAdded: String = "",

    /** e.g. "Fantasy and adventure" — the grouping from the source watch list. */
    val category: String = "",

    /** The one-line reason to pick this, e.g. "If you want a show where nobody is cruel." */
    val hook: String = "",

    /** The full write-up. */
    val description: String = "",

    /** Episode counts, seasons, which version this is — whatever is worth knowing. */
    val meta: String = "",

    /** Factual line from Crunchyroll: "28 episodes   2 seasons   2023". */
    val facts: String = "",

    /** Crunchyroll's maturity rating, e.g. "TV-14". */
    val rating: String = "",

    /**
     * Crunchyroll's own content labels, e.g. "Violence, Profanity, Smoking".
     * These flag presence, not severity — useful, not a substitute for a parent
     * having watched it.
     */
    val advisories: String = "",

    val cast: List<CastMember> = emptyList()
) {
    /** True when there is anything worth showing in the detail panel. */
    val hasBlurb: Boolean
        get() = hook.isNotBlank() || description.isNotBlank()

    /** True when the More Info screen would have anything beyond the panel. */
    val hasMoreInfo: Boolean
        get() = cast.isNotEmpty() || facts.isNotBlank() || advisories.isNotBlank()
}
