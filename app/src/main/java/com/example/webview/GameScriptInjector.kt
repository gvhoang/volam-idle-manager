package com.example.webview

import org.json.JSONObject

object GameScriptInjector {

    fun quote(str: String): String {
        return "\"" + str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b") + "\""
    }

    fun buildClearStorageScript(): String {
        return """
        try {
            localStorage.clear();
            sessionStorage.clear();
        } catch(e) {}
        """.trimIndent()
    }

    fun buildAdjustViewportScript(): String {
        return """
        try {
            if (!document.getElementById('__vl_mobile_opt_style') && document.head) {
                const st = document.createElement('style');
                st.id = '__vl_mobile_opt_style';
                st.textContent = `
                    * {
                        backdrop-filter: none !important;
                        -webkit-backdrop-filter: none !important;
                    }
                    input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                        min-width: 44px !important;
                    }
                    @media (max-width: 280px) {
                        input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                            min-width: 36px !important;
                            width: 100% !important;
                        }
                    }
                `;
                document.head.appendChild(st);
            }
            window.dispatchEvent(new Event('resize'));
            if (document.body) {
                document.body.style.width = '100%';
                document.body.style.height = '100%';
            }
        } catch(e) {}
        """.trimIndent()
    }

    // Auto-Login Tuần Tự (Chuẩn Giao Diện Phiên Bản 1031: Ấn "Bắt Đầu Trò Chơi" -> Khung "Đăng Nhập" -> Điền "Tài khoản:" & "Mật mã:" -> Tích "Nhớ tài khoản" -> Ấn "Xác Định")
    // Tối ưu đặc biệt cho cả chế độ 4 Cột Dọc (màn hình hẹp ~90dp/cột trên điện thoại dọc) lẫn Lưới 2x2 và Màn ngang.
    fun buildAutoLoginStepScript(
        tabIndex: Int,
        username: String,
        password: String
    ): String {
        val safeUserJson = quote(username)
        val safePassJson = quote(password)

        return """
        (function(username, password) {
            try {
                const tabIdx = $tabIndex;

                // Bơm CSS chống bóp nghẹt ô <input> khi ở chế độ 4 tab dọc trên màn hình dọc điện thoại (~90px/cột)
                // và tắt backdrop-filter blur giúp 4 WebView chạy mượt nhẹ trên GPU điện thoại
                try {
                    if (!document.getElementById('__vl_mobile_opt_style') && document.head) {
                        const st = document.createElement('style');
                        st.id = '__vl_mobile_opt_style';
                        st.textContent = `
                            * {
                                backdrop-filter: none !important;
                                -webkit-backdrop-filter: none !important;
                            }
                            input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                                min-width: 44px !important;
                            }
                            @media (max-width: 280px) {
                                input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                                    min-width: 36px !important;
                                    width: 100% !important;
                                }
                            }
                        `;
                        document.head.appendChild(st);
                    }
                } catch(e) {}

                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                // Kiểm tra phần tử có đang hiển thị hay không.
                // Lưu ý quan trọng:
                // 1. Tuyệt đối KHÔNG dùng regex /\bhidden\b/ trên className vì sẽ khớp nhầm với "overflow-hidden".
                // 2. Ở chế độ 4 tab dọc trên điện thoại dọc, bề ngang mỗi cột rất hẹp (~90dp), khiến thẻ <input>
                //    nằm cạnh nhãn "Tài khoản:" / "Mật mã:" trong dòng flex có thể bị ép rect.width về 0..2px.
                //    Do đó với INPUT / BUTTON / SELECT / TEXTAREA, chỉ cần phần tử và tổ tiên không bị display:none /
                //    visibility:hidden / opacity:0 và có offsetParent hoặc chiều cao > 0 là coi như đang hiển thị!
                function isVisible(el) {
                    if (!el || !el.isConnected) return false;
                    if ((el.type || '').toLowerCase() === 'hidden') return false;
                    let cur = el;
                    while (cur && cur !== document.body && cur !== document.documentElement) {
                        if (cur.hidden === true) return false;
                        try {
                            const style = window.getComputedStyle(cur);
                            if (style && (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || parseFloat(style.opacity) === 0)) {
                                return false;
                            }
                        } catch(e) {}
                        cur = cur.parentElement;
                    }
                    const tag = (el.tagName || '').toUpperCase();
                    const isFormOrBtn = (tag === 'INPUT' || tag === 'BUTTON' || tag === 'SELECT' || tag === 'TEXTAREA' || el.getAttribute('role') === 'button');
                    try {
                        const rect = el.getBoundingClientRect();
                        if (isFormOrBtn) {
                            if (rect.height > 0 || rect.width > 0 || el.offsetParent !== null) return true;
                            if (el.parentElement) {
                                const pRect = el.parentElement.getBoundingClientRect();
                                return pRect.height > 0 || pRect.width > 0;
                            }
                        }
                        return rect.width > 1 && rect.height > 1;
                    } catch(e) {
                        return el.offsetParent !== null;
                    }
                }

                // Click chuẩn xác 1 lần duy nhất trên phần tử nút bấm (tránh double-click làm đóng lại bảng vừa mở)
                function realClick(el) {
                    if (!el) return;
                    const target = el.closest('button, [role="button"], a, input[type="button"], input[type="submit"]') || el;
                    try {
                        target.disabled = false;
                        target.removeAttribute('disabled');
                        if (target.classList) target.classList.remove('disabled');
                    } catch(e) {}
                    try {
                        const rect = target.getBoundingClientRect();
                        const cx = rect.left + Math.max(2, rect.width / 2);
                        const cy = rect.top + Math.max(2, rect.height / 2);
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(type => {
                            try {
                                target.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof target.click === 'function') {
                        try { target.click(); } catch(e) {}
                    } else {
                        try {
                            target.dispatchEvent(new MouseEvent('click', {
                                view: window, bubbles: true, cancelable: true
                            }));
                        } catch(e) {}
                    }
                }

                // Hàm Điền Chữ "Ép Cứng" Chuẩn React 18 / Vue 3 / Native DOM
                // Kèm cơ chế khóa bàn phím ảo (inputmode="none") tạm thời để khi điền 4 tab trên điện thoại
                // không làm bật bàn phím ảo của Android gây giật khung hình hoặc mất focus.
                function forceFillReactInput(inputEl, textValue) {
                    if (!inputEl || textValue === undefined || textValue === null) return;
                    const strVal = String(textValue);
                    const prevInputMode = inputEl.getAttribute('inputmode');
                    try {
                        inputEl.setAttribute('inputmode', 'none');
                    } catch(e) {}

                    try { inputEl.focus({ preventScroll: true }); } catch(e) {
                        try { inputEl.focus(); } catch(e2) {}
                    }

                    if (inputEl._valueTracker) {
                        try { inputEl._valueTracker.setValue(''); } catch(e) {}
                    }

                    let nativeSetter = null;
                    try {
                        nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
                        if (!nativeSetter) {
                            nativeSetter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(inputEl), 'value')?.set;
                        }
                    } catch(e) {}

                    if (nativeSetter) {
                        try { nativeSetter.call(inputEl, strVal); } catch(e) { inputEl.value = strVal; }
                    } else {
                        inputEl.value = strVal;
                    }

                    try { inputEl.dispatchEvent(new Event('focus', { bubbles: true })); } catch(e) {}
                    try { inputEl.dispatchEvent(new Event('keydown', { bubbles: true })); } catch(e) {}
                    try {
                        if (typeof InputEvent === 'function') {
                            inputEl.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: strVal, inputType: 'insertText' }));
                        } else {
                            inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                    } catch(e) {
                        try { inputEl.dispatchEvent(new Event('input', { bubbles: true })); } catch(e2) {}
                    }
                    try { inputEl.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                    try { inputEl.dispatchEvent(new Event('keyup', { bubbles: true })); } catch(e) {}
                    try { inputEl.dispatchEvent(new Event('blur', { bubbles: true })); } catch(e) {}

                    setTimeout(function() {
                        try {
                            if (prevInputMode === null) inputEl.removeAttribute('inputmode');
                            else inputEl.setAttribute('inputmode', prevInputMode);
                        } catch(e) {}
                    }, 600);
                }

                // Lấy thông tin nhãn/placeholder gắn trực tiếp với từng ô <input>
                // Dùng textContent thay vì chỉ innerText để không bị ngắt dòng làm sai từ khi cột hẹp 90px,
                // và chỉ lấy text của parent khi parent bọc riêng đúng 1 ô <input> (tránh lẫn "Máy chủ: Võ Lâm Idle")
                function getOwnInputMeta(inp) {
                    const parts = [
                        inp.placeholder || '',
                        inp.name || '',
                        inp.id || '',
                        inp.getAttribute('aria-label') || '',
                        inp.getAttribute('autocomplete') || '',
                        inp.title || ''
                    ];
                    if (inp.labels && inp.labels.length > 0) {
                        for (const lb of Array.from(inp.labels)) {
                            parts.push(lb.textContent || lb.innerText || '');
                        }
                    }
                    const closestLabel = inp.closest('label');
                    if (closestLabel) {
                        parts.push(closestLabel.textContent || closestLabel.innerText || '');
                    }
                    const prev = inp.previousElementSibling;
                    if (prev) {
                        const prevTxt = (prev.textContent || prev.innerText || '').replace(/\s+/g, ' ').trim();
                        if (prevTxt.length <= 50) parts.push(prevTxt);
                    }
                    const parent = inp.parentElement;
                    if (parent) {
                        const inputsInParent = parent.querySelectorAll('input').length;
                        if (inputsInParent === 1) {
                            const pTxt = (parent.textContent || parent.innerText || '').replace(/\s+/g, ' ').trim();
                            if (pTxt.length <= 70) {
                                parts.push(pTxt);
                            }
                        }
                    }
                    return noAccent(parts.join(' '));
                }

                // Loại trừ dòng "Máy chủ: Võ Lâm Idle" nếu nó là thẻ <input>
                function isServerField(inp) {
                    if ((inp.type || '').toLowerCase() === 'password') return false;
                    if (inp.readOnly || inp.disabled) return true;
                    const valNA = noAccent(inp.value || '');
                    if (valNA.includes('vo lam idle')) return true;
                    const meta = getOwnInputMeta(inp);
                    if (meta.includes('tai khoan') || meta.includes('mat ma') || meta.includes('mat khau') || meta.includes('username') || meta.includes('password')) {
                        return false;
                    }
                    return meta.includes('may chu') || meta.includes('server');
                }

                function isConfirmField(inp) {
                    const meta = getOwnInputMeta(inp);
                    return meta.includes('xac nhan') || meta.includes('nhap lai') || meta.includes('confirm') || meta.includes('retype');
                }

                function isPasswordField(inp) {
                    if (isServerField(inp)) return false;
                    if ((inp.type || '').toLowerCase() === 'password') return true;
                    const meta = getOwnInputMeta(inp);
                    return meta.includes('mat ma') || meta.includes('mat khau') || meta.includes('password') || meta.includes('pass');
                }

                function isUsernameField(inp) {
                    if (isServerField(inp) || isPasswordField(inp) || isConfirmField(inp)) return false;
                    const meta = getOwnInputMeta(inp);
                    return meta.includes('tai khoan') || meta.includes('ten dang nhap') || meta.includes('ten nhan vat') ||
                           meta.includes('username') || meta.includes('account') || meta.includes('user');
                }

                // Ô chat / ô soạn tin ở đáy màn hình chơi KHÔNG phải ô đăng nhập.
                // Màn hình chơi luôn có sẵn ô "Nhập tin nhắn..." nên nếu tính nó là ô auth thì
                // getVisibleEditableInputs().length luôn >= 1 và tab không bao giờ được báo IN_GAME.
                // Build 1041 (h5.211): ô chat là #u20txt ("Nhập tin nhắn… (Enter để chat)") trong
                // khung #u20log, bản PC dùng #chatTxt ("Nhập tin nhắn…"), còn #hhChTxt là ô
                // "Nhập mật ngữ…" của bảng Kết bạn. Cả ba đều là ô gõ chữ trong game.
                const CHAT_COMPOSER_KEYWORDS = ['nhan tin', 'tin nhan', 'chat', 'nhap tin', 'noi dung', 'message', 'comment', 'mat ngu'];

                function isChatOrComposer(inp) {
                    const id = (inp.id || '').toLowerCase();
                    if (id === 'u20txt' || id === 'chattxt' || id === 'hhchtxt') return true;
                    const meta = getOwnInputMeta(inp);
                    for (const kw of CHAT_COMPOSER_KEYWORDS) {
                        if (meta.includes(kw)) return true;
                    }
                    return false;
                }

                // Ô nhập KHÔNG BAO GIỜ được coi là ô đăng nhập. Danh sách lấy từ toàn bộ <input>
                // có id trong app.js build 1041, trừ các ô của chính cổng đăng nhập
                // (#ngU/#ngP/#ngP2) và bảng tài khoản mây (#naU/#naP - chặn riêng bên dưới):
                //   gcIn mã quà · nmTo/nmIn/nmNote thư · ptName/ptTo/ptNote tổ đội
                //   hhName/hhMnName/hhDenName/hhChTxt kết bạn · ncName đặt tên nhân vật
                //   mkU/mkM/mkC/mkP1/mkP2 đổi mật mã · bsTb/bsTuyen tuyển bang
                //   prQ/hfQ/nkQ ô tìm kiếm · petNmI tên thú
                const NON_AUTH_IDS = ['gcin', 'nmto', 'nmin', 'nmnote', 'ptname', 'ptto', 'ptnote',
                                      'hhname', 'hhmnname', 'hhdenname', 'ncname',
                                      'mku', 'mkm', 'mkc', 'mkp1', 'mkp2', 'bstb', 'bstuyen',
                                      'prq', 'hfq', 'nkq', 'petnmi', 'nau', 'nap'];

                // Cổng đăng nhập của build 1041 do netGate() dựng trong #modal:
                // <div class="dn20" data-dn20="1"> ... #ngU / #ngP / #ngIn ...
                // Chỉ điền tài khoản khi thật sự đang đứng trong cổng này.
                function authGateRoot() {
                    return document.querySelector('#modal .dn20[data-dn20], .dn20[data-dn20]');
                }

                // Phạm vi được phép điền mật khẩu:
                // - Có cổng .dn20 => chỉ điền trong cổng đó.
                // - Không có cổng nhưng có bảng tài khoản mây (#naP/#naIn) => không điền gì cả.
                // - Bản cũ không có cả hai => giữ nguyên hành vi cũ trên toàn trang.
                function authScopeRoot() {
                    const gate = authGateRoot();
                    if (gate) return gate;
                    if (document.getElementById('naP') || document.getElementById('naIn')) return null;
                    return document;
                }

                const allClickables = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], [role="button"], a, div, span, p, label'));

                function getVisibleEditableInputs() {
                    const scope = authScopeRoot();
                    if (!scope) return [];
                    return Array.from(document.querySelectorAll('input')).filter(inp => {
                        if (scope !== document && !scope.contains(inp)) return false;
                        if (NON_AUTH_IDS.indexOf((inp.id || '').toLowerCase()) >= 0) return false;
                        if (inp.classList && inp.classList.contains('bhin')) return false;
                        const t = (inp.type || 'text').toLowerCase();
                        // number/search/date... không bao giờ là ô tài khoản; loại luôn để các
                        // bảng Kỳ Trân Các, Chợ, Chuyển sinh không bị tính là "ô đăng nhập".
                        if (['hidden', 'checkbox', 'radio', 'button', 'submit', 'reset', 'image', 'file', 'range', 'color', 'number', 'search', 'date', 'time', 'datetime-local', 'month', 'week'].includes(t)) {
                            return false;
                        }
                        if (!isVisible(inp)) return false;
                        if (isServerField(inp)) return false;
                        if (isChatOrComposer(inp)) return false;
                        return true;
                    });
                }

                // =========================================================================
                // NHẬN DIỆN MÀN HÌNH THEO CẤU TRÚC DOM CỦA BUILD 1041
                // Bản 1041 không còn HUD chữ "Hành trang / Kỹ năng / Chế độ :" đứng yên trên
                // màn hình chơi, nhưng #app được render sẵn ngay cả khi cổng đăng nhập đang
                // phủ lên trên. Vì vậy KHÔNG được đoán "đã vào game" bằng chữ trong body.
                // =========================================================================
                function byId(id) {
                    return document.getElementById(id);
                }

                function gateLoginVisible() {
                    const gate = authGateRoot();
                    if (!gate || !isVisible(gate)) return false;
                    const u = byId('ngU');
                    const p = byId('ngP');
                    return !!((u && isVisible(u)) || (p && isVisible(p)));
                }

                function gateMenuVisible() {
                    const b = byId('ngStart');
                    return !!(b && isVisible(b));
                }

                function charSelectVisible() {
                    const c = byId('csel');
                    return !!(c && isVisible(c));
                }

                // Đang thật sự trong game: #modeSw do modeSwSync() tạo và chỉ bỏ class "hidden"
                // khi S.fac có giá trị; ngoài ra body mang class u20/u2m (u20Apply chỉ bật khi
                // !!S) và #stageLbl chỉ được updateTop() ghi khi đã vào game.
                function inGameNow() {
                    if (gateLoginVisible() || gateMenuVisible() || charSelectVisible()) return false;
                    const msw = byId('modeSw');
                    if (msw && !msw.classList.contains('hidden')) return true;
                    const body = document.body;
                    if (body && (body.classList.contains('u20') || body.classList.contains('u2m'))) return true;
                    const stage = byId('stageLbl');
                    if (stage && (stage.textContent || '').replace(/\s+/g, '') !== '') return true;
                    const logBox = byId('logBox');
                    if (logBox && logBox.children && logBox.children.length > 0) return true;
                    return false;
                }

                // Nút xác nhận của cổng 1041 là #ngIn (chữ "Xác Định"). Dùng id trước để
                // không phụ thuộc vào việc nút có bị đổi nhãn hay không.
                function findGateSubmitButton() {
                    const gate = authGateRoot();
                    const btn = byId('ngIn');
                    if (btn && isVisible(btn) && (!gate || gate.contains(btn))) return btn;
                    return null;
                }

                // Tích "Nhớ tài khoản". Build 1041 dùng <button id="ngRem" class="cb on">
                // (aria-label="Nhớ tài khoản"), không còn <input type="checkbox">.
                function ensureRememberAccount() {
                    const rem = byId('ngRem');
                    if (rem) {
                        if (!rem.classList.contains('on')) {
                            try { rem.click(); } catch(e) {}
                        }
                        return;
                    }
                    const visibleCheckboxes = Array.from(document.querySelectorAll('input[type="checkbox"]')).filter(isVisible);
                    for (const cb of visibleCheckboxes) {
                        const lb = cb.closest('label');
                        const nextEl = cb.nextElementSibling;
                        const parentEl = cb.parentElement;
                        const singleCbInParent = parentEl && parentEl.querySelectorAll('input[type="checkbox"]').length === 1;
                        const cbText = noAccent(
                            (lb ? (lb.textContent || lb.innerText || '') : '') + ' ' +
                            (nextEl ? (nextEl.textContent || nextEl.innerText || '') : '') + ' ' +
                            (singleCbInParent ? (parentEl.textContent || parentEl.innerText || '') : '')
                        );

                        if (cbText.includes('hien mat') || cbText.includes('hien thi mat') || cbText.includes('show pass')) {
                            if (cb.checked) {
                                try { cb.click(); } catch(e) {}
                            }
                            continue;
                        }

                        if (cbText.includes('nho tai khoan') || cbText.includes('ghi nho') || cbText.includes('remember')) {
                            if (!cb.checked) {
                                try { cb.click(); } catch(e) {}
                                if (!cb.checked) {
                                    cb.checked = true;
                                    try { cb.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                                }
                            }
                        }
                    }
                }

                // Màn hình chọn nhân vật build 1041: #csel (append vào body) với các thẻ
                // .cs-slot[data-i] và nút #csGo "Xác Định". #csGo bị disabled khi ô đang chọn
                // chưa có nhân vật — TUYỆT ĐỐI không gỡ disabled: cselEnter() sẽ ghi SLOT_PTR
                // của ô trống rồi reload, gây vòng lặp tải lại vô hạn.
                function tryEnterCharacterSelect() {
                    const csel = byId('csel');
                    if (!csel || !isVisible(csel)) return null;
                    const go = byId('csGo');
                    if (!go || !isVisible(go) || go.disabled) {
                        // Ô đang chọn trống nhưng tài khoản còn nhân vật ở ô khác: chuyển sang thẻ
                        // CÓ nhân vật (app.js: .cs-slot.empty khi ô trống). TUYỆT ĐỐI không bấm
                        // thẻ trống — onclick của thẻ trống gọi cselEnter() và ghi SLOT_PTR rỗng.
                        if (Date.now() - (window.__lastCharPickTime || 0) >= 1500) {
                            const cards = csel.querySelectorAll('.cs-slot[data-i]:not(.empty)');
                            for (let i = 0; i < cards.length; i++) {
                                const card = cards[i];
                                if (card.classList.contains('on')) continue;
                                window.__lastCharPickTime = Date.now();
                                realClick(card);
                                return 'CLICKED_CHARACTER_CARD';
                            }
                        }
                        return 'WAITING_CHAR_SELECT';
                    }
                    if (Date.now() - (window.__lastCharGoTime || 0) < 1500) return 'WAITING_CHAR_SELECT';

                    const onCard = csel.querySelector('.cs-slot.on');
                    if (onCard) realClick(onCard);

                    window.__lastCharGoTime = Date.now();
                    setTimeout(function() {
                        try {
                            // Truy vấn lại: cselPanel() có thể đã dựng lại .cs-panel và thay nút.
                            const btn = byId('csGo');
                            if (btn && !btn.disabled && isVisible(btn)) realClick(btn);
                        } catch(e) {}
                    }, 120);

                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "Đã chọn nhân vật và ấn [Xác Định] vào game");
                    }
                    return "CLICKED_CHARACTER_SELECT";
                }

                // Tìm nút bấm theo danh sách từ khóa chuẩn hóa:
                // Kiểm tra khớp chuỗi (textContent / innerText) TRƯỚC khi gọi isVisible(el)
                // để giảm 99% số lệnh getComputedStyle/getBoundingClientRect giúp điện thoại chạy cực mượt!
                function findBestButtonByText(matcherFn) {
                    let bestEl = null;
                    let bestScore = -999;

                    for (const el of allClickables) {
                        const rawContent = (el.textContent || el.value || '').replace(/\s+/g, ' ').trim();
                        if (!rawContent || rawContent.length > 45) continue;
                        const contentNA = noAccent(rawContent);

                        let matchScore = matcherFn(contentNA, rawContent, el);
                        if (matchScore <= 0) {
                            // Dùng textContent (không phải innerText) để không kích hoạt reflow đồng bộ
                            const rawInner = (el.textContent || el.value || '').replace(/\s+/g, ' ').trim();
                            if (rawInner && rawInner.length <= 45 && rawInner !== rawContent) {
                                matchScore = matcherFn(noAccent(rawInner), rawInner, el);
                            }
                        }
                        if (matchScore <= 0) continue;

                        if (!isVisible(el)) continue;

                        let score = matchScore;
                        const tag = (el.tagName || '').toUpperCase();
                        if (tag === 'BUTTON' || tag === 'INPUT') score += 60;
                        else if (el.getAttribute('role') === 'button' || tag === 'A') score += 45;
                        else if (el.closest('button, [role="button"], a')) score += 35;

                        if (el.children && el.children.length === 0) score += 20;
                        else if (el.children && el.children.length <= 4) score += 10;

                        if (score > bestScore) {
                            bestScore = score;
                            bestEl = el;
                        }
                    }
                    return bestEl;
                }

                // Tìm nút Submit trong khung Đăng Nhập:
                // Trong phiên bản 1031, nút xác nhận đăng nhập có chữ "Xác Định" (nằm bên trái nút "Hủy", phía dưới ô "Mật mã:")
                function findLoginSubmitButton(referenceInput) {
                    const refRect = referenceInput ? referenceInput.getBoundingClientRect() : null;
                    return findBestButtonByText(function(txtNA, rawTxt, el) {
                        if (txtNA === 'huy' || txtNA.includes('dang ky') || txtNA.includes('quen mat') ||
                            txtNA.includes('hien mat') || txtNA.includes('nho tai khoan') ||
                            txtNA.includes('tao nhan vat') || txtNA.includes('chua co tai khoan') ||
                            txtNA.includes('bat dau tro choi')) {
                            return -1;
                        }

                        let base = 0;
                        if (txtNA === 'xac dinh') base = 200;
                        else if (txtNA === 'xac nhan' || txtNA === 'dang nhap' || txtNA === 'login') base = 120;
                        else if (txtNA.startsWith('xac dinh') || txtNA.startsWith('dang nhap')) base = 90;
                        else return -1;

                        if (referenceInput && refRect && refRect.height > 0) {
                            try {
                                const btnRect = el.getBoundingClientRect();
                                if (btnRect.height > 0 && btnRect.top >= refRect.top - 20) {
                                    base += 80;
                                }
                            } catch(e) {}
                        }
                        return base;
                    });
                }

                // Hàm thực hiện điền Tài khoản + Mật mã + tích Nhớ tài khoản + ấn "Xác Định" khi khung Đăng Nhập đã mở
                function tryFillAndSubmitLoginModal() {
                    const editableInputs = getVisibleEditableInputs();
                    if (editableInputs.length === 0) return null;

                    let passInput = null;
                    let userInput = null;

                    for (const inp of editableInputs) {
                        if ((inp.type || '').toLowerCase() === 'password' && !isConfirmField(inp)) {
                            passInput = inp;
                            break;
                        }
                    }
                    if (!passInput) {
                        for (const inp of editableInputs) {
                            if (isPasswordField(inp) && !isConfirmField(inp)) {
                                passInput = inp;
                                break;
                            }
                        }
                    }
                    // Fallback chuẩn cho bảng Đăng Nhập 2 ô (Ô 1: Tài khoản, Ô 2: Mật mã) trên màn hình dọc 4 cột hẹp.
                    // Chỉ dùng khi thật sự có cổng .dn20: ngoài cổng thì hai ô cạnh nhau (đổi mật mã,
                    // đặt tên, tuyển bang…) không được coi là ô đăng nhập.
                    if (!passInput && editableInputs.length === 2 && !isConfirmField(editableInputs[1]) && authGateRoot()) {
                        userInput = editableInputs[0];
                        passInput = editableInputs[1];
                    }

                    if (!passInput) return null;

                    if (!username || !password) {
                        if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                            window.AndroidBridge.onGameLog(tabIdx, "Chờ nhập TK & MK ở nút 🔑 TK");
                        }
                        return "NO_CREDENTIALS";
                    }

                    // Tìm ô "Tài khoản:" nếu chưa gán từ fallback
                    if (!userInput) {
                        for (const inp of editableInputs) {
                            if (inp === passInput) continue;
                            if (isUsernameField(inp)) {
                                userInput = inp;
                                break;
                            }
                        }
                    }
                    if (!userInput) {
                        const passIdx = editableInputs.indexOf(passInput);
                        if (passIdx > 0) {
                            for (let i = passIdx - 1; i >= 0; i--) {
                                const candidate = editableInputs[i];
                                if (!isConfirmField(candidate) && !isPasswordField(candidate)) {
                                    userInput = candidate;
                                    break;
                                }
                            }
                        }
                    }
                    if (!userInput) {
                        for (const inp of editableInputs) {
                            if (inp !== passInput && !isConfirmField(inp)) {
                                userInput = inp;
                                break;
                            }
                        }
                    }

                    const nowMs = Date.now();
                    const timeSinceLastSubmit = nowMs - (window.__lastLoginSubmitTime || 0);
                    const valuesAlreadyMatch = (!userInput || userInput.value === username) && (passInput.value === password);

                    if (valuesAlreadyMatch && timeSinceLastSubmit < 950) {
                        return "WAITING_LOGIN_RESPONSE";
                    }

                    // 1. Điền Tài khoản và Mật mã ngay lập tức
                    if (userInput) {
                        forceFillReactInput(userInput, username);
                    }
                    forceFillReactInput(passInput, password);

                    // 2. Tích "Nhớ tài khoản", TUYỆT ĐỐI KHÔNG tích "Hiện mật mã"
                    ensureRememberAccount();

                    // 3. Sau 65ms (và nhịp bồi dự phòng ở 240ms nếu bảng vẫn mở), kiểm tra lại giá trị và bấm nút "Xác Định"
                    function confirmAndClickSubmit() {
                        try {
                            if (!passInput || !passInput.isConnected || !isVisible(passInput)) return;
                            if (userInput && userInput.isConnected && userInput.value !== username) {
                                forceFillReactInput(userInput, username);
                            }
                            if (passInput.value !== password) {
                                forceFillReactInput(passInput, password);
                            }
                            const submitBtn = findGateSubmitButton() || findLoginSubmitButton(passInput);
                            if (submitBtn) {
                                window.__lastLoginSubmitTime = Date.now();
                                realClick(submitBtn);
                                if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                                    window.AndroidBridge.onGameLog(tabIdx, "Đã ấn [Xác Định] đăng nhập: " + username);
                                }
                                // Build 1041: #ngP.onkeydown Enter cũng gọi đúng submit này.
                                // Bấm nút rồi gửi thêm Enter = hai lần đăng nhập, nên dừng ở đây.
                                return;
                            }
                            try {
                                passInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));
                                passInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));
                            } catch(e) {}
                            if (passInput.form) {
                                try {
                                    if (typeof passInput.form.requestSubmit === 'function') {
                                        passInput.form.requestSubmit();
                                    } else {
                                        passInput.form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                                    }
                                } catch(e) {}
                            }
                        } catch(e) {}
                    }

                    setTimeout(confirmAndClickSubmit, 65);
                    setTimeout(function() {
                        if (Date.now() - (window.__lastLoginSubmitTime || 0) >= 220) {
                            confirmAndClickSubmit();
                        }
                    }, 250);

                    return "FILLED_AND_CLICKED_XAC_DINH";
                }

                // =========================================================================
                // BƯỚC 0: ƯU TIÊN XỬ LÝ HỘP THOẠI TRANH CHẤP PHIÊN / QUY ĐỊNH NẾU ĐANG HIỂN THỊ
                // =========================================================================
                const playHereBtn = findBestButtonByText(function(txtNA, rawTxt) {
                    if (rawTxt === 'Chơi ở đây' || rawTxt === 'Chơi ở tab này' ||
                        rawTxt.includes('Chơi ở đây') || rawTxt.includes('Chơi ở tab này') ||
                        rawTxt.includes('Tiếp tục ở đây') || rawTxt.includes('Kết nối lại')) {
                        return 100;
                    }
                    return -1;
                });
                if (playHereBtn) {
                    realClick(playHereBtn);
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "⚡ Đã tự động chọn [Chơi ở đây] để tiếp tục chạy game");
                    }
                    return "CLICKED_PLAY_HERE";
                }

                const agreeBtn = findBestButtonByText(function(txtNA) {
                    if (txtNA === 'toi dong tinh' || txtNA === 'dong tinh' || txtNA === 'toi da hieu') {
                        return 100;
                    }
                    return -1;
                });
                if (agreeBtn) {
                    realClick(agreeBtn);
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "Đã nhấn [Tôi Đồng Tình]");
                    }
                    return "CLICKED_AGREE";
                }

                // =========================================================================
                // BƯỚC 1: NẾU KHUNG "ĐĂNG NHẬP" ĐÃ MỞ (CÓ Ô TÀI KHOẢN & MẬT MÃ) -> ĐIỀN & ẤN "XÁC ĐỊNH"
                // =========================================================================
                const loginModalResult = tryFillAndSubmitLoginModal();
                if (loginModalResult !== null) {
                    return loginModalResult;
                }

                // =========================================================================
                // BƯỚC 1.5: KIỂM TRA MÀN HÌNH CHỌN NHÂN VẬT
                // Build 1041: #csel + .cs-slot[data-i] + #csGo "Xác Định" (xem cselPanel()).
                // =========================================================================
                const charSelectResult = tryEnterCharacterSelect();
                if (charSelectResult !== null) {
                    return charSelectResult;
                }

                // Máy chủ cũ: danh sách nút chữ "Xác Định" / "Vào Game" / "Bắt Đầu" không có #csel.
                const charSelectConfirmBtn = findBestButtonByText(function(txtNA, rawTxt, el) {
                    if (txtNA === 'xac dinh' || txtNA === 'vao game' || txtNA === 'bat dau') {
                        // Phải đảm bảo nó không nằm trong popup đăng nhập
                        const isLoginModal = el.closest('form') || (el.parentElement && el.parentElement.querySelector && el.parentElement.querySelector('input[type="password"]'));
                        if (!isLoginModal) return 130;
                    }
                    return -1;
                });

                if (charSelectConfirmBtn && !charSelectConfirmBtn.disabled && getVisibleEditableInputs().length === 0) {
                     // Click thẻ nhân vật trước nếu có
                     const sects = ['Thiếu Lâm', 'Thiên Vương', 'Đường Môn', 'Ngũ Độc', 'Nga Mi', 'Thúy Yên', 'Cái Bang', 'Thiên Nhẫn', 'Võ Đang', 'Côn Lôn', 'Đoàn Thị'];
                     let charCard = null;
                     for (const el of allClickables) {
                         if (!isVisible(el)) continue;
                         const txt = (el.innerText || '');
                         if (txt.includes('Cấp') || txt.includes('Lv') || sects.some(s => txt.includes(s))) {
                             const rect = el.getBoundingClientRect();
                             if (rect.height > 20 && rect.width > 50) {
                                 charCard = el;
                                 break;
                             }
                         }
                     }

                     if (charCard) {
                         realClick(charCard);
                     }

                     setTimeout(function() {
                         try {
                             if (charSelectConfirmBtn.isConnected && !charSelectConfirmBtn.disabled) realClick(charSelectConfirmBtn);
                         } catch(e) {}
                         if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                             window.AndroidBridge.onGameLog(tabIdx, "Đã chọn nhân vật và ấn vào game");
                         }
                     }, 150);

                     return "CLICKED_CHARACTER_SELECT";
                }

                // =========================================================================
                // BƯỚC 2: NẾU ĐANG Ở MÀN HÌNH CHỜ BAN ĐẦU -> ẤN NÚT "BẮT ĐẦU TRÒ CHƠI"
                // (Trước khi vào khu vực đăng nhập phải ấn "Bắt Đầu Trò Chơi")
                // =========================================================================
                const startGameBtn = findBestButtonByText(function(txtNA) {
                    if (txtNA === 'bat dau tro choi') return 120;
                    if (txtNA.includes('bat dau tro choi')) return 90;
                    return -1;
                });

                if (startGameBtn) {
                    realClick(startGameBtn);
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "Đã nhấn [Bắt Đầu Trò Chơi] -> Mở khung Đăng Nhập");
                    }
                    // Đón lõng khung Đăng Nhập ngay khi vừa render ở các mốc 180ms, 380ms, 650ms, 950ms
                    [180, 380, 650, 950].forEach(function(delayMs) {
                        setTimeout(function() {
                            try {
                                tryFillAndSubmitLoginModal();
                            } catch(e) {}
                        }, delayMs);
                    });
                    return "CLICKED_BAT_DAU_TRO_CHOI";
                }

                // Kiểm tra các nút mở đăng nhập / vào game khác nếu có
                const otherOpenBtn = findBestButtonByText(function(txtNA) {
                    if (txtNA === 'vao game' || txtNA === 'vao giang ho' || txtNA === 'bat dau' ||
                        txtNA === 'vao tro choi' || txtNA === 'choi ngay') {
                        return 80;
                    }
                    return -1;
                });
                if (otherOpenBtn) {
                    realClick(otherOpenBtn);
                    [180, 380, 650].forEach(function(delayMs) {
                        setTimeout(function() {
                            try { tryFillAndSubmitLoginModal(); } catch(e) {}
                        }, delayMs);
                    });
                    return "CLICKED_OPEN_LOGIN";
                }

                // =========================================================================
                // BƯỚC 3: KIỂM TRA ĐÃ VÀO TRONG GAME THỰC SỰ HAY CHƯA
                // Build 1041: #app (header + #battle + #panel + #tabs) được render sẵn ngay cả
                // khi cổng đăng nhập còn phủ trên màn hình, nên chữ "Hành trang" / "Võ công" /
                // "Hệ thống" của thanh tab LUÔN có trong body.innerText. Đoán theo chữ sẽ báo
                // IN_GAME sai ngay trên màn hình đăng nhập. Dùng cấu trúc DOM thay thế.
                // =========================================================================
                if (inGameNow()) {
                    if (window.AndroidBridge && window.AndroidBridge.onInGameDetected) {
                        window.AndroidBridge.onInGameDetected(tabIdx);
                    }
                    return "IN_GAME";
                }

                // Bản cũ không có #modeSw / body.u20 / body.u2m / #stageLbl: giữ nguyên cách dò chữ.
                if (!byId('modeSw') && !byId('stageLbl') && !byId('csel') && !authGateRoot()) {
                    const bodyText = document.body ? (document.body.innerText || '') : '';
                    const hasBattleMenu = (bodyText.includes('Hành trang') && (bodyText.includes('Kỹ năng') || bodyText.includes('Võ công') || bodyText.includes('Hệ thống'))) ||
                                         bodyText.includes('Chế độ :') ||
                                         bodyText.includes('Chế độ:') ||
                                         bodyText.includes('Chế Độ :') ||
                                         bodyText.includes('Chế Độ:');
                    if (hasBattleMenu) {
                        if (window.AndroidBridge && window.AndroidBridge.onInGameDetected) {
                            window.AndroidBridge.onInGameDetected(tabIdx);
                        }
                        return "IN_GAME";
                    }
                }

                return "WAITING_SCREEN";
            } catch (err) {
                if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                    window.AndroidBridge.onGameLog($tabIndex, "Lỗi script: " + err.message);
                }
                return "ERROR: " + err.message;
            }
        })($safeUserJson, $safePassJson);
        """.trimIndent()
    }

