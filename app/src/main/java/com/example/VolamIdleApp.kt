package com.example

import android.app.Application
import com.example.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Application process-scoped.
 *
 * Vì sao không WorkManager cho vòng kiểm tra 12h: thêm dependency + IPC surface chỉ để
 * chạy một request GET mà kết quả (nếu có) vẫn phải chờ người dùng bấm tải và bấm cài.
 * Một coroutine trong process là đủ; process này sống 24/7 vì app farm liên tục.
 *
 * Thứ tự khởi động có chủ ý: `GameTabManager.init` dựng 4 WebView ngay khi ViewModel được
 * tạo, nên lần kiểm tra đầu TIÊN phải trễ 3 giây để Chromium được CPU trước — không giành
 * băng thông/luồng với lần load trang đầu.
 */
class VolamIdleApp : Application() {

    val processScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        updateRepository = UpdateRepository(this, processScope)
        // Robolectric dựng Application thật trong unit test: khởi vòng 12h ở đó là gọi mạng
        // thật từ một test JVM. Bỏ qua — vòng này chỉ có nghĩa trên máy thật.
        if (!isUnitTestRuntime()) {
            startUpdateLoop()
        }
    }

    private fun isUnitTestRuntime(): Boolean =
        android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

    private fun startUpdateLoop() {
        processScope.launch {
            // Cold start: sau frame đầu + 3s.
            delay(FIRST_CHECK_DELAY_MS)
            runCatching { updateRepository.checkForUpdate(manual = false) }

            // Sau đó mỗi 12h ± 30 phút. Jitter để nhiều máy không dồn cùng một giây vào
            // hạn mức 60 request/giờ/IP của GitHub API.
            while (true) {
                val jitter = Random.nextLong(-AUTO_CHECK_JITTER_MS, AUTO_CHECK_JITTER_MS + 1)
                delay(UpdateRepository.AUTO_CHECK_INTERVAL_MS + jitter)
                runCatching { updateRepository.checkForUpdate(manual = false) }
            }
        }
    }

    companion object {
        private const val FIRST_CHECK_DELAY_MS = 3_000L
        private const val AUTO_CHECK_JITTER_MS = UpdateRepository.AUTO_CHECK_JITTER_MS
    }
}
