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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        if (!GuardPermissions.allGranted(context)) {
            Log.w(TAG, "boot: guard not started, missing ${GuardPermissions.missing(context)}")
            return
        }
        Log.i(TAG, "boot: starting guard")
        GuardService.start(context)
    }

    private companion object {
        const val TAG = "CLGuard"
    }
}
