package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testViewLayoutModes() {
    val modes = com.example.model.ViewLayoutMode.values()
    assertEquals(3, modes.size)
    assertTrue(modes.contains(com.example.model.ViewLayoutMode.FOUR_COLUMNS))
    assertTrue(modes.contains(com.example.model.ViewLayoutMode.TWO_BY_TWO))
    assertTrue(modes.contains(com.example.model.ViewLayoutMode.SINGLE_TAB))
  }

  @Test
  fun testConflictResolutionScripts() {
    val loginScript = com.example.webview.GameScriptInjector.buildAutoLoginStepScript(0, "testuser", "testpass")
    assertTrue("Login script must contain 'Chơi ở đây'", loginScript.contains("Chơi ở đây"))
    assertTrue("Login script must contain 'Chơi ở tab này'", loginScript.contains("Chơi ở tab này"))

    val watchdogScript = com.example.webview.GameScriptInjector.buildPeriodicWatchdogAndScanScript(0)
    assertTrue("Watchdog script must contain 'Chơi ở đây'", watchdogScript.contains("Chơi ở đây"))
    assertTrue("Watchdog script must contain 'Chơi ở tab này'", watchdogScript.contains("Chơi ở tab này"))
    // bodyText nay la "let" vi duoc loc bo van ban khung chat truoc khi kiem tra auth
    assertTrue("Watchdog script must declare bodyText", watchdogScript.contains("let bodyText ="))
    assertTrue("Watchdog script must declare allElements", watchdogScript.contains("const allElements ="))
  }

  @Test
  fun testSyncAutoModeScript() {
    val questScript = com.example.webview.GameScriptInjector.buildSyncAutoModeScript("Nhiệm Vụ")
    val dungeonScript = com.example.webview.GameScriptInjector.buildSyncAutoModeScript("Phó Bản")
    val grindScript = com.example.webview.GameScriptInjector.buildSyncAutoModeScript("Luyện Công")

    assertTrue(questScript.contains("Nhiệm Vụ"))
    assertTrue(dungeonScript.contains("Phó Bản"))
    assertTrue(grindScript.contains("Luyện Công"))
    assertTrue(questScript.contains("HTMLSelectElement"))
    assertTrue(questScript.contains("isBottomMenuBarElement"))
  }

  @Test
  fun testSyncBlackScreenScript() {
    val enableScript = com.example.webview.GameScriptInjector.buildSyncBlackScreenScript(true)
    val disableScript = com.example.webview.GameScriptInjector.buildSyncBlackScreenScript(false)

    assertTrue(enableScript.contains("findHeThongButton"))
    assertTrue(enableScript.contains("findHienThiTab"))
    assertTrue(enableScript.contains("findBlackScreenCheckbox"))
    assertTrue(enableScript.contains("man hinh den"))
    assertTrue(enableScript.endsWith("(true);"))
    assertTrue(disableScript.endsWith("(false);"))
  }

  @Test
  fun testBackgroundKeepAliveAndAntiJitterScript() {
    val fpsScript = com.example.webview.GameScriptInjector.buildFpsLimiterScript(30)
    assertTrue(fpsScript.contains("visibilityState"))
    assertTrue(fpsScript.contains("__isAppInBackground"))
    assertTrue(fpsScript.contains("safeDelta"))
    assertTrue(fpsScript.contains("initSilentKeepAliveAudio"))
  }

  @Test
  fun testNewLoginUiScript() {
    val loginScript = com.example.webview.GameScriptInjector.buildAutoLoginStepScript(0, "binhdvk29", "pass123")
    assertTrue("Must check visibility of inputs/modals", loginScript.contains("function isVisible(el)"))
    assertFalse("Must NOT use /\\b(hidden/ regex that breaks Tailwind overflow-hidden", loginScript.contains("(hidden|d-none"))
    assertTrue("Must click 'bat dau tro choi' first", loginScript.contains("bat dau tro choi"))
    assertTrue("Must support 'mat ma' password label", loginScript.contains("mat ma"))
    assertTrue("Must ignore 'hien mat' show-password checkbox", loginScript.contains("hien mat"))
    assertTrue("Must check 'nho tai khoan' checkbox", loginScript.contains("nho tai khoan"))
    assertTrue("Must click 'xac dinh' submit button", loginScript.contains("xac dinh"))
    assertTrue("Must score submit button below passInput", loginScript.contains("findLoginSubmitButton"))
  }
}
