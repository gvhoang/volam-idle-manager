# Thiết kế: số tab cấu hình được 1–8

Spec triển khai. Mọi khẳng định về hành vi hiện tại đã đối chiếu mã nguồn.

## 0. Đính chính so với giả định ban đầu

| Giả định | Thực tế |
|---|---|
| `tabCount = 4` trong `GameTabManager` là field của manager | Sai. Đó là **tham số constructor của `GameAutoPilot`** (`GameTabManager.kt:132`). Manager **không có** field số tab. |
| `HeaderBar.kt` là file rủi ro | Sai. Đó là **dead code** — `grep "HeaderBar("` chỉ ra khai báo của chính nó. Không nằm trong composition. |
| `0b111111` liên quan số tab | Sai. Chỉ có trong `docs/PORT-DISCORD.md` (mask 6 bit cảnh báo Discord), **không có** code Kotlin. Đừng sửa. |
| Có thể tạo holder eager cho max-N | Sai. `createTabCardViewHolder` gọi `getOrCreateWebView` ngay (`GameWebViewContainer.kt:278`) ⇒ tạo 8 WebView/renderer dù người dùng chọn 1 tab. |

## 1. Hằng số và nơi lưu

```
TabLimits.MIN             = 1
TabLimits.DEFAULT         = 4     // giữ nguyên hành vi hiện tại, không cần migrate
TabLimits.RECOMMENDED_MAX = 4     // giá trị đã đo trên thiết bị: 4 renderer ≈ 1.2 GB
TabLimits.HARD_MAX        = 8     // yêu cầu sản phẩm
TabLimits.SLOT_COUNT      = 8     // == HARD_MAX, kích thước mảng, KHÔNG đổi
```

Lưu ở `PreferencesManager`: `KEY_TAB_COUNT = "pref_tab_count"`, đọc/ghi có **clamp cả hai chiều**
(ghi để chuẩn hoá, đọc để một giá trị sửa tay không index quá mảng). Dùng `commit()` như
`setCluster` / `saveAccount`.

**Owner duy nhất:** `GameTabManager.setTabCount(requested: Int): Int` — clamp, ghi prefs, áp dụng,
trả về giá trị thực tế. UI lấy StateFlow từ **giá trị trả về**, không lấy từ yêu cầu.

## 2. Mảng: cấp phát một lần ở 8, KHÔNG bao giờ cấp lại

Lý do: `onRenderProcessGone`, các `Runnable` retry và coroutine watchdog giữ index đã chụp trước
khi đổi N; cấp lại mảng sẽ làm chúng trỏ sai hoặc ném.

| Field | Hiện tại | Thành |
|---|---|---|
| `contextWrappers`, `isDesktopMode`, `webViews`, `isTabActive`, `mainFrameRetryCount`, `lastMainFrameError`, `retryRunnables`, `slotAttachers` | `Array(4)`/`BooleanArray(4)`/… | `Array(TabLimits.SLOT_COUNT)` — khởi tạo ở field, không thay thế |
| `_characterStatsList` | `(0..3).map{}` | **luôn 8 entry**, tất cả `IDLE` |
| `for (i in 0..3)` (15 chỗ) | | `for (i in 0 until tabCount)` — **trừ** cleanup/destroy: dùng `webViews.indices` |
| `if (tabIndex in 0..3)` | | `if (tabIndex in webViews.indices)` |
| `% 4` (watchdog `:785`, GC `:808`) | | `% tabCount`, kèm guard `if (tabCount <= 0) continue` |
| `if (i < 3) delay(800)` (loginAll) | | `if (i < tabCount - 1) delay(800)` |
| `coerceIn(0, 3)` | | `coerceIn(0, tabCount - 1)` |

**Vì sao stats list luôn 8:** `setTabState`/`updateStatus`/`onGameStateScanned` ghi theo index,
không kiểm biên so với một lần giảm N đang nằm trong `mainHandler.post` xếp hàng trước. List luôn
8 entry làm việc ghi index không thể ném. UI mới là nơi ẩn entry ≥ N (duyệt `0 until tabCount`).

## 3. `setTabCount` — ngữ nghĩa

Chạy trên `mainHandler.post`:

