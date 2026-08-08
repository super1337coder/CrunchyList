package com.lastgenlabs.clprobe;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

/**
 * Feasibility probe for the CrunchyList guard.
 *
 * Polls UsageStatsManager and reports the most recent foregrounded activity.
 * The question it answers: does an ordinary app actually receive Crunchyroll's
 * activity CLASS NAMES (not just the package)? If yes, a guard can distinguish
 *
 *     ...showdetails.ui.ShowDetailsActivity   -> approved
 *     ...player.ui.PlayerActivity             -> approved
 *     ...main.ui.MainActivity                 -> browsing, bounce back
 *
 * without an AccessibilityService and without reading any UI text.
 */
public class ProbeActivity extends Activity {

    private static final String TAG = "CLPROBE";
    private static final String CR = "com.crunchyroll.crunchyroid";
    private static final long WINDOW_MS = 60_000L;
    private static final long POLL_MS = 2_000L;

    private UsageStatsManager usm;
    private TextView view;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String lastReported = "";
    private int pollCount = 0;
    private int bounceAttempts = 0;

    private final Runnable poller = new Runnable() {
        @Override public void run() { poll(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new TextView(this);
        view.setTextSize(22f);
        view.setTextColor(Color.WHITE);
        view.setBackgroundColor(Color.parseColor("#101014"));
        view.setPadding(48, 48, 48, 48);
        setContentView(view);

        usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (usm == null) {
            fail("USAGE_STATS_SERVICE unavailable");
            return;
        }
        Log.i(TAG, "probe started");
        poll();
    }

    private void poll() {
        pollCount++;
        long now = System.currentTimeMillis();

        UsageEvents events;
        try {
            events = usm.queryEvents(now - WINDOW_MS, now);
        } catch (SecurityException se) {
            fail("SecurityException: " + se.getMessage());
            return;
        }

        if (events == null) {
            fail("queryEvents returned null — permission almost certainly not granted");
            return;
        }

        String latestPkg = null;
        String latestClass = null;
        long latestTs = 0;
        int resumedCount = 0;

        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                resumedCount++;
                if (e.getTimeStamp() >= latestTs) {
                    latestTs = e.getTimeStamp();
                    latestPkg = e.getPackageName();
                    latestClass = e.getClassName();
                }
            }
        }

        if (resumedCount == 0) {
            render("no ACTIVITY_RESUMED events in the last 60s.\n"
                 + "If this persists, usage access is not granted.\n\npolls: " + pollCount);
            handler.postDelayed(poller, POLL_MS);
            return;
        }

        String key = latestPkg + "/" + latestClass;
        if (!key.equals(lastReported)) {
            lastReported = key;
            // The line that matters: is className populated, or null?
            Log.i(TAG, "FOREGROUND pkg=" + latestPkg + " class=" + latestClass);
        }

        String verdict;
        if (latestClass == null) {
            verdict = "className is NULL -> guard NOT viable this way";
        } else if (CR.equals(latestPkg)) {
            if (latestClass.contains("ShowDetailsActivity") || latestClass.contains("PlayerActivity")) {
                verdict = "CR: APPROVED screen";
            } else {
                verdict = "CR: NOT APPROVED -> bouncing";
                attemptBounce();
            }
        } else {
            verdict = "not Crunchyroll";
        }

        render("package:\n  " + latestPkg
             + "\n\nclass:\n  " + latestClass
             + "\n\nverdict:\n  " + verdict
             + "\n\nresumed events in window: " + resumedCount
             + "\npolls: " + pollCount);

        handler.postDelayed(poller, POLL_MS);
    }

    /**
     * The other half of the guard question: detection is proven, but can a
     * BACKGROUNDED app actually pull itself back to the front? Android 10+
     * restricts background activity launches (BAL). Try the naive approach and
     * log the outcome — a BAL denial shows up in logcat as
     * "Background activity launch blocked".
     *
     * Bounces at most twice so a failure doesn't spin forever.
     */
    private void attemptBounce() {
        if (bounceAttempts >= 2) {
            Log.w(TAG, "BOUNCE giving up after " + bounceAttempts + " attempts");
            return;
        }
        bounceAttempts++;
        Log.i(TAG, "BOUNCE attempt " + bounceAttempts + " — startActivity(self) from background");
        try {
            android.content.Intent self = new android.content.Intent(this, ProbeActivity.class);
            self.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(self);
            Log.i(TAG, "BOUNCE startActivity() returned without throwing");
        } catch (Exception ex) {
            Log.e(TAG, "BOUNCE threw: " + ex);
        }
    }

    private void render(String s) {
        if (view != null) view.setText(s);
    }

    private void fail(String msg) {
        Log.e(TAG, "FAIL " + msg);
        render("FAILED\n\n" + msg);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(poller);
        super.onDestroy();
    }
}
