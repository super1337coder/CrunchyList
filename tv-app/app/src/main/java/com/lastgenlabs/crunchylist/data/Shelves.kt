package com.lastgenlabs.crunchylist.data

/**
 * How the home screen is grouped.
 *
 * One alphabetical grid was fine at six shows and is wrong at twenty-nine: every
 * move down a row is arbitrary, and finding something means scanning the lot. A
 * shelf per category makes each vertical move mean something — "I want something
 * funny" is one press rather than a search — and the categories were already in
 * the data.
 *
 * Pure, and tested, because the ways this goes wrong are quiet: a show in no
 * shelf disappears from a parental control's list of allowed shows, and a show in
 * the wrong shelf is just never found.
 */
object Shelves {

    data class Shelf(val title: String, val shows: List<Show>)

    const val KEEP_WATCHING = "Keep watching"

    /**
     * Deliberate rather than alphabetical. The kids this is for ask for action and
     * for funny first, so those lead; Quiet ones is a mood you go looking for, so
     * it sits at the end where it is not in the way.
     */
    private val ORDER = listOf(
        "Action and fights",
        "Comedy",
        "Fantasy and adventure",
        "Games and science",
        "School and sports",
        "Quiet ones"
    )

    /**
     * @param recentIds most recently played first; ids no longer on the list are
     *   ignored rather than dropped from storage, so removing a show and adding it
     *   back does not lose its place.
     */
    fun build(shows: List<Show>, recentIds: List<String> = emptyList()): List<Shelf> {
        if (shows.isEmpty()) return emptyList()

        val byId = shows.associateBy { it.seriesId.uppercase() }
        val recent = recentIds.mapNotNull { byId[it.uppercase()] }

        val categorised = shows.filter { it.category.isNotBlank() }
            .groupBy { it.category }
        val uncategorised = shows.filter { it.category.isBlank() }

        // Known categories in the order above, then anything a parent invented,
        // alphabetically. An unrecognised category must still get a shelf — the
        // alternative is a show that is on the whitelist and nowhere on screen.
        val known = ORDER.filter { it in categorised }
        val unknown = categorised.keys.filterNot { it in ORDER }.sorted()

        val categoryShelves = (known + unknown).map { Shelf(it, categorised.getValue(it)) }

        val leftovers = when {
            uncategorised.isEmpty() -> emptyList()
            // Nothing else to contrast with — a lone shelf called "Everything
            // else" reads as though something is missing.
            categoryShelves.isEmpty() -> listOf(Shelf("All shows", uncategorised))
            else -> listOf(Shelf("Everything else", uncategorised))
        }

        val keepWatching = if (recent.isEmpty()) emptyList() else listOf(Shelf(KEEP_WATCHING, recent))
        return keepWatching + categoryShelves + leftovers
    }
}
