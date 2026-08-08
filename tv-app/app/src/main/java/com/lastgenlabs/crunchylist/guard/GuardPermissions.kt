package com.lastgenlabs.crunchylist.guard

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

/**
 * The two special-access grants the guard needs. Neither is an install-time
 * permission — declaring them in the manifest is necessary but not sufficient.
 *
 * Grant on a device with adb (one-time):
 *
 *     adb shell appops set com.lastgenlabs.crunchylist GET_USAGE_STATS allow
 *     adb shell appops set com.lastgenlabs.crunchylist SYSTEM_ALERT_WINDOW allow
 *
 * Or via Settings > Apps > Special app access. Whether those screens are
 * reachable in the Google TV Streamer's UI is unverified (audit §4.2).
 */
object GuardPermissions {

    /** Needed to see which activity is in the foreground. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Needed to bring CrunchyList back to the front from the background.
     *
     * Without it, Android blocks the bounce — "Background activity launch blocked!
     * goo.gle/android-bal" — and, critically, startActivity() returns WITHOUT
     * throwing. The guard would look like it was working while doing nothing.
     * With it the system reports BAL_ALLOW_SAW_PERMISSION and the bounce lands.
     */
    fun hasOverlayAccess(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun allGranted(context: Context): Boolean =
        hasUsageAccess(context) && hasOverlayAccess(context)

    fun missing(context: Context): List<String> = buildList {
        if (!hasUsageAccess(context)) add("Usage access (GET_USAGE_STATS)")
        if (!hasOverlayAccess(context)) add("Display over other apps (SYSTEM_ALERT_WINDOW)")
    }
}
