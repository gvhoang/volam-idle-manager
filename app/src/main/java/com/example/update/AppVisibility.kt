package com.example.update

/**
 * Cờ "app đang ở foreground" — process-scoped, cập nhật từ MainActivity.
 *
 * Dùng cho hai chỗ mà Android bắt buộc phải biết app có visible hay không:
 * 1. `PackageInstaller` commit: chỉ được bắt đầu từ một Activity đang STARTED
 *    (Android 10+ chặn start Activity từ background — "BAL").
 * 2. `STATUS_PENDING_USER_ACTION`: Intent xác nhận của hệ thống chỉ được start khi visible.
 *
 * Không dùng để làm gì khác. Không ảnh hưởng vòng farm.
 */
object AppVisibility {

    @Volatile
    var isForeground: Boolean = false
        private set

    fun onActivityStarted() {
        isForeground = true
    }

    fun onActivityStopped() {
        isForeground = false
    }
}