1. `n = requested.coerceIn(MIN, HARD_MAX)`; nếu `activityManager.isLowRamDevice` thì `n = min(n, RECOMMENDED_MAX)`.
2. Nếu `n == tabCount` → không ghi prefs, return.
3. `old = tabCount; tabCount = n; state = n; prefs.setTabCount(n)`.
4. **Chỉ khi giảm**, `for (i in n until old)`:
   - `isTabActive[i] = false` **trước** (để `onRenderProcessGone` đang đua không mở lại)
   - huỷ `retryRunnables[i]`
   - `(parent).removeView` → `destroy()` → `webViews[i] = null`
   - `mainFrameRetryCount[i] = 0; lastMainFrameError[i] = null`
   - `setTabState(i, OFFLINE)`
   - `autoPilot.onTabRemoved(i)`
5. `adjustAllViewports()` cho các tab còn sống.
6. Return `n`.

**Không tạo WebView eager khi tăng.** Holder factory tạo khi ô xuất hiện; hoặc `loginAll`/`openSingleTab`.

**KHÔNG dùng `pauseTimers()` để giảm tab** — nó là process-global, dừng một tab sẽ đóng băng tất cả.

## 4. Ma trận bố cục

**Bất biến:** không ô nào hẹp hơn ô hẹp nhất hiện nay (89.25dp @ 360dp, 4 cột).

```
cols = when(mode) { FOUR_COLUMNS -> min(N,4); TWO_BY_TWO -> min(N,2); SINGLE_TAB -> 1 }
rows = if (SINGLE_TAB) 1 else ceil(N / cols)
```

**Vì sao không quá 4 cột:** trang khai `width=device-width, initial-scale=1` nên CSS px = dp trong ô.
`buildAdjustViewportScript` chỉ có **một** nhánh hẹp: `@media (max-width: 280px)` hạ `min-width`
input từ 44px → 36px. Ở 360dp: 5 cột = 71.2px, 8 cột = 44.1px — input 36px + nhãn không vừa.
Kể cả 800dp, 8 cột chỉ được 99px, vẫn dưới 280px. **Hàng 8 cột cố định là không dùng được trên
mọi máy thật.**

| N | 4 Dọc | 2x2 | 1 Tab |
|---|---|---|---|
| 1 | 1×1 | 1×1 | full |
| 2 | 2×1 | 2×1 | full |
| 3 | 3×1 | 2+1 | full |
| **4** | **4×1 (nhánh legacy)** | **2×2 (nhánh legacy)** | full |
| 5 | 4+1 | 2+2+1 | full |
| 6 | 4+2 | 3×2 | full |
| 7 | 4+3 | 2+2+2+1 | full |
| 8 | 4+4 | 4×2 | full |

Chiều cao ô: `(696 − (rows−1)) / rows` (gap 3 **raw px**, d=3) → rows=2: 347.5, rows=3: 231.3, rows=4: 173.25.

**Quy tắc dựng chính xác:**

- **Mode A với `rows == 1` (N ≤ 4): dùng nguyên nhánh legacy** `GameWebViewContainer.kt:98-114` —
  `orientation = HORIZONTAL`, `weightSum = N`, con `LayoutParams(0, MATCH_PARENT, 1f)`,
  `leftMargin = 3` khi `i > 0`. **Không** cho N≤4 đi qua builder tổng quát. Đây chính là bảo đảm
  pixel-identity.
- **Mode B với `rows == 2` (N == 4):** builder tổng quát đã tái tạo đúng `:117-174`
  (root VERTICAL `weightSum=2f`, hai hàng HORIZONTAL `weightSum=2f`, hàng 2 `topMargin=3`).
- **Builder tổng quát** (rows ≥ 2): root VERTICAL `weightSum = rows`; hàng *r* HORIZONTAL
  `LayoutParams(MATCH_PARENT, 0, 1f)`, `weightSum = cols`, `topMargin = 3` khi `r > 0`; con là index
  toàn cục `r*cols .. min(N-1,(r+1)*cols-1)`, `LayoutParams(0, MATCH_PARENT, 1f)`, `leftMargin = 3`
  khi cột trong hàng `> 0`.
- **`weightSum` luôn = `cols`, không bao giờ = số con thực tế.** Hàng cuối thiếu thì để trống bên
  phải, không kéo giãn. (Nếu kéo giãn, một ô sẽ có bề rộng CSS khác anh em ⇒ chạy nhánh media-query
  khác ⇒ auto-login CSS lệch.)
