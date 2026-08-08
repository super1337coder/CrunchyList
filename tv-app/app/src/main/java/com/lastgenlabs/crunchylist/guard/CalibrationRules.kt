package com.lastgenlabs.crunchylist.guard

/**
 * Whether calibration is allowed to learn a screen as approved — as a pure
 * function, because an earlier version of this rule silently disabled the
 * entire filter.
 *
 * That version compared only the *final* screen of each phase. Both phases had
 * captured transit screens before Crunchyroll finished loading, two different
 * transients are not equal, so the check passed and `MainActivity` — the whole
 * catalogue — was written down as approved while the UI reported success.
 *
 * The rule now is stronger and stated positively: a screen is learnable only if
 * it is **unreachable when Crunchyroll is opened normally**.
 */
object CalibrationRules {

    sealed interface Outcome {
        data class Learn(val className: String) : Outcome
        data class Reject(val reason: String) : Outcome
    }

    /**
     * @param candidate  where the series deep link settled
     * @param homePhaseSeen every screen observed while Crunchyroll was opened
     *                      normally — not merely the last one
     */
    fun evaluate(candidate: String?, homePhaseSeen: Set<String>): Outcome {
        if (candidate.isNullOrBlank()) {
            return Outcome.Reject("Crunchyroll never reached a show page.")
        }
        if (homePhaseSeen.isEmpty()) {
            return Outcome.Reject("Couldn't tell what Crunchyroll's own screens look like.")
        }
        if (candidate in homePhaseSeen) {
            return Outcome.Reject(
                "Couldn't tell the show page apart from Crunchyroll's own screens. " +
                    "Nothing was changed — the guard is still using its existing rules."
            )
        }
        return Outcome.Learn(candidate)
    }
}
