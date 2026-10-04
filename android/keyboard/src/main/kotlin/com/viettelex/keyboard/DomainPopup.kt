package com.viettelex.keyboard

import kotlin.math.floor

/**
 * Popup nhiều lựa chọn kiểu stock iOS: giữ phím ⇒ hàng ô, ô gốc chọn sẵn, trượt để chọn,
 * nhấc chèn, trượt xa huỷ. Bản THUẦN song sinh iOS/Keyboard/DomainPopup.swift — sửa ở đây
 * thì sửa y hệt bên kia. Dùng cho:
 *  - giữ "." ở bàn CHỮ ô URL / thanh địa chỉ / email ⇒ đuôi tên miền ([tlds]);
 *  - giữ phím bàn số / ký hiệu ⇒ biến thể ký tự ([KeyVariants]).
 * Toạ độ trong file này là dp (view chia mật độ trước khi gọi).
 */
object DomainPopup {
    /** Ô có hàng đuôi tên miền (app ánh xạ từ loại ô Android). */
    enum class Field { NORMAL, URL, EMAIL }

    /**
     * Đuôi tên miền — .com mặc định như stock, .vn / .com.vn kế tiếp cho người dùng Việt
     * (issue #113: bỏ phím ".com" riêng ở ô URL, gom vào giữ ".").
     */
    val tlds = listOf(".com", ".vn", ".com.vn", ".net", ".org", ".edu")

    /** Phím `key` ở bàn CHỮ của ô [field] có popup không — chỉ phím "." ô URL / email; ô khác rỗng (0 chi phí). */
    fun choices(field: Field, key: String, lettersPlane: Boolean): List<String> {
        if (!lettersPlane || key != ".") return emptyList()
        return when (field) {
            Field.URL, Field.EMAIL -> tlds
            Field.NORMAL -> emptyList()
        }
    }

    /**
     * Bố cục hàng ô. Ô đầu (mặc định) nằm ngay trên tâm phím; không đủ chỗ bên phải thì lật
     * ngược (ô đầu ở bên phải, hàng loe sang trái) như stock, rồi kẹp trong [margin, width − margin].
     */
    data class Layout(val originX: Float, val itemWidth: Float, val count: Int,
                      /** true ⇒ ô 0 ở bên PHẢI (hàng đọc từ phải sang trái). */
                      val mirrored: Boolean) {
        val width: Float get() = itemWidth * count
        /** Mép trái ô [index]. */
        fun slotMinX(index: Int): Float {
            val slot = if (mirrored) count - 1 - index else index
            return originX + slot * itemWidth
        }
    }

    fun layout(keyMidX: Float, itemWidth: Float, count: Int, containerWidth: Float, margin: Float = 2f): Layout {
        val w = itemWidth * count
        val rightFits = keyMidX - itemWidth / 2 + w <= containerWidth - margin
        val raw = if (rightFits) keyMidX - itemWidth / 2 else keyMidX + itemWidth / 2 - w
        val x = minOf(maxOf(raw, margin), maxOf(margin, containerWidth - margin - w))
        return Layout(x, itemWidth, count, !rightFits)
    }

    /** Ngón trượt ra ngoài hàng quá ngần này (ngang, dp) ⇒ huỷ. */
    const val CANCEL_SLACK_X = 30f
    /** Ngón trượt lên trên đỉnh popup / xuống dưới đáy phím quá ngần này (dp) ⇒ huỷ. */
    const val CANCEL_SLACK_Y = 40f

    /**
     * Lựa chọn theo độ dời ngang của ngón so với lúc mở ([startX]): lúc mở luôn là ô 0 — kể
     * cả khi hàng bị kẹp mép không nằm đúng dưới ngón; mỗi bề rộng ô trượt theo hướng hàng
     * loe ⇒ sang ô kế. null = huỷ (ra ngoài hàng/phím quá slack). [top] = đỉnh popup,
     * [bottom] = đáy phím. Quay lại gần hàng thì chọn lại được (như stock).
     */
    fun index(x: Float, y: Float, startX: Float, layout: Layout, top: Float, bottom: Float): Int? {
        val l = layout
        if (l.count <= 0 || x < l.originX - CANCEL_SLACK_X || x > l.originX + l.width + CANCEL_SLACK_X ||
            y < top - CANCEL_SLACK_Y || y > bottom + CANCEL_SLACK_Y) return null
        val q = (x - startX) / l.itemWidth
        // làm tròn nửa xa 0 như Swift .rounded()
        val step = if (q >= 0) floor(q + 0.5f).toInt() else -floor(-q + 0.5f).toInt()
        val i = if (l.mirrored) -step else step
        return i.coerceIn(0, l.count - 1)
    }

