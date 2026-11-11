# Thiết kế bố cục — tối đa diện tích game

Tài liệu này là spec triển khai cho việc thu gọn chrome (thanh chức năng) để màn hình game
rộng nhất. Mọi số đo bên dưới đã đối chiếu mã nguồn tại thời điểm viết.

## 1. Ngân sách chiều cao

Điện thoại tham chiếu 360×800dp, status bar 24dp, nav 3 nút 48dp ⇒ vùng dùng được
`800 − 24 − 48 = 728dp`.

### Portrait

| Chế độ | Chrome trước | Chrome sau | Game trước | Game sau | Δ | % cửa sổ |
|---|---|---|---|---|---|---|
| **1 Tab** | `44 + 36 + 28 = 108dp` | **32dp** | 620dp | **696dp** | **+76dp (+12.3%)** | 77.5% → **87.0%** |
| **4 Dọc** | `44 + 28 = 72dp` | **32dp** | 656dp | **696dp** | +40dp (+6.1%) | 82.0% → **87.0%** |
| **Lưới 2x2** | `44 + 28 = 72dp` | **32dp** | 656dp | **696dp** | +40dp (+6.1%) | 82.0% → **87.0%** |

### Landscape (800×360dp, dùng được 288dp)

| Chế độ | Chrome trước | Chrome sau | Game trước | Game sau | Δ |
|---|---|---|---|---|---|
| 1 Tab | 108dp | 32dp | 180dp | **256dp** | +76dp (+42.2%) |
| 4 Dọc | 72dp | 32dp | 216dp | **256dp** | +40dp (+18.5%) |
| 2x2 | 72dp | 32dp | 216dp | **256dp** | +40dp (+18.5%) |

Nguồn số đo: `HeaderBar.kt:75` (44dp), `GameWebViewContainer.kt:96` (36dp),
`GameWebViewContainer.kt:353` (`28 * density`).

## 2. Kiến trúc

Ba thanh xếp chồng gộp thành **một dải 32dp ở đáy, là sibling trong layout — KHÔNG phải overlay**.

```
Column (fillMaxSize, bg #090D14, insets safeDrawing Top + Horizontal)
├── [AndroidView weight(1f)]      ← 4-tab LinearLayout, giữ nguyên
└── GameStatusStrip 32dp + bottom inset   ← MỚI, luôn hiện
```

Mọi thứ không vừa 32dp chuyển vào **một bottom sheet** (`ControlSheet`), mở bằng ô `⋯`.
Sheet là overlay, chỉ mở khi người dùng gọi.

**Vì sao dải phải là sibling, không phải overlay:** `AndroidView` là `View` con thật;
`WebView` nhận `dispatchTouchEvent` trước lớp vẽ Compose, nên overlay Compose không đảm bảo
thắng touch. Là sibling nghĩa là dải không bao giờ đè lên WebView ⇒ không thể ăn mất một
cú chạm nào của game. Đây là ràng buộc an toàn, không phải thẩm mỹ.

### Portrait + 1 Tab

```
y=0    ┌────────────────────────────────────┐
       │ status bar                   24dp  │  tô #090D14
y=24   ├────────────────────────────────────┤  ← 0dp chrome
       │  WebView  (game HUD bắt đầu ở đây) │  696dp
y=720  ├────────────────────────────────────┤
       │ ☰ │T1│ T2: Ngọc Diệp  │T3│T4│ ⋯   │  32dp — luôn hiện
       ├────────────────────────────────────┤
       │ nav bar                      48dp  │  tô #0F172A
y=800  └────────────────────────────────────┘
```

### Portrait + 4 Dọc

Bốn cột, **không còn header từng cột**; dải ở đáy là nơi duy nhất báo trạng thái 4 tab.

### Landscape + 4 Dọc (drawer docked)

Vẫn dải đáy, span theo bề rộng container (`800 − 265 = 535dp`), đủ chỗ `44×5 + 4×44`.

## 3. Bản đồ di chuyển điều khiển

Số tap tính từ trạng thái mặc định: portrait, 1 Tab, drawer đóng, sheet đóng.