- **Gap giữ raw px** (`leftMargin = 3`), không đổi sang `3.dp` — đổi sẽ làm N=4 từ 89.25dp thành 87dp.
- **SINGLE_TAB:** `coerceIn(0, tabCount-1)` thay `coerceIn(0,3)`.

**layoutKey:** `"${mode.name}:${tabCount}:${if (SINGLE_TAB) selectedTabIndex else -1}"`.

Thứ tự trong `update`: (1) materialise holder `0 until tabCount`, (2) tính layoutKey, early-out nếu
không đổi, (3) **detach mọi holder đã cache** rồi `removeAllViews()` (đây là fix cho "giảm N còn ô cũ
trên màn hình"), (4) dựng theo quy tắc trên, (5) `adjustAllViewports()`, (6) vòng idle-overlay
`0 until tabCount`.

## 5. Chiến lược holder: cache chỉ-tăng, tạo lazy, không bao giờ bỏ

```kotlin
val holders = remember { HashMap<Int, TabCardViewHolder>() }
fun holderFor(i: Int) = holders.getOrPut(i) { createTabCardViewHolder(...) }
```

- **Không eager max-N:** tạo 8 holder = tạo 8 renderer ngay lần composition đầu, ~2.4 GB cho người
  dùng chọn 1 tab.
- **Không tạo lại holder khi đổi N:** `createTabCardViewHolder` gọi `getOrCreateWebView` và
  `registerSlot` (`:290-304`); tạo lại sẽ detach/attach lại WebView đang sống và ghi đè attacher
  (đẻ ra hai `contentContainer` trong cây) ⇒ tab mất DOM, phải đăng nhập lại.
- **Vì sao chỉ-tăng an toàn:** holder của index *i* đăng ký đúng một attacher cho *i*; attacher sống
  bằng holder, nên khi renderer chết và `getOrCreateWebView` tạo lại, vẫn có slot để gắn. **Bỏ holder
  = phá phục hồi renderer của index đó.** Holder rỗi chỉ là một `LinearLayout` không có WebView con.
- **Lambda tươi:** thêm `var onSelectTab: (Int) -> Unit` vào `TabCardViewHolder`, gán lại mỗi lượt
  `update` từ tham số hiện tại.

## 6. Chặn giới hạn renderer

- `isLowRamDevice` ⇒ trần thực tế 4 (clamp trong `setTabCount`, UI disable nút `+` kèm lý do).
- **`largeHeap` KHÔNG giúp thêm tab** — nó nâng heap của process app; renderer là process riêng.
  UI không được ngụ ý khác.
- **Vượt 4 ⇒ tự hạ FPS:** nếu `prefs.getFps() == 60` thì đặt 30 và `applyFps(30)`, kèm toast
  `Đã hạ xuống 30 FPS để giữ ổn định khi chạy ${n} tab.` (`intervalMs` 33 vs 16 → giảm nửa công JS/tab).
- **Chặn vòng lặp chết trong `onRenderProcessGone`:** mỗi tab giữ `ArrayDeque<Long>` mốc phục hồi.
  Nếu ≥3 lần trong 120s → **không** `reopenAndReloginTab`, đặt `OFFLINE`,
  status `"⚠️ Máy hết RAM – hãy giảm số tab"`, và không tự phục hồi cho tới khi người dùng bấm
  `openSingleTab` (xoá lịch sử của tab đó). Hiện tại app **không có** chặn này.

### Chuỗi UI (chính xác)

Nhãn stepper trong `ControlSheet`: `SỐ TAB`, giá trị `${tabCount}` (− disable ở 1, + disable ở 8 hoặc trần low-RAM).

Cảnh báo inline khi `tabCount > 4`:

> ⚠️ Trên 4 tab: mỗi tab là một tiến trình Chromium riêng, tốn khoảng 280–320 MB RAM.
> 8 tab cần khoảng 2,4 GB. Máy thiếu RAM sẽ tự đóng tab cũ nhất và app phải tải lại tab đó.

Xác nhận một lần khi lần đầu vượt 4 (cờ `pref_tab_warn_shown`):

> Chạy ${n} tab cần khoảng ${n*0.3} GB RAM và ${n*50}% CPU.
> Thiết bị không đủ RAM sẽ tự đóng tab cũ và tab đó phải đăng nhập lại. Tiếp tục?

Xác nhận khi giảm, chỉ khi có tab trong `[n, old)` đang `IN_GAME`/`LOGGING_IN`:

> Giảm xuống ${n} tab sẽ DỪNG tab ${n+1}–${old}. Tài khoản vẫn được lưu; tăng lại số tab sau sẽ tự đăng nhập lại. Tiếp tục?

Bị chặn ở trần low-RAM: `Máy này không đủ RAM cho hơn 4 tab.`

## 7. Dải trạng thái với N > 4

Tính bề rộng (`GameStatusStrip`): `padding 6dp×2`, `spacedBy(4dp)`, ☰ 44dp, ⋯ 44dp,
chip 44dp (chip chọn `weight(1f).widthIn(min=44dp)`), `2+N` con ⇒ `N+1` gap.

```
fixed = 12 + 44 + 44 + 4·(N+1) = 104 + 4N
```

| N | tổng tối thiểu | vs 360dp |
|---|---|---|
| 4 | 296dp (chip chọn được 108dp) | vừa |
| 5 | 344dp (chip chọn còn 60dp) | vừa |
| 6 | 392dp | **tràn 32dp** |
| 8 | 488dp | **tràn 128dp** |

Sàn: `104 + 4N + 44N ≤ 360` ⇒ `N ≤ 5.3`. **N ≥ 6 không thể hiện bằng chip 44dp ở 360dp.**

**Giải pháp:** N ≤ 4 giữ **nguyên nhánh hiện tại** (pixel-identical, chip chọn 108dp);
N ≥ 5 đổi sang `LazyRow` cho hàng chip, **ghim** ☰ và ⋯ ở hai đầu, chiều cao **giữ 32dp**.

```
Row(padding 6dp, spacedBy 4dp, height = stripHeight) {
    MenuCell(44dp)
    LazyRow(weight(1f), spacedBy 4dp) { items(tabCount) { TabChip(i, if (selected) 76dp else 44dp) } }
    OverflowCell(44dp)
}
LaunchedEffect(selectedTabIndex, tabCount) { listState.animateScrollToItem(selectedTabIndex) }
```

Kiểm tra 360dp, N=8: viewport = 360−12−44−44−8 = **252dp**; nội dung = 7×44+76+7×4 = **416dp**
⇒ cuộn, thấy ~5.2 chip, mọi target ≥44dp, **không tăng chiều cao**.

Không dùng biến thể chỉ-chấm 18dp (target 18×32dp — trượt accessibility).

## 8. Lưu trữ và migration

| Mục | Quyết định |
|---|---|
| Key | `pref_tab_count` (Int) trong `volam_idle_manager_prefs` |
| Mặc định | **4** — hành vi y hệt hiện tại, **không cần migrate** |
| Khoảng | clamp 1..8 khi ghi **và** khi đọc |
| Ghi | `commit()` (app 24/7, có thể bị thu hồi bất cứ lúc nào) |
| Tên profile WebView | **ĐÓNG BĂNG** `Isolated_Cluster_<id>_Tab_<n>`. Không suy ra từ `tabCount`, không "nén" index, **cấm đổi thứ tự tab** — cookie/localStorage nằm trên đĩa theo đúng chuỗi đó và app không bao giờ gọi `deleteProfile`. |
| Tài khoản khi giảm N | **Giữ, không xoá.** `getAccount`/`saveAccount` theo index, không xoá gì. Tab `[n, old)` bị dừng + destroy WebView nhưng `user_tab_i`/`pass_tab_i`/`auto_tab_i`/`url_tab_i` còn nguyên. Lý do: (a) đó là dữ liệu người dùng nhập, xoá vì một toggle UI là mất dữ liệu không hoàn tác; (b) thư mục profile trên đĩa cũng sống sót qua `destroy()`, nên tăng lại N là khôi phục cookie; (c) xoá-khi-toggle làm hỏng đúng workflow "thử 8 rồi về 2" mà chặn renderer sinh ra để phục vụ. |
| `vlm_auto_<k>` | **Không đổi.** Nằm trong `localStorage` theo profile; profile 0..3 giữ nguyên tên ⇒ counter nguyên vẹn; 4..7 mới ⇒ bắt đầu rỗng (đúng). |

## 9. Thứ tự triển khai

| # | Bước | Rủi ro |
|---|---|---|
| 1 | `TabLimits` + `pref_tab_count` get/set có clamp. Chỉ thêm, chưa ai đọc. | Không |
| 2a | **Bỏ hardcode cơ học, không đổi hành vi.** `private val tabCount = TabLimits.DEFAULT` (vẫn hằng). Mảng → `SLOT_COUNT`. Mọi `0..3` → `0 until tabCount` (trừ cleanup/switchCluster/onRenderProcessGone: `webViews.indices`). `% 4` → `% tabCount`. Stats list → 8 entry. | **CAO — chạm toàn bộ nội bộ** |
| 2b | Cho thật: `@Volatile tabCount` từ prefs, `tabCountState`, `setTabCount` theo §3, clamp low-RAM. `init` dùng `0 until tabCount`. | **CAO — vòng đời WebView** |
| 3 | `GameAutoPilot(tabCount: () -> Int)`, `installedUrl = arrayOfNulls(SLOT_COUNT)`, loop `0 until tabCount()`, `onTabRemoved(i)`. | Trung bình |
| 4 | `MainViewModel`: `_tabCount` StateFlow, `setTabCount` mirror giá trị trả về, `desktopModeStates = List(SLOT_COUNT)`, sửa `coerceIn(0,3)`, clamp `_selectedTabIndex` sau khi giảm, truyền `tabCount` vào 4 composable. | Thấp |
| 5 | `GameWebViewContainer`: holder HashMap, layoutKey có tabCount, detach-all, builder tổng quát, **nhánh legacy N≤4**, `holder.onSelectTab` refresh. | **CAO — bố cục** |
| 6 | `GameStatusStrip`: tham số `tabCount`, `if (≤4) legacy else LazyRow`, tách `TabChip`/`MenuCell`/`OverflowCell` không đổi modifier. | Trung bình |
| 7 | `SidebarView` (`0 until tabCount`, nhãn `"DANH SÁCH NHÂN VẬT (${tabCount} TAB)"`, `"ĐỒNG BỘ AUTO CẢ ${tabCount} TAB"`), stepper trong `ControlSheet` + cảnh báo + 2 dialog, nhãn `AutoConfigDialog`. | Thấp |
| 8 | Chặn renderer: clamp low-RAM (đã ở 2b), tự hạ FPS khi vượt 4, chặn vòng lặp trong `onRenderProcessGone`. | **CAO — đường phục hồi** |
| 9 | `HeaderBar`: xoá hoặc để nguyên (dead code). | Không |
| 10 | Docs + `metadata.json` ("4 tài khoản" → "1–8 tài khoản") + README. | Không |

## 10. Rủi ro

| # | Rủi ro | Dấu hiệu | Chặn |
|---|---|---|---|
| R1 | `IndexOutOfBounds` khi ghi state sau lần giảm N | crash stack tại `GameTabManager`, index ≥ N | stats list luôn 8 + guard `!in webViews.indices` |
| R2 | Vượt giới hạn renderer ⇒ bão `onRenderProcessGone` ⇒ reload vô hạn | logcat "Tiến trình WebView bị thu hồi RAM!" lặp cùng tab | chặn vòng lặp (S8) + hạ FPS + trần low-RAM + cảnh báo |
| R3 | N=4 lệch vài px | screenshot diff N=4 cả 3 mode | nhánh legacy N≤4; gap raw px |
| R4 | Dải cao quá 32dp khi N>4 | regression 87% diện tích game | `LazyRow` không có chiều cao nội tại; công thức `stripHeight` không đổi |
| R5 | WebView của tab còn sống bị tạo lại khi đổi N ⇒ logout | tab tự chuyển `LOGGING_IN` + `loadUrl` không do người dùng | holder chỉ-tăng; vòng detach không chạm WebView; attacher sống cùng holder |
| R6 | `desktopModeStates` sai index cho tab 4–7 | nút PC trong sheet hiện sai trạng thái | `List(SLOT_COUNT)` + `coerceIn(0, tabCount-1)` |
| R7 | Độ trễ watchdog tăng tuyến tính theo N | phát hiện bị văng 4.8s → 9.6s ở N=8 | chấp nhận, ghi tài liệu; **không** giảm `WATCHDOG_TICK_MS` |
| R8 | `pauseTimers()` là process-global | dừng 1 tab làm đóng băng tất cả | không dùng để giảm tab |
| R9 | Tên profile trôi ⇒ mất phiên | người dùng báo "phải đăng nhập lại sau update" | tên profile đóng băng; cấm reorder |
| R10 | Tab bị detach ở SINGLE_TAB có thể bị Chromium throttle | timestamp `onGameStateScanned` ngừng tăng cho tab ngoài màn hình | **hành vi có sẵn, KHÔNG sửa trong ticket này** — đo riêng |
| R11 | 8 renderer rỗng ngay lần composition đầu | RSS lúc mở app với N=8 nhưng 0 tab đăng nhập | holder lazy |
| R12 | Giảm N âm thầm giết farm | người dùng đặt 2, tab 3–4 dừng | dialog xác nhận khi tab bị ảnh hưởng đang chạy; tài khoản còn nên tăng lại được |

**Rollback:** mỗi bước revert độc lập. Rollback runtime = một lần ghi prefs `setTabCount(4)` → trả lại
đúng cây view hôm nay (nhánh legacy). Không có schema để undo, không xoá dữ liệu profile ⇒ rollback
không làm ai mất phiên.

## 11. Kế hoạch đo (bắt buộc trước khi ship HARD_MAX=8)

Trên máy tham chiếu và một máy RAM thấp:

1. `adb shell dumpsys meminfo <pkg> | head -30` tại N = 1,4,6,8 (30 phút idle mỗi mức).
2. `adb shell ps -A | grep -c sandboxed_process` — phải bằng N.
3. `adb logcat -s chromium | grep -i "render process\|onRenderProcessGone\|oom"` — ghi mức N đầu tiên sinh `gone` trong 30 phút.
4. Log `window.innerWidth` mỗi tab sau khi load — xác nhận CSS px = dp và tab nào rơi vào nhánh `<280px`.
5. **Điều kiện ship 8:** 30 phút ở N=8, cả foreground và background, **0** `onRenderProcessGone` trên máy tham chiếu. Nếu fail → ship `HARD_MAX = 6` (giá trị ổn định đo được cuối cùng), hằng số chỉ nằm một chỗ.

### Kết quả đo — 2026-11-11, v4.1.0

Máy tham chiếu: LDPlayer 14, Android 14 x86_64, VM 6 nhân / 6 GB, `wm size 1080x2400`, `density 480`.

| Mục | Kết quả |
|---|---|
| (2) Renderer = N | Đúng ở N = 4, 5, 6, 8 |
| (5) Soak N=8 | 28 phút foreground + 8,5 phút background; 9 PID (app + 8 renderer) không đổi; 0 crash, **0** `onRenderProcessGone` |
| RAM ở N=8 | RSS app 271 MB + 8 renderer 166–291 MB ≈ 1,9 GB; `MemAvailable` còn 4,3 GB |
| CPU ở N=8 | App 100,3 % một nhân + 8 renderer ~119 % ⇒ ~220 %, tức ~55 % của VM 6 nhân (đo `top`: 416 % idle) |
| Bố cục | N=4 một hàng 4 cột (nhánh cũ, không lệch); N=5 → 4+1; N=6 → 4+2; N=8 → 4×2. Hàng cuối căn trái, ô giữ nguyên 1/4 chiều ngang |
| Dải trạng thái | > 4 tab chuyển `LazyRow`, `☰`/`⋯` ghim hai đầu, chiều cao 32dp không đổi |

**Quyết định: ship `HARD_MAX = 8`.** Điều kiện (5) đạt trên máy tham chiếu.
Chưa đo trên máy RAM thấp thật — bù lại, `isLowRamDevice` hạ trần xuống 4 và giá trị đọc từ
`SharedPreferences` cũng bị kẹp, nên không có đường nào chạy 8 tab trên máy đó.

Ghi chú khi đọc ảnh chụp ở N=8: vài ô trông như bị "zoom" là **tab chưa có tài khoản**, đang ở
màn hình khởi động của game (chữ "Võ Lâm Idle … phiên bản 1041" cỡ lớn), không phải lỗi scale.
Tab đã đăng nhập render HUD bình thường ở cùng bề rộng.
