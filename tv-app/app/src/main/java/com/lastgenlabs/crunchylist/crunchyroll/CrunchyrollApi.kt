package com.lastgenlabs.crunchylist.crunchyroll

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches series titles and poster art from Crunchyroll's public CMS API.
 *
 * Ported from the Chrome extension's background.js, which is the one piece of it
 * worth keeping.
 *
 * DO NOT send a browser User-Agent from here. It is tempting — the extension used
 * one, and a desktop `curl` needs one or Cloudflare 403s. On Android it does the
 * opposite: measured 2026-08-07 with [TokenDiagnostics],
 *
 *     Chrome UA, minimal headers   -> 403
 *     Chrome UA, full client hints -> 403
 *     honest app UA                -> 200
 *     okhttp/plain UA              -> 200
 *     no UA at all                 -> 200
 *
 * Cloudflare is fingerprinting the client. Claiming to be Chrome while presenting
 * Android's TLS/HTTP2 stack is a *mismatch*, and the mismatch is what gets blocked.
 * Being honest about what we are sails straight through.
 */
object CrunchyrollApi {

    private const val TAG = "CLApi"
    private const val TOKEN_URL = "https://www.crunchyroll.com/auth/v1/token"

    /** Honest client identity. See the class note — do not "fix" this to a browser UA. */
    private const val UA = "CrunchyList/0.1 (Android TV; parental control) okhttp/4.12.0"

    data class SeriesInfo(val seriesId: String, val title: String?, val imageUrl: String?)

    private var cachedToken: String? = null
    private var tokenExpiresAt = 0L

    /**
     * Pulls the series ID out of a Crunchyroll series URL, or accepts a bare ID.
     *
     * The bare-ID case is deliberately strict. Every Crunchyroll series ID observed
     * is `G` followed by 8 alphanumerics (G4PH0WXVJ, GEXH3WKP7, …). A loose pattern
     * happily accepts a typo — a stray leading character produced "EG4PH0WXVJ"
     * during testing, which is added silently and then 404s inside the Crunchyroll
     * app, where the parent has no idea why. Since the metadata API can't currently
     * be relied on to validate the ID (see [fetchSeries]), the shape check is the
     * only guard against that.
     */
    fun parseSeriesId(input: String): String? {
        val fromUrl = Regex(
            """crunchyroll\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?series/([A-Z0-9]+)""",
            RegexOption.IGNORE_CASE
        ).find(input)?.groupValues?.get(1)
        if (fromUrl != null) return fromUrl.uppercase()

        val bare = input.trim()
        return bare.takeIf { SERIES_ID_SHAPE.matches(it) }?.uppercase()
    }

    /** True when a string looks like a Crunchyroll series ID but isn't exactly one. */
    fun looksLikeTypo(input: String): Boolean {
        val bare = input.trim()
        return bare.isNotEmpty() &&
            !bare.contains('/') &&
            !SERIES_ID_SHAPE.matches(bare) &&
            Regex("^[A-Z0-9]{5,14}$", RegexOption.IGNORE_CASE).matches(bare)
    }

    private val SERIES_ID_SHAPE = Regex("^G[A-Z0-9]{8}$", RegexOption.IGNORE_CASE)

    suspend fun fetchSeries(seriesId: String): SeriesInfo? = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext null
        val url = "https://www.crunchyroll.com/content/v2/cms/series/$seriesId?locale=en-US"
        val body = get(url, mapOf("Authorization" to "Bearer $token")) ?: return@withContext null

        try {
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext null
            val series = data.optJSONObject(0) ?: return@withContext null
            SeriesInfo(
                seriesId = seriesId,
                title = series.optString("title").takeIf { it.isNotBlank() },
                imageUrl = bestPoster(series.optJSONObject("images"))
            )
        } catch (e: Exception) {
            Log.w(TAG, "parse failed for $seriesId", e)
            null
        }
    }

    /**
     * Poster art. CR nests these as images.poster_tall[0][] with ascending sizes,
     * so the last entry is the largest. Falls back to poster_wide.
     */
    private fun bestPoster(images: JSONObject?): String? {
        if (images == null) return null
        for (key in listOf("poster_tall", "poster_wide")) {
            val outer = images.optJSONArray(key) ?: continue
            val variants = outer.optJSONArray(0) ?: continue
            if (variants.length() == 0) continue
            val largest = variants.optJSONObject(variants.length() - 1) ?: continue
            largest.optString("source").takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun token(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAt) return it }

        val body = post(
            TOKEN_URL,
            "grant_type=client_id&client_id=cr_web&client_secret=",
            mapOf("Content-Type" to "application/x-www-form-urlencoded")
        ) ?: return null

        return try {
            val json = JSONObject(body)
            val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
            val ttl = json.optLong("expires_in", 300L)
            cachedToken = access
            // Expire 30s early so a request never starts against a token about to die.
            tokenExpiresAt = now + (ttl - 30).coerceAtLeast(30) * 1000
            access
        } catch (e: Exception) {
            Log.w(TAG, "token parse failed", e)
            null
        }
    }

    private fun get(url: String, headers: Map<String, String>): String? =
        request(url, "GET", null, headers)

    private fun post(url: String, body: String, headers: Map<String, String>): String? =
        request(url, "POST", body, headers)

    private fun request(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "$method $url -> HTTP $code")
                return null
            }
            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: Exception) {
            Log.w(TAG, "$method $url failed", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
}