    /**
     * MỘT lượt giữ phím có popup (thuần, không đồng hồ): view chạm ⇒ phím đã [KeyCommitQueue.arm]
     * ký tự gốc; hết giờ view gọi [fire] — nếu phím còn chờ chốt thì đổi thành "chèn ô đang
     * chọn" (chốt lúc nhấc / khi ngón khác chạm, cùng đường với phím thường ⇒ thứ tự đúng).
     * Trượt xa ⇒ không chọn ⇒ nhấc không chèn gì. Toạ độ dp, cùng hệ với [layout].
     */
    class Hold(val choices: List<String>) {
        var fired = false; private set
        var layout: Layout? = null; private set
        /** Ô đang chọn; null = huỷ (trượt xa) hoặc chưa mở. */
        var selection: Int? = null; private set
        private var startX = 0f
        private var top = 0f
        private var bottom = 0f

        /** Chữ sẽ chèn khi nhấc (null = không gì). */
        val chosen: String? get() = selection?.let { choices.getOrNull(it) }

        /**
         * Hết giờ giữ. false ⇒ phím đã chốt (ngón khác chạm trước) — không mở popup.
         * [insert] chạy lúc chốt với ô đang chọn (không gọi nếu đã huỷ).
         */
        fun fire(commits: KeyCommitQueue, key: Any, layout: Layout, top: Float, bottom: Float,
                 x: Float, y: Float, insert: (String) -> Unit): Boolean {
            if (fired || !commits.isArmed(key)) return false
            commits.disarm(key)
            commits.arm(key) { chosen?.let(insert) }
            return open(layout, top, bottom, x, y)
        }

        /**
         * Mở hàng KHÔNG qua hàng chốt — menu hành động (giữ 😊, [EmojiKeyMenu]): view đọc
         * [selection] lúc nhấc rồi tự chạy. false nếu đã mở.
         */
        fun open(layout: Layout, top: Float, bottom: Float, x: Float, y: Float): Boolean {
            if (fired) return false
            this.layout = layout; this.top = top; this.bottom = bottom; startX = x
            selection = index(x, y, x, layout, top, bottom)
            fired = true
            return true
        }

        /** Ngón di chuyển sau khi mở; true nếu ô chọn đổi. */
        fun move(x: Float, y: Float): Boolean {
            val l = layout ?: return false
            val s = index(x, y, startX, l, top, bottom)
            if (s == selection) return false
            selection = s
            return true
        }
    }
}

/**
 * Ô email: vừa gõ "@" (hoặc tiền tố tên miền sau "@") ⇒ chip đuôi mail trên thanh gợi ý
 * ("@gmail.com" trước). Chạm chip chèn PHẦN CÒN THIẾU (không lặp "@" / tiền tố đã gõ).
 * Thuần — session chỉ gọi ở ô email (ô khác 0 việc). Song sinh iOS EmailDomains.
 */
object EmailDomains {
    val providers = listOf("gmail.com", "icloud.com", "yahoo.com", "outlook.com")

    /** [label] trên thanh gợi ý ("@gmail.com"); [insert] chèn sau con trỏ ("ail.com" sau "phuc@gm"). */
    data class Chip(val label: String, val insert: String)

    /**
     * Chip cho chữ trước con trỏ. Token cuối (sau khoảng trắng / "," / ";" / "<") phải có
     * đúng một "@", phần trước "@" không rỗng; phần sau là tiền tố (không phân biệt hoa
     * thường) của tên miền và chưa gõ đủ.
     */
    fun chips(before: String, limit: Int = 3): List<Chip> {
        if (limit <= 0) return emptyList()
        val at = before.lastIndexOf('@')
        if (at < 0) return emptyList()
        val partial = before.substring(at + 1)
        if (partial.length >= 12 || !partial.all { it.code < 128 && (it.isLetterOrDigit() || it == '.' || it == '-') })
            return emptyList()
        var i = at - 1
        while (i >= 0) {
            val c = before[i]
            if (c.isWhitespace() || c == ',' || c == ';' || c == '<') break
            if (c == '@') return emptyList()
            i--
        }
        if (i == at - 1) return emptyList()          // phần trước "@" rỗng
        val p = partial.lowercase()
        return providers.asSequence().filter { it.startsWith(p) && it != p }.take(limit)
            .map { Chip("@$it", it.substring(p.length)) }.toList()
    }
}
