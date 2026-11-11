package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CharacterStats
import com.example.model.TabLifecycleState
import com.example.model.TabLimits
import com.example.model.ViewLayoutMode
import com.example.webview.GameTabManager

/**
 * Ô hiển thị của một tab. `onSelectTab` là lambda TƯƠI: composition gán lại mỗi lượt
 * `update`, nên ô không giữ tham chiếu tới callback của lần composition đầu.
 */
class TabCardViewHolder(
    val root: LinearLayout,
    val idleOverlay: LinearLayout,
    val idleOpenBtn: Button,
    val idleTitle: TextView,
    var onSelectTab: (Int) -> Unit
)

@Composable
fun GameWebViewContainer(
    tabManager: GameTabManager,
    tabCount: Int,
    characterStatsList: List<CharacterStats>,
    selectedTabIndex: Int,
    layoutMode: ViewLayoutMode,
    isDownloading: Boolean,
    onSelectTab: (Int) -> Unit,
    onOpenControlSheet: (Int) -> Unit = {},
    onOpenSidebar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // THUỐC ĐẶC TRỊ SỐ 2: Cấp Window Token thực tế (Activity) cho WebView
    // để Android OS cho phép tạo cửa sổ Popup <select> thay vì bị crash/đóng lập tức.
    tabManager.updateActivityContext(context)

    // =========================================================================
    // CACHE HOLDER CHỈ-TĂNG, TẠO LAZY, KHÔNG BAO GIỜ BỎ (docs/DESIGN-TAB-COUNT.md §5)
    //
    // Vì sao không tạo lại holder khi đổi N: createTabCardViewHolder gọi getOrCreateWebView
    // và registerSlot. Tạo lại sẽ detach/attach lại WebView ĐANG SỐNG và ghi đè attacher
    // (đẻ ra hai contentContainer trong cây) ⇒ tab mất DOM, phải đăng nhập lại.
    //
    // Vì sao chỉ-tăng là an toàn: holder của index i đăng ký đúng một attacher cho i,
    // attacher sống bằng holder, nên khi renderer chết và getOrCreateWebView tạo lại thì
    // vẫn có slot để gắn. Bỏ holder = phá phục hồi renderer của index đó.
    // Holder rỗi chỉ là một LinearLayout không có WebView con.
    //
    // Vì sao lazy: tạo trước 8 holder là tạo 8 renderer ngay lần composition đầu,
    // ~2.4 GB cho người dùng chọn 1 tab.
    // =========================================================================
    val holders = remember { HashMap<Int, TabCardViewHolder>() }

    fun holderFor(index: Int): TabCardViewHolder = holders.getOrPut(index) {
        createTabCardViewHolder(context, index, tabManager)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
            .testTag("webview_game_container")
    ) {
        AndroidView(
            factory = { ctx ->
                LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.parseColor("#090D14"))
                }
            },
            update = { rootLayout ->
                val safeCount = tabCount.coerceIn(0, TabLimits.SLOT_COUNT)
                val keySelected = if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
                    selectedTabIndex.coerceIn(0, (safeCount - 1).coerceAtLeast(0))
                } else {
                    -1
                }
                val currentLayoutKey = "${layoutMode.name}:${safeCount}:${keySelected}"
                val lastLayoutKey = rootLayout.tag as? String

                // Holder nào được ĐẶT trên màn hình ở chế độ hiện tại. SINGLE_TAB chỉ cần
                // một ô: materialise cả N là dựng N khung view vô ích.
                val visibleIndices: List<Int> = if (layoutMode == ViewLayoutMode.SINGLE_TAB) {
                    if (safeCount == 0) emptyList() else listOf(keySelected)
                } else {
                    (0 until safeCount).toList()
                }

                // Lambda tươi: gán lại MỖI lượt update (kể cả khi bố cục không đổi) từ tham số
                // hiện tại, để ô không giữ tham chiếu tới callback của lần composition cũ.
                for (index in visibleIndices) {
                    holderFor(index).onSelectTab = onSelectTab
                }

                if (lastLayoutKey != currentLayoutKey || rootLayout.childCount == 0) {
                    rootLayout.tag = currentLayoutKey

                    // Detach MỌI holder đã cache trước khi dựng lại — kể cả holder không còn
                    // trong layout mới. Đây là fix cho "giảm N còn ô cũ trên màn hình".
                    for (holder in holders.values) {
                        (holder.root.parent as? ViewGroup)?.removeView(holder.root)
                    }
                    rootLayout.removeAllViews()

                    // Bố cục do TabLayoutPlan quyết định (unit test kiểm được): N ≤ 4 ở chế độ
                    // 4 Dọc BẮT BUỘC đi nhánh legacy, không đi builder tổng quát.
                    val plan = TabLayoutPlan.planFor(layoutMode, safeCount)

                    when (layoutMode) {
                        ViewLayoutMode.FOUR_COLUMNS -> {
                            if (plan.useLegacyRow) {
                                // ===== NHÁNH LEGACY — NGUYÊN VĂN, PIXEL-IDENTICAL =====
                                // N ≤ 4 ở chế độ 4 Dọc: 1 hàng × N cột, weightSum = N,
                                // leftMargin = 3 RAW PX (không phải 3.dp).
                                // Ở 360dp/density 3: (360 − 3)/4 = 89.25dp mỗi ô — đúng
                                // giá trị hôm nay.
                                rootLayout.orientation = LinearLayout.HORIZONTAL
                                rootLayout.weightSum = safeCount.toFloat()

                                for (i in 0 until safeCount) {
                                    val holder = holderFor(i)
                                    val params = LinearLayout.LayoutParams(
                                        0,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        1f
                                    ).apply {
                                        if (i > 0) leftMargin = TabLayoutPlan.GAP_PX
                                    }
                                    holder.root.layoutParams = params
                                    rootLayout.addView(holder.root)
                                }
                            } else {
                                buildGrid(rootLayout, safeCount, plan.cols, plan.rows) { index ->
                                    holderFor(index)
                                }
                            }
                        }

                        ViewLayoutMode.TWO_BY_TWO -> {
                            // N == 4: builder tổng quát tái tạo đúng cây 2×2 hôm nay
                            // (root VERTICAL weightSum=2, hai hàng HORIZONTAL weightSum=2,
                            //  hàng 2 topMargin=3, cột 2 leftMargin=3).
                            buildGrid(rootLayout, safeCount, plan.cols, plan.rows) { index ->
                                holderFor(index)
                            }
                        }

                        ViewLayoutMode.SINGLE_TAB -> {
                            // Chế độ 3: 1 Tab (Toàn màn hình) - Hiển thị duy nhất tab được chọn
                            rootLayout.orientation = LinearLayout.VERTICAL
                            rootLayout.weightSum = 1f
                            if (safeCount > 0) {
                                val holder = holderFor(keySelected)
                                val params = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1f
                                )
                                holder.root.layoutParams = params
                                rootLayout.addView(holder.root)
                            }
                        }
                    }

                    // Căn chỉnh lại Viewport mà không reload trang (chỉ gọi khi đổi layout)
                    tabManager.adjustAllViewports()
                }

                // Cập nhật trạng thái hiển thị của từng ô và Idle Overlay trực tiếp tại chỗ
                // (không remove/add View). Mini-header 28dp đã bỏ: trạng thái tab do dải 32dp báo.
                for (i in visibleIndices) {
                    val holder = holderFor(i)
                    val stats = characterStatsList.getOrElse(i) { CharacterStats(tabIndex = i) }

                    if (stats.state == TabLifecycleState.IDLE || stats.state == TabLifecycleState.OFFLINE) {
                        if (holder.idleOverlay.visibility != View.VISIBLE) {
                            holder.idleOverlay.visibility = View.VISIBLE
                        }
                        val desiredIdleText = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) {
                            "Tab ${i + 1}\nChờ mở"
                        } else {
                            "Tab ${i + 1}: Chờ mở tab (Idle)"
                        }
                        if (holder.idleTitle.text != desiredIdleText) {
                            holder.idleTitle.text = desiredIdleText
                        }
                        val desiredBtnText = if (layoutMode == ViewLayoutMode.FOUR_COLUMNS) {
                            "▶ Mở"
                        } else {
                            "▶ MỞ TAB ${i + 1}"
                        }
                        if (holder.idleOpenBtn.text != desiredBtnText) {
                            holder.idleOpenBtn.text = desiredBtnText
                        }
                    } else {
                        if (holder.idleOverlay.visibility != View.GONE) {
                            holder.idleOverlay.visibility = View.GONE
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Dải trạng thái 32dp: sibling của AndroidView, KHÔNG phải overlay.
        // Là view Compose riêng nằm dưới WebView trong Column nên không bao giờ đè lên
        // vùng chạm của game; WebView giữ đúng bề rộng và nhận trọn phần còn lại.
        GameStatusStrip(
            tabManager = tabManager,
            tabCount = tabCount,
            characterStatsList = characterStatsList,
            selectedTabIndex = selectedTabIndex,
            isDownloading = isDownloading,
            onSelectTab = onSelectTab,
            onOpenSidebar = onOpenSidebar,
            onOpenControlSheet = onOpenControlSheet
        )
    }
}

