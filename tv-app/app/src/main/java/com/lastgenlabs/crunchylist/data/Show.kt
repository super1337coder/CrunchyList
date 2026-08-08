package com.lastgenlabs.crunchylist.data

/**
 * One main character, for the More Info screen.
 *
 * [role] is a one-line "who they are and what they do" — the thing a kid who
 * likes knowing a show before starting it actually wants. Written by hand rather
 * than scraped: the source data was inconsistent in voice, leaked wiki markup,
 * and occasionally gave away plot.
 *
 * [bio] is the same thing at length: what they can actually do, how they behave,
 * why they are worth watching. A one-liner turned out to be too thin — knowing a
 * character before starting is most of the reason this screen exists.
 */
data class CastMember(
    val name: String,
    val role: String = "",
    val image: String? = null,
    val bio: String = ""
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

    /**
     * The short write-up, for the side panel.
     *
     * Kept short on purpose: the panel is sized to fit rather than scroll, because
     * focus stays in the grid and an unreachable scrollbar would just hide the tail
     * of it. Length belongs in [about].
     */
    val description: String = "",

    /**
     * The long write-up, for the More Info screen only.
     *
     * Paragraphs separated by a blank line. This is where the show actually gets
     * described — what the world is, what the characters are trying to do, what
     * watching it is like — for a kid deciding whether to start it.
     */
    val about: String = "",

    /** Episode counts, seasons, which version this is — whatever is worth knowing. */
    val meta: String = "",

    /** Factual line from Crunchyroll: "28 episodes   2 seasons   2023". */
    val facts: String = "",

    /** Crunchyroll's maturity rating, e.g. "TV-14". */
    val rating: String = "",

    /**
     * Crunchyroll's own content labels, e.g. "Violence, Profanity, Smoking".
     *
     * Kept in the data but no longer shown to the kids. These flag presence, not
     * severity, and they are how a parent decides whether a show goes on the list
     * at all — which means by the time a show is on the list the decision has been
     * made, and putting "Violence, Suicide" under something already vetted only
     * makes it look unvetted. `tools/fetch-show.ps1` prints them for that decision.
     */
    val advisories: String = "",

    val cast: List<CastMember> = emptyList()
) {
    /** True when there is anything worth showing in the detail panel. */
    val hasBlurb: Boolean
        get() = hook.isNotBlank() || description.isNotBlank()

    /**
     * True when the More Info screen has anything to show.
     *
     * Includes the plain [description], because the panel can ellipsize it and
     * More info is then the only place the rest of it exists. A show added by
     * pasting a URL has no [about], no cast and no labels — without this it would
     * have no More info button and a trimmed blurb would be genuinely unreadable.
     */
    val hasMoreInfo: Boolean
        get() = longRead.isNotBlank() || cast.isNotEmpty() || facts.isNotBlank()

    /** [about] if there is one, falling back to the panel's shorter text. */
    val longRead: String
        get() = about.ifBlank { description }
}
