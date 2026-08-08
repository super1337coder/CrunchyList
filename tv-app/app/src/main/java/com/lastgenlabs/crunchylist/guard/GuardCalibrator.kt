package com.lastgenlabs.crunchylist.guard

import android.content.Context
import android.util.Log
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll
import kotlinx.coroutines.delay

/**
 * Teaches the guard which Crunchyroll screens are approved, by observation rather
 * than by hardcoded class names (audit §4.2.4).
 *
 * The insight that makes this work: **CrunchyList controls the launch.** Whatever
 * activity settles after firing a known-good deep link *is* the approved series
 * screen, by definition. That survives Crunchyroll renaming — or even obfuscating —
 * its classes, because calibration never needs to know what the class is called.
 *
 * Two phases, so "approved" is learned relative to "not approved" rather than
 * against a hardcoded list:
 *
 *   A. Launch Crunchyroll normally  -> whatever settles is its HOME. Not approved.
 *   B. Fire crunchyroll://series/ID -> whatever settles is the SHOW PAGE. Approved.
 *
 * If B settles on the same class as A, the deep link is no longer working and the
 * guard is told nothing. Failing to learn leaves the policy fail-closed.
 */
class GuardCalibrator(private val context: Context) {

    data class Result(val ok: Boolean, val message: String)

    private val watcher = ForegroundWatcher(context)
    private val policy = ScreenPolicy(context)

    suspend fun calibrate(referenceSeriesId: String): Result {
        if (!Crunchyroll.isInstalled(context)) {
            return Result(false, "Crunchyroll isn't installed.")
        }
        if (!GuardPermissions.hasUsageAccess(context)) {
            return Result(false, "Usage access isn't granted — can't observe screens.")
        }

        // Keep the guard from bouncing the launches calibration itself performs.
        LaunchGrace.begin(TOTAL_GRACE_MS)

        val home = runCatching { phaseHome() }.getOrNull()
            ?: return finish(false, "Couldn't tell what Crunchyroll's home screen is.")

        val show = runCatching { phaseShow(referenceSeriesId) }.getOrNull()
            ?: return finish(false, "Crunchyroll never reached a show page.")

        // The safety rule lives in CalibrationRules so it can be unit-tested — see
        // that file for why it is stated the way it is.
        return when (val outcome = CalibrationRules.evaluate(show.settled, home.seen)) {
            is CalibrationRules.Outcome.Reject -> finish(false, outcome.reason)

            is CalibrationRules.Outcome.Learn -> {
                policy.rememberApproved(outcome.className)
                Crunchyroll.versionCode(context)?.let { policy.calibratedForVersion = it }
                Log.i(TAG, "calibrated: approved=${outcome.className}; home phase saw ${home.seen}")
                finish(true, "Verified. Approved screen: ${outcome.className.substringAfterLast('.')}")
            }
        }
    }

    /** What a phase observed: where it ended up, and everything it passed through. */
    private data class Observation(val settled: String, val seen: Set<String>)

    /** Launch Crunchyroll the way the app menu would, and see where it lands. */
    private suspend fun phaseHome(): Observation? {
        val launch = Crunchyroll.launchIntent(context)
        if (launch == null) {
            Log.w(TAG, "phaseHome: no launch intent for ${Crunchyroll.PACKAGE}")
            return null
        }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i(TAG, "phaseHome: launching Crunchyroll")
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "phaseHome: startActivity failed", it); return null }
        val obs = awaitSettled()
        Log.i(TAG, "phaseHome: settled=${obs?.settled ?: "<nothing>"} seen=${obs?.seen}")
        return obs
    }

    /** Fire a known-good series deep link and see where it lands. */
    private suspend fun phaseShow(seriesId: String): Observation? {
        Log.i(TAG, "phaseShow: deep-linking to $seriesId")
        runCatching { context.startActivity(Crunchyroll.seriesIntent(seriesId)) }
            .onFailure { Log.w(TAG, "phaseShow: startActivity failed", it); return null }
        val obs = awaitSettled()
        Log.i(TAG, "phaseShow: settled=${obs?.settled ?: "<nothing>"} seen=${obs?.seen}")
        return obs
    }

    /**
     * Waits for Crunchyroll's foreground activity to stop changing, and reports
     * every class seen along the way.
     *
     * Patience is the whole game here. Crunchyroll transits splash -> startup ->
     * main -> detail, and each hop can sit still for a second or two on a cold
     * start. An earlier 2s threshold declared the *splash screen* settled, which
     * poisoned calibration. The confirmation pass afterwards exists because a
     * screen can look stable and then still be replaced.
     */
    private suspend fun awaitSettled(): Observation? {
        val seen = linkedSetOf<String>()
        var candidate: String? = null
        var stable = 0

        repeat(MAX_POLLS) {
            delay(POLL_MS)
            val fg = watcher.current()
            if (fg?.packageName != Crunchyroll.PACKAGE) {
                candidate = null
                stable = 0
                return@repeat
            }
            val cls = fg.className ?: return@repeat
            seen += cls

            if (cls == candidate) {
                stable++
                if (stable >= STABLE_POLLS) {
                    // Confirmation pass: keep watching a while longer and make sure
                    // nothing supersedes it.
                    repeat(CONFIRM_POLLS) {
                        delay(POLL_MS)
                        val again = watcher.current()
                        if (again?.packageName == Crunchyroll.PACKAGE) {
                            again.className?.let { seen += it }
                            if (again.className != null && again.className != cls) {
                                candidate = again.className
                                stable = 1
                                return@repeat
                            }
                        }
                    }
                    if (candidate == cls) return Observation(cls, seen)
                }
            } else {
                candidate = cls
                stable = 1
            }
        }
        Log.w(TAG, "awaitSettled: never stabilised; seen=$seen")
        return null
    }

    private fun finish(ok: Boolean, message: String): Result {
        LaunchGrace.clear()
        // Log every outcome, not just success. Calibration runs unattended-ish and
        // navigates away from the screen showing its result, so a silent failure is
        // invisible from both directions.
        if (ok) Log.i(TAG, "calibration OK: $message") else Log.w(TAG, "calibration FAILED: $message")

        // Return to Settings, not the home screen: Settings is where the result is
        // displayed, and bouncing the parent to the grid throws the message away.
        runCatching {
            context.startActivity(
                android.content.Intent(
                    context,
                    com.lastgenlabs.crunchylist.settings.SettingsActivity::class.java
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }.onFailure { Log.w(TAG, "couldn't return to settings", it) }
        return Result(ok, message)
    }

    private companion object {
        const val TAG = "CLGuard"
        const val POLL_MS = 500L
        const val MAX_POLLS = 90          // ~45s per phase — cold starts are slow
        const val STABLE_POLLS = 10       // ~5s unchanged before believing it
        const val CONFIRM_POLLS = 6       // ~3s more to catch a late replacement
        const val TOTAL_GRACE_MS = 150_000L
    }
}
