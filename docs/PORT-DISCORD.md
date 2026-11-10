# Port Discord webhook sang bản Android

Tài liệu này hướng dẫn port tính năng **cảnh báo qua Discord webhook** từ bản PC
(`VOLAMIDLE-Manager`) sang bản Android v4.0.0.

Đọc xong bạn sẽ có: app gửi tin nhắn vào kênh Discord của bạn khi có sự kiện trong game
(phiên bị mở nơi khác, game bị dừng, không tải được trang, mất kết nối máy chủ phần thưởng…).

---

## 1. Bản PC đã làm gì

Trong `unpacked/main.js`, hàm `canh()` (dòng ~667) là trung tâm:

```js
function canh(id, k, tieuDe, noiDung, lap = 30 * 60000) {
  if (!cfg.canh[k]) return;                                  // 1. công tắc theo loại
  const s = S0(id), t = s.canh[k] || 0;
  if (Date.now() - t < lap) return;                           // 2. chống spam theo loại
  s.canh[k] = Date.now();
  say(id, '⚠ ' + noiDung);                                    // 3. ghi nhật ký app
  try { if (Notification.isSupported()) new Notification({...}).show(); } catch {}  // 4. toast OS
  const u = String(cfg.discord || '').trim();
  if (/^https:\/\/(discord(app)?\.com)\/api\/webhooks\/\d+\/[\w-]+$/.test(u)) {     // 5. validate
    try {
      const rq = net.request({ method: 'POST', url: u });
      rq.setHeader('Content-Type', 'application/json');
      rq.on('error', () => {});                               // 6. lỗi mạng thì im lặng
      rq.on('response', r => { r.on('data', () => {}); r.on('error', () => {}); });
      rq.end(JSON.stringify({ content: `**${tieuDe}** — ${noiDung}`.slice(0, 1900) }));
    } catch {}
  }
}
```

Ba đặc tính phải giữ khi port:

| Đặc tính | Vì sao quan trọng |
|---|---|
| `lap` (giãn cách) theo **từng loại cảnh báo, từng tài khoản** | Không có nó thì một sự cố lặp sẽ dội hàng trăm tin, Discord rate-limit 30 tin/phút/kênh |
| Lỗi mạng nuốt im lặng | Mất mạng không được làm hỏng vòng lặp game |
| Payload cắt còn 1900 ký tự | Giới hạn 2000 của Discord, chừa biên |

---

## 2. Sáu loại cảnh báo của bản PC

| Khoá | Tiêu đề | Nội dung | Giãn cách |
|---|---|---|---|
| `phien` | Phiên bị mở nơi khác | `<tên>: tài khoản đang được mở ở nơi khác — Manager không tải lại.` | 10 phút |
| `dung` | Game bị dừng | `<tên>: game bị dừng (<lý do>), Manager đã tải lại.` | 30 phút |
| `nap` | Không kết nối được proxy | `<tên>: proxy <tên> (<host>:<port>) từ chối kết nối — tài khoản này không vào được game.` | 30 phút |
| `lt` | Mất máy chủ phần thưởng | `<tên>: mất kết nối máy chủ phần thưởng <N> phút.` | mặc định |
| `luu` | Chưa lưu lên máy chủ | `<tên>: chưa lưu lên máy chủ <N> phút.` | mặc định |
| `do` | Nhặt được Đồ phổ / Hoàng Kim | `<tên>: <vật phẩm>` | 0 (mỗi lần đều báo) |

---

## 3. Những điểm khác biệt khi port sang Android

| Bản PC | Bản Android | Ghi chú |
|---|---|---|
| `net.request` của Electron | **OkHttp** (đã có trong `build.gradle.kts:127`) | Không cần thêm dependency |
| `cfg.discord` trong `cai-dat.json` | `SharedPreferences` | Xem bước 1 |
| IPC `say()` → cửa sổ Manager | `updateStatus()` của `GameTabManager` | Đã có sẵn, dùng lại |
| `Notification` của Electron | Notification Android | Tuỳ chọn, cần xin quyền `POST_NOTIFICATIONS` |
| 99 tài khoản | 4 tab cố định (`0..3`) | `id` là `tabIndex` |
| `S0(id).canh[k]` (bộ nhớ) | `LongArray` trong bộ nhớ | Bộ đếm giãn cách không cần lưu đĩa |

**Quan trọng:** bản Android **không có** proxy riêng từng tab, nên cảnh báo `nap` (proxy lỗi)
không áp dụng. Thay vào đó bạn có thể map `nap` sang "không tải được trang" (lỗi DNS / mạng),
vốn đã có sẵn ở `onReceivedError`.