| Điều khiển | Trước | Nhà mới | Sau | Δ |
|---|---|---|---|---|
| `☰` mở drawer | 1 | Ô 1 của dải (44dp) | 1 | 0 |
| Tiêu đề app | – | Bỏ khỏi chrome (nằm trong drawer) | – | 0 |
| Traffic MB + ↓ | 0 (liếc) | Dòng network trong sheet; `⋯` đổi màu `#34D399` khi có tải | 2 | +2 |
| Reset traffic | 1 | Sheet, target 36dp | 2 | +1 |
| FPS 30↔60 | 1 | Sheet hàng toggle | 2 | +1 |
| `🌙 Treo máy` | 1 | Sheet hàng toggle | 2 | +1 |
| Chọn bố cục | 2 (menu lồng) | Sheet hàng segment (3 nút) | 2 | **−2 cho chọn trực tiếp** |
| `▼` dropdown bố cục | 1 | **Xoá** — segment thay thế | – | – |
| Chip T1–T4 (header) | 1 | Chip trong dải | 1 | 0 |
| Chip T1–T4 (hàng riêng) | 1 | **Xoá** — gộp vào dải | 1 | 0 |
| Tab đang chọn | – | Chip selected: fill `#1D4ED8`, border `#38BDF8` | 0 | 0 |
| Chấm trạng thái ×4 | – | 7dp trên mỗi chip, thấy cả 4 mọi chế độ | 0 | 0 |
| `▶ Mở` | 1 | Sheet hàng per-tab **+ giữ nguyên nút trong ô idle** | 1–2 | 0…+1 |
| `PC` (desktop UA) | 1 | Sheet hàng per-tab, fill `#9333EA` khi ON | 2 | +1 |
| `↻` reload | 1 | Sheet hàng per-tab | 2 | +1 |
| `⏹` stop | 1 | Sheet hàng per-tab | 2 | +1 |
| Idle overlay `▶ MỞ TAB N` | 1 | **Giữ nguyên tại chỗ** | 1 | 0 |
| Drawer (16 control) | 2 | **Giữ nguyên** | 2 | 0 |
| OLED unlock | 1 gesture | **Giữ nguyên** | 1 | 0 |

Không mất chức năng nào. 7 control tần suất thấp tăng 1 tap; đổi lại 4 control nặng
(`PC`/`↻`/`⏹`) từ nút 22dp trong header cuộn ngang 90dp → nút 44dp trong sheet đáy.

## 4. Vòng đời sheet

| Sự kiện | Quy định |
|---|---|
| Mở | Tap `⋯` (44dp), hoặc tap chip đang chọn |
| Scrim | `#000000` @ `0.65f`, tap để đóng |
| Vào | slide-up 180ms `FastOutSlowInEasing`, scrim fade 150ms |
| Ra | slide-down 140ms `AccelerateEasing` |
| Timeout | **Không có.** Timeout sẽ đóng ngay dưới ngón tay người dùng |
| Đóng | Tap scrim, `✕`, Back, kéo xuống >40dp |
| Dưới OLED | Render **trước** `OledBlackScreen` để sleep che lên; mở khoá quay lại sheet |

## 5. Ràng buộc touch guard (quan trọng nhất)

`lastTouchTimeMs` chỉ được cập nhật khi chạm **vào WebView** (`GameTabManager.kt:44-49`).
Tap lên chrome không cập nhật ⇒ vòng 850ms và watchdog vẫn bắn click tổng hợp trong lúc
người dùng đang thao tác → đúng lớp lỗi đã gây treo khung/dropdown ở 4.0.0.

**Bắt buộc thêm vào `GameTabManager`:**

```kotlin
fun markUiInteraction() {
    val now = System.currentTimeMillis()
    for (i in 0..3) webViews[i]?.lastTouchTimeMs = now
}
```

Gọi ở: mọi tap lên dải, mở sheet, mọi nút trong sheet, đóng sheet, mở drawer.
Không thêm timer mới — dùng lại đúng cơ chế guard đang có.

## 6. Dải luôn hiện: 32dp

Nội dung tối thiểu phải nói: *có mấy tab, đang xem tab nào, tab nào còn sống*.
= 1 chấm 7dp + 1 nhãn 9.5sp một dòng, ×4, + 2 ô icon.

Cơ sở chọn 32dp:
- Sàn nội dung: 1 dòng text ~13dp + 3dp padding trên/dưới = 19dp.
- Sàn touch: chip là target thứ cấp, tối thiểu 32dp.
- Đồng bộ: nằm giữa 28dp (mini-header) và 36dp (hàng chip) mà app đang có.
- Chiều cao hiệu dụng = `32 + navInset` = 80dp (nav 3 nút) hoặc 56dp (gesture) ⇒ vượt 44dp.

Font scale: `stripHeight = 32.dp + (min(fontScale, 2f) − 1f) * 12.dp` → 32/38/44dp.

