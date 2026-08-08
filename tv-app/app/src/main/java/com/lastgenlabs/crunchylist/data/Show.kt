package com.lastgenlabs.crunchylist.data

/**
 * One parent-approved series.
 *
 * [seriesId] is Crunchyroll's catalogue ID (e.g. "G4PH0WXVJ"). These are content
 * identifiers, not app internals — they survive Crunchyroll app updates, which is
 * why the whitelist itself carries no rename risk (audit §4.2.4).
 *
 * The four text fields are the parent's own words, not Crunchyroll's synopsis.
 * They are what lets a kid choose a show for a reason rather than by cover art,
 * and they are all optional — a show added by pasting a URL has none of them and
 * still works.
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
    val meta: String = ""
) {
    /** True when there is anything worth showing in the detail panel. */
    val hasBlurb: Boolean
        get() = hook.isNotBlank() || description.isNotBlank()
}