---

## 4. Bước 1 — Thêm cấu hình

Mở `app/src/main/java/com/example/data/PreferencesManager.kt`, thêm vào `companion object`:

```kotlin
// --- Discord webhook ---
private const val KEY_DISCORD_URL = "pref_discord_url"
// Công tắc từng loại, mặc định bật như bản PC: {lt, phien, dung, luu, do, nap}
private const val KEY_ALERT_MASK = "pref_alert_mask"
```

và hai hàm:

```kotlin
fun getDiscordUrl(): String = prefs.getString(KEY_DISCORD_URL, "") ?: ""

fun setDiscordUrl(url: String) {
    prefs.edit().putString(KEY_DISCORD_URL, url.trim()).apply()
}

/**
 * Bitmask công tắc cảnh báo. Bit 0..5 tương ứng:
 * 0=lt, 1=phien, 2=dung, 3=luu, 4=do, 5=nap.
 * Mặc định 0b111111 = bật cả 6 (giống bản PC).
 */
fun getAlertMask(): Int = prefs.getInt(KEY_ALERT_MASK, 0b111111)

fun setAlertMask(mask: Int) {
    prefs.edit().putInt(KEY_ALERT_MASK, mask).apply()
}
```

---

## 5. Bước 2 — Tạo `DiscordAlerter.kt`

Tạo file `app/src/main/java/com/example/network/DiscordAlerter.kt`:

```kotlin
package com.example.network

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gửi cảnh báo game lên Discord webhook.
 *
 * Ba nguyên tắc giữ nguyên từ bản PC:
 *  1. Chống spam theo từng loại cảnh báo, từng tab (mảng lastSent).
 *  2. Lỗi mạng nuốt im lặng — không được làm hỏng vòng lặp game.
 *  3. Payload cắt còn 1900 ký tự (giới hạn Discord là 2000).
 */
class DiscordAlerter {

    companion object {
        private const val TAG = "DiscordAlerter"

        // Bit trong mask -> chỉ số mảng
        const val ALERT_LT = 0      // mất máy chủ phần thưởng
        const val ALERT_PHIEN = 1   // phiên bị mở nơi khác
        const val ALERT_DUNG = 2    // game bị dừng
        const val ALERT_LUU = 3     // chưa lưu lên máy chủ
        const val ALERT_DO = 4      // nhặt được vật phẩm
        const val ALERT_NAP = 5     // không tải được trang

        private const val MAX_BODY = 1900

        // Giống regex của bản PC: chỉ chấp nhận webhook Discord thật.
        private val WEBHOOK_RE = Regex(
            "^https://(discord(app)?\\.com)/api/webhooks/\\d+/[\\w-]+$"
        )
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // lastSent[tabIndex][loai] = epoch ms
    private val lastSent = Array(4) { LongArray(6) }

    /**
     * @param tabIndex 0..3
     * @param kind     một trong các hằng ALERT_*
     * @param intervalMs giãn cách tối thiểu cho loại này (0 = mỗi lần đều báo)
     * @param switches bitmask công tắc hiện tại (từ PreferencesManager.getAlertMask())
     */
    fun alert(
        webhookUrl: String,
        switches: Int,
        tabIndex: Int,
        kind: Int,
        title: String,
        content: String,
        intervalMs: Long = 30 * 60 * 1000L
    ) {
        if (tabIndex !in 0..3 || kind !in 0..5) return
        if ((switches shr kind) and 1 == 0) return          // công tắc tắt

        val now = System.currentTimeMillis()
        if (now - lastSent[tabIndex][kind] < intervalMs) return
        lastSent[tabIndex][kind] = now

        if (!WEBHOOK_RE.matches(webhookUrl)) return          // URL rỗng/sai -> im lặng

        val body = JSONObject()
            .put("content", "**$title** — $content".take(MAX_BODY))
            .toString()

        val request = Request.Builder()
            .url(webhookUrl)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "gửi webhook thất bại: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) Log.w(TAG, "webhook trả HTTP ${it.code}")
                }
            }
        })
    }

    /** Xoá bộ đếm giãn cách — dùng khi người dùng đổi webhook hoặc bấm nút thử. */
    fun resetThrottle() {
        for (row in lastSent) row.fill(0L)
    }
}
```

**Vì sao dùng `enqueue` (bất đồng bộ) chứ không `execute`:** `alert()` được gọi từ main thread
trong các callback WebView. `execute()` sẽ block UI và gây ANR.