    // Bộ Canh Gác & Quét Thông Số Định Kỳ (mỗi 2500ms khi đã ở trong game)
    fun buildPeriodicWatchdogAndScanScript(tabIndex: Int): String {
        return """
        (function() {
            try {
                const tabIdx = $tabIndex;
                
                // User Touch Lock: Dừng quét ngầm nếu đang thao tác ô chọn (SELECT, INPUT, TEXTAREA)
                const activeEl = document.activeElement;
                if (activeEl) {
                    const tag = (activeEl.tagName || '').toUpperCase();
                    if (tag === 'SELECT' || tag === 'INPUT' || tag === 'TEXTAREA') {
                        return "USER_IS_INTERACTING";
                    }
                }
                
                // User Touch Lock: Nếu hệ thống chặn qua Kotlin (biến cờ từ Java)
                if (window.__isUserTouching) {
                    return "USER_IS_TOUCHING_FROM_KOTLIN";
                }

                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function isVisible(el) {
                    if (!el || !el.isConnected) return false;
                    if ((el.type || '').toLowerCase() === 'hidden') return false;
                    let cur = el;
                    while (cur && cur !== document.body && cur !== document.documentElement) {
                        if (cur.hidden === true) return false;
                        try {
                            const style = window.getComputedStyle(cur);
                            if (style && (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || parseFloat(style.opacity) === 0)) {
                                return false;
                            }
                        } catch(e) {}
                        cur = cur.parentElement;
                    }
                    const tag = (el.tagName || '').toUpperCase();
                    const isFormOrBtn = (tag === 'INPUT' || tag === 'BUTTON' || tag === 'SELECT' || tag === 'TEXTAREA' || el.getAttribute('role') === 'button');
                    try {
                        const rect = el.getBoundingClientRect();
                        if (isFormOrBtn) {
                            if (rect.height > 0 || rect.width > 0 || el.offsetParent !== null) return true;
                            if (el.parentElement) {
                                const pRect = el.parentElement.getBoundingClientRect();
                                return pRect.height > 0 || pRect.width > 0;
                            }
                        }
                        return rect.width > 1 && rect.height > 1;
                    } catch(e) {
                        return el.offsetParent !== null;
                    }
                }

                function realClick(el) {
                    if (!el) return;
                    const target = el.closest('button, [role="button"], a, input[type="button"], input[type="submit"]') || el;
                    try {
                        const rect = target.getBoundingClientRect();
                        const cx = rect.left + Math.max(2, rect.width / 2);
                        const cy = rect.top + Math.max(2, rect.height / 2);
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(type => {
                            try {
                                target.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof target.click === 'function') {
                        try { target.click(); } catch(e) {}
                    }
                }

                function cleanText(txt) {
                    if (!txt) return "";
                    return txt.replace(/\n+/g, ' ').replace(/\s+/g, ' ').trim();
                }

                // Loại văn bản trong khung chat khỏi bodyText: người chơi gõ "tạo nhân vật",
                // "nhớ tài khoản", "đăng ký tài khoản" vào chat sẽ khiến các kiểm tra auth bên
                // dưới tưởng nhầm là màn đăng nhập và kích hoạt đăng nhập lại liên tục.
                // Build 1041: ngoài #chatLines (bản PC, gắn trong #battle) và #u20lines (khung
                // thông báo di động) còn #logBox — nhật ký "Giang hồ" của tab #t-log, tab này
                // mở sẵn nên mọi dòng chữ của nó đều nằm trong body.innerText.
                let bodyText = document.body ? (document.body.innerText || '') : '';
                for (const chatId of ['chatLines', 'u20lines', 'u20log', 'chatBox', 'logBox']) {
                    const cel = document.getElementById(chatId);
                    if (cel && cel.innerText) bodyText = bodyText.split(cel.innerText).join(' ');
                }
                const bodyNA = noAccent(bodyText);
                const allElements = Array.from(document.querySelectorAll('button, [role="button"], a, div, span, p, label'));

                // =========================================================================
                // GÓI TRẠNG THÁI GAME (2 giây / lần) — app.js: vlmSt() + postMessage({vlm:'st'})
                // Trường dùng được khi đã vào game:
                //   o      : "dangnhap" | "menu" | "game" | "uythac"
                //   ten    : tên nhân vật      phaiTen : tên phái (chữ)
                //   cap    : cấp               xpp     : % kinh nghiệm (đã làm tròn 0,1)
                //   dt     : [đã làm hôm nay, trần ngày] của Dã Tẩu
                // Đây là dữ liệu do chính game công bố, không phụ thuộc tab nào đang mở.
                // =========================================================================
                if (!window.__vlScanSt) {
                    window.__vlScanSt = { t: 0, st: null };
                    window.addEventListener('message', function (msgEvt) {
                        try {
                            if (msgEvt.source !== window) return;
                            const d = msgEvt.data;
                            if (!d || d.vlm !== 'st' || !d.st || typeof d.st !== 'object') return;
                            window.__vlScanSt.st = d.st;
                            window.__vlScanSt.t = Date.now();
                        } catch (errSt) {}
                    });
                }
                const stAge = window.__vlScanSt ? (Date.now() - (window.__vlScanSt.t || 0)) : 1e9;
                const stp = (window.__vlScanSt && stAge < 15000) ? window.__vlScanSt.st : null;
                const stInGame = !!(stp && stp.o === 'game');
                // "Ra ngoài game" chỉ tin mẫu MỚI: game post 2 giây/lần, nên mẫu cũ hơn 5 giây
                // có thể là trạng thái trước khi đăng nhập xong và sẽ báo KICKED sai một nhịp.
                const stOutGame = !!(stp && stAge < 5000 && (stp.o === 'dangnhap' || stp.o === 'menu'));

                // 1. TỰ ĐỘNG BẤM "CHƠI Ở ĐÂY" / "CHƠI Ở TAB NÀY" NẾU CÓ TRANH CHẤP PHIÊN
                // Chỉ gọi isVisible(el) khi chuỗi văn bản đã khớp để tránh gây giật lag trên điện thoại
                for (const el of allElements) {
                    const txt = (el.textContent || el.innerText || '').replace(/\s+/g, ' ').trim();
                    if (!txt || txt.length > 35) continue;
                    const isRetryBtn = (txt === 'Thử lại');
                    if (txt === 'Chơi ở đây' || txt === 'Chơi ở tab này' ||
                        txt.includes('Chơi ở đây') || txt.includes('Chơi ở tab này') || 
                        txt.includes('Tiếp tục ở đây') || txt.includes('Kết nối lại') ||
                        isRetryBtn) {
                        if (!isVisible(el)) continue;
                        if (isRetryBtn) {
                            // Build 1041: "Thử lại" của #sfRe / #ngRetry là location.reload().
                            // Bấm mỗi 2,5 giây sẽ thành vòng lặp tải lại cả 8 tab. Vẫn tự phục
                            // hồi nhưng tối đa 1 lần / 60 giây; sessionStorage sống qua reload.
                            const nowRt = Date.now();
                            let lastRt = 0;
                            try { lastRt = parseInt(sessionStorage.getItem('__vl_retry_at') || '0', 10) || 0; } catch(eRt) {}
                            if (nowRt - lastRt < 60000) continue;
                            try { sessionStorage.setItem('__vl_retry_at', String(nowRt)); } catch(eRt2) {}
                        }
                        realClick(el);
                        if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                            window.AndroidBridge.onGameLog(tabIdx, "⚡ Đã tự bấm [" + txt + "] tiếp tục phiên chơi");
                        }
                        return "CLICKED_PLAY_HERE";
                    }
                }

                // Nếu thấy tiêu đề cảnh báo tranh chấp phiên
                if (bodyText.includes('Tài khoản đang mở ở nơi khác') || bodyText.includes('đang mở ở máy / tab khác')) {
                    for (const el of allElements) {
                        const txt = (el.textContent || el.innerText || '').replace(/\s+/g, ' ').trim();
                        if (!txt || txt.length > 35) continue;
                        if (txt.includes('Chơi ở đây') || txt.includes('Chơi ở tab') || txt.includes('Tiếp tục')) {
                            if (!isVisible(el)) continue;
                            realClick(el);
                            if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                                window.AndroidBridge.onGameLog(tabIdx, "⚡ Tranh chấp phiên: Đã chọn [" + txt + "]");
                            }
                            return "CLICKED_PLAY_HERE";
                        }
                    }
                }

                // 2. PHÁT HIỆN MÀN HÌNH ĐĂNG NHẬP / CHỌN NHÂN VẬT (bị văng phiên)
                // Build 1041: cổng đăng nhập do netGate() dựng trong #modal dưới dạng
                // .dn20[data-dn20] chứa #ngU / #ngP / #ngIn / #ngStart; màn chọn nhân vật là
                // #csel + #csGo. Chỉ hai dấu hiệu cấu trúc này là đủ và không thể nhầm với chữ
                // trong #app của game đang chạy.
                function visById(id) {
                    const e = document.getElementById(id);
                    return !!(e && isVisible(e));
                }
                const gateMarkup = !!(document.getElementById('ngStart') || document.getElementById('ngU') ||
                                      document.getElementById('ngP') || document.getElementById('csel'));
                const gateOnScreen = visById('ngStart') || visById('ngU') || visById('ngP') || visById('csel');

                if (gateMarkup) {
                    if (gateOnScreen || stOutGame) {
                        if (window.AndroidBridge && window.AndroidBridge.onGameKickedDetected) {
                            window.AndroidBridge.onGameKickedDetected(tabIdx);
                        }
                        return "KICKED_DETECTED";
                    }
                } else if (!stInGame) {
                    // Bản cũ không có cổng .dn20 / #csel: giữ nguyên cách dò chữ.
                    let isStartOrLoginVisible = bodyNA.includes('bat dau tro choi') ||
                                                bodyNA.includes('dang ky tai khoan') ||
                                                bodyNA.includes('chua co tai khoan') ||
                                                bodyNA.includes('moi tai khoan 1 nhan vat') ||
                                                bodyNA.includes('nho tai khoan') ||
                                                bodyNA.includes('hien mat ma') ||
                                                bodyNA.includes('tao nhan vat');

                    if (!isStartOrLoginVisible) {
                        for (const el of allElements) {
                            const rawTxt = (el.textContent || el.innerText || '').replace(/\s+/g, ' ').trim();
                            if (!rawTxt || rawTxt.length > 35) continue;
                            const tNA = noAccent(rawTxt);
                            if (tNA === 'bat dau tro choi' || tNA === 'dang ky tai khoan' || tNA === 'xac dinh' || tNA === 'tao nhan vat') {
                                if (isVisible(el)) {
                                    isStartOrLoginVisible = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!isStartOrLoginVisible) {
                        // Ô mật khẩu của bảng "Tài khoản · Lưu đám mây" (#naP) KHÔNG phải cổng
                        // đăng nhập của game: loại nó ra để không tự kích hoạt đăng nhập lại.
                        const visibleAuthInputs = Array.from(document.querySelectorAll('input')).filter(inp => {
                            if (inp.readOnly || inp.disabled) return false;
                            const nid = (inp.id || '').toLowerCase();
                            if (nid === 'nau' || nid === 'nap') return false;
                            const t = (inp.type || '').toLowerCase();
                            if (['hidden', 'checkbox', 'radio', 'button', 'submit'].includes(t)) return false;
                            if (!isVisible(inp)) return false;
                            if (t === 'password') return true;
                            const pTxt = inp.parentElement ? (inp.parentElement.textContent || inp.parentElement.innerText || '') : '';
                            const meta = noAccent((inp.placeholder || '') + ' ' + (inp.name || '') + ' ' + (inp.id || '') + ' ' + pTxt);
                            return meta.includes('mat ma') || meta.includes('mat khau') || meta.includes('password') || meta.includes('tai khoan');
                        });
                        if (visibleAuthInputs.length > 0) {
                            isStartOrLoginVisible = true;
                        }
                    }

                    if (isStartOrLoginVisible) {
                        if (window.AndroidBridge && window.AndroidBridge.onGameKickedDetected) {
                            window.AndroidBridge.onGameKickedDetected(tabIdx);
                        }
                        return "KICKED_DETECTED";
                    }
                }

                // 3. QUÉT THÔNG SỐ NHÂN VẬT
                // Build 1041 là giao diện tab: #t-char / #t-quest / #t-inv mặc định display:none
                // nên innerText của chúng KHÔNG nằm trong bodyText, và mở tab ra chỉ để đọc số
                // là thao tác làm nhảy màn hình người chơi. Ưu tiên gói trạng thái game.
                let charName = '';
                let sect = '';
                let level = '';
                let expPercent = '';
                // Bốn chỉ số dưới đây từng nằm trong HUD chữ của bản 1031. Build 1041 chỉ còn
                // chúng bên trong tab/modal riêng, không có phần tử thường trực nào trên màn hình:
                //   Đại Hồng Bao / Hồng Bao -> modal 🧧 (số lượng nằm trong túi KTC "hb"/"dhb")
                //   Kỷ lục tháp              -> modal Leo Tháp dòng "Kỷ lục: tầng N"
                //   Boss Hoàng Kim           -> chỉ là loại quái trong R, không có bộ đếm hiển thị
                // Gửi chuỗi rỗng thay vì "0": GameTabManager đọc bằng optString(key, prev) nên
                // giá trị cũ được giữ nguyên, không bị ghi đè thành 0 mỗi 2,5 giây.
                let daiHongBao = '';
                let hongBao = '';
                let thapRecord = '';
                let bossHoangKim = '';
                let daTauQuest = '';
                let currentModeText = "Idle";

                if (stp) {
                    if (stp.ten) charName = cleanText(String(stp.ten)).slice(0, 25);
                    if (stp.phaiTen) sect = cleanText(String(stp.phaiTen));
                    if (stp.cap !== undefined && stp.cap !== null && stp.cap !== '') level = String(stp.cap | 0);
                    if (typeof stp.xpp === 'number' && isFinite(stp.xpp)) expPercent = String(stp.xpp) + '%';
                    if (stp.dt && stp.dt.length === 2) {
                        daTauQuest = 'Hôm nay ' + (stp.dt[0] | 0) + '/' + (stp.dt[1] | 0);
                    }
                    // st KHÔNG có trường m/mapTen (app.js vlmSt chỉ phát "noi"): chế độ đọc
                    // từ #mswBtn, trạng thái thuyền đọc từ #stageLbl ở khối DOM bên dưới.
                    if (stp.o === 'uythac') {
                        currentModeText = "💤 Đang uỷ thác";
                    }
                }

                // Dự phòng bằng DOM của chính HUD build 1041 (updateTop ghi mỗi khung hình):
                // #lv / #lvS là cấp, #xpTxt là "12.3%". Không cần mở tab nào.
                if (!level) {
                    const lvEl = document.getElementById('lvS') || document.getElementById('lv');
                    const lvTxt = lvEl ? cleanText(lvEl.textContent || '') : '';
                    if (/^\d+$/.test(lvTxt)) level = lvTxt;
                }
                if (!expPercent) {
                    const xpEl = document.getElementById('xpTxt');
                    const xpTxt = xpEl ? cleanText(xpEl.textContent || '') : '';
                    if (/^\d+(?:[.,]\d+)?%$/.test(xpTxt)) expPercent = xpTxt.replace(',', '.');
                }

                // Bản cũ không có gói trạng thái: giữ nguyên toàn bộ cách dò chữ cũ.
                if (!stp) {
                    // Quét tên nhân vật CÓ GIỚI HẠN: bản cũ duyệt toàn bộ span/div của cả trang mỗi 2500ms.
                    // Nay ưu tiên container chuyên dụng, chỉ dự phòng span/div khi chưa tìm thấy,
                    // cắt ở 400 phần tử đầu và dừng ngay khi có tên để không đốt CPU vô ích.
                    const NAME_SCAN_LIMIT = 400;
                    function isNameLikeText(txt, txtNA) {
                        if (txt.length < 2 || txt.length > 25) return false;
                        if (/^\d+$/.test(txt)) return false;
                        if (txtNA.includes('luc tay') || txtNA.includes('chien luc') || txtNA.includes('che do') ||
                            txtNA.includes('may chu') || txtNA.includes('vo lam') || txtNA.includes('tai khoan') ||
                            txtNA.includes('mat ma') || txtNA.includes('bat dau') || txtNA.includes('dang ky')) return false;
                        if (txt.includes('Cấp') || txt.includes('HP') || txt.includes('MP') || txt.includes('EXP')) return false;
                        return true;
                    }
                    function scanCharNameIn(nodes) {
                        for (const el of nodes) {
                            if (el.children && el.children.length > 2) continue;
                            const txt = cleanText(el.textContent || '');
                            if (!txt) continue;
                            const txtNA = noAccent(txt);
                            if (!isNameLikeText(txt, txtNA)) continue;
                            const parentText = el.parentElement ? (el.parentElement.textContent || '') : '';
                            const parentNA = noAccent(parentText);
                            if ((parentText.includes('Cấp') || parentText.includes('Lv') || parentText.includes('%')) && !parentNA.includes('luc tay 0')) {
                                if (isVisible(el)) return txt;
                            }
                        }
                        return '';
                    }
                    charName = scanCharNameIn(
                        Array.from(document.querySelectorAll('.player-name, .character-name, .font-bold, .text-yellow-400, .text-amber-400')).slice(0, NAME_SCAN_LIMIT)
                    );
                    if (!charName) {
                        charName = scanCharNameIn(Array.from(document.querySelectorAll('span, div')).slice(0, NAME_SCAN_LIMIT));
                    }

                    const sects = ['Thiếu Lâm', 'Thiên Vương', 'Đường Môn', 'Ngũ Độc', 'Nga Mi', 'Thúy Yên', 'Cái Bang', 'Thiên Nhẫn', 'Võ Đang', 'Côn Lôn', 'Đoàn Thị'];
                    for (const s of sects) {
                        if (bodyText.includes(s)) {
                            sect = s;
                            break;
                        }
                    }

                    const levelMatch = bodyText.match(/(?:Cấp|Lv\.?|Level)\s*:?\s*(\d+)/i) || bodyText.match(/(\d{1,3})\s*(?:cấp|level)/i);
                    if (levelMatch) level = levelMatch[1];
                    const expMatch = bodyText.match(/(\d+(?:\.\d+)?)\s*%/);
                    if (expMatch) expPercent = expMatch[1] + '%';

                    const daiHbMatch = bodyText.match(/Đại\s*hồng\s*bao\s*:?\s*(\d+)/i) || bodyText.match(/🧧\s*Đại\s*:?\s*(\d+)/i);
                    if (daiHbMatch) daiHongBao = daiHbMatch[1];

                    const hbMatch = bodyText.match(/(?:Hồng\s*bao|Bao\s*lì\s*xì)\s*:?\s*(\d+)/i) || bodyText.match(/🧧\s*:?\s*(\d+)/i);
                    if (hbMatch) hongBao = hbMatch[1];

                    const thapMatch = bodyText.match(/(?:Kỷ\s*lục\s*tầng\s*tháp|Tầng\s*tháp|Tháp)\s*:?\s*([^\n,]{1,20})/i);
                    if (thapMatch) thapRecord = cleanText(thapMatch[1]);

                    const bossMatch = bodyText.match(/(?:Boss\s*Hoàng\s*Kim|Boss\s*HK)\s*:?\s*(\d+)/i);
                    if (bossMatch) bossHoangKim = bossMatch[1];

                    const daTauMatch = bodyText.match(/(?:Dã\s*Tẩu|Nhiệm\s*vụ\s*Dã\s*Tẩu)\s*:?\s*([^\n,]{1,25})/i);
                    if (daTauMatch) daTauQuest = cleanText(daTauMatch[1]);
                }

                // Chế độ lấy từ #mswBtn: "Chế Độ: ⚔ Luyện Công ▴" -> "⚔ Luyện Công".
                // Trạng thái thuyền lấy từ #stageLbl: "⛵ Phong Lăng Độ".
                if (currentModeText === "Idle" && (!stp || stp.o === 'game')) {
                    var modeBtn = document.getElementById('mswBtn');
                    if (modeBtn) {
                        var modeTxt = cleanText(modeBtn.textContent || '');
                        modeTxt = modeTxt.replace(/^Chế\s*Độ\s*:\s*/i, '').replace(/[▴▾]/g, '').trim();
                        if (modeTxt) currentModeText = modeTxt;
                    }
                    var stageEl = document.getElementById('stageLbl');
                    var stageTxt = stageEl ? cleanText(stageEl.textContent || '') : '';
                    if (stageTxt.indexOf('⛵') >= 0 || stageTxt.indexOf('Phong Lăng') >= 0) {
                        currentModeText = "⛵ Đang đi thuyền";
                    }
                }

                // Không báo "Đang chạy ổn định" khi nhân vật đang ở màn đăng nhập / menu.
                var statusMsg = '🟢 Đang chạy ổn định';
                var stO = stp ? String(stp.o || '') : '';
                if (stO === 'dangnhap') statusMsg = '🔑 Đang đăng nhập (chưa vào game)';
                else if (stO === 'menu') statusMsg = '📋 Đang ở menu chọn nhân vật';
                else if (stO === 'uythac') statusMsg = '💤 Đang uỷ thác';
                else if (currentModeText !== "Idle") statusMsg = '🟢 ' + currentModeText;
                
                let tasksStr = '';
                try {
                    const vnDay = function () {
                        var v = new Date(Date.now() + 7 * 3600e3);
                        return v.getUTCFullYear() + '-' + (v.getUTCMonth() + 1) + '-' + v.getUTCDate();
                    };
                    // Khoá đếm kèm tên nhân vật, khớp với dayKey() trong GameAutoPilot:
                    // mọi tab cùng origin dùng chung localStorage nên khoá trần sẽ khiến
                    // các tài khoản đọc chung bộ đếm của nhau.
                    const getStat = function(k) {
                        try {
                            var who = stp && stp.ten ? String(stp.ten) : '';
                            var key = 'vlm_auto_' + (who ? who + '_' : '') + k;
                            var o = JSON.parse(localStorage.getItem(key) || 'null');
                            return o && o.d === vnDay() ? (o.n | 0) : 0;
                        } catch(e) { return 0; }
                    };
                    let tasks = [];
                    let notDone = [];
                    let pbx = getStat('pbx'); if (pbx > 0) tasks.push('Phó bản'); else notDone.push('Phó bản');
                    let k2k = getStat('k2k'); if (k2k > 0) tasks.push('KNB khóa');
                    let lb = getStat('lb'); if (lb > 0) tasks.push('Lệnh Bài');
                    let knb = getStat('knb'); if (knb > 0) tasks.push('KNB');
                    
                    if (tasks.length === 0) tasks.push('Chưa có');
                    tasksStr = tasks.join(', ');
                    if (notDone.length > 0) tasksStr += ' | Chưa xong: ' + notDone.join(', ');
                } catch(e) {}

                if (window.AndroidBridge && window.AndroidBridge.onGameStateScanned) {
                    window.AndroidBridge.onGameStateScanned(tabIdx, JSON.stringify({
                        isOnline: true,
                        characterName: charName,
                        sect: sect,
                        level: level,
                        expPercent: expPercent,
                        daiHongBao: daiHongBao,
                        hongBao: hongBao,
                        thapRecord: thapRecord,
                        bossHoangKim: bossHoangKim,
                        daTauQuest: daTauQuest,
                        tasksCompleted: tasksStr,
                        lastStatus: statusMsg
                    }));
                }
                return "SCANNED_SUCCESS";
            } catch(e) {
                return "ERROR: " + e.message;
            }
        })();
        """.trimIndent()
    }

