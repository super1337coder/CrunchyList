package com.lastgenlabs.crunchylist.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.lastgenlabs.crunchylist.MainActivity
import com.lastgenlabs.crunchylist.R
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the foreground activity and bounces the kid back to CrunchyList whenever
 * Crunchyroll is showing anything other than an approved screen.
 *
 * This is what makes CrunchyList a parental control rather than a bookmark list.
 * Without it, one Back press from a show lands in Crunchyroll's full catalogue,
 * and Crunchyroll can be opened straight from the app menu (audit §4.2).
 */
class GuardService : Service() {

    private lateinit var watcher: ForegroundWatcher
    private lateinit var policy: ScreenPolicy
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    /** Suppresses bouncing while a launch we initiated is still settling. */
    private var lastBounceAt = 0L

    override fun onCreate() {
        super.onCreate()
        watcher = ForegroundWatcher(this)
        policy = ScreenPolicy(this)
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job == null) {
            val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = s
            job = s.launch { watchLoop() }
            Log.i(TAG, "guard started")
        }
        // Restart if the system kills us; the guard is the whole point of the app.
        return START_STICKY
    }

    private suspend fun watchLoop() {
        while (scope?.isActive == true) {
            try {
                tick()
            } catch (t: Throwable) {
                // Never let a transient failure kill the guard.
                Log.e(TAG, "tick failed", t)
            }
            delay(POLL_MS)
        }
    }

    private fun tick() {
        // A parent is doing something in Crunchyroll that the guard would
        // otherwise make impossible — signing in, most obviously. Expires by
        // itself; see GuardPause for why it is not a switch.
        if (GuardPause.isActive(this)) return

        val fg = watcher.current() ?: return

        val verdict = policy.classify(fg.packageName, fg.className)
        val action = GuardDecision.decide(
            verdict = verdict,
            sessionApproved = SessionOrigin.isApproved(),
            graceActive = LaunchGrace.isActive()
        )

        if (action.clearSession) SessionOrigin.leftCrunchyroll()
        if (action.affirmSession) SessionOrigin.beginApprovedSession()
        if (action.clearGrace) LaunchGrace.clear()
        if (action.bounce) bounce(fg.className)
    }

    private fun bounce(fromClass: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceAt < BOUNCE_COOLDOWN_MS) return
        lastBounceAt = now
        // Whatever that session was, it is over.
        SessionOrigin.leftCrunchyroll()

        Log.i(TAG, "bouncing from ${fromClass ?: "<unknown>"}")

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(MainActivity.EXTRA_BOUNCED, true)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            // Note: when the BAL exemption is missing this does NOT throw — it is
            // silently dropped. Absence of an exception here is not proof the
            // bounce worked; GuardPermissions.hasOverlayAccess is the real check.
            Log.e(TAG, "bounce threw", t)
        }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.guard_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.guard_notification_title))
            .setContentText(getString(R.string.guard_notification_text))
            .setSmallIcon(R.drawable.banner)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "guard stopping")
        scope?.cancel()
        scope = null
        job = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CLGuard"
        private const val CHANNEL_ID = "guard"
        private const val NOTIFICATION_ID = 1

        /**
         * Poll interval. The probe used 2s, which let CR's home screen show for up
         * to two seconds before the bounce. 600ms keeps the flash short; battery is
         * irrelevant on a mains-powered TV box.
         */
        private const val POLL_MS = 600L

        /** Stops a bounce storm if a launch loops. */
        private const val BOUNCE_COOLDOWN_MS = 1_500L

        /**
         * Starts the guard, returning false if the system refused.
         *
         * Android 12+ blocks starting a foreground service from the background,
         * and a `specialUse` service is not one of the exempt types — so this
         * legitimately fails when called from [BootReceiver] on some builds. It
         * must never crash the app: throwing here would take down the very thing
         * that is supposed to be protecting the TV.
         *
         * When it does fail, opening CrunchyList re-arms the guard (MainActivity
         * calls this from onResume, where the app is foreground and the start is
         * always allowed).
         */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, GuardService::class.java))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "could not start guard: $t")
            false
        }

        // NOTE: no stop() helper. Nothing should casually switch the guard off, and
        // an unused one is an invitation. Force-stopping the app is the honest way,
        // and Application.onCreate re-arms on next launch.
    }
}

/**
 * Grace window covering launches CrunchyList itself initiates.
 *
 * A legitimate `crunchyroll://series/{id}` transits StartupActivity and MainActivity
 * before landing on the show page. Both are bounce triggers, so without this the
 * guard would cancel its own navigation.
 */
object LaunchGrace {
    @Volatile
    private var until = 0L

    /** Call immediately before firing a Crunchyroll intent. */
    fun begin(durationMs: Long = 10_000L) {
        until = SystemClock.elapsedRealtime() + durationMs
    }

    /** Called once an approved screen is reached — no need to stay permissive. */
    fun clear() {
        until = 0L
    }

    fun isActive(): Boolean = SystemClock.elapsedRealtime() < until
}