/**
 * Builder tổng quát cho rows ≥ 2 (và cho 2x2).
 *
 * Bất biến: `weightSum` luôn = `cols`, KHÔNG BAO GIỜ = số con thực tế. Hàng cuối thiếu thì
 * để trống bên phải, không kéo giãn — nếu kéo giãn, ô đó có bề rộng CSS khác anh em, rơi
 * vào nhánh media-query khác và auto-login CSS lệch.
 *
 * Gap giữ RAW PIXEL (leftMargin/topMargin = 3), không đổi sang 3.dp: đổi sẽ làm N=4 từ
 * 89.25dp xuống 87dp ở density 3.
 */
private fun buildGrid(
    root: LinearLayout,
    count: Int,
    cols: Int,
    rows: Int,
    holderFor: (Int) -> TabCardViewHolder
) {
    root.orientation = LinearLayout.VERTICAL
    root.weightSum = rows.toFloat()

    for (r in 0 until rows) {
        val row = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = cols.toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                if (r > 0) topMargin = TabLayoutPlan.GAP_PX
            }
        }

        val start = r * cols
        val end = minOf(count - 1, (r + 1) * cols - 1)
        for (i in start..end) {
            val holder = holderFor(i)
            val columnInRow = i - start
            val params = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                if (columnInRow > 0) leftMargin = TabLayoutPlan.GAP_PX
            }
            holder.root.layoutParams = params
            row.addView(holder.root)
        }

        root.addView(row)
    }
}

