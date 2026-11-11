package com.example

import com.example.update.HttpHops
import com.example.update.UpdateFailure
import com.example.update.VersionRules
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm soát thật của tính năng tự cập nhật (docs/DESIGN-AUTOUPDATE.md §9 "Test nên viết"):
 * allowlist redirect và luật so phiên bản. Chạy JVM thuần — không Android API, không mạng.
 */
class UpdateRulesTest {

    // ---------- Redirect / allowlist ----------

    @Test
    fun allowlistAcceptsEveryGithubHop() {
        val hosts = listOf(
            "https://api.github.com/repos/x/y/releases/latest",
            "https://github.com/x/y/releases/download/v4.0.0/app.apk",
            "https://release-assets.githubusercontent.com/github-production-release-asset/1?x=1",
            "https://objects.githubusercontent.com/whatever"
        )
        for (url in hosts) {
            // Không ném = hợp lệ.
            HttpHops.validate(url.toHttpUrl())
        }
    }

    @Test
    fun allowlistRejectsLookalikeAndForeignHosts() {
        val bad = listOf(
            "https://github.com.evil.example/x.apk",
            "https://evilgithub.com/x.apk",
            "https://release-assets.githubusercontent.com.attacker.test/x",
            "https://example.com/x.apk",
            "https://api.github.com@attacker.test/x.apk"
        )
        for (url in bad) {
            val failure = runCatching { HttpHops.validate(url.toHttpUrl()) }.exceptionOrNull()
            assertTrue("Phải từ chối $url", failure is UpdateFailure)
            assertEquals(UpdateFailure.Kind.BAD_HOST, (failure as UpdateFailure).kind)
        }
    }

    @Test
    fun allowlistRejectsCleartextAndNonDefaultPort() {
        assertTrue(
            runCatching { HttpHops.validate("http://github.com/x.apk".toHttpUrl()) }
                .exceptionOrNull() is UpdateFailure
        )
        assertTrue(
            runCatching { HttpHops.validate("https://github.com:8443/x.apk".toHttpUrl()) }
                .exceptionOrNull() is UpdateFailure
        )
        // Port 443 ghi tường minh vẫn hợp lệ.
        HttpHops.validate("https://github.com:443/x.apk".toHttpUrl())
    }

    @Test
    fun maxHopsIsThree() {
        assertEquals(3, HttpHops.MAX_HOPS)
    }

    // ---------- Luật phiên bản (§3.3) ----------

    @Test
    fun versionCodeFromTagUsesMajorMinorPatch() {
        // major*10000 + minor*100 + patch
        assertEquals(40000, VersionRules.versionCodeFromTag("v4.0.0"))
        assertEquals(40100, VersionRules.versionCodeFromTag("4.1.0"))
        assertEquals(40010, VersionRules.versionCodeFromTag("v4.0.10"))
        assertEquals(50000, VersionRules.versionCodeFromTag("v5.0.0-beta.1"))
        assertNull(VersionRules.versionCodeFromTag("release-2026-01"))
    }

    @Test
    fun tupleOrderingBeatsStringOrdering() {
        // So chuỗi sẽ sai ở đây: "4.9.0" > "4.10.0" theo alphabet.
        val newer = VersionRules.versionCodeFromTag("V4.10.0")!!
        val older = VersionRules.versionCodeFromTag("v4.9.0")!!
        assertTrue(VersionRules.isNewer(newer, older))
        assertFalse(VersionRules.isNewer(older, newer))
        assertTrue(VersionRules.versionCodeFromTag("v10.0.0")!! > VersionRules.versionCodeFromTag("v9.99.99")!!)
    }

    @Test
    fun explicitBodyHintWinsOverTagTriple() {
        // Giai đoạn chuyển đổi: tag v4.0.0 (triple 40000) nhưng versionCode thật là 4.
        val body = "## Bản 4.0.0\nversionCode: 4\n- sửa lỗi"
        assertEquals(4, VersionRules.versionCodeFromBody(body))
        assertEquals(4, VersionRules.resolveVersionCode("v4.0.0", body))
    }

    @Test
    fun fallsBackToTagWhenBodyHasNoHint() {
        assertEquals(40100, VersionRules.resolveVersionCode("v4.1.0", "không có số ở đây"))
        assertNull(VersionRules.resolveVersionCode("khong-doc-duoc", "cũng không có"))
    }
}
