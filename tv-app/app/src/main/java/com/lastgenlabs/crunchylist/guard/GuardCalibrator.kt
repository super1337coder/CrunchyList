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

        if (show == home) {
            return finish(
                false,
                "The deep link no longer opens a show page — Crunchyroll may have changed it. " +
                    "Shows can't be opened until this is fixed."
            )
        }

        policy.rememberApproved(show)
        Crunchyroll.versionCode(context)?.let { policy.calibratedForVersion = it }

        Log.i(TAG, "calibrated: approved=$show home=$home")
        return finish(true, "Verified. Approved screen: ${show.substringAfterLast('.')}")
    }

    /** Launch Crunchyroll the way the app menu would, and see where it lands. */
    private suspend fun phaseHome(): String? {
        val launch = context.packageManager.getLaunchIntentForPackage(Crunchyroll.PACKAGE)
        if (launch == null) {
            Log.w(TAG, "phaseHome: no launch intent for ${Crunchyroll.PACKAGE}")
            return null
        }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i(TAG, "phaseHome: launching Crunchyroll")
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "phaseHome: startActivity failed", it); return null }
        val settled = awaitSettled()
        Log.i(TAG, "phaseHome: settled on ${settled ?: "<nothing>"}")
        return settled
    }

    /** Fire a known-good series deep link and see where it lands. */
    private suspend fun phaseShow(seriesId: String): String? {
        Log.i(TAG, "phaseShow: deep-linking to $seriesId")
        runCatching { context.startActivity(Crunchyroll.seriesIntent(seriesId)) }
            .onFailure { Log.w(TAG, "phaseShow: startActivity failed", it); return null }
        val settled = awaitSettled()
        Log.i(TAG, "phaseShow: settled on ${settled ?: "<nothing>"}")
        return settled
    }

    /**
     * Waits for Crunchyroll's foreground activity to stop changing.
     *
     * "Settled" means the same class is reported [STABLE_POLLS] times running —
     * a launch transits several activities before arriving, and taking the first
     * sighting would learn the splash screen.
     */
    private suspend fun awaitSettled(): String? {
        var candidate: String? = null
        var stable = 0

        repeat(MAX_POLLS) {
            delay(POLL_MS)
            val fg = watcher.current()
            if (fg?.packageName != Crunchyroll.PACKAGE) {
                // Left Crunchyroll entirely — restart the count rather than
                // learning whatever happened to be showing.
                candidate = null
                stable = 0
                return@repeat
            }
            val cls = fg.className ?: return@repeat

            if (cls == candidate) {
                stable++
                if (stable >= STABLE_POLLS) return cls
            } else {
                candidate = cls
                stable = 1
            }
        }
        // Ran out of time; accept a candidate seen more than once, else give up.
        return if (stable >= 2) candidate else null
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
        const val MAX_POLLS = 40          // ~20s per phase
        const val STABLE_POLLS = 4        // ~2s unchanged
        const val TOTAL_GRACE_MS = 60_000L
    }
}
