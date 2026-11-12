package com.example.webview

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Cấu hình "Tự động" — phản chiếu đúng đối tượng DEF của preload-game.js.
 *
 * Mặc định khoá đúng như bản PC: chỉ những việc miễn phí là bật (Trợ thủ, Dã Tẩu,
 * Sát Thủ, Phó Bản, quà, khấn tượng, dùng Tiên Thảo trong túi). Mọi việc TIÊU KNB
 * (k2k, lbMua, knbMua, ttlMua, tt2Mua) mặc định 0 / tắt, và "on" mặc định TẮT.
 */
data class AutoConfig(
    val on: Boolean = false,
    val dt: Boolean = true,
    val ldtMua: Boolean = true,
    val k2k: Int = 0,
    val pd: Boolean = true,
    val st: Boolean = true,
    val lbMua: Int = 0,
    val knbMua: Int = 0,
    val knbGiu: Int = 0,
    val qua: Boolean = true,
    val evHb: Boolean = false,
    val evDhb: Boolean = false,
    val khan: Boolean = true,
    val ttl: Boolean = true,
    val ttlMua: Boolean = false,
    val tt2: Boolean = true,
    val tt2Mua: Boolean = false,
    val pb: Boolean = true
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("on", on)
        o.put("dt", dt)
        o.put("ldtMua", ldtMua)
        o.put("k2k", k2k)
        o.put("pd", pd)
        o.put("st", st)
        o.put("lbMua", lbMua)
        o.put("knbMua", knbMua)
        o.put("knbGiu", knbGiu)
        o.put("qua", qua)
        o.put("evHb", evHb)
        o.put("evDhb", evDhb)
        o.put("khan", khan)
        o.put("ttl", ttl)
        o.put("ttlMua", ttlMua)
        o.put("tt2", tt2)
        o.put("tt2Mua", tt2Mua)
        o.put("pb", pb)
        return o
    }

    companion object {
        // Trần nhập liệu, lấy đúng min/max của bảng PC trong shell.html.
        const val K2K_MAX = 100_000
        const val LB_MUA_MAX = 100
        const val KNB_MUA_MAX = 1000
        const val KNB_GIU_MAX = 10_000_000

        private val DEF = AutoConfig()

        /**
         * Đọc cấu hình từ chuỗi JSON đã lưu. Hỏng / rỗng / thiếu khoá đều rơi về mặc định,
         * và mặc định luôn là on = false — không bao giờ tự bật.
         */
        fun fromJson(raw: String?): AutoConfig {
            if (raw.isNullOrBlank()) return DEF
            val o = try {
                JSONObject(raw)
            } catch (_: Exception) {
                return DEF
            }
            return AutoConfig(
                on = o.optBoolean("on", DEF.on),
                dt = o.optBoolean("dt", DEF.dt),
                ldtMua = o.optBoolean("ldtMua", DEF.ldtMua),
                k2k = clamp(o.optInt("k2k", DEF.k2k), K2K_MAX),
                pd = o.optBoolean("pd", DEF.pd),
                st = o.optBoolean("st", DEF.st),
                lbMua = clamp(o.optInt("lbMua", DEF.lbMua), LB_MUA_MAX),
                knbMua = clamp(o.optInt("knbMua", DEF.knbMua), KNB_MUA_MAX),
                knbGiu = clamp(o.optInt("knbGiu", DEF.knbGiu), KNB_GIU_MAX),
                qua = o.optBoolean("qua", DEF.qua),
                evHb = o.optBoolean("evHb", DEF.evHb),
                evDhb = o.optBoolean("evDhb", DEF.evDhb),
                khan = o.optBoolean("khan", DEF.khan),
                ttl = o.optBoolean("ttl", DEF.ttl),
                ttlMua = o.optBoolean("ttlMua", DEF.ttlMua),
                tt2 = o.optBoolean("tt2", DEF.tt2),
                tt2Mua = o.optBoolean("tt2Mua", DEF.tt2Mua),
                pb = o.optBoolean("pb", DEF.pb)
            )
        }

        private fun clamp(v: Int, max: Int): Int = if (v < 0) 0 else if (v > max) max else v
    }
}

/**
 * GameAutoPilot: cổng bộ "Tự động" của bản PC (preload-game.js) sang Android WebView.
 *
 * Nguyên tắc cốt lõi giống hệt bản PC: KHÔNG mô phỏng chuột / bàn phím. Game tự mở
 * một lệnh ẩn `<button id="vlmAuto">`; ta chỉ đặt dataset.act / dataset.arg, bấm nút,
 * rồi chờ game ghi dataset.xong / dataset.kq. Vì dataset nằm trong ngữ cảnh trang,
 * toàn bộ vòng chờ (polling) phải do JS trong trang làm — Kotlin chỉ bơm mã và cấu hình.
 *
 * Kotlin không gọi api() và không tự quyết định mua bán: nó chỉ giữ script sống
 * (bơm lại sau reload / tiến trình WebView bị thu hồi) và đẩy cấu hình mới xuống tab.
 */