## 7. Một tay

Vùng ngón cái thoải mái = 1/3 dưới (`y ≥ 533dp`). Sau redesign: **mọi control luôn hiện và
mọi hành động per-tab đều nằm trong 1/3 dưới**. Trước đây `☰`, FPS, sleep, đổi bố cục và
4 chip đều ở `y = 24–68`.

Ngoài phạm vi nhưng nên sửa: drawer mở mặc định (`MainViewModel.kt:40` = `true`) che 89%
màn hình 360dp ngay lần đầu. Đổi thành `false` ở portrait.

## 8. Thông số visual

Toàn bộ màu đã có trong code. **Không tạo palette mới.** `Color.kt` chỉ chứa màu template chết.

### Dải

| Thuộc tính | Giá trị | Token nguồn |
|---|---|---|
| Cao | `32.dp` + bottom inset | §6 |
| Nền | `#0F172A` (tô luôn dải nav bar) | `HeaderBar.kt:77` |
| Viền trên | `1.dp #1E293B` | `HeaderBar.kt:78` |
| Padding ngang | `6.dp` | `GameWebViewContainer.kt:104` |
| Khoảng cách cell | `4.dp` | – |
| Cell cố định | `44.dp` rộng: `☰`, T1, T3, T4, `⋯` | target |
| Cell co giãn | chip selected `weight(1f)`, min `44.dp`; ở 360dp nhận `112.dp` | – |
| Bo chip | `RoundedCornerShape(4.dp)` | `GameWebViewContainer.kt:117` |
| Chip thường | fill `#1E293B`, border `1.dp #334155`, chữ `#94A3B8` | `GameWebViewContainer.kt:118-119` |
| Chip chọn | fill `#1D4ED8`, border `1.dp #38BDF8`, chữ `#FFFFFF` | `SidebarView.kt:266` |
| Chấm trạng thái | `7.dp` | `GameWebViewContainer.kt:129` |
| Màu chấm | IN_GAME `#10B981`, LOGGING_IN `#F59E0B` (vẽ **vòng** 2dp stroke), IDLE `#64748B`, OFFLINE `#EF4444` | `GameWebViewContainer.kt:295-297`, `SidebarView.kt:449` |
| Nhãn | `9.5.sp`, Bold khi chọn, `maxLines = 1`, ellipsis | `HeaderBar.kt:183` |
| Icon | `☰` 18dp, `⋯` 18dp, tint `#38BDF8`; `⋯` thành `#34D399` khi `downloadSpeed > 0` | `HeaderBar.kt:107, 181` |

**Sửa lỗi phát hiện khi thiết kế:** mini-header vẽ OFFLINE bằng xám `#64748B`
(`GameWebViewContainer.kt:297`) còn drawer vẽ cùng trạng thái bằng đỏ `#EF4444`
(`SidebarView.kt:449`). Dải dùng mapping 4 trạng thái của drawer.

### Sheet

| Thuộc tính | Giá trị |
|---|---|
| Neo | `Alignment.BottomCenter`, `fillMaxWidth` |
| Nền | `#0F172A`; bo trên `6.dp`; viền trên `1.dp #1E293B` |
| Padding trong | `10.dp` ngang, `8.dp` trên, `8.dp` + nav inset dưới |
| Scrim | `#000000` @ `0.65f` |
| Cao tối đa | `0.46 × usableHeight`, bọc `verticalScroll` |
| Grabber | `24.dp × 3.dp`, `#334155` |
| Cao hàng | header 32, per-tab 44, view-mode 40, toggles 40, network 36; cách `8.dp` |
| Bo nút | `6.dp`; icon `16.dp`; nhãn `10.5.sp` bold |

Màu nút (đều có sẵn): `▶ Mở` `#15803D`; `PC` off `#4B5563` / on `#9333EA`; `↻` `#3B82F6`;
`⏹` `#B91C1C`; segment chọn `#1D4ED8` + border `#38BDF8`; FPS 30 `#78350F`/`#D97706`/`#FDE68A`;
`🌙` `#312E81`/`#6366F1`/`#E0E7FF`; network `#022C22`/`#065F46`/`#34D399`.

### testTag (snake_case theo convention hiện có)

`game_status_strip`, `strip_sidebar_button`, `strip_tab_chip_0..3`, `strip_overflow_button`,
`control_sheet`, `sheet_close_button`, `sheet_open_tab`, `sheet_pc_toggle`, `sheet_reload_tab`,
`sheet_stop_tab`, `sheet_view_four`, `sheet_view_grid`, `sheet_view_single`, `sheet_fps_toggle`,
`sheet_sleep_button`, `sheet_sidebar_button`, `sheet_network_reset`.

