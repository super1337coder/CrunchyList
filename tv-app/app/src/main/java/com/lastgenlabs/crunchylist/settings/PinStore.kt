package com.lastgenlabs.crunchylist.settings

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN gate for the parent settings screen.
 *
 * Honest about what this is: a 4-digit PIN has 10,000 possibilities, so no amount
 * of key stretching makes it strong. It is a speed bump that stops a curious kid
 * poking at Settings, not a security boundary. It is stored salted + stretched
 * anyway, because storing it weakly would be gratuitous — the Chrome extension
 * used a bare unsalted SHA-256, which is rainbow-table trivial (audit §3.7).
 */
class PinStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pin", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun set(pin: String) {
        require(pin.matches(Regex("\\d{4}"))) { "PIN must be 4 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.b64())
            .putString(KEY_HASH, hash(pin, salt).b64())
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.unb64() ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.unb64() ?: return false
        val actual = hash(pin, salt)
        // Constant-time compare. Overkill here, but free and avoids a bad habit.
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    fun clear() = prefs.edit().clear().apply()

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEY_HASH = "hash"
        const val KEY_SALT = "salt"
        const val ITERATIONS = 120_000
    }
}
