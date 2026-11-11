package com.example.update

import com.example.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Client đọc `releases/latest` của repo PUBLIC (docs/DESIGN-AUTOUPDATE.md §3).
 *
 * Ba điểm không được nới:
 * 1. GET không xác thực, `User-Agent` KHÔNG chứa định danh thiết bị (§T12).
 * 2. Mọi hop redirect đi qua [HttpHops] — https + allowlist chính xác + tối đa 3 hop.
 * 3. `/releases/latest` xếp theo thời gian TẠO, không theo semver. Cổng
 *    `remoteVersionCode > BuildConfig.VERSION_CODE` ở repository biến "latest cũ" thành no-op.
 */
class UpdateChecker(
    private val versionNameForUa: String,
    private val client: OkHttpClient = HttpHops.apiClient(),
    private val owner: String = "gvhoang",
    private val repo: String = "volam-idle-manager"
) {

    sealed interface Outcome {
        /** `304` — dùng cache đã lưu. */
        data object NotModified : Outcome

        data class Found(val release: UpdateRelease, val etag: String?) : Outcome

        /** Tag không đọc được và body không khai báo versionCode (§3.3). */
        data class Unparsable(val releasesUrl: String) : Outcome
    }

    private val apiUrl: String
        get() = "https://api.github.com/repos/$owner/$repo/releases/latest"

    private val releasesPageUrl: String
        get() = "https://github.com/$owner/$repo/releases"

    private val userAgent: String
        get() = "VOLAMIdleManager/$versionNameForUa (Android; +https://github.com/$owner/$repo)"

    /**
     * Blocking — gọi trong `withContext(Dispatchers.IO)`.
     *
     * Backoff chỉ cho lỗi mạng và 5xx: 2s → 8s → 30s, tối đa 3 lần. 403/429 KHÔNG retry.
     */
    fun fetchLatest(etag: String?): Outcome {
        var attempt = 0
        while (true) {
            attemptOnce(etag)?.let { return it }
            attempt++
            if (attempt > MAX_RETRIES) break
            try {
                Thread.sleep(BACKOFF_MS[attempt - 1])
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        throw lastFailure
            ?: UpdateFailure(R.string.update_state_nonetwork, kind = UpdateFailure.Kind.NETWORK)
    }

    private var lastFailure: UpdateFailure? = null

    /** Trả null khi được phép retry; ném khi lỗi không retry được. */
    private fun attemptOnce(etag: String?): Outcome? {
        lastFailure = null
        val builder = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .get()
        if (!etag.isNullOrBlank()) {
            builder.header("If-None-Match", etag)
        }

        val response = try {
            HttpHops.execute(client, builder.build())
        } catch (io: IOException) {
            lastFailure = UpdateFailure(
                R.string.update_state_nonetwork,
                kind = UpdateFailure.Kind.NETWORK
            )
            return null
        }

        response.use { res ->
            val remaining = res.header("X-RateLimit-Remaining")?.toIntOrNull()
            when {
                res.code == 304 -> return Outcome.NotModified

                res.code == 403 || res.code == 429 -> {
                    val retryAt = retryAtFrom(res.header("Retry-After"), res.header("X-RateLimit-Reset"))
                    throw UpdateFailure(
                        R.string.update_state_ratelimited,
                        kind = UpdateFailure.Kind.RATE_LIMITED,
                        retryAtMillis = retryAt
                    )
                }

                res.code == 404 -> throw UpdateFailure(
                    R.string.update_error_no_asset,
                    kind = UpdateFailure.Kind.HTTP
                )

                res.code in 500..599 -> {
                    lastFailure = UpdateFailure(
                        R.string.update_state_nonetwork,
                        args = listOf(res.code),
                        kind = UpdateFailure.Kind.SERVER
                    )
                    return null
                }

                res.code != 200 -> throw UpdateFailure(
                    R.string.update_error_generic,
                    args = listOf("HTTP ${res.code}"),
                    kind = UpdateFailure.Kind.HTTP
                )

                remaining != null && remaining <= 0 -> throw UpdateFailure(
                    R.string.update_state_ratelimited,
                    kind = UpdateFailure.Kind.RATE_LIMITED,
                    retryAtMillis = retryAtFrom(null, res.header("X-RateLimit-Reset"))
                )
            }

            val body = res.body?.string()
                ?: throw UpdateFailure(R.string.update_error_generic, args = listOf("EMPTY_BODY"), kind = UpdateFailure.Kind.HTTP)
            return parse(body, res.header("ETag"))
        }
    }

    private fun parse(json: String, etag: String?): Outcome {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            throw UpdateFailure(
                R.string.update_error_generic,
                args = listOf("BAD_JSON"),
                kind = UpdateFailure.Kind.HTTP
            )
        }

        val tag = root.optString("tag_name", "")
        val notes = root.optString("body", "")
        val htmlUrl = root.optString("html_url", releasesPageUrl)
        val versionCode = VersionRules.resolveVersionCode(tag, notes)
            ?: return Outcome.Unparsable(htmlUrl)

        val asset = selectAsset(root)
        val digest = asset.optString("digest", "").takeIf { it.isNotBlank() }
            ?: shaFromBody(notes)

        return Outcome.Found(
            release = UpdateRelease(
                versionCode = versionCode,
                versionName = VersionRules.versionNameFromTag(tag).ifBlank { tag },
                tagName = tag,
                notes = notes,
                htmlUrl = htmlUrl,
                assetName = asset.optString("name", ""),
                assetUrl = asset.optString("browser_download_url", ""),
                assetSize = asset.optLong("size", 0L),
                sha256 = digest?.substringAfter(':', digest)?.lowercase()
            ),
            etag = etag
        )
    }

    /**
     * Tên cố định được ưu tiên; nếu không có, chỉ nhận khi ĐÚNG MỘT asset `.apk`.
     * Từ hai trở lên thì fail — không đoán (§3.1).
     */
    private fun selectAsset(root: JSONObject): JSONObject {
        val assets = root.optJSONArray("assets") ?: throw UpdateFailure(
            R.string.update_error_no_asset,
            kind = UpdateFailure.Kind.NO_ASSET
        )
        var fixed: JSONObject? = null
        val apks = ArrayList<JSONObject>(assets.length())
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            if (name == PREFERRED_ASSET_NAME) fixed = a
            if (name.endsWith(".apk", ignoreCase = true)) apks.add(a)
        }
        fixed?.let { return it }
        if (apks.size == 1) return apks[0]
        throw UpdateFailure(R.string.update_error_no_asset, kind = UpdateFailure.Kind.NO_ASSET)
    }

    private fun shaFromBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val m = SHA_IN_BODY.find(body) ?: return null
        return m.groupValues[1].lowercase()
    }

    private fun retryAtFrom(retryAfter: String?, rateLimitReset: String?): Long {
        val now = System.currentTimeMillis()
        retryAfter?.trim()?.toLongOrNull()?.let { seconds ->
            if (seconds > 0) return now + seconds * 1000L
        }
        rateLimitReset?.trim()?.toLongOrNull()?.let { epochSeconds ->
            if (epochSeconds > 0) return epochSeconds * 1000L
        }
        return now + DEFAULT_RATE_LIMIT_BACKOFF_MS
    }

    companion object {
        const val PREFERRED_ASSET_NAME = "volamidle-manager.apk"

        /** Số lần THỬ LẠI (không tính lần đầu) khi lỗi mạng / 5xx. 403/429 không vào đây. */
        private const val MAX_RETRIES = 3

        /** Giãn cách của lần thử lại thứ 1, 2, 3: 2s → 8s → 30s. */
        private val BACKOFF_MS = longArrayOf(2_000L, 8_000L, 30_000L)
        private const val DEFAULT_RATE_LIMIT_BACKOFF_MS = 15 * 60 * 1000L

        private val SHA_IN_BODY = Regex(
            "(?:sha-?256)\\s*[:=]?\\s*([0-9a-fA-F]{64})",
            setOf(RegexOption.IGNORE_CASE)
        )
    }
}