## 9. Accessibility

### Contrast (đã tính, sRGB relative luminance)

| Cặp | Tỉ lệ | Kết luận |
|---|---|---|
| `#94A3B8` trên `#1E293B` | 5.67:1 | AA pass |
| `#FFFFFF` trên `#1D4ED8` | 6.74:1 | AA pass |
| `#FFFFFF` trên `#0284C7` (style hiện tại) | **4.09:1** | **trượt AA ở 9.5sp** → đổi sang `#1D4ED8` |
| `#10B981` trên `#0F172A` | 6.94:1 | pass |
| `#F59E0B` trên `#0F172A` | 8.28:1 | pass |
| `#EF4444` trên `#0F172A` | 4.70:1 | pass |
| `#64748B` trên `#0F172A` | 3.74:1 | pass (biên mỏng) |

⇒ **Nền dải phải giữ `#0F172A`**. Tối hơn nữa thì chấm xám tụt dưới 3:1.

### Trạng thái không chỉ bằng màu

Thêm hình dạng ở 0dp chi phí: `LOGGING_IN` = vòng 7dp stroke 2dp, ruột trong suốt;
`IN_GAME` = chấm đặc; `IDLE` = chấm đặc alpha 55%; `OFFLINE` = chấm đặc có khấc 1dp.

### Font scale

Nhãn 9.5–10.5sp. Ở `fontScale = 2.0` sẽ tràn dải 32dp. Quy định:
- Cao dải theo công thức §6.
- Nhãn dải **clamp**: `LocalDensity provides Density(density, fontScale = min(fontScale, 1.3f))`
  bọc riêng dải. Lý do: đây là HUD trạng thái, không phải bề mặt đọc; tăng vô hạn sẽ ăn
  vào viewport game — đúng thứ đang muốn tối đa.
- Nhãn sheet **không clamp** — sheet cuộn được.

### Semantics

Mỗi chip: `selectable(selected = i == selectedTabIndex, role = Role.Tab)`,
`contentDescription = "Tab ${i+1}"`, `stateDescription` = `"đang chơi"`/`"đang vào game"`/
`"chờ mở"`/`"đã dừng"` + tên nhân vật nếu có. `⋯`: `"Mở bảng điều khiển nhanh"`.
Nút icon-only cần contentDescription: `↻` = `"Tải lại tab"`, `⏹` = `"Dừng tab"`.

### Target được sửa

Trước: nút mini-header 22dp, reset 13dp, chip header padding 5dp/3dp, nút sidebar 30dp.
Sau: không gì dưới 36dp; cell chính của dải 44dp rộng, cao hiệu dụng 56–80dp.

## 10. KHÔNG được đổi

1. **Nội bộ `GameTabManager`** — thứ tự tạo WebView vs `WebViewCompat.setProfile`,
   renderer priority, `WebSettings`, `onRenderProcessGone`, retry runnable, interval
   watchdog/login/GC, `onAppBackgrounded/Foregrounded`, `toggleDesktopMode`.
   Chỉ được THÊM `markUiInteraction()`.
2. **`contentContainer` + `registerSlot` + `idleOverlay`** — WebView phải được add ở index 0
   và được attacher gắn lại sau khi renderer chết. **Không bọc WebView trong view override
   `onMeasure`** (comment tại `GameWebViewContainer.kt:485-487` ghi lại chính lỗi này).
3. **Bộ máy layout 4 tab** — orientation, `weightSum`, gap `3` px, `layoutKey`, một lần
   `adjustAllViewports()` khi đổi chế độ.
4. **Bề rộng cột** — 1 Tab `MATCH_PARENT`, 4 cột `weight 1f`, 2x2 hai hàng `weight 1f`.
   Đổi bề rộng là vượt ngưỡng `@media (max-width: 280px)` của game.
5. **`GameScriptInjector`** toàn bộ.
6. **Layout `SidebarView`** — 320dp/265dp, card 76dp, cột cuộn.
7. `OledBlackScreen`, `AccountDialog`, `AutoConfigDialog`, `KeepAliveService`,
   `PreferencesManager`, `TrafficMonitor`, shape state của `MainViewModel` (chỉ thêm).
