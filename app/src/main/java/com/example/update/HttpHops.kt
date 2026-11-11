package com.example.update

import com.example.R
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Theo redirect THỦ CÔNG với allowlist host khớp chính xác.
 *
 * Vì sao không `followRedirects(true)`: asset của GitHub Releases trả
 * `302 → https://release-assets.githubusercontent.com/...`, nên `assert(host == "github.com")`
 * fail còn OkHttp thì tin bất cứ đâu GitHub trỏ tới. Cả hai đều sai. Ở đây mỗi hop đều được
 * kiểm trước khi gửi request tiếp theo: scheme `https`, host nằm ĐÚNG trong tập cho phép
 * (không `endsWith`, không wildcard), port 443, tối đa 3 hop.
 *
 * `if-none-match` của API: `api.github.com` không redirect trong điều kiện bình thường; cùng
 * một cơ chế hop áp cho nó để không có đường nào đi vòng kiểm soát.
 */
internal object HttpHops {

    /** Khớp CHÍNH XÁC. Thêm host ở đây là một quyết định bảo mật, không phải tiện tay. */
    val ALLOWED_HOSTS: Set<String> = setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    const val MAX_HOPS = 3
    private const val HTTPS_PORT = 443

    fun validate(url: HttpUrl) {
        if (url.scheme != "https") {
            throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
        }
        if (url.host !in ALLOWED_HOSTS) {
            throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
        }
        // HttpUrl luôn trả port hiệu dụng: url không ghi port của https ⇒ 443.
        if (url.port != HTTPS_PORT) {
            throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
        }
    }

    /**
     * Gửi [request] và tự đi từng hop. Response trả về là response CUỐI (không phải 3xx),
     * người gọi phải `close()` nó.
     */
    fun execute(client: OkHttpClient, request: Request): Response {
        var current = request
        var hops = 0
        while (true) {
            validate(current.url)
            val response = client.newCall(current).execute()
            if (!response.isRedirect) return response

            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) {
                throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
            }
            hops++
            if (hops > MAX_HOPS) {
                throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
            }
            // resolve() xử lý cả absolute lẫn relative Location.
            val next = current.url.resolve(location)
                ?: throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
            current = current.newBuilder().url(next).build()
        }
    }

    /**
     * Client cho API: KHÔNG tự redirect, KHÔNG retry ngầm (retry do ta quyết, có backoff),
     * `callTimeout` 8s như §3.4. OkHttp dùng system trust store và tự verify TLS ở mọi hop.
     */
    fun apiClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Client cho tệp 22 MB: không đặt `callTimeout` (tổng thời gian tải phụ thuộc mạng),
     * nhưng chặn read-timeout để một kết nối treo không giữ Mutex vô hạn.
     */
    fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
