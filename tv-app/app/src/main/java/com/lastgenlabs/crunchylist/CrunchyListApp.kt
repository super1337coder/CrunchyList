package com.lastgenlabs.crunchylist

import android.app.Application
import android.util.Log
import com.lastgenlabs.crunchylist.guard.GuardPermissions
import com.lastgenlabs.crunchylist.guard.GuardService

/**
 * Single place the guard is armed.
 *
 * Previously only MainActivity.onResume started it, which left a real hole: any
 * entry path that skips the home screen — opening Settings directly, an app
 * update, a force-stop, `pm clear` — left the TV completely unguarded while the
 * app still looked healthy. Application.onCreate runs for *every* component
 * launch, so there is no way into this app that doesn't pass through here.
 *
 * GuardService.start() is deliberately failure-tolerant: Android can refuse a
 * foreground-service start from the background, and the app must not crash when
 * it does.
 */
class CrunchyListApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!GuardPermissions.allGranted(this)) {
            Log.w(TAG, "guard not armed, missing: ${GuardPermissions.missing(this)}")
            return
        }
        if (!GuardService.start(this)) {
            Log.w(TAG, "guard start refused; will retry when an activity resumes")
        }
    }

    private companion object {
        const val TAG = "CLGuard"
    }
}
