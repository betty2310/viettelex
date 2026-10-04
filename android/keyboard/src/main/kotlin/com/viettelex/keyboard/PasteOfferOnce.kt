package com.viettelex.keyboard

/**
 * "Dán …" (thẻ Dán + chip STK/SĐT/OTP) chỉ mời MỘT lần cho mỗi nội dung clipboard
 * (Phil 04/10/2026): đã HIỆN trên bar mà không dùng (gõ chữ khác, bị gợi ý thay chỗ, ẩn
 * bàn phím, đổi ô) thì không mời lại cho chính mục đó — kể cả lần hiện sau, app/ô khác.
 * Copy mới ⇒ mời lại một lần. Bảng lịch sử 📋 không liên quan.
 *
 * Thuần: id do [ClipboardSource.clipId] cấp (timestamp ClipDescription — không đọc nội
 * dung); IME lưu id vào prefs riêng qua [persist] (chỉ số, không bao giờ nội dung).
 * Cùng logic với iOS PasteOfferOnce.swift.
 */
class PasteOfferOnce(offeredId: Long? = null, var persist: (Long) -> Unit = {}) {
    /** Id mục đã HIỆN lời mời (lưu bền qua lần khởi động bàn phím). */
    var offeredId: Long? = offeredId
        private set
    /** Id đang hiện trên bar NGAY LÚC NÀY — vẫn vẽ lại được tới khi rời bar lần đầu. */
    var showingId: Long? = null
        private set

    /** Nạp lại id đã lưu; không đụng mục đang hiện. */
    fun reload(offeredId: Long?) { if (showingId == null) this.offeredId = offeredId }

    /** Được mời dán mục [id] không: chưa từng hiện, hoặc đang hiện dở. */
    fun canOffer(id: Long): Boolean = id != offeredId || id == showingId

    /** Bar vừa vẽ lời mời cho [id] (đã thực sự hiện). */
    fun displayed(id: Long) {
        showingId = id
        if (offeredId != id) { offeredId = id; persist(id) }
    }

    /** Lời mời rời bar (gõ chữ, bị thay, ẩn bàn phím, đổi ô) — từ đây không mời lại. */
    fun ended() { showingId = null }

    /** Đã chạm Dán / chip: cũng tính là đã mời. */
    fun used(id: Long) {
        showingId = null
        if (offeredId != id) { offeredId = id; persist(id) }
    }
}
