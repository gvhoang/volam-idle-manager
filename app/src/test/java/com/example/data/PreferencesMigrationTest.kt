package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AccountConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bảo vệ logic migrate khóa SharedPreferences khi bỏ chiều "Cụm".
 *
 * Bản cũ (có chọn Cụm) ghi 5 nhóm khóa mỗi lần lưu, nhưng CHỈ nhóm `pref_*` và hai nhóm
 * `cluster_<cụm>`/`cluster_<số>` có đủ 4 trường; nhóm `user_tab_N`/`pass_tab_N` chỉ có
 * user/pass — KHÔNG có `auto_tab_N`, KHÔNG có `url_tab_N`.
 * Vì vậy bất kỳ thay đổi nào ở đây đều có thể làm mất tài khoản của người dùng đang nâng cấp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreferencesMigrationTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("volam_idle_manager_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = PreferencesManager(context)
    }

    private fun raw() = context.getSharedPreferences("volam_idle_manager_prefs", Context.MODE_PRIVATE)

    /** Ghi đúng bộ khóa mà bản cũ (có Cụm) tạo ra khi lưu tài khoản. */
    private fun seedOldBuild(
        tab: Int,
        user: String,
        pass: String,
        auto: Boolean,
        url: String,
        cluster: String = "Cụm 1"
    ) {
        val ckey = cluster.replace(" ", "_").lowercase()
        val num = when (cluster) {
            "Cụm 2" -> "2"
            "Cụm 3" -> "3"
            else -> "1"
        }
        raw().edit()
            .putString("user_cluster_${ckey}_tab_$tab", user)
            .putString("pass_cluster_${ckey}_tab_$tab", pass)
            .putBoolean("auto_cluster_${ckey}_tab_$tab", auto)
            .putString("url_cluster_${ckey}_tab_$tab", url)
            .putString("user_cluster_${num}_tab_$tab", user)
            .putString("pass_cluster_${num}_tab_$tab", pass)
            .putBoolean("auto_cluster_${num}_tab_$tab", auto)
            .putString("url_cluster_${num}_tab_$tab", url)
            .putString("user_cluster_${cluster}_tab_$tab", user)
            .putString("pass_cluster_${cluster}_tab_$tab", pass)
            .putString("pref_user_tab_$tab", user)
            .putString("pref_pass_tab_$tab", pass)
            .putBoolean("pref_auto_tab_$tab", auto)
            .putString("pref_url_tab_$tab", url)
            .putString("user_tab_$tab", user)
            .putString("pass_tab_$tab", pass)
            .putString("pref_cluster", cluster)
            .commit()
    }

    @Test
    fun `nang cap giu duoc mat khau, co tu dong dang nhap va URL tuy chinh`() {
        seedOldBuild(0, "u1", "p1", auto = false, url = "https://custom.example")

        val account = prefs.getAccount(0)

        assertEquals("u1", account.username)
        assertEquals("p1", account.password)
        assertFalse("Cờ tự động đăng nhập phải được giữ", account.autoLoginEnabled)
        assertEquals("https://custom.example", account.customUrl)
    }

    @Test
    fun `xoa tai khoan sau khi nang cap khong bi hoi sinh`() {
        seedOldBuild(0, "olduser", "oldpass", auto = false, url = "https://old.example")
        prefs.getAccount(0) // chạy migrate lần đầu

        prefs.saveAccount(AccountConfig(tabIndex = 0, username = "", password = ""))
        val account = prefs.getAccount(0)

        assertEquals("", account.username)
        assertEquals("", account.password)
        assertFalse(raw().contains("pref_user_tab_0"))
        assertFalse(raw().contains("user_cluster_cụm_1_tab_0"))
    }

    @Test
    fun `xoa tai khoan tren Cum 2 khong hoi sinh tai khoan cua Cum 1`() {
        seedOldBuild(0, "u1", "p1", auto = false, url = "https://cu1.example", cluster = "Cụm 1")
        seedOldBuild(0, "", "", auto = true, url = "", cluster = "Cụm 2")

        val account = prefs.getAccount(0)

        assertEquals("", account.username)
        assertEquals("", account.password)
    }

    @Test
    fun `lan luu cuoi thang khi nguoi dung tung doi Cum`() {
        seedOldBuild(0, "cu1user", "cu1pass", auto = false, url = "https://cu1.example", cluster = "Cụm 1")
        seedOldBuild(0, "cu2user", "cu2pass", auto = true, url = "https://cu2.example", cluster = "Cụm 2")

        val account = prefs.getAccount(0)

        assertEquals("cu2user", account.username)
        assertEquals("cu2pass", account.password)
        assertEquals("https://cu2.example", account.customUrl)
    }

    @Test
    fun `ghi va doc lai khong mat du lieu`() {
        val saved = AccountConfig(
            tabIndex = 2,
            username = "newuser",
            password = "newpass",
            autoLoginEnabled = false,
            customUrl = "https://x.example"
        )
        prefs.saveAccount(saved)

        assertEquals(saved, prefs.getAccount(2))
    }

    @Test
    fun `tab chua cau hinh tra ve gia tri mac dinh`() {
        val account = prefs.getAccount(1)

        assertEquals("", account.username)
        assertEquals("", account.password)
        assertTrue(account.autoLoginEnabled)
        assertEquals("", account.customUrl)
    }

    @Test
    fun `doc lai lan hai khong doi gia tri`() {
        seedOldBuild(0, "u1", "p1", auto = false, url = "https://custom.example")

        val first = prefs.getAccount(0)
        val second = prefs.getAccount(0)

        assertEquals(first, second)
    }

    @Test
    fun `don sach khoa dinh dang cu sau khi migrate`() {
        seedOldBuild(0, "u1", "p1", auto = false, url = "https://custom.example")
        prefs.getAccount(0)

        val leftovers = listOf(
            "pref_user_tab_0", "pref_pass_tab_0", "pref_auto_tab_0", "pref_url_tab_0",
            "user_cluster_cụm_1_tab_0", "pass_cluster_cụm_1_tab_0",
            "auto_cluster_cụm_1_tab_0", "url_cluster_cụm_1_tab_0",
            "user_cluster_1_tab_0", "pass_cluster_1_tab_0",
            "auto_cluster_1_tab_0", "url_cluster_1_tab_0",
            "user_cluster_Cụm 1_tab_0", "pass_cluster_Cụm 1_tab_0",
            "pref_cluster"
        ).filter { raw().contains(it) }

        assertEquals("Khóa cũ còn sót: $leftovers", emptyList<String>(), leftovers)
    }

    @Test
    fun `khoa cu dang cum_1 phai la NFC giong ham lowercase cua ban cu`() {
        // Bản cũ sinh khóa bằng "Cụm 1".replace(" ", "_").lowercase() -> phải khớp byte-for-byte,
        // nếu file bị lưu lại dạng NFD thì việc migrate sẽ im lặng thất bại.
        val derived = "Cụm 1".replace(" ", "_").lowercase()
        assertEquals("cụm_1", derived)
        assertEquals(
            "cụm_1".toByteArray(Charsets.UTF_8).toList(),
            derived.toByteArray(Charsets.UTF_8).toList()
        )
    }
}