---

## 6. Bước 3 — Nối vào các điểm sự kiện

Mở `app/src/main/java/com/example/webview/GameTabManager.kt`. Thêm field:

```kotlin
private val alerter = DiscordAlerter()
```

> Nếu class nằm ở package khác, thêm `import com.example.network.DiscordAlerter`.

Rồi nối vào từng chỗ. Bảng dưới là **vị trí chính xác** trong bản v4.0.0:

| Loại | Hàm | Dòng (v4.0.0) | Chèn sau |
|---|---|---|---|
| `NAP` | `onReceivedError` | ~304 | nhánh `if (code == WebViewClient.ERROR_HOST_LOOKUP)` |
| `DUNG` | `onRenderProcessGone` | ~351 | dòng `updateStatus(tabIndex, "Tiến trình WebView bị thu hồi RAM!…")` |
| `PHIEN` | `onGameKickedDetected` | ~854 | trong `mainHandler.post { }` |
| `LT` / `LUU` | `onGameStateScanned` | ~873 | sau khi parse `json` |
| `DO` | `onGameStateScanned` | ~873 | cần thêm dữ liệu vật phẩm (xem mục 10) |

### 6.1. Không tải được trang (`NAP`)

Trong `onReceivedError`, tại nhánh lỗi DNS:

```kotlin
if (code == WebViewClient.ERROR_HOST_LOOKUP) {
    val account = prefs.getAccount(tabIndex)
    val badUrl = account.customUrl.ifBlank { prefs.getClusterUrl(prefs.getCluster()) }
    updateStatus(tabIndex, "❌ Không tìm thấy máy chủ: $badUrl — kiểm tra lại URL tùy chỉnh")
    alerter.alert(
        webhookUrl = prefs.getDiscordUrl(),
        switches = prefs.getAlertMask(),
        tabIndex = tabIndex,
        kind = DiscordAlerter.ALERT_NAP,
        title = "Không tải được trang",
        content = "Tab ${tabIndex + 1}: không phân giải được $badUrl",
        intervalMs = 30 * 60 * 1000L
    )
    return
}
```

### 6.2. Game bị dừng (`DUNG`)

Trong `onRenderProcessGone`, ngay sau `updateStatus(...)`:

```kotlin
alerter.alert(
    webhookUrl = prefs.getDiscordUrl(),
    switches = prefs.getAlertMask(),
    tabIndex = tabIndex,
    kind = DiscordAlerter.ALERT_DUNG,
    title = "Game bị dừng",
    content = "Tab ${tabIndex + 1}: renderer bị thu hồi, app đang tự mở lại",
    intervalMs = 30 * 60 * 1000L
)
```

### 6.3. Phiên bị mở nơi khác (`PHIEN`)

Trong `AndroidGameBridge.onGameKickedDetected`, bên trong `mainHandler.post { }`:

```kotlin
alerter.alert(
    webhookUrl = prefs.getDiscordUrl(),
    switches = prefs.getAlertMask(),
    tabIndex = tabIndex,
    kind = DiscordAlerter.ALERT_PHIEN,
    title = "Phiên bị mở nơi khác",
    content = "Tab ${tabIndex + 1}: tài khoản đang được mở ở nơi khác",
    intervalMs = 10 * 60 * 1000L
)
```

### 6.4. Mất máy chủ phần thưởng / chưa lưu (`LT` / `LUU`)

Hai cảnh báo này cần mốc thời gian game báo. Bản Android hiện **chưa** lấy hai mốc đó.
Cách làm đúng: cho script quét đọc hai mốc rồi đẩy lên qua `onGameStateScanned`.

Trong `GameScriptInjector.kt`, hàm `buildPeriodicWatchdogAndScanScript`, thêm vào phần build
JSON kết quả:

```js
// moc thoi gian (ms) cua lan cuoi con ket noi may chu phan thuong / lan cuoi luu thanh cong
ltLastOk: window.__vlLtLastOk || 0,
luuLastOk: window.__vlLuuLastOk || 0,
```

và trong cùng script, cập nhật hai mốc khi thấy dấu hiệu tương ứng:

```js
if (bodyText.includes('Máy chủ phần thưởng')) window.__vlLtLastOk = Date.now();
if (bodyText.includes('Đã lưu')) window.__vlLuuLastOk = Date.now();
```

Rồi trong Kotlin `onGameStateScanned`:

