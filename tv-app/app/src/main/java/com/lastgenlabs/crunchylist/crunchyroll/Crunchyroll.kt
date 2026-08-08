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
     * error. Verified 2026-08-07.
     */
    fun seriesIntent(seriesId: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("crunchyroll://series/$seriesId")).apply {
            setPackage(PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Deep link straight into playback of one episode.
     *
     * Note the verb is `episode`, NOT `watch` — the web URL path says /watch/ but
     * `crunchyroll://watch/{id}` is not a route and falls back to the home screen.
     */
    fun episodeIntent(episodeId: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("crunchyroll://episode/$episodeId")).apply {
            setPackage(PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
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

    /**
     * Every activity Crunchyroll declares. Used by calibration to match approved
     * screens by shape rather than by a hardcoded fully-qualified name.
     */
    fun declaredActivities(context: Context): List<String> = try {
        val flags = PackageManager.GET_ACTIVITIES
        context.packageManager.getPackageInfo(PACKAGE, flags)
            .activities
            ?.mapNotNull { it.name }
            ?.filter { it.startsWith(PACKAGE) }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
