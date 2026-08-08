package com.lastgenlabs.crunchylist.data

/**
 * How the bundled starter list is reconciled with what the parent actually has.
 *
 * The original rule was "seed once, then never look at the bundle again". That is
 * right about *which* shows are allowed — a parent who removes a show means it —
 * but wrong about the text attached to them. Improving a write-up and shipping an
 * update did nothing: the new copy sat in the APK while the device kept rendering
 * the text it had cached on first run. Which is this project's usual failure
 * shape — everything reports success and the change is invisible.
 *
 * So the split is:
 *
 *  - **Membership is the parent's.** A bundled show is added only if this install
 *    has never seeded it. Remove one and it stays gone, however many updates land.
 *  - **Text is the bundle's.** Any show still on the list gets the bundled
 *    write-up, cast and labels refreshed from the APK on every launch.
 *
 * Pure and separately tested, like the guard's rules, because the failure mode is
 * silent in both directions: too eager and deleted shows resurrect, too shy and
 * the update is a no-op.
 */
object SeedMerge {

    data class Result(
        val shows: List<Show>,
        /** Every bundled ID this install has ever offered, for the next run. */
        val seenIds: Set<String>
    )

    /**
     * @param current what the parent has now
     * @param bundled the list shipped in this APK
     * @param everSeeded IDs previously offered by any build — empty on a first run
     */
    fun merge(current: List<Show>, bundled: List<Show>, everSeeded: Set<String>): Result {
        val byId = bundled.associateBy { it.seriesId.uppercase() }
        val seen = everSeeded.mapTo(mutableSetOf()) { it.uppercase() }

        // Refresh in place. dateAdded is the parent's history, not the bundle's,
        // and seriesId is the identity — everything else is editorial and comes
        // from the APK.
        val refreshed = current.map { show ->
            val fresh = byId[show.seriesId.uppercase()] ?: return@map show
            fresh.copy(seriesId = show.seriesId, dateAdded = show.dateAdded)
        }

        val have = current.mapTo(mutableSetOf()) { it.seriesId.uppercase() }
        val additions = bundled.filter {
            val id = it.seriesId.uppercase()
            id !in seen && id !in have
        }

        bundled.forEach { seen += it.seriesId.uppercase() }
        return Result(refreshed + additions, seen)
    }
}