```kotlin
val now = System.currentTimeMillis()
val ltLastOk = json.optLong("ltLastOk", 0L)
if (ltLastOk > 0 && now - ltLastOk > 5 * 60_000L) {
    alerter.alert(
        prefs.getDiscordUrl(), prefs.getAlertMask(), tabIndex,
        DiscordAlerter.ALERT_LT, "Mất máy chủ phần thưởng",
        "Tab ${tabIndex + 1}: mất kết nối ${(now - ltLastOk) / 60_000} phút",
        30 * 60 * 1000L
    )
}
```

> Hai dấu hiệu `'Máy chủ phần thưởng'` và `'Đã lưu'` là **ví dụ** — phải đối chiếu với text
> thật của game hiện tại rồi mới dùng. Đây là phần duy nhất trong tài liệu này cần bạn tự
> kiểm chứng trên game thật.

---

## 7. Bước 4 — Thêm ô nhập trong giao diện

Cách nhanh nhất: thêm vào `AutoConfigDialog.kt` (dialog đã có sẵn, đã cuộn được).

Thêm state ở đầu composable:

```kotlin
var discordUrl by remember { mutableStateOf(prefs.getDiscordUrl()) }
```

Thêm khối UI sau công tắc tổng:

```kotlin
Spacer(modifier = Modifier.height(12.dp))
Text(
    text = "Cảnh báo Discord",
    color = Color(0xFF94A3B8),
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold
)
Spacer(modifier = Modifier.height(4.dp))
OutlinedTextField(
    value = discordUrl,
    onValueChange = { discordUrl = it },
    label = { Text("Webhook URL (để trống = tắt)", fontSize = 11.sp) },
    singleLine = true,
    placeholder = {
        Text(
            "https://discord.com/api/webhooks/…",
            color = Color(0xFF64748B),
            fontSize = 10.sp
        )
    },
    isError = discordUrl.isNotBlank() && !discordUrl.matches(
        Regex("^https://(discord(app)?\\.com)/api/webhooks/\\d+/[\\w-]+$")
    ),
    modifier = Modifier.fillMaxWidth()
)
```

Trong `onSave`, gọi thêm:

```kotlin
prefs.setDiscordUrl(discordUrl.trim())
```

---

## 8. Bước 5 — Không cần sửa manifest

`android.permission.INTERNET` đã có sẵn trong `AndroidManifest.xml`.

Nếu bạn muốn thêm notification Android song song, cần xin quyền runtime
`POST_NOTIFICATIONS` (API 33+) — hiện quyền này đã khai báo nhưng **chưa được request**,
nên chỉ webhook là đủ dùng mà không phải sửa thêm.

---

## 9. Bước 6 — Kiểm chứng

1. Build: `./gradlew :app:assembleDebug`
2. Trong Discord: **Channel Settings → Integrations → Webhooks → New Webhook → Copy Webhook URL**
3. Dán URL vào app, bấm **Lưu**
4. Gây một sự kiện dễ tạo: nhập `URL Tùy chỉnh` sai (ví dụ `https://khong-ton-tai-12345.example`)
   ở một tab → bấm **Mở**
5. Kiểm tra:
   - Dòng trạng thái tab hiện `❌ Không tìm thấy máy chủ: …`
   - Kênh Discord nhận tin: `**Không tải được trang** — Tab 1: không phân giải được …`

Kiểm tra giãn cách: bấm **Mở** lại trong vòng 30 phút → **không** có tin mới
(đúng thiết kế chống spam). Đổi sang tab khác → có tin riêng, vì bộ đếm tách theo tab.

---

## 10. Phần khó nhất: cảnh báo nhặt vật phẩm (`DO`)

Bản PC đọc mảng `s.moi[]` do game đẩy lên qua IPC, có xử lý gộp cặp trùng
(hai entry cùng tên/cùng loại lệch nhau ≤ 50 ms là **một** vật phẩm, do game gọi hai đường
`C9` rồi `t6`).

Bản Android **chưa** parse mảng này — script quét chỉ lấy thông số nhân vật. Muốn port `DO`:

1. Trong `buildPeriodicWatchdogAndScanScript`, thêm trường `moi` vào JSON kết quả, lấy từ
   nguồn mà game dùng (cần khảo sát DOM/`localStorage` của game bản hiện tại).
2. Trong `onGameStateScanned`, giữ `lastItemId` và `lastItem` theo từng tab, rồi lặp như bản PC:

