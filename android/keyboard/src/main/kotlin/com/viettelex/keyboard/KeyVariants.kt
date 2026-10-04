package com.viettelex.keyboard

/**
 * Giữ phím ở bàn SỐ / KÝ HIỆU ra hàng biến thể như stock iOS: giữ `"` ⇒ " ” “ „ » «, giữ `$`
 * ⇒ $ ₫ € … Bản THUẦN song sinh iOS/Keyboard/KeyVariants.swift (cùng dữ liệu) — sửa ở đây thì
 * sửa y hệt bên kia. View dùng chung popup trượt-chọn của [DomainPopup].
 *
 * Thứ tự = từ phím ra ngoài: ô 0 là ký tự GỐC (mặc định được chọn khi popup mở, như stock).
 *
 * Bàn CHỮ không có biến thể: giữ phím chữ đã là ký tự phụ số/ký hiệu ([KeyAlternates] —
 * q…p → 1…0, a → @…) và "," → "."; dấu tiếng Việt gõ bằng Telex/VNI.
 */
object KeyVariants {
    /** Bảng biến thể (khoá = ký tự gốc trên phím). ₫ đứng ngay sau $ cho người dùng Việt. */
    val table: Map<String, List<String>> = linkedMapOf(
        // bàn 123
        "1" to listOf("1", "¹", "½", "⅓", "¼", "⅛"),
        "2" to listOf("2", "²", "⅔"),
        "3" to listOf("3", "³", "¾", "⅜"),
        "4" to listOf("4", "⁴"),
        "5" to listOf("5", "⅝"),
        "7" to listOf("7", "⅞"),
        "0" to listOf("0", "°"),
        "-" to listOf("-", "–", "—", "•"),
        "/" to listOf("/", "\\"),
        "\$" to listOf("\$", "₫", "€", "£", "¥", "₩", "₹", "₽", "¢"),
        "&" to listOf("&", "§"),
        "." to listOf(".", "…"),
        "?" to listOf("?", "¿"),
        "!" to listOf("!", "¡"),
        "'" to listOf("'", "‘", "’", "`"),
        "\"" to listOf("\"", "”", "“", "„", "»", "«"),
        // bàn #+=
        "%" to listOf("%", "‰"),
        "=" to listOf("=", "≠", "≈"),
        "+" to listOf("+", "±"),
        "<" to listOf("<", "≤", "‹", "«"),
        ">" to listOf(">", "≥", "›", "»"),
        "*" to listOf("*", "×"),
        "~" to listOf("~", "≈"),
        "€" to listOf("€", "\$", "£", "¥", "₫"),
        "•" to listOf("•", "·", "°"),
    )

    /** Hàng biến thể của phím [key] ở bàn số/ký hiệu; rỗng = không có. Bàn chữ / ô số luôn rỗng. */
    fun variants(key: String, symbolPlane: Boolean, numericField: Boolean = false): List<String> {
        if (!symbolPlane || numericField) return emptyList()
        return table[key] ?: emptyList()
    }
}
