package com.lastgenlabs.crunchylist.crunchyroll

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Everything CrunchyList knows about the Crunchyroll Android TV app.
 *
 * The URI grammar here is UNDOCUMENTED and was derived empirically
 * (see docs/AUDIT-2026-08.md §5.1). Re-verify with tools/probe-deeplinks.ps1
 * after any Crunchyroll app update.
 */
object Crunchyroll {

    const val PACKAGE = "com.crunchyroll.crunchyroid"

    /**
     * Deep link to a series page.
     *
     * PATH-ONLY. Do not add a host: `crunchyroll://www.crunchyroll.com/series/...`
     * silently lands on Crunchyroll's home screen instead of the series, with no
     * error at all. Verified 2026-08-07, and covered by a unit test precisely
     * because the failure is invisible.
     */
    fun seriesUri(seriesId: String): String = "crunchyroll://series/$seriesId"

    /**
     * Deep link straight into playback of one episode.
     *
     * Note the verb is `episode`, NOT `watch` — the web URL path says /watch/ but
     * `crunchyroll://watch/{id}` is not a route and falls back to the home screen.
     */
    fun episodeUri(episodeId: String): String = "crunchyroll://episode/$episodeId"

    fun seriesIntent(seriesId: String): Intent = viewIntent(seriesUri(seriesId))

    fun episodeIntent(episodeId: String): Intent = viewIntent(episodeUri(episodeId))

    /**
     * CLEAR_TASK is not tidiness — it is part of the filter.
     *
     * `FLAG_ACTIVITY_NEW_TASK` alone *reuses* Crunchyroll's existing task and
     * brings it to the front, so whatever was left on that stack comes back with
     * it. Seen on the real TV: opening Mob Psycho landed correctly and then
     * flashed over to a show details page left behind by an earlier launch.
     *
     * Cosmetically that is a flicker. Behaviourally it is a hole: the restored
     * screen is a `ShowDetails`, the classifier only sees the class name, and the
     * session is approved because CrunchyList did start it — so an unapproved show
     * sitting on Crunchyroll's stack would be shown *and permitted*.
     *
     * Clearing costs nothing worth keeping. Resume position lives on the account,
     * not the activity stack, so "Continue: E7" still works.
     */
    private fun viewIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Launches Crunchyroll the way the TV app menu does.
     *
     * Must be the *leanback* lookup. `getLaunchIntentForPackage()` searches for
     * `CATEGORY_LAUNCHER`, and Crunchyroll's TV build only declares
     * `CATEGORY_LEANBACK_LAUNCHER` on its SplashActivity — so the ordinary call
     * returns null and looks indistinguishable from "not installed".
     */
    fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(PACKAGE)
            ?: pm.getLaunchIntentForPackage(PACKAGE)
    }

    /**
     * Crunchyroll's version code. The guard stores this and re-calibrates when it
     * changes, so a CR update can't silently invalidate the approved-screen list
     * (audit §4.2.4). Requires the <queries> entry in the manifest.
     */
    fun versionCode(context: Context): Long? = try {
        context.packageManager.getPackageInfo(PACKAGE, 0).longVersionCode
    } catch (_: Exception) {
        null
    }

}