private fun createTabCardViewHolder(
    context: Context,
    tabIndex: Int,
    tabManager: GameTabManager
): TabCardViewHolder {
    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(AndroidColor.parseColor("#0F172A"))
    }

    val density = context.resources.displayMetrics.density

    // Khung chứa WebView + Idle Overlay
    val contentContainer = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        setBackgroundColor(AndroidColor.parseColor("#090D14"))
    }

    // Gắn WebView độc lập của Tab trực tiếp vào khung chứa.
    // (Đã bỏ lớp wrapper chặn onMeasure: View.measure() vốn đã bỏ qua khi spec không đổi,
    //  còn wrapper thì có thể đóng băng một kích thước sai sau khi đổi layout.)
    val webView = tabManager.getOrCreateWebView(tabIndex)
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    contentContainer.addView(webView)

    // Ô hiển thị này chỉ được tạo 1 lần (cache chỉ-tăng), nên khi GameTabManager phải tạo lại
    // WebView (bị thu hồi RAM / đổi Cụm) UI phải tự gắn WebView mới vào đúng ô.
    // Chèn ở index 0 để không đè lên idleOverlay đang nằm phía trên.
    var attachedWebView: WebView = webView
    tabManager.registerSlot(tabIndex) { newWebView ->
        if (newWebView !== attachedWebView) {
            (attachedWebView.parent as? ViewGroup)?.removeView(attachedWebView)
            attachedWebView = newWebView
        }
        (newWebView.parent as? ViewGroup)?.removeView(newWebView)
        contentContainer.addView(
            newWebView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    // GIAI ĐOẠN 1: Màn hình chờ (Idle Overlay) có nút [▶ Mở Tab X]
    val idleOverlay = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val pad = (4 * density).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(AndroidColor.parseColor("#0B0F19"))
    }

    val idleTitle = TextView(context).apply {
        gravity = Gravity.CENTER
        text = "Tab ${tabIndex + 1}: Chờ mở tab (Idle)"
        setTextColor(AndroidColor.parseColor("#94A3B8"))
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    idleOverlay.addView(idleTitle)

    // Holder chưa tồn tại lúc dựng nút: giữ tham chiếu nullable rồi gán trước khi return.
    var holderRef: TabCardViewHolder? = null

    val idleOpenBtn = Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (32 * density).toInt()
        ).apply {
            topMargin = (8 * density).toInt()
        }
        val btnPadH = (8 * density).toInt()
        setPadding(btnPadH, 0, btnPadH, 0)
        minWidth = (48 * density).toInt()
        minimumWidth = (48 * density).toInt()
        text = "▶ MỞ TAB ${tabIndex + 1}"
        textSize = 9.5f
        isSingleLine = true
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AndroidColor.WHITE)
        setBackgroundColor(AndroidColor.parseColor("#15803D"))
        setOnClickListener {
            holderRef?.onSelectTab?.invoke(tabIndex)
            tabManager.openSingleTab(tabIndex)
        }
    }
    idleOverlay.addView(idleOpenBtn)

    contentContainer.addView(idleOverlay)
    card.addView(contentContainer)

    return TabCardViewHolder(
        root = card,
        idleOverlay = idleOverlay,
        idleOpenBtn = idleOpenBtn,
        idleTitle = idleTitle,
        onSelectTab = {}
    ).also { holderRef = it }
}