class GameAutoPilot(
    private val scope: CoroutineScope,
    /**
     * Số tab đang chạy, đọc LẠI mỗi nhịp. Không nhận Int cố định: người dùng đổi số tab
     * giữa phiên, và vòng giữ script phải tự thu theo — không được giữ index đã chết.
     */
    private val tabCount: () -> Int,
    initialConfigJson: String?,
    private val webViewAt: (Int) -> KeepAliveWebView?,
    private val isTabActive: (Int) -> Boolean,
    private val isSystemRunning: () -> Boolean
) {
    companion object {
        // Nhịp Kotlin chỉ để giữ script sống và đẩy cấu hình; nhịp làm việc thật nằm
        // trong JS (tick 20s + hạn chờ riêng từng việc, đúng như bản PC).
        private const val TICK_MS = 30_000L

        // Cứ FULL_EVERY nhịp thì bơm lại toàn bộ (không chỉ đẩy cấu hình) để tự phục hồi
        // khi trang reload cùng URL, hoặc tiến trình renderer bị thu hồi RAM.
        private const val FULL_EVERY = 4L

        const val SCRIPT_VER = "vlmauto-1"

        // Trần ngày của game / của bản PC — dùng cho phần UI và cho tài liệu hoá.
        const val KNB_NGAY_MAX = 1000
        const val EV_DHB_MAX = 1
        const val EV_HB_MAX = 3
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cfg: AutoConfig = AutoConfig.fromJson(initialConfigJson)

    // URL lúc cài script của từng tab. Lệch URL => trang đã đổi => phải cài lại.
    // Cấp phát một lần ở trần slot cứng: index tab không bao giờ vượt quá nó, còn số tab
    // đang chạy đọc qua `tabCount()`.
    private val installedUrl = arrayOfNulls<String>(com.example.model.TabLimits.SLOT_COUNT)

    private var ticks = 0L
    private var job: Job? = null

    fun isEnabled(): Boolean = cfg.on

    fun currentConfig(): AutoConfig = cfg

    /** Bắt đầu vòng giữ script. Chạy vô hại khi cấu hình đang tắt. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                try {
                    tick()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Một tab lỗi không được làm chết vòng giữ script của các tab khác.
                }
                delay(TICK_MS)
            }
        }
    }

    fun shutdown() {
        job?.cancel()
        job = null
    }

    /**
     * Người dùng vừa lưu cấu hình trong hộp thoại: ghi nhận rồi bơm ngay xuống các tab
     * đang mở, không đợi nhịp kế tiếp. Bật/tắt tức thời là điều kiện an toàn — người dùng
     * bấm tắt phải có hiệu lực trước lần mua sau.
     */
    fun updateConfig(json: String?) {
        cfg = AutoConfig.fromJson(json)
        ticks = 0
        // Xoá mọi dấu URL (kể cả slot chưa dùng): cấu hình đổi thì mọi tab phải cài lại script.
        for (i in installedUrl.indices) {
            installedUrl[i] = null
        }
        if (job == null || job?.isActive != true) start()
        mainHandler.post { runCatching { tickNow() } }
    }

    /** Xoá bộ đếm ngày trên mọi tab (mở khoá khi bộ đếm lệch ngày của game). */
    fun resetDailyCounters() {
        mainHandler.post {
            for (i in 0 until tabCount().coerceAtMost(installedUrl.size)) {
                val wv = webViewAt(i) ?: continue
                if (!isTabActive(i)) continue
                if (wv.url == null || wv.url == "about:blank") continue
                runCatching { wv.evaluateJavascript(buildResetScript(), null) }
            }
        }
    }

    /** Gọi khi một tab được mở lại: buộc cài lại script ở nhịp kế tiếp. */
    fun onTabRestarted(tabIndex: Int) {
        if (tabIndex in installedUrl.indices) installedUrl[tabIndex] = null
    }

    /**
     * Gọi khi giảm số tab: slot [tabIndex] vừa bị destroy. Xoá dấu URL để nếu người dùng
     * tăng số tab trở lại, vòng giữ script cài lại từ đầu thay vì tưởng trang vẫn còn.
     */
    fun onTabRemoved(tabIndex: Int) {
        if (tabIndex in installedUrl.indices) installedUrl[tabIndex] = null
    }

    private fun tickNow() {
        tick()
    }

    private fun tick() {
        if (!cfg.on) return
        if (!isSystemRunning()) return

        ticks++
        val forceFull = ticks % FULL_EVERY == 0L
        val cfgJson = cfg.toJson().toString()

        // evaluateJavascript chỉ hợp lệ trên đúng luồng đã tạo WebView. Dù scope truyền vào
        // đã là Main, vẫn đẩy qua Handler để không phụ thuộc vào dispatcher của người gọi.
        mainHandler.post { injectAll(forceFull, cfgJson) }
    }

    private fun injectAll(forceFull: Boolean, cfgJson: String) {
        if (!cfg.on) return
        for (i in 0 until tabCount().coerceAtMost(installedUrl.size)) {
            if (!isTabActive(i)) {
                installedUrl[i] = null
                continue
            }
            val wv = webViewAt(i) ?: continue
            val url = wv.url ?: continue
            if (url == "about:blank") continue

            // Trang đã đổi (đăng nhập lại, đổi cụm, tải lại): bắt buộc cài lại toàn bộ.
            val needFull = forceFull || installedUrl[i] != url
            val script = if (needFull) {
                installedUrl[i] = url
                buildAutoScript(i, cfgJson, wv.lastTouchTimeMs)
            } else {
                buildPushScript(cfgJson, wv.lastTouchTimeMs)
            }
            runCatching { wv.evaluateJavascript(script, null) }
        }
    }

    // =========================================================================
    // Script đẩy cấu hình (nhỏ): chỉ chạy khi script đã cài đúng phiên bản.
    // Nếu script không còn (renderer bị thu hồi) thì im lặng, nhịp FULL_EVERY sẽ cài lại.
    // =========================================================================
    private fun buildPushScript(cfgJson: String, touchMs: Long): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.ver==='" + SCRIPT_VER +
            "'){a.setCfg(" + cfgJson + "," + touchMs + ");}}catch(e){}})();"
    }

    private fun buildResetScript(): String {
        return "(function(){try{var a=window.__vlmAuto;if(a&&a.resetDay)a.resetDay();}catch(e){}})();"
    }

    // =========================================================================
    // Script tự đứng một mình: cài api(act,arg) + toàn bộ hành động của bản PC.
    // Cài hai lần là hai vòng lặp và hai lần tiêu KNB, nên đầu script có cổng chặn
    // theo window.__vlmAuto.ver — đúng cách bản PC tự bảo vệ khi bơm UserScript.
    // =========================================================================
    private fun buildAutoScript(tabIndex: Int, cfgJson: String, touchMs: Long): String {
        return BOOTSTRAP_JS
            .replace("__TAB__", tabIndex.toString())
            .replace("__CFG__", cfgJson)
            .replace("__TOUCH__", touchMs.toString())
            .replace("__VER__", SCRIPT_VER)
    }

    private val BOOTSTRAP_JS: String = """
(function () {
  'use strict';
  var VER = '__VER__';
  var TAB = __TAB__;
  var CFG0 = __CFG__;
  var TOUCH0 = __TOUCH__;

  // Đã cài đúng bản: chỉ cập nhật cấu hình. Không cài lại (hai vòng lặp = tiêu gấp đôi).
  try {
    if (window.__vlmAuto && window.__vlmAuto.ver === VER) {
      window.__vlmAuto.setCfg(CFG0, TOUCH0);
      return 'ok';
    }
  } catch (e0) {}

  var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };

  // Mốc ngày theo giờ Việt Nam (UTC+7) — cùng công thức với bản PC.
  var vnDay = function () {
    var v = new Date(Date.now() + 7 * 3600e3);
    return v.getUTCFullYear() + '-' + (v.getUTCMonth() + 1) + '-' + v.getUTCDate();
  };

  var DEF = { on: false, dt: true, ldtMua: true, k2k: 0, pd: true, st: true, lbMua: 0,
    knbMua: 0, knbGiu: 0, qua: true, evHb: false, evDhb: false, khan: true, ttl: true,
    ttlMua: false, tt2: true, tt2Mua: false, pb: true };

  var A = {
    cfg: Object.assign({}, DEF, CFG0 || {}),
    busy: false,
    userAt: 0,
    touchK: (typeof TOUCH0 === 'number' ? TOUCH0 : 0),
    cool: {},
    st: null,
    stT: 0,
    started: 0,
    last: '',
    lastAt: 0,
    pbOut: 0,
    pbSeen: 0,
    info: null,
    gate: 0
  };

  // ===== Nhật ký: đẩy về Kotlin để hiện ở dòng trạng thái của thẻ tab =====
  function note(m) {
    A.last = String(m || '');
    A.lastAt = Date.now();
    try {
      if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
        window.AndroidBridge.onGameLog(TAB, '🤖 ' + A.last);
      }
    } catch (e) {}
  }

  // ===== Cổng "người chơi đang rảnh" =====
  // Sự kiện tin cậy trong trang + mốc chạm cuối do Kotlin gửi kèm (KeepAliveWebView.lastTouchTimeMs).
  var evList = ['mousedown', 'keydown', 'wheel', 'touchstart'];
  for (var ei = 0; ei < evList.length; ei++) {
    (function (ev) {
      document.addEventListener(ev, function (e) {
        if (e && e.isTrusted) A.userAt = Date.now();
      }, true);
    })(evList[ei]);
  }

  // Cổng trạng thái của game: game tự postMessage {vlm:'st'} trong chính khung của nó.
  window.addEventListener('message', function (e) {
    try {
      var d = e.data;
      if (!d || typeof d !== 'object' || e.source !== window) return;
      if (d.vlm === 'st' && d.st && typeof d.st === 'object') {
        // Chặn payload phình (cùng lý do với bản PC).
        try { if (JSON.stringify(d.st).length > 64 * 1024) return; } catch (e2) { return; }
        A.st = d.st;
        A.stT = Date.now();
      }
    } catch (e3) {}
  });

  // ===== Bộ đếm ngày: localStorage, dạng {d:'YYYY-M-D', n:N}, trần y như bản PC =====
  var dayGet = function (k) {
    try {
      var o = JSON.parse(localStorage.getItem('vlm_auto_' + k) || 'null');
      return o && o.d === vnDay() && isFinite(o.n) && o.n >= 0 && o.n < 1e9 ? Math.floor(o.n) : 0;
    } catch (e) { return 0; }
  };

  var dayAdd = function (k, n) {
    try {
      if (!isFinite(n) || n < 0) return false;
      var max = k === 'knb' ? 1000 : k === 'evDhb' ? 1 : k === 'evHb' ? 3 : 999999999;
      localStorage.setItem('vlm_auto_' + k,
        JSON.stringify({ d: vnDay(), n: Math.min(max, dayGet(k) + Math.floor(Math.min(n, max))) }));
      return true;
    } catch (e) { return false; }
  };

  // Cấu hình mới từ Kotlin: ghi nhận rồi áp ngay (mọi vòng đang chờ đều thấy A.cfg.on = false).
  function setCfg(j, t) {
    try {
      var c = (typeof j === 'string') ? JSON.parse(j) : (j || {});
      A.cfg = Object.assign({}, DEF, c || {});
    } catch (e) {}
    if (typeof t === 'number' && t > A.touchK) A.touchK = t;
    if (!A.cfg.on) A.started = 0;
  }

  function resetDay() {
    try {
      var ks = [];
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && k.indexOf('vlm_auto_') === 0 && k !== 'vlm_auto_prev') ks.push(k);
      }
      for (var j = 0; j < ks.length; j++) localStorage.removeItem(ks[j]);
      A.pbOut = 0; A.pbSeen = 0; A.gate = 0;
      note('đã đặt lại bộ đếm ngày');
    } catch (e) {}
  }

  // Hạn chờ theo từng việc (giây->ms), đúng như bản PC. ready() chỉ cấp một lượt trong cửa sổ.
  var ready = function (k, ms) {
    if (A.cool[k] > Date.now()) return false;
    A.cool[k] = Date.now() + ms;
    return true;
  };

  // Người chơi không đụng vào tab này 60 giây: chỉ khi đó mới đổi chế độ / đi khấn / mở bảng.
  var ranh = function () {
    var at = Math.max(A.userAt, A.touchK || 0);
    return Date.now() - at > 60000;
  };

  // Nội dung chưa đủ: người chơi có thể đóng bảng của auto rồi mở lại chính bảng đó.
  var modalCuaTa = function (md, moAt, dungBang) {
    return !!md && A.userAt === moAt && !md.classList.contains('hidden') && dungBang();
  };

  var prevGet = function () { try { return localStorage.getItem('vlm_auto_prev'); } catch (e) { return null; } };
  var prevSet = function (m) {
    try { if (m) localStorage.setItem('vlm_auto_prev', m); else localStorage.removeItem('vlm_auto_prev'); } catch (e) {}
  };

  var MODE_VI = { farm: 'Luyện Công', dg: 'Phó Bản', tower: 'Leo Tháp', quest: 'Nhiệm Vụ' };
  var pbXong = function () { return dayGet('pbx') > 0; };

  // ===== Cầu lệnh ẩn #vlmAuto của game =====
  async function api(act, arg, ms) {
    arg = arg || {};
    ms = ms || 45000;
    var b = document.createElement('button');
    b.id = 'vlmAuto';
    b.type = 'button';
    b.style.display = 'none';
    b.dataset.act = act;
    b.dataset.arg = JSON.stringify(arg);
    (document.body || document.documentElement).appendChild(b);
    try {
      b.click();
      var end = Date.now() + ms;
      while (!b.dataset.xong && Date.now() < end) await sleep(120);
      if (!b.dataset.xong) return { ok: false, why: 'hết giờ' };
      try { return JSON.parse(b.dataset.kq || '{}'); } catch (e) { return { ok: false, why: 'kết quả lạ' }; }
    } finally {
      try { b.remove(); } catch (e) {}
    }
  }

  async function info() {
    var i = await api('info', {}, 8000);
    if (i && i.ok) { A.info = i; return i; }
    return null;
  }

  // ===== Bật Trợ thủ (Dã Tẩu tự trả/nhận, tự dùng Lệnh Bài, tự gọi Sát Thủ) =====
  async function troThu(I) {
    var dt = I.dt || {};
    var can = (A.cfg.dt && (!dt.auto || !I.autoLdt)) || (A.cfg.st && !I.autoSt);
    if (!can || !ready('tt', 30 * 60000)) return;
    var r = await api('troThu', { dt: !!A.cfg.dt, ldt: !!A.cfg.dt, st: !!A.cfg.st });
    if (r && r.ok && r.bat && r.bat.length) {
      var ten = { dt: 'Dã Tẩu tự trả / nhận', ldt: 'tự dùng Lệnh Bài Dã Tẩu', st: 'tự gọi Sát Thủ' };
      var ds = [];
      for (var i = 0; i < r.bat.length; i++) if (ten[r.bat[i]]) ds.push(ten[r.bat[i]]);
      note('bật Trợ thủ: ' + ds.join(', '));
    }
  }

  // ===== Khấn tượng (+20% kinh nghiệm 8 giờ) =====
  async function khan(I) {
    if (!A.cfg.khan || I.khan || I.dg || I.tower || I.sat || I.dead || !ranh() || !ready('khan', 10 * 60000)) return;
    var r = await api('khan', {}, 60000);
    if (r && r.ok && r.xong) {
      var I2 = await info();
      if (I2 && I2.khan) note('khấn tượng: +20% kinh nghiệm 8 giờ');
    }
  }

  // ===== Tiên Thảo Lộ (thường / đặc biệt) =====
  async function tienThao(I) {
    var tui = I.tui || {};
    var ds = [
      ['tt', 'tt', A.cfg.ttl, A.cfg.ttlMua, 'Tiên Thảo Lộ'],
      ['ttsp', 'tt2', A.cfg.tt2, A.cfg.tt2Mua, 'Tiên Thảo Lộ Đặc Biệt']
    ];
    for (var i = 0; i < ds.length; i++) {
      var k = ds[i][0], bk = ds[i][1], dung = ds[i][2], mua = ds[i][3], ten = ds[i][4];
      if (!dung || (I[bk] | 0) > 60000 || !ready('ttl_' + k, 3 * 60000)) continue;
      if ((tui[k] | 0) > 0) {
        var r = await api('dungTui', { k: k });
        if (r && r.ok && r.n) { note('dùng ' + ten + ' (trong túi)'); continue; }
      }
      if (mua) {
        var r2 = await api('muaKtc', { k: k, n: 1 });
        if (r2 && r2.ok && r2.n) note('mua và dùng ' + ten + ' (KNB khóa)');
      }
    }
  }

  // ===== Quà điểm danh + mốc cấp =====
  async function qua(I) {
    var q = I.qua || {};
    if (!A.cfg.qua || !(q.login || q.lvms) || !ready('qua', 60000)) return;
    var r = await api('qua');
    if (r && r.ok && (r.login || r.lv)) {
      var ds = [];
      if (r.login) ds.push('điểm danh');
      if (r.lv) ds.push(r.lv + ' mốc cấp');
      note('nhận quà: ' + ds.join(', '));
    }
  }

  // Số Lệnh Bài Dã Tẩu còn thiếu để đủ hôm nay
  function need(I) {
    var dt = I.dt || {};
    if (dt.du) return 0;
    return Math.max(0, (dt.con | 0) - (I.ldt | 0));
  }

  // ===== Đổi KNB thường -> KNB khóa =====
  async function k2k(knbk) {
    var con = Math.max(0, (A.cfg.k2k | 0) - dayGet('k2k'));
    if (!con || knbk <= 0) return 0;
    var n = Math.min(con, Math.max(10, knbk));
    var r = await api('k2k', { n: n });
    if (r && r.ok && r.n > 0) {
      dayAdd('k2k', r.n);
      note('đổi ' + r.n + ' KNB → KNB khóa');
      return r.n;
    }
    return 0;
  }

  // ===== Dã Tẩu tối đa: mua Lệnh Bài + bình/rương Phúc Duyên =====
  async function daTau(I) {
    if (!A.cfg.dt || (I.lvl | 0) < 20 || need(I) <= 0 || !ready('ldt', 5 * 60000)) return;

    if (A.cfg.ldtMua) {
      var got = 0, doi = false;
      for (var i = 0; i < 3; i++) {
        var n = Math.min(need(I), Math.max(0, (I.ldtMax | 0) - (I.ldtMua | 0)));
        if (n <= 0) break;
        var r = await api('muaKtc', { k: 'ldt', n: n });
        var I2 = await info();
        if (I2) I = I2;
        if (r && r.ok && r.n > 0) got += r.n;
        if (r && r.ok && r.n >= n) break;
        if (doi || !(A.cfg.k2k > 0)) break;
        doi = true;
        var gia = (I.gia && I.gia.ldt) || 20;
        var can = (n - (r.n | 0)) * gia - (I.knbk | 0);
        if (!await k2k(can)) break;
        var I3 = await info();
        if (I3) I = I3;
      }
      if (got) note('mua ' + got + ' Lệnh Bài Dã Tẩu (KNB khóa)');
    }

    if (A.cfg.pd && need(I) > 0) {
      var p = I.pdl || {};
      if (p && ((p.cho | 0) >= 10 || (p.l && p.l.some(function (n) { return n > 0; })))) {
        var r4 = await api('pdl');
        if (r4 && r4.ok && (r4.dong || r4.fd)) {
          note('bình Phúc Duyên Lộ: ' + (r4.dong ? 'đóng hết, ' : '') + 'dùng hết (+' + (r4.fd | 0) + ' Phúc Duyên)');
        }
        var I4 = await info();
        if (I4) I = I4;
      }
      var mo = 0;
      for (var j = 0; j < 10 && need(I) > 0 && (I.fd | 0) >= 10; j++) {
        var r5 = await api('ruong', { n: Math.min(10, Math.floor((I.fd | 0) / 10)) });
        if (!r5 || !r5.ok || !r5.n) break;
        mo += r5.n;
        var I5 = await info();
        if (!I5) break;
        I = I5;
      }
      if (mo) note('mở ' + mo + ' Rương Phúc Duyên (lấy Lệnh Bài Dã Tẩu)');
    }
  }

  // ===== Mua Lệnh Bài Sát Thủ =====
  async function muaLb(I) {
    var cap = A.cfg.lbMua | 0;
    if (!A.cfg.st || cap <= 0 || (I.lb | 0) > 0 || (I.lvl | 0) < 20 || !ready('lb', 5 * 60000)) return;
    var n = Math.min(10, cap - dayGet('lb'), Math.max(0, (I.lbMax | 0) - (I.lbMua | 0)));
    if (n <= 0) return;
    var r = await api('muaLb', { n: n });
    if (r && r.ok && r.n > 0) {
      dayAdd('lb', r.n);
      note('mua ' + r.n + ' Lệnh Bài Sát Thủ (' + (r.n * 100) + ' KNB)');
    }
  }

  // ===== Đổi nguyên liệu thừa ở Sự kiện / Tri Ân nhà giáo =====
  async function doiHongBao(I) {
    var ds = [
      ['evDhb', 4, 1, 'Đại Hồng Bao', 400],
      ['evHb', 2, 3, 'Hồng Bao', 80]
    ];
    var canChay = function () {
      if (!A.cfg.on || !ranh() || !A.st || A.st.o !== 'game' || Date.now() - A.stT >= 10000) return false;
      if (['phoban', 'bosstuan', 'thap'].indexOf(A.st.noi) >= 0) return false;
      if (I.dg || I.tower || I.sat || I.dead) return false;
      for (var i = 0; i < ds.length; i++) {
        var k = ds[i][0], max = ds[i][2];
        if (A.cfg[k] && dayGet(k) < max) return true;
      }
      return false;
    };
    if (!canChay() || (A.cool.evHongBao > Date.now())) return;

    var md = document.getElementById('modal');
    var gift = document.getElementById('giftBtn');
    if (!md || !md.classList.contains('hidden') || !gift || gift.disabled) return;
    if (!ready('evHongBao', 60000)) return;

    var giftMo = function () {
      return !md.classList.contains('hidden') && document.querySelector('#mBody #giftTabs');
    };
    // Chỉ tìm trong bảng quà; tuyệt đối không chạm mốc, nhận quà hay gift-code.
    var nut = function (i) {
      return giftMo() ? document.querySelector('#mBody [data-ng="doi:' + i + '"]') : null;
    };
    var dem = function (b) {
      var row = b && b.closest('.qrow');
      var s = row && row.querySelector('small');
      var m = s && /(?:^|[^\d.\-+])(\d+)\s*\/\s*(\d+)\s+hôm nay/.exec(s.textContent || '');
      var n = m ? Number(m[1]) : NaN, max = m ? Number(m[2]) : NaN;
      if (isFinite(n) && isFinite(max) && n === Math.floor(n) && max === Math.floor(max) &&
        0 <= n && n <= max && max <= 1000) return n;
      return null;
    };
    var cho = async function (tim) {
      var end = Date.now() + 2000;
      while (canChay() && !tim() && Date.now() < end) await sleep(100);
      return canChay() && tim();
    };

    if (!canChay()) return;
    var moAt = A.userAt;
    var daMo = false;
    try {
      daMo = true;
      gift.click();
      if (!await cho(giftMo)) { A.cool.evHongBao = Date.now() + 30 * 60000; return; }
      var tab = document.querySelector('#mBody #giftTabs button[data-g="ng"]');
      if (!tab || tab.disabled) { A.cool.evHongBao = Date.now() + 30 * 60000; return; }
      if (!canChay()) return;
      tab.click();
      if (!await cho(function () { return nut(4) || nut(2); })) {
        A.cool.evHongBao = Date.now() + 30 * 60000;
        return;
      }
      // Đại Hồng Bao giá trị cao hơn: chờ RPC và bảng vẽ lại xong mới xét Hồng Bao.
      for (var q = 0; q < ds.length; q++) {
        var k = ds[q][0], idx = ds[q][1], max = ds[q][2], ten = ds[q][3], gia = ds[q][4];
        if (!canChay()) break;
        if (!A.cfg[k] || dayGet(k) >= max) continue;
        var moi = await info();
        if (!moi) break;
        I = moi;
        if (!canChay() || !A.cfg[k] || dayGet(k) >= max) continue;
        var b = nut(idx);
        if (!b) continue;
        var truoc = dem(b);
        if (truoc === null) continue;   // không đọc được bộ đếm thì không đoán thành công
        if (truoc > dayGet(k) && !dayAdd(k, Math.min(max, truoc) - Math.min(max, dayGet(k)))) break;
        if (b.disabled || dayGet(k) >= max) continue;
        b.click();
        await sleep(1500);
        var sau = dem(nut(idx));
        if (sau !== null && sau > truoc) {
          if (!dayAdd(k, Math.max(0, Math.min(max, sau) - Math.min(max, dayGet(k))))) break;
          note('đổi 1 ' + ten + ' ở sự kiện (' + gia + ' nguyên liệu)');
        } else {
          // RPC chậm / lỗi: không báo thành công và không gửi tiếp lệnh đổi khi chưa rõ kết quả.
          A.cool.evHongBao = Date.now() + 30 * 60000;
          note('sự kiện: chưa xác nhận đổi ' + ten + ', chờ kiểm tra lại');
          break;
        }
      }
    } finally {
      if (daMo && modalCuaTa(md, moAt, giftMo)) {
        var x = document.getElementById('mClose');
        if (x) x.click();
        if (modalCuaTa(md, moAt, giftMo)) md.click();
      }
    }
  }

  // ===== KNB thường ở Kỳ Trân Các =====
  // Giá cố định 1 KNB = 1 vạn lượng (1e4) và trần 1.000 KNB / ngày / tài khoản.
  // Lệnh muaKtc của game CHỈ nhận VLM_AUTO_KTC = ["ldt","tt","ttsp"] và ném
  // "không mua được <k>" với mọi khoá khác, nên KNB thường không có đường lệnh ẩn:
  // phải bấm nút trên chính bảng Kỳ Trân Các. Trần, lượng và hạn mức máy chủ vẫn do game kiểm.
  var KNB_GIA = 1e4, KNB_NGAY = 1000;
  var knbGhiLoi = false;

  function ghiKnb(n) {
    if (knbGhiLoi) return false;
    if (dayAdd('knb', Math.min(KNB_NGAY, n))) return true;
    knbGhiLoi = true;   // dừng cả phiên: bộ đếm chưa ghi được thì không tiêu thêm
    note('không ghi được bộ đếm ngày — dừng mua KNB');
    return false;
  }

  var demKnb = function () { return Math.min(KNB_NGAY, dayGet('knb')); };
  // Sàn lượng: giữ lại ít nhất knbGiu, cộng đệm 1 KNB khi có đặt sàn.
  var sanKnb = function () { return Math.max(0, A.cfg.knbGiu | 0) + (A.cfg.knbGiu > 0 ? KNB_GIA : 0); };

  async function choViMoi(t) {
    var end = Date.now() + 2600;
    while (A.stT <= t && Date.now() < end) await sleep(100);
    if (A.stT > t && Date.now() - A.stT <= 2000) return true;
    note('KNB: không có trạng thái ví mới — dừng vòng mua');
    return false;
  }

  // Số KNB còn mua được, tính từ trạng thái game (st.luong là VẠN lượng).
  // Làm tròn xuống chỉ làm con số thấp hơn thực tế — sai theo hướng an toàn.
  function viKnb() {
    var l = A.st && A.st.luong;
    return (typeof l === 'number' && isFinite(l) && l >= 0) ? Math.floor(l) * KNB_GIA : null;
  }

  // Mở bảng Kỳ Trân Các bằng chính nút của game. Trả null khi người chơi đang mở bảng khác:
  // vẽ lại #mBody sẽ xoá màn hình họ đang xem.
  function moKtc() {
    var md = document.getElementById('modal');
    if (!md || !md.classList.contains('hidden')) return null;
    var ids = ['ktcBtn', 'bKtcT', 'u20ktc'];
    for (var i = 0; i < ids.length; i++) {
      var nut = document.getElementById(ids[i]);
      if (!nut) continue;
      try { nut.click(); } catch (e) { continue; }
      if (!md.classList.contains('hidden')) return md;
    }
    if (ready('knbMo', 30 * 60000)) note('Kỳ Trân Các: không mở được bảng — bản game đã đổi nút');
    return null;
  }

  function dongKtc(md, moAt) {
    var dungBang = function () {
      return document.querySelector('#mBody [data-ktc="knb"], #mBody [data-kb]');
    };
    if (!modalCuaTa(md, moAt, dungBang)) return;
    var x = document.getElementById('mClose');
    if (x) { try { x.click(); } catch (e) {} }
    if (!modalCuaTa(md, moAt, dungBang)) return;
    // #mClose biến mất hoặc bấm không ăn: bấm nền. Chính #modal bắt sự kiện này để đóng bảng,
    // nên không tự đặt class — đặt thẳng sẽ bỏ sót trạng thái game gán cho #modal.
    try { md.click(); } catch (e) {}
  }

  // Bảng bản mới chia 2 thẻ (KNB khóa / Kim Nguyên Bảo): thẻ KNB thường nằm sau thẻ thứ hai.
  function nutKnb() {
    var mb = document.getElementById('mBody');
    if (!mb) return null;
    if (!mb.querySelector('[data-ktc="knb"]')) {
      var ts = mb.querySelectorAll('[data-kb]');
      for (var i = 0; i < ts.length; i++) {
        if (/Kim Nguyên Bảo/i.test(ts[i].textContent || '')) {
          try { ts[i].click(); } catch (e) {}
          break;
        }
      }
    }
    return mb.querySelector('[data-ktc="knb"]');
  }

  function ngayKnb(b) {
    var row = b && b.closest('.kc, .shoprow');
    var s = row && (row.querySelector('.kc-s') || row.querySelector('small'));
    var text = (s && s.textContent) || '';
    var m = /Hôm nay đã đổi\s+(\d+)(?![\d.])/i.exec(text) || /(?:^|[^\d.\-+])(\d+)\s*\/\s*1000\s+hôm nay/i.exec(text);
    var n = m ? Number(m[1]) : NaN;
    return (isFinite(n) && n === Math.floor(n) && n >= 0 && n <= KNB_NGAY) ? n : null;
  }

  function khopNgayKnb(b) {
    var n = ngayKnb(b);
    // Không đọc được bộ đếm của game thì không dùng bộ đếm cục bộ để cho phép mua.
    if (n === null) return false;
    return ghiKnb(Math.max(demKnb(), n) - demKnb());
  }

  // Mỗi giao dịch KNB có xác nhận máy chủ riêng: chờ bộ đếm KTC trên chính giao diện cập nhật.
  async function bamKnb(n) {
    if (Date.now() - A.stT > 2000 && !await choViMoi(A.stT)) return 0;
    var du = n, got = 0, stAt = A.stT;
    for (var i = 0; i < 100 && du > 0; i++) {
      if (!A.cfg.on || !(A.cfg.knbMua > 0) || !ranh() || !A.st || A.st.o !== 'game' ||
        Date.now() - A.stT >= 60000) break;
      if (['phoban', 'bosstuan', 'thap'].indexOf(A.st.noi) >= 0) break;
      var md = document.getElementById('modal');
      if (!md || md.classList.contains('hidden')) break;
      var mot = nutKnb();
      if (!mot || !khopNgayKnb(mot)) break;
      var truoc = ngayKnb(mot);
      if (truoc === null) break;
      var vi = viKnb();
      if (vi === null || Date.now() - A.stT >= 60000) break;
      du = Math.min(du, (A.cfg.knbMua | 0) - demKnb(), KNB_NGAY - demKnb(),
        Math.floor((vi - sanKnb()) / KNB_GIA));
      if (du <= 0) break;
      if (mot.disabled) break;
      var b100 = document.querySelector('#mBody [data-ktc100="knb"]');
      var b10 = document.querySelector('#mBody [data-ktc10="knb"]');
      var nut = null, so = 0;
      if (du >= 100 && b100 && !b100.disabled) { nut = b100; so = 100; }
      else if (du >= 10 && b10 && !b10.disabled) { nut = b10; so = 10; }
      else if (!mot.disabled) { nut = mot; so = 1; }
      if (!nut) break;
      // Ghi đủ trừ trước khi bấm: lỗi storage thì không tiêu thêm, bấm lỗi chỉ đếm dư.
      if (!ghiKnb(so)) break;
      stAt = A.stT;
      try { nut.click(); } catch (e) { break; }
      // Game hoàn tất knb_spend bất đồng bộ rồi mới vẽ lại dòng Kỳ Trân Các. Phải xác nhận
      // dòng đó trước khi gửi lần bấm tiếp, nếu không cổng bận của game sẽ nuốt lệnh.
      var end = Date.now() + 6000;
      var xong = false, sau = null;
      while (Date.now() < end) {
        if (!A.cfg.on || !ranh()) break;
        sau = ngayKnb(nutKnb());
        if (sau !== null && sau > truoc) { xong = true; break; }
        await sleep(100);
      }
      if (!xong) {
        note('KNB: chưa xác nhận xong giao dịch — tạm dừng để tránh gửi lệnh trùng');
        break;
      }
      if (!ghiKnb(Math.max(0, sau - demKnb()))) break;
      got += Math.min(so, sau - truoc);
      du -= so;
    }
    if (got > 0 && A.stT <= stAt) await choViMoi(stAt);
    return got;
  }

  async function muaKnb(I) {
    var cap = A.cfg.knbMua | 0;
    if (knbGhiLoi || !A.cfg.on || cap <= 0 || (I.lvl | 0) < 20 || !ranh()) return;
    if (I.dg || I.tower || I.sat || I.dead) return;   // đang đánh nhau: không mở bảng để len tran

    var daMua = demKnb();
    // Ba trần độc lập, lấy cái chặt nhất: cấu hình người dùng, trần 1.000/ngày của game,
    // và hạn mức máy chủ nếu bản game có báo.
    var con = Math.min(cap - daMua, KNB_NGAY - daMua, KNB_NGAY);
    if (typeof I.knbCon === 'number' && isFinite(I.knbCon)) con = Math.min(con, Math.max(0, I.knbCon | 0));
    if (con <= 0) return;

    // Sàn lượng: giữ lại ít nhất knbGiu lượng. Không biết còn bao nhiêu lượng thì KHÔNG tiêu.
    var vi = viKnb();
    if (vi === null) return;
    con = Math.min(con, Math.floor((vi - sanKnb()) / KNB_GIA));
    if (con <= 0) return;
    if (!ready('knb', 60000)) return;

    var n = con;
    var k0 = I.knb | 0;
    var got = 0;
    if (!ghiKnb(0)) return;

    // Không gọi api('muaKtc', { k: 'knb' }): VLM_AUTO.muaKtc chỉ nhận ldt/tt/ttsp và luôn
    // ném "không mua được knb". Đường duy nhất là mở bảng Kỳ Trân Các rồi bấm nút.
    // Bảng toàn màn hình che game: chỉ mở khi người chơi đã rảnh 60 giây.
    if (!ranh()) return;
    var I1 = await info();
    if (!I1 || I1.dg || I1.tower || I1.sat || I1.dead) return;
    var vi1 = viKnb();
    if (vi1 === null) return;
    n = Math.min(n, (A.cfg.knbMua | 0) - demKnb(), KNB_NGAY - demKnb(),
      Math.floor((vi1 - sanKnb()) / KNB_GIA));
    if (typeof I1.knbCon === 'number' && isFinite(I1.knbCon)) n = Math.min(n, Math.max(0, I1.knbCon | 0));
    if (n <= 0 || !ranh() || !A.st || A.st.o !== 'game' || Date.now() - A.stT >= 10000) return;
    // Đọc lại cấu hình ngay trước khi mở bảng: người dùng có thể vừa tắt tự động hoặc
    // hạ số KNB trong lúc chờ info(), và đây là thao tác tốn lượng nhất trong cả bảng.
    if (!A.cfg.on || (A.cfg.knbMua | 0) <= 0) return;
    var moAt = A.userAt;
    var md = moKtc();
    if (!md) return;
    try { got = await bamKnb(n); } finally { dongKtc(md, moAt); }

    if (knbGhiLoi || got <= 0) return;

    // Lấy max(chênh lệch ví, số đã bấm): đếm dư theo hướng an toàn, không hứa số thực nhận.
    var I2 = await info();
    var lech = (I2 && typeof I2.knb === 'number') ? (I2.knb | 0) - k0 : 0;
    var that = Math.max(lech, got);
    if (that > 0) {
      if (!ghiKnb(Math.max(0, that - got))) return;
      var soLuong = (that * KNB_GIA).toLocaleString('vi-VN');
      note('mua ' + that + ' KNB (' + soLuong + ' lượng)');
    } else if (ready('knbIm', 30 * 60000)) {
      note('Kỳ Trân Các: đã bấm mua nhưng không nhận được KNB — kiểm tra lại cấu hình');
    }
  }

  // ===== Chế độ: Phó Bản (còn lượt) -> Nhiệm Vụ (còn Dã Tẩu / có Lệnh Bài) -> về chế độ cũ =====
  async function cheDo(I) {
    var lv = I.lvl | 0, t = I.dt || {}, mode = I.mode || 'farm';

    if (A.cfg.pb && !pbXong()) {
      // Ở chế độ Phó Bản mà 2 phút không vào phòng ban nào = hết lượt hôm nay.
      var trong = I.dg || (A.st && A.st.noi === 'phoban'), now = Date.now();
      var ltOk = !A.st || A.st.lt === 'noi' || A.st.lt === 'khong';
      var lien = A.pbSeen && now - A.pbSeen < 60000;
      A.pbSeen = now;
      if (mode !== 'dg' || trong || I.dead || I.sat || !ltOk || !lien) A.pbOut = 0;
      else if (!A.pbOut) A.pbOut = Date.now();
      else if (Date.now() - A.pbOut > 120000) {
        dayAdd('pbx', 1);
        A.pbOut = 0;
        note('xong phó bản hôm nay (hết lượt)');
      }
    }

    var pbCon = A.cfg.pb && !pbXong();
    var dtCon = A.cfg.dt && lv >= 20 && !t.du && ((t.today | 0) < (t.max | 0) || ((I.ldt | 0) > 0 && (t.con | 0) > 0));
    var stCon = A.cfg.st && lv >= 20 && (I.lb | 0) > 0;
    var want = pbCon ? 'dg' : ((dtCon || stCon) ? 'quest' : null);
    var prev = prevGet();

    if (want && mode !== want) {
      if (I.dg || I.tower || I.sat || I.dead || I.st) return;   // đang trong phòng ban / tháp / truy sát: chờ xong
      if (mode === 'tower' && !prev) return;                    // người chơi tự chọn Leo Tháp: không đổi
      if (!ranh() || !ready('mode', 60000)) return;
      var r = await api('cheDo', { m: want });
      if (r && r.ok && r.m === want) {
        if (!prev) prevSet(mode);
        A.pbOut = 0;
        note(want === 'dg'
          ? 'chuyển Chế độ Phó Bản (còn lượt phó bản)'
          : ('chuyển Chế độ Nhiệm Vụ (' + (dtCon ? 'còn Dã Tẩu' : 'có Lệnh Bài Sát Thủ') + ')'));
      }
    } else if (!want && prev && MODE_VI[prev] && mode !== prev && (mode === 'quest' || mode === 'dg')) {
      if (I.dg || I.st || I.sat || !ranh() || !ready('mode', 60000)) return;
      var r2 = await api('cheDo', { m: prev });
      if (r2 && r2.ok && r2.m === prev) {
        prevSet(null);
        note('xong việc: về chế độ ' + MODE_VI[prev]);
      }
    } else if (!want && prev && mode === prev) {
      prevSet(null);
    }
  }

  async function tick() {
    if (A.busy || !A.cfg.on) return;
    var ingame = !!(A.st && A.st.o === 'game' && Date.now() - A.stT < 10000);

    // Cổng phiên bản: bản game cũ không có lệnh ẩn -> không làm gì, báo một lần.
    if (ingame && (A.st.v | 0) < 990) {
      if (!A.gate) {
        A.gate = 1;
        note('bản game ' + (A.st.v | 0) + ' < 990 — không chạy tự động');
        try { console.log('[vlmAuto] game version too old:', A.st.v); } catch (e) {}
      }
      return;
    }
    if (!ingame) { A.started = 0; return; }

    if (!A.started) A.started = Date.now();
    if (Date.now() - A.started < 30000) return;   // vừa vào game: chờ game ổn định

    A.busy = true;
    try {
      var fns = [troThu, khan, tienThao, qua, daTau, muaLb, doiHongBao, muaKnb, cheDo];
      for (var i = 0; i < fns.length; i++) {
        if (!A.cfg.on) break;
        var I = await info();
        if (!I) break;
        await fns[i](I);
      }
    } catch (e) {
      // Một hành động lỗi không được chặn các hành động còn lại ở nhịp sau.
    } finally {
      A.busy = false;
    }
  }

  window.__vlmAuto = {
    ver: VER,
    setCfg: setCfg,
    resetDay: resetDay,
    tick: tick,
    trangThai: function () {
      return { on: !!A.cfg.on, busy: A.busy, last: A.last, lastAt: A.lastAt, st: A.st };
    }
  };

  setCfg(CFG0, TOUCH0);
  setInterval(function () { tick().catch(function () {}); }, 20000);
  return 'installed';
})();
    """.trimIndent()
}
