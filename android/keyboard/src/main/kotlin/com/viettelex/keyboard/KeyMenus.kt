package com.viettelex.keyboard

/**
 * Popup giữ phím ở hàng đáy bàn chữ (Android) — phần THUẦN, dùng chung hàng ô
 * [DomainPopup.layout] / [DomainPopup.Hold] (ô đầu chọn sẵn, trượt chọn, nhấc chạy, trượt xa huỷ).
 * TalkBack / touch exploration tắt popup ⇒ view giữ hành vi giữ lâu cũ.
 */
object CommaPopup {
    /**
     * Giữ "," (bàn chữ ô thường; ô URL/email có "/" "@" riêng): dấu câu hay dùng. KHÔNG có 🎤
     * (giọng nói ở menu giữ 😊 — [EmojiKeyMenu]). "," đứng đầu ⇒ giữ rồi nhấc tại chỗ vẫn ra ",".
     */
    val choices: List<String> = listOf(",", ".", "?", "!", ":", ";", "'", "\"", "-", "…")
    const val PRESELECT = 0

    /** Bề rộng ô (dp): 10 ô ⇒ hẹp hơn ô biến thể (38) để hàng không phủ kín bề ngang điện thoại. */
    fun itemWidthDp(tablet: Boolean): Float = if (tablet) 48f else 34f

    /** Lựa chọn của phím `key` ở bàn chữ; rỗng ⇒ phím khác (không hẹn giờ). */
    fun choices(key: String, lettersPlane: Boolean): List<String> =
        if (lettersPlane && key == ",") choices else emptyList()
}

/** Mục menu giữ phím 😊. */
enum class EmojiKeyAction {
    /** Danh sách bàn phím hệ thống (hành động giữ 😊 cũ). */
    SWITCH_KEYBOARD,
    /** Gõ giọng nói — chỉ khi máy có IME giọng nói. */
    VOICE,
    /** Bật / tắt chế độ một tay (điện thoại). */
    ONE_HAND,
    /** Mở app VietTelex (cài đặt). */
    SETTINGS,
}

/**
 * Giữ 😊 ⇒ hàng icon: 🌐 đổi bàn phím (chọn sẵn — giữ rồi nhấc tại chỗ = hành vi cũ) · 🎤 ·
 * ✋ một tay · ⚙ cài đặt. Có phím 🌐 thật (needsGlobe) cũng vậy.
 */
object EmojiKeyMenu {
    const val PRESELECT = 0

    fun actions(voiceAvailable: Boolean, oneHandAvailable: Boolean): List<EmojiKeyAction> {
        val out = ArrayList<EmojiKeyAction>(4)
        out += EmojiKeyAction.SWITCH_KEYBOARD
        if (voiceAvailable) out += EmojiKeyAction.VOICE
        if (oneHandAvailable) out += EmojiKeyAction.ONE_HAND
        out += EmojiKeyAction.SETTINGS
        return out
    }

    /** Nhãn (mô tả trợ năng / debug); một tay đang bật ⇒ mục là "tắt". */
    fun label(a: EmojiKeyAction, oneHandOn: Boolean): String = when (a) {
        EmojiKeyAction.SWITCH_KEYBOARD -> tr("Đổi bàn phím")
        EmojiKeyAction.VOICE -> tr("Gõ bằng giọng nói")
        EmojiKeyAction.ONE_HAND -> if (oneHandOn) tr("Tắt chế độ một tay") else tr("Chế độ một tay")
        EmojiKeyAction.SETTINGS -> tr("Cài đặt VietTelex")
    }
}
