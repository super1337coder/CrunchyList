package com.lastgenlabs.crunchylist.crunchyroll

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Works out which client identity Crunchyroll's edge will actually accept.
 *
 * Background: the token endpoint returns 200 to curl on a desktop but 403 to the
 * same request from Android, from the same IP. That rules out IP reputation and
 * points at Cloudflare fingerprinting the client — so the fix is empirical, not
 * theoretical. This tries several header sets in one pass and reports what works.
 *
 * Kept in the app (behind Settings > Test connection) rather than thrown away:
 * if Crunchyroll tightens its edge later, this is how you find out what changed.
 */
object TokenDiagnostics {

    private const val TAG = "CLApi"
    private const val TOKEN_URL = "https://www.crunchyroll.com/auth/v1/token"
    private const val BODY = "grant_type=client_id&client_id=cr_web&client_secret="

    private const val CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    data class Variant(val name: String, val headers: Map<String, String>, val body: String = BODY)

    private val variants: List<Variant> = listOf(
        Variant(
            "1-current-chrome-minimal",
            mapOf(
                "User-Agent" to CHROME_UA,
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json",
                "Referer" to "https://www.crunchyroll.com/"
            )
        ),
        Variant(
            // A Chrome UA with none of Chrome's client hints is itself a bot signal.
            "2-chrome-full-hints",
            mapOf(
                "User-Agent" to CHROME_UA,
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Origin" to "https://www.crunchyroll.com",
                "Referer" to "https://www.crunchyroll.com/",
                "sec-ch-ua" to "\"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
                "sec-ch-ua-mobile" to "?0",
                "sec-ch-ua-platform" to "\"Windows\"",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"
            )
        ),
        Variant(
            // Honest app identity. An edge that serves Crunchyroll's own Android
            // client may treat this better than a browser UA it can tell is fake.
            "3-cr-android-app",
            mapOf(
                "User-Agent" to "Crunchyroll/3.67.0 Android/14 okhttp/4.12.0",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json"
            )
        ),
        Variant(
            "4-okhttp-plain",
            mapOf(
                "User-Agent" to "okhttp/4.12.0",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
        ),
        Variant(
            // No override at all — whatever Android sends by default.
            "5-android-default",
            mapOf("Content-Type" to "application/x-www-form-urlencoded")
        )
    )

    /** Returns a human-readable summary and logs each result under CLApi. */
    suspend fun run(): String = withContext<String>(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        var winner: String? = null

        for (v in variants) {
            val result = attempt(v)
            Log.i(TAG, "DIAG ${v.name} -> $result")
            lines.add("${v.name}: $result")
            if (winner == null && result.endsWith("OK")) winner = v.name
        }

        Log.i(TAG, "DIAG summary: ${winner ?: "none worked"}")
        val header = if (winner != null) "Works: $winner" else "All variants blocked."
        header + "\n" + lines.joinToString("\n")
    }

    private fun attempt(v: Variant): String {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                v.headers.forEach { (k, value) -> setRequestProperty(k, value) }
                doOutput = true
                outputStream.use { it.write(v.body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code in 200..299) "HTTP $code OK" else "HTTP $code"
        } catch (e: Exception) {
            "EX ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            conn?.disconnect()
        }
    }
}