8. `ui/theme/Color.kt`, `Theme.kt`.
9. `enableEdgeToEdge()` và `FLAG_KEEP_SCREEN_ON`.
10. **Toàn bộ chuỗi tiếng Việt và emoji, nguyên byte.**
11. `HeaderBar.kt` như một file — giữ compile được kể cả khi đã bỏ khỏi composition.

## 11. Thứ tự triển khai

Mỗi bước compile được và verify độc lập.

1. **Hook guard (không UI).** Thêm `markUiInteraction()`. Gắn vào header hiện tại
   (`onToggleSidebar`, `onSelectTab`) để chạy thử ngay.
2. **State sheet.** `MainViewModel`: `_isControlSheetOpen`, `openControlSheet(tabIndex)`,
   `dismissControlSheet()`. Chưa UI.
3. **Dựng sheet + đường vào tạm.** `ui/components/ControlSheet.kt` với 5 hàng. Render từ
   root `Box` của `MainScreen`, **trước** `OledBlackScreen`. Mở bằng long-press hàng chip
   SINGLE_TAB hiện có. Chưa xoá gì ⇒ so sánh được cũ/mới trên cùng màn hình.
4. **Nối hành động sheet.** Per-tab → `openSingleTab`/`toggleDesktopMode`/
   `getOrCreateWebView().reload()`/`stopSingleTab`; segment → `setLayoutMode`; FPS →
   `toggleFps`; sleep → `setSleepMode(true)`; drawer → `toggleSidebar`; reset →
   `resetNetworkCounters`. Gọi `markUiInteraction()` ở mọi lần bấm. Trạng thái `PC` phải
   đọc từ `tabManager`, **không** lưu trong màu nền nút như code cũ.
5. **Thêm dải.** `ui/components/GameStatusStrip.kt`, đặt dưới `AndroidView` trong `Column`
   của `GameWebViewContainer`. Giữ `HeaderBar` và hàng chip để đối chiếu.
6. **Xoá mini-header.** Bỏ scroll view + header layout + 4 nút; bỏ field khỏi
   `TabCardViewHolder`; bỏ dòng update tương ứng; **giữ nguyên khối idle overlay**.
   Verify renderer death vẫn gắn lại được.
7. **Xoá hai thanh Compose.** Bỏ hàng chip, bỏ `HeaderBar` khỏi `MainActivity`, chuyển
   tham số vào sheet. Verify cả 3 chế độ ở cả 2 hướng.
8. **Insets + reach + a11y.** `Scaffold(contentWindowInsets = WindowInsets(0))`, đẩy inset
   lên `Column`; selected chip `#1D4ED8`; vòng cho `LOGGING_IN`; semantics; công thức font
   scale; (tuỳ chọn) footer drawer; (tuỳ chọn) dim nhãn sau 15s.

## 12. Rủi ro

1. **Touch dispatch Compose trên `AndroidView`** — rủi ro cao nhất. App đã dùng đúng pattern
   này cho drawer portrait (`MainActivity.kt:165-195`) nên ít mới; nếu thiết bị nào scrim
   chết, fallback là host sheet như `View` sibling trong `contentContainer` (cơ chế
   `idleOverlay`, `GameWebViewContainer.kt:562`).
2. **Phục hồi renderer death** — nếu ai "đơn giản hoá" `contentContainer` thành container do
   Compose quản lý thì WebView không gắn lại được, tab trắng vĩnh viễn sau áp lực RAM.
3. **Layout churn trên canvas game** — mọi thay đổi kích thước WebView làm reflow HUD game và
   có thể đóng `<select>` đang mở. Vì vậy sheet phải là overlay, không phải panel đẩy xuống,
   và dải phải có mặt từ frame đầu (không animate xuất hiện làm đổi chiều cao lúc chạy).
4. **Ngưỡng bề rộng** — thiết kế này đổi **0** bề rộng. Verify `innerWidth` từng chế độ
   trước/sau; lệch >1px nghĩa là ai đó đã đổi weight/margin.
5. **Guard 5 giây là hành vi dễ vỡ nhất** — thiếu `markUiInteraction()` ở bất kỳ nút chrome
   nào là bug, không phải polish.
6. **Tăng tap là thật** — `PC`/`↻`/`⏹`/FPS/sleep/bố cục 1→2 tap; traffic 0→2 (bù bằng tint
   `⋯`). Nếu người dùng không chấp nhận, cách rẻ nhất là thêm hàng 32dp thứ hai chỉ ở
   portrait — nhưng lại tốn 32dp, phải quyết bằng đo lường.