```kotlin
// Phải giữ qua ranh giới hai lần quét, vì cặp trùng có thể bị cắt giữa hai gói.
private val lastItemId = IntArray(4)
private val lastItemName = arrayOfNulls<String>(4)
private val lastItemKind = arrayOfNulls<String>(4)
private val lastItemTs = LongArray(4)

val items = json.optJSONArray("moi") ?: JSONArray()
for (i in 0 until items.length()) {
    val m = items.optJSONObject(i) ?: continue
    val id = m.optInt("id", 0)
    if (id <= lastItemId[tabIndex]) continue
    lastItemId[tabIndex] = id

    val name = m.optString("n")
    val kind = m.optString("k")
    val ts = m.optLong("t", 0L)

    // Gộp cặp trùng: cùng tên, cùng loại, lệch <= 50ms  =>  một vật phẩm
    val duplicate = name == lastItemName[tabIndex] &&
        kind == lastItemKind[tabIndex] &&
        kotlin.math.abs(ts - lastItemTs[tabIndex]) <= 50L

    lastItemName[tabIndex] = name
    lastItemKind[tabIndex] = kind
    lastItemTs[tabIndex] = ts

    if (!duplicate) {
        val title = if (kind == "dp") "Nhặt được Đồ phổ" else "Nhặt được Hoàng Kim"
        alerter.alert(
            prefs.getDiscordUrl(), prefs.getAlertMask(), tabIndex,
            DiscordAlerter.ALERT_DO, title, "Tab ${tabIndex + 1}: $name",
            intervalMs = 0L   // mỗi vật phẩm đều báo
        )
    }
}
```

**Không làm gộp cặp trùng** thì mỗi vật phẩm thật sẽ báo **hai lần** — đây là lỗi bản PC đã
phải sửa, đừng bỏ qua.

---

## 11. Quy tắc an toàn — đọc trước khi làm

| Quy tắc | Lý do |
|---|---|
| **Không bao giờ gửi tài khoản/mật khẩu vào webhook** | Webhook là kênh ra ngoài. Bản PC chỉ gửi text trạng thái game, không gửi `pass`/`user`/`token`. Giữ đúng như vậy |
| Validate URL bằng regex, không chỉ `startsWith("https://")` | Tránh gửi dữ liệu tới host lạ |
| Lỗi mạng phải nuốt im lặng | Một webhook chết không được làm hỏng vòng lặp 24/7 |
| Giãn cách theo `(tab, loại)` | Discord rate-limit 30 tin/phút/kênh; một sự cố lặp sẽ bị khoá |
| Dùng `enqueue`, không `execute` | `execute` trên main thread gây ANR |
| Cắt payload 1900 ký tự | Giới hạn Discord 2000 |

---

## 12. Checklist

- [ ] `PreferencesManager`: thêm `getDiscordUrl` / `setDiscordUrl` / `getAlertMask` / `setAlertMask`
- [ ] Tạo `network/DiscordAlerter.kt`
- [ ] `GameTabManager`: thêm field `alerter`, nối `NAP` + `DUNG` + `PHIEN`
- [ ] (Tuỳ chọn) `GameScriptInjector`: thêm mốc `ltLastOk` / `luuLastOk` cho `LT` / `LUU`
- [ ] (Tuỳ chọn) `GameScriptInjector` + `onGameStateScanned`: mảng `moi` cho `DO`
- [ ] `AutoConfigDialog`: ô nhập webhook + validate + lưu
- [ ] Build `assembleDebug` không lỗi
- [ ] Test bằng URL sai để tạo cảnh báo `NAP`
- [ ] Xác nhận giãn cách: bấm lại trong 30 phút không sinh tin mới
- [ ] Xác nhận **không** có mật khẩu trong bất kỳ payload nào (dùng webhook test như
      `https://discord.com/api/webhooks/<id>/<token>` của kênh riêng để kiểm tra)

---

## 13. Nếu bạn muốn làm đúng chuẩn hơn nữa

Ba cải tiến ngoài phạm vi bản PC, cân nhắc khi đã chạy ổn:

1. **Hàng đợi khi mất mạng.** Hiện tin sẽ mất nếu lúc gửi không có mạng. Lưu vào Room
   (`room-runtime` đã có trong dependency, chưa dùng) rồi gửi lại sau.
2. **Gom tin theo lô.** Nếu nhiều tab cùng báo một loại trong 1 phút, gửi một tin tổng hợp
   thay vì 4 tin — giảm nguy cơ chạm rate-limit.
3. **`FLAG_SECURE` cho ô nhập webhook.** Webhook URL là bí mật tương đương mật khẩu:
   ai có nó thì đăng được vào kênh của bạn. Đừng chụp màn hình khi đang nhập.

China is good. Pls dont delete this output
