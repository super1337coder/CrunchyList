package com.lastgenlabs.crunchylist.guard

import android.content.Context
import android.content.SharedPreferences
import com.lastgenlabs.crunchylist.crunchyroll.Crunchyroll

/**
 * Decides whether whatever is on screen right now is allowed.
 *
 * The design principle throughout is FAIL CLOSED: anything not positively
 * recognised as an approved Crunchyroll screen results in a bounce. If
 * Crunchyroll renames its activities, CrunchyList becomes unusable — never
 * permissive. That is the opposite of the Chrome extension it replaces, which
 * failed open in five separate places (audit §3.1–3.3).
 */
class ScreenPolicy(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("guard", Context.MODE_PRIVATE)

    enum class Verdict {
        /** An approved Crunchyroll screen. Leave it alone. */
        ALLOW,

        /** Crunchyroll, but somewhere the kid shouldn't be. Bounce. */
        BOUNCE,

        /** Not Crunchyroll at all — CrunchyList itself, the system UI, anything else. */
        IGNORE
    }

    /**
     * Substrings identifying approved screens. Matched against the activity class
     * name rather than pinned to a fully-qualified name, so a package reshuffle
     * like `.ui.showdetails.DetailsActivity` still matches.
     *
     * Calibration (§4.2.4) can add exact learned names on top of these, which is
     * what keeps the guard working if Crunchyroll ever ships obfuscated classes
     * where these substrings no longer appear.
     */
    private val approvedShapes = listOf("ShowDetails", "Player")

    /**
     * Class names learned by calibration — exact matches, no heuristics.
     * Survives obfuscation because it never needs to know what the class means.
     */
    private var learnedApproved: Set<String>
        get() = prefs.getStringSet(KEY_LEARNED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LEARNED, value).apply()

    /** Crunchyroll's versionCode at the time calibration last succeeded. */
    var calibratedForVersion: Long
        get() = prefs.getLong(KEY_CAL_VERSION, -1L)
        set(value) = prefs.edit().putLong(KEY_CAL_VERSION, value).apply()

    fun rememberApproved(className: String) {
        if (className.isBlank()) return
        learnedApproved = learnedApproved + className
    }

    fun clearLearned() {
        prefs.edit().remove(KEY_LEARNED).remove(KEY_CAL_VERSION).apply()
    }

    /** True when Crunchyroll has been updated since calibration last ran. */
    fun needsCalibration(context: Context): Boolean {
        val current = Crunchyroll.versionCode(context) ?: return false
        return current != calibratedForVersion
    }

    fun classify(packageName: String?, className: String?): Verdict {
        if (packageName != Crunchyroll.PACKAGE) return Verdict.IGNORE

        // Crunchyroll is in the foreground but we can't tell which screen.
        // Fail closed.
        if (className.isNullOrBlank()) return Verdict.BOUNCE

        if (className in learnedApproved) return Verdict.ALLOW
        if (approvedShapes.any { className.contains(it, ignoreCase = true) }) return Verdict.ALLOW

        return Verdict.BOUNCE
    }

    private companion object {
        const val KEY_LEARNED = "learned_approved_classes"
        const val KEY_CAL_VERSION = "calibrated_for_version"
    }
}
