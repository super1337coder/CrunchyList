package com.lastgenlabs.crunchylist.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the guard after a reboot.
 *
 * Without this, power-cycling the TV silently disables enforcement — the kid would
 * simply find Crunchyroll unguarded the next morning.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handled = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in handled) return

        if (!GuardPermissions.allGranted(context)) {
            Log.w(TAG, "boot: guard not started, missing ${GuardPermissions.missing(context)}")
            return
        }

        // Must not throw. Android 12+ can refuse a foreground-service start from a
        // broadcast receiver, and an uncaught ForegroundServiceStartNotAllowedException
        // here crashes the app on every boot.
        if (GuardService.start(context)) {
            Log.i(TAG, "boot: guard started")
        } else {
            Log.w(TAG, "boot: system refused the guard start; it will arm when CrunchyList is opened")
        }
    }

    private companion object {
        const val TAG = "CLGuard"
    }
}