    fun buildDomGarbageCollectionScript(): String {
        return """
        (function() {
            try {
                // Không dọn khi người dùng đang gõ (chat/ô nhập đang focus) — tránh xoá node
                // ngay dưới tay người dùng và tránh làm handler blur/focus chạy sai thứ tự.
                const ae = document.activeElement;
                if (ae) {
                    const t = (ae.tagName || '').toUpperCase();
                    if (t === 'INPUT' || t === 'TEXTAREA' || t === 'SELECT' || ae.isContentEditable) return;
                }
                // Chỉ dùng id thật của game (chat log không có class). TUYỆT ĐỐI không dùng
                // [class*="chat"]: selector đó khớp cả body.nochat, và script xoá các node con
                // đầu tiên -> có thể xoá #app và làm sập toàn bộ game.
                const chatSelectors = ['#chatLines', '#u20lines'];
                for (const selector of chatSelectors) {
                    const containers = document.querySelectorAll(selector);
                    containers.forEach(container => {
                        const items = Array.from(container.children);
                        if (items.length > 30) {
                            const toRemove = items.length - 30;
                            for (let i = 0; i < toRemove; i++) {
                                if (items[i] && items[i].remove) {
                                    items[i].remove();
                                }
                            }
                        }
                    });
                }
                // Dọn các hiệu ứng số sát thương nổi (floating text) bị tồn đọng nếu có.
                // Bỏ [class*="floating"] (khớp quá rộng), chỉ dùng class cụ thể.
                const floatingSelectors = ['.floating-text', '.damage-text', '.damage-number'];
                for (const sel of floatingSelectors) {
                    const nodes = document.querySelectorAll(sel);
                    if (nodes.length > 20) {
                        for (let i = 0; i < nodes.length - 10; i++) {
                            try { nodes[i].remove(); } catch(e) {}
                        }
                    }
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }

    fun buildFpsLimiterScript(fps: Int): String {
        val intervalMs = if (fps <= 30) 33 else 16
        return """
        (function() {
            window.__targetFrameIntervalMs = $intervalMs;
            if (window.__fpsThrottlerInjected) return;
            window.__fpsThrottlerInjected = true;

            // =========================================================================
            // 1. GIẢ LẬP TRANG LUÔN HIỂN THỊ (CHỐNG TẠM DỪNG VẼ KHI CHUYỂN APP KHÁC)
            // Ngăn game nhận sự kiện ẩn tab (visibilitychange / blur) gây tích tụ gói tin
            // và nhảy vị trí nhân vật khi quay lại game.
            // =========================================================================
            try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                document.hasFocus = function() { return true; };

                const blockBgEvents = function(e) {
                    try {
                        e.stopImmediatePropagation();
                        e.stopPropagation();
                    } catch(err) {}
                };
                window.addEventListener('visibilitychange', blockBgEvents, true);
                document.addEventListener('visibilitychange', blockBgEvents, true);
                document.addEventListener('webkitvisibilitychange', blockBgEvents, true);
                // CHỈ chặn blur của CHÍNH window (app mất focus), KHÔNG chặn blur của từng ô nhập.
                // Blur của element cũng đi qua capture trên window; chặn tất cả sẽ giết mọi handler
                // onblur của trang. Game dựa vào blur của #u20txt để gỡ class body.u2kb (chế độ bàn
                // phím ảo); nếu blur bị chặn thì u2kb kẹt vĩnh viễn -> ẩn thanh kỹ năng, thu khung
                // chat còn 3 dòng, khoá cuộn trang => "chat xong là bị treo khung".
                window.addEventListener('blur', function(e) {
                    if (e.target === window || e.target === document) blockBgEvents(e);
                }, true);
                window.addEventListener('pagehide', blockBgEvents, true);
            } catch(e) {}

            // =========================================================================
            // 2. BỘ ĐIỀU PHỐI KHUNG HÌNH HYBRID (VSYNC + BACKGROUND TIMER + ANTI-RUBBERBAND)
            // - Khi đang mở app: dùng VSync mượt theo tốc độ khóa FPS (30/60 FPS).
            // - Khi chuyển sang app khác (Android ngắt VSync): tự động chuyển sang bộ nhịp
            //   ngầm 20 FPS bằng setTimeout để nhân vật tiếp tục di chuyển thời gian thực,
            //   không bị dồn hàng đợi gói tin WebSocket.
            // - Kẹp (Clamp) bước nhảy thời gian (delta time <= 50ms) để khi quay lại app
            //   tuyệt đối KHÔNG bị tua nhanh, giật hình hay nhảy vị trí!
            // =========================================================================
            let lastFrameTime = performance.now();
            let virtualTime = lastFrameTime;
            const originalRAF = window.requestAnimationFrame ? window.requestAnimationFrame.bind(window) : null;
            const originalCAF = window.cancelAnimationFrame ? window.cancelAnimationFrame.bind(window) : null;

            // Sổ đăng ký khung hình đang chờ: handleId -> { timer, innerTimer, rafId, done }.
            // requestAnimationFrame của shim trả về handleId (không phải id của setTimeout),
            // nhờ vậy cancelAnimationFrame hủy được CẢ VSync, timer chờ VSync và fallback timer.
            const pendingRafHandles = new Map();
            let nextRafHandleId = 1;

            window.requestAnimationFrame = function(callback) {
                const targetInterval = window.__targetFrameIntervalMs || 33;
                const bgInterval = 50; // 20 nhịp/giây khi chạy ngầm ở app khác
                const handleId = nextRafHandleId++;
                const handle = { timer: null, innerTimer: null, rafId: null, done: false };
                pendingRafHandles.set(handleId, handle);

                function releaseHandle() {
                    if (handle.timer !== null) { clearTimeout(handle.timer); handle.timer = null; }
                    if (handle.innerTimer !== null) { clearTimeout(handle.innerTimer); handle.innerTimer = null; }
                    pendingRafHandles.delete(handleId);
                }

                function invokeSmoothly(rawNow) {
                    if (handle.done) return;
                    handle.done = true;
                    // Dọn mọi timer còn treo TRƯỚC khi gọi callback, để không còn timer mồ côi
                    releaseHandle();

                    const now = (typeof rawNow === 'number' && rawNow > 0) ? rawNow : performance.now();
                    const elapsed = now - lastFrameTime;
                    lastFrameTime = now;

                    // Chống nhảy vị trí (Anti-Jump): Nếu vừa chuyển từ app khác về và có độ trễ lớn,
                    // giới hạn bước tiến thời gian tối đa 50ms thay vì nhảy vọt hàng chục giây!
                    const safeDelta = (elapsed > 0 && elapsed <= 50) ? elapsed : Math.min(Math.max(elapsed, 16), 50);
                    virtualTime += safeDelta;

                    try {
                        callback(virtualTime);
                    } catch(err) {}
                }

                // Nếu đang chạy ngầm ở app khác, dùng trực tiếp bộ đếm setTimeout để giữ game luôn chạy thực tế
                if (window.__isAppInBackground || !originalRAF) {
                    handle.timer = setTimeout(function() {
                        invokeSmoothly(performance.now());
                    }, bgInterval);
                    return handleId;
                }

                // Khi ở tiền cảnh: kết hợp originalRAF (VSync) và phao cứu sinh fallback nếu VSync bị ngắt đột ngột
                const nowCheck = performance.now();
                const waitMs = Math.max(0, targetInterval - (nowCheck - lastFrameTime));

                handle.timer = setTimeout(function() {
                    invokeSmoothly(performance.now());
                }, Math.max(waitMs + 45, 75));

                const scheduleVSync = function() {
                    if (handle.done || !originalRAF) return;
                    handle.rafId = originalRAF(function(ts) {
                        handle.rafId = null;
                        invokeSmoothly(ts);
                    });
                };

                if (waitMs <= 4) {
                    scheduleVSync();
                } else {
                    handle.innerTimer = setTimeout(function() {
                        handle.innerTimer = null;
                        if (handle.done) return;
                        scheduleVSync();
                    }, waitMs);
                }

                return handleId;
            };

            // Shim cancelAnimationFrame: hủy VSync đã đăng ký + mọi timer dự phòng của lần gọi đó.
            // Nếu không có trong sổ (id do trang khác tạo trước khi inject) thì chuyển cho bản gốc.
            window.cancelAnimationFrame = function(handleId) {
                const handle = pendingRafHandles.get(handleId);
                if (!handle) {
                    if (originalCAF) { try { originalCAF(handleId); } catch(e) {} }
                    return;
                }
                handle.done = true;
                if (handle.timer !== null) { clearTimeout(handle.timer); handle.timer = null; }
                if (handle.innerTimer !== null) { clearTimeout(handle.innerTimer); handle.innerTimer = null; }
                if (handle.rafId !== null && originalCAF) {
                    try { originalCAF(handle.rafId); } catch(e) {}
                    handle.rafId = null;
                }
                pendingRafHandles.delete(handleId);
            };

            // =========================================================================
            // 3. KÍCH HOẠT AUDIO CONTEXT IM LẶNG ĐỂ CHROMIUM KHÔNG ĐÓNG BĂNG RENDERER NGẦM
            // =========================================================================
            function initSilentKeepAliveAudio() {
                if (window.__silentAudioStarted) return;
                try {
                    const AudioCtx = window.AudioContext || window.webkitAudioContext;
                    if (!AudioCtx) return;
                    const ctx = new AudioCtx();
                    const osc = ctx.createOscillator();
                    const gain = ctx.createGain();
                    gain.gain.value = 0.00001; // Hoàn toàn im lặng
                    osc.connect(gain);
                    gain.connect(ctx.destination);
                    osc.start(0);
                    window.__silentAudioCtx = ctx;
                    window.__silentAudioStarted = true;
                } catch(e) {}
            }
            window.addEventListener('pointerdown', initSilentKeepAliveAudio, { once: true, passive: true });
            window.addEventListener('touchstart', initSilentKeepAliveAudio, { once: true, passive: true });
        })();
        """.trimIndent()
    }

    fun buildSyncAutoModeScript(modeText: String): String {
        val safeModeJson = quote(modeText)
        return """
        (function(rawMode) {
            try {
                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function realClick(el) {
                    if (!el) return;
                    try {
                        const rect = el.getBoundingClientRect();
                        const cx = rect.left + rect.width / 2;
                        const cy = rect.top + rect.height / 2;
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(type => {
                            try {
                                el.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof el.click === 'function') {
                        try { el.click(); } catch(e) {}
                    }
                }

                const reqNoAccent = noAccent(rawMode);
                // Chuẩn hóa chính xác Title Case giống hệt cách "Phó Bản" hoạt động trong game:
                // "Nhiệm Vụ", "Phó Bản", "Luyện Công"
                let titleCaseMode = rawMode;
                let lowerMode = norm(rawMode);
                let valueAliases = [];

                if (reqNoAccent.includes('nhiem vu') || reqNoAccent.includes('da tau') || reqNoAccent.includes('quest')) {
                    titleCaseMode = 'Nhiệm Vụ';
                    lowerMode = 'nhiệm vụ';
                    valueAliases = ['nhiem vu', 'nhiemvu', 'nhiem_vu', 'quest', 'task', 'datau', 'da tau'];
                } else if (reqNoAccent.includes('pho ban') || reqNoAccent.includes('dungeon')) {
                    titleCaseMode = 'Phó Bản';
                    lowerMode = 'phó bản';
                    valueAliases = ['pho ban', 'phoban', 'pho_ban', 'dungeon', 'instance'];
                } else if (reqNoAccent.includes('luyen cong') || reqNoAccent.includes('train') || reqNoAccent.includes('grind')) {
                    titleCaseMode = 'Luyện Công';
                    lowerMode = 'luyện công';
                    valueAliases = ['luyen cong', 'luyencong', 'luyen_cong', 'train', 'training', 'grind', 'farm'];
                }

                const targetNoAccent = noAccent(titleCaseMode);

                // =========================================================================
                // ĐƯỜNG CHÍNH (build 1041): KÊNH ẨN #vlmAuto -> VLM_AUTO.cheDo({m})
                // app.js khai báo MODES = [["farm","⚔","Luyện Công",…],["dg","🏯","Phó Bản",…],
                // ["quest","📜","Nhiệm Vụ",…],["tower","🗼","Leo Tháp",…]] và cheDo() gọi đúng
                // setMode() mà nút "Chế Độ:" vẫn gọi. Vì vậy đi qua kênh ẩn bền hơn bấm DOM:
                // không phụ thuộc dropdown có đang mở hay không, và tự kiểm được kết quả.
                // =========================================================================
                var modeId = '';
                if (targetNoAccent === 'nhiem vu') modeId = 'quest';
                else if (targetNoAccent === 'pho ban') modeId = 'dg';
                else if (targetNoAccent === 'luyen cong') modeId = 'farm';
                else if (targetNoAccent === 'leo thap') modeId = 'tower';

                // Bấm thẳng mục trong menu "Chế Độ:" — dùng khi kênh ẩn từ chối (game chưa vào
                // hoặc bản cũ không có cheDo). #mswMenu do modeSwSync() dựng, mỗi mục gắn sẵn
                // onclick = setMode(data-msw) nên bấm được cả khi menu đang đóng.
                function clickModeMenuItem() {
                    if (!modeId) return false;
                    var item = document.querySelector('#mswMenu [data-msw="' + modeId + '"]');
                    if (!item) return false;
                    try { item.click(); return true; } catch (eMsw) { return false; }
                }

                function syncModeViaChannel() {
                    if (!modeId) return false;
                    var msw = document.getElementById('modeSw');
                    // .hidden = chưa vào game (modeSwSync gắn class khi !S.fac): để đường DOM cũ xử lý.
                    if (!msw || msw.classList.contains('hidden')) return false;

                    var mswBtn = document.getElementById('mswBtn');
                    if (mswBtn && noAccent(mswBtn.textContent || '').indexOf(targetNoAccent) >= 0) {
                        return 'ALREADY_IN_MODE:' + titleCaseMode;
                    }

                    var host = document.body || document.documentElement;
                    if (!host) return false;

                    var btn = document.createElement('button');
                    btn.id = 'vlmAuto';
                    btn.type = 'button';
                    btn.style.display = 'none';
                    btn.dataset.act = 'cheDo';
                    btn.dataset.arg = JSON.stringify({ m: modeId });
                    host.appendChild(btn);
                    try { btn.click(); } catch (eC0) {
                        try { btn.remove(); } catch (eC1) {}
                        return false;
                    }

                    var deadline = Date.now() + 6000;
                    var poll = function () {
                        var finished = false;
                        try { finished = !!btn.dataset.xong; } catch (eC2) {}
                        if (finished) {
                            var kq = {};
                            try { kq = JSON.parse(btn.dataset.kq || '{}') || {}; } catch (eC3) {}
                            try { btn.remove(); } catch (eC4) {}
                            // kq.ok !== true: lệnh không chạy (chưa vào game / bản cũ) -> bấm nút DOM.
                            // kq.ok === true nhưng m khác: chính game từ chối (vd Leo Tháp cần cấp
                            // TOWER_LV) và đã tự đặt lại chế độ — bấm lại nút chỉ lặp lại lời từ chối.
                            if (kq.ok !== true) {
                                if (!clickModeMenuItem()) {
                                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                                        // Tham số đầu bị AndroidGameBridge bỏ qua: bridge đã gắn
                                        // sẵn đúng tabIndex của WebView đang chạy script này.
                                        window.AndroidBridge.onGameLog(0, '⚠ Không đổi được chế độ ' + titleCaseMode + ': ' + String(kq.why || 'không rõ') + ' — thử lại');
                                    }
                                }
                            }
                            return;
                        }
                        if (Date.now() >= deadline) {
                            try { btn.remove(); } catch (eC5) {}
                            clickModeMenuItem();
                            return;
                        }
                        setTimeout(poll, 120);
                    };
                    setTimeout(poll, 120);
                    return 'OPENED_TRIGGER_AND_SELECTING:' + titleCaseMode;
                }

                var channelResult = syncModeViaChannel();
                if (channelResult) return channelResult;

                function isMatchingOption(textStr, valStr) {
                    const tRaw = (textStr || '').trim();
                    const vRaw = (valStr || '').trim();
                    if (tRaw.includes(titleCaseMode) || vRaw.includes(titleCaseMode)) return true;
                    const tNorm = norm(tRaw);
                    const vNorm = norm(vRaw);
                    if (tNorm.includes(lowerMode) || vNorm.includes(lowerMode)) return true;
                    const tNA = noAccent(tRaw);
                    const vNA = noAccent(vRaw);
                    if (tNA.includes(targetNoAccent) || vNA.includes(targetNoAccent)) return true;
                    return valueAliases.some(alias => tNA.includes(alias) || vNA === alias || vNA.includes(alias));
                }

                // =========================================================================
                // CƠ CHẾ 1 (CHUẨN NHẤT): TÌM THẺ <select> CỦA "Chế Độ:" / "Chế độ:"
                // Lưu ý: Trong game chữ "Chế Độ:" viết hoa chữ Đ và các option viết hoa
                // chữ cái đầu ("⚔️ Luyện Công", "⚔️ Phó Bản", "📜 Nhiệm Vụ").
                // =========================================================================
                const selects = Array.from(document.querySelectorAll('select'));
                let targetSelect = null;

                // Ưu tiên 1A: Quét <select> có chứa các option Nhiệm Vụ / Phó Bản / Luyện Công
                for (const s of selects) {
                    const opts = Array.from(s.options || []);
                    const isModeSelect = opts.some(o => {
                        const na = noAccent(o.text || o.textContent || '');
                        const va = noAccent(o.value || '');
                        return na.includes('pho ban') || na.includes('luyen cong') || na.includes('nhiem vu') ||
                               va.includes('phoban') || va.includes('luyencong') || va.includes('nhiemvu') ||
                               va.includes('dungeon') || va.includes('train') || va.includes('quest');
                    });
                    if (isModeSelect) {
                        targetSelect = s;
                        break;
                    }
                }

                // Ưu tiên 1B: Quét <select> mà phần tử cha có chữ "Chế Độ" hoặc "Chế độ" (không phân biệt hoa/thường)
                if (!targetSelect) {
                    for (const s of selects) {
                        const parentText = s.parentElement ? s.parentElement.innerText : '';
                        const containerText = s.closest('label, div') ? s.closest('label, div').innerText : '';
                        if (noAccent(parentText).includes('che do') || noAccent(containerText).includes('che do')) {
                            targetSelect = s;
                            break;
                        }
                    }
                }

                if (targetSelect) {
                    const options = Array.from(targetSelect.options);
                    for (let i = 0; i < options.length; i++) {
                        const opt = options[i];
                        if (isMatchingOption(opt.text || opt.textContent, opt.value)) {
                            targetSelect.selectedIndex = i;
                            opt.selected = true;

                            // Ép cập nhật giá trị chuẩn React/Vue + Native DOM giống hệt cơ chế Phó Bản
                            if (targetSelect._valueTracker) {
                                try { targetSelect._valueTracker.setValue(''); } catch(e) {}
                            }
                            try {
                                const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value')?.set;
                                if (nativeSetter) {
                                    nativeSetter.call(targetSelect, opt.value);
                                } else {
                                    targetSelect.value = opt.value;
                                }
                            } catch(e) {
                                targetSelect.value = opt.value;
                            }
                            targetSelect.value = opt.value;
                            targetSelect.dispatchEvent(new Event('input', { bubbles: true }));
                            targetSelect.dispatchEvent(new Event('change', { bubbles: true }));
                            return "SELECTED_OPTION:" + (opt.text || opt.value);
                        }
                    }
                }

                // =========================================================================
                // CƠ CHẾ 2: NẾU CHẾ ĐỘ LÀ NÚT BẤM / CUSTOM DROPDOWN TRONG DOM
                // Loại trừ thanh menu dưới đáy màn hình (Nhân vật | Hành trang | Võ công | Lò rèn | Nhiệm vụ | Tổ đội | Hệ thống)
                // để khi bấm "Nhiệm Vụ" KHÔNG bị bấm nhầm vào bảng Nhiệm vụ ở đáy màn hình!
                // =========================================================================
                function isBottomMenuBarElement(el) {
                    let cur = el;
                    for (let depth = 0; depth < 4 && cur; depth++) {
                        const t = noAccent(cur.innerText || '');
                        if ((t.includes('hanh trang') && t.includes('he thong')) ||
                            (t.includes('vo cong') && t.includes('lo ren')) ||
                            (t.includes('nhan vat') && t.includes('to doi'))) {
                            return true;
                        }
                        cur = cur.parentElement;
                    }
                    return false;
                }

                const candidates = Array.from(document.querySelectorAll('button, div[role="button"], li, option, span, a, div'));

                // Bước 2A: Tìm phần tử khớp trực tiếp với Title Case ("Nhiệm Vụ", "Phó Bản", "Luyện Công") giống hệt "Phó Bản"
                // nhưng không phải là nút ở thanh menu đáy và không phải là nhãn bao ngoài chứa cả cụm "Chế Độ:"
                for (const b of candidates) {
                    if (b.children && b.children.length > 2) continue;
                    const txt = (b.innerText || b.textContent || '').trim();
                    if (!txt || txt.length > 35) continue;
                    if (isBottomMenuBarElement(b)) continue;
                    const txtNA = noAccent(txt);
                    if (txtNA.includes('che do')) continue;

                    if (txt === titleCaseMode || txt.includes(titleCaseMode) || txtNA === targetNoAccent || txtNA.includes(targetNoAccent)) {
                        realClick(b);
                        return "CLICKED_DIRECT:" + txt;
                    }
                }

                // Bước 2B: Nếu "Chế Độ: ⚔️ Luyện Công ▾" là một nút đóng/mở menu (Custom Dropdown Trigger)
                // Kiểm tra xem nút "Chế Độ:" hiện tại đã ở đúng chế độ chưa; nếu chưa thì mở menu rồi chọn!
                let modeTrigger = null;
                for (const el of candidates) {
                    const txt = (el.innerText || el.textContent || '').trim();
                    const txtNA = noAccent(txt);
                    if (txtNA.startsWith('che do:') || txtNA.startsWith('che do :')) {
                        if (txt.length <= 45) {
                            modeTrigger = el;
                            break;
                        }
                    }
                }

                if (modeTrigger) {
                    const currentTriggerNA = noAccent(modeTrigger.innerText || modeTrigger.textContent || '');
                    if (currentTriggerNA.includes(targetNoAccent)) {
                        return "ALREADY_IN_MODE:" + titleCaseMode;
                    }
                    // Mở dropdown "Chế Độ:" và chọn mục tương ứng sau 80ms
                    realClick(modeTrigger);
                    setTimeout(() => {
                        try {
                            const popupItems = Array.from(document.querySelectorAll('button, div[role="button"], li, span, a, div'));
                            for (const item of popupItems) {
                                if (item === modeTrigger) continue;
                                if (item.children && item.children.length > 2) continue;
                                const t = (item.innerText || item.textContent || '').trim();
                                if (!t || t.length > 35) continue;
                                if (isBottomMenuBarElement(item)) continue;
                                const tNA = noAccent(t);
                                if (tNA.includes('che do')) continue;
                                if (t === titleCaseMode || t.includes(titleCaseMode) || tNA.includes(targetNoAccent)) {
                                    realClick(item);
                                    break;
                                }
                            }
                        } catch(e) {}
                    }, 80);
                    return "OPENED_TRIGGER_AND_SELECTING:" + titleCaseMode;
                }

                return "NOT_FOUND";
            } catch(e) {
                return "ERROR:" + e.message;
            }
        })($safeModeJson);
        """.trimIndent()
    }

    // Đồng bộ bật/tắt "Màn hình đen" cho cả 4 Tab theo trình tự: Hệ thống (⚙️) -> Hiển Thị -> Màn hình đen
    fun buildSyncBlackScreenScript(enabled: Boolean): String {
        val targetState = if (enabled) "true" else "false"
        return """
        (function(targetEnabled) {
            try {
                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function realClick(el) {
                    if (!el) return;
                    const target = el.closest('button, [role="button"], [role="tab"], a, label') || el;
                    try {
                        const rect = target.getBoundingClientRect();
                        const cx = rect.left + rect.width / 2;
                        const cy = rect.top + rect.height / 2;
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(type => {
                            try {
                                target.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof target.click === 'function') {
                        try { target.click(); } catch(e) {}
                    }
                }

                // Tìm ô checkbox đứng trước dòng "Màn hình đen" trong mục Hệ thống -> Hiển Thị
                function findBlackScreenCheckbox() {
                    const checkboxes = Array.from(document.querySelectorAll('input[type="checkbox"]'));
                    for (const cb of checkboxes) {
                        const labelEl = cb.closest('label');
                        const labelText = noAccent(labelEl ? labelEl.innerText : '');
                        const parentText = noAccent(cb.parentElement ? cb.parentElement.innerText : '');
                        const nextText = noAccent(cb.nextElementSibling ? cb.nextElementSibling.innerText : '');
                        if (labelText.includes('man hinh den') || parentText.includes('man hinh den') || nextText.includes('man hinh den')) {
                            return cb;
                        }
                        const grandParent = cb.parentElement ? cb.parentElement.parentElement : null;
                        const grandParentText = noAccent(grandParent ? grandParent.innerText : '');
                        if (grandParentText.includes('man hinh den') && grandParentText.length < 200) {
                            return cb;
                        }
                    }
                    return null;
                }

                // Tích hoặc bỏ tích ô checkbox chuẩn React/DOM (không bị click đúp làm đảo ngược trạng thái)
                function setCheckboxState(cb, desired) {
                    if (!cb) return false;
                    if (Boolean(cb.checked) === Boolean(desired)) {
                        return true;
                    }
                    try { cb.focus(); } catch(e) {}
                    try { cb.click(); } catch(e) {}
                    if (Boolean(cb.checked) === Boolean(desired)) {
                        return true;
                    }
                    const parentLabel = cb.closest('label');
                    if (parentLabel) {
                        try { parentLabel.click(); } catch(e) {}
                        if (Boolean(cb.checked) === Boolean(desired)) {
                            return true;
                        }
                    }
                    try {
                        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'checked')?.set;
                        if (setter) {
                            setter.call(cb, desired);
                        } else {
                            cb.checked = desired;
                        }
                    } catch(e) {
                        cb.checked = desired;
                    }
                    if (cb._valueTracker) {
                        try { cb._valueTracker.setValue(String(!desired)); } catch(e) {}
                    }
                    try { cb.dispatchEvent(new Event('click', { bubbles: true })); } catch(e) {}
                    try { cb.dispatchEvent(new Event('input', { bubbles: true })); } catch(e) {}
                    try { cb.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                    return Boolean(cb.checked) === Boolean(desired);
                }

                // Nếu bỏ chọn (targetEnabled === false) và đang có lớp phủ "Màn hình đen - bấm vào màn hình để bật hình lại"
                function tryWakeBlackScreenOverlayIfNeeded() {
                    if (targetEnabled) return false;
                    const els = Array.from(document.querySelectorAll('div, canvas, span, p'));
                    for (const el of els) {
                        const t = noAccent(el.innerText || el.textContent || '');
                        if ((t.includes('man hinh den') && t.includes('game van chay')) ||
                            t.includes('bam vao man hinh de bat')) {
                            realClick(el);
                            return true;
                        }
                    }
                    return false;
                }

                // Tìm nút "Hệ thống" (hình bánh răng ở góc dưới màn hình game)
                function findHeThongButton() {
                    const els = Array.from(document.querySelectorAll('button, [role="button"], a, div, span'));
                    for (const el of els) {
                        if (el.children && el.children.length > 3) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'he thong' || (t.includes('he thong') && t.length <= 18)) {
                            return el;
                        }
                    }
                    return null;
                }

                // Tìm tab "Hiển Thị" / "Hiển thị" bên trong cửa sổ Hệ thống
                function findHienThiTab() {
                    const els = Array.from(document.querySelectorAll('button, [role="tab"], [role="button"], li, a, div, span'));
                    for (const el of els) {
                        if (el.children && el.children.length > 3) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'hien thi' || (t.includes('hien thi') && !t.includes('man hinh') && !t.includes('hieu ung') && t.length <= 20)) {
                            return el;
                        }
                    }
                    return null;
                }

                // Bước 0: Nếu bảng Hệ thống -> Hiển Thị đang mở sẵn trên màn hình thì tích/bỏ tích ngay lập tức!
                const existingCb = findBlackScreenCheckbox();
                if (existingCb) {
                    setCheckboxState(existingCb, targetEnabled);
                    if (!targetEnabled) {
                        tryWakeBlackScreenOverlayIfNeeded();
                    }
                    return "TOGGLED_IMMEDIATELY:" + targetEnabled;
                }

                // Nếu bỏ chọn và màn hình đang bị che bởi lớp phủ Màn hình đen, đánh thức màn hình trước
                if (!targetEnabled) {
                    tryWakeBlackScreenOverlayIfNeeded();
                }

                // Bước 1: Kiểm tra xem tab "Hiển Thị" đã xuất hiện chưa; nếu chưa thì bấm mở "Hệ thống" (⚙️)
                const existingHienThi = findHienThiTab();
                if (existingHienThi) {
                    realClick(existingHienThi);
                } else {
                    const heThongBtn = findHeThongButton();
                    if (heThongBtn) {
                        realClick(heThongBtn);
                    }
                }

                // Bước 2: Sau 150ms khi bảng Hệ thống mở ra -> Bấm vào tab "Hiển Thị" (hoặc tích luôn nếu đã ở tab Hiển Thị)
                setTimeout(() => {
                    try {
                        let cb = findBlackScreenCheckbox();
                        if (cb) {
                            setCheckboxState(cb, targetEnabled);
                            return;
                        }
                        const hienThiTab = findHienThiTab();
                        if (hienThiTab) {
                            realClick(hienThiTab);
                        }
                        // Bước 3: Sau khi mở tab "Hiển Thị" 150ms -> Tìm ô trắng "Màn hình đen" để tích/bỏ tích
                        setTimeout(() => {
                            try {
                                cb = findBlackScreenCheckbox();
                                if (cb) {
                                    setCheckboxState(cb, targetEnabled);
                                }
                            } catch(e) {}
                        }, 150);
                    } catch(e) {}
                }, 150);

                return "SEQUENCE_STARTED:" + targetEnabled;
            } catch(e) {
                return "ERROR:" + e.message;
            }
        })($targetState);
        """.trimIndent()
    }
}