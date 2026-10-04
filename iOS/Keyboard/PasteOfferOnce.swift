// "Dán …" (thẻ Dán + chip STK/SĐT/OTP) chỉ mời MỘT lần cho mỗi nội dung clipboard
// (Phil 04/10/2026): đã HIỆN trên bar mà không dùng (gõ chữ khác, bị gợi ý thay chỗ,
// ẩn bàn phím, đổi ô) thì không mời lại cho chính mục đó — kể cả lần hiện sau, app/ô
// khác. Copy mới (changeCount mới) ⇒ mời lại một lần. Bảng lịch sử 📋 không liên quan.
// THUẦN (không UIKit): controller đưa id (UIPasteboard.changeCount — đọc không bật hỏi
// quyền) + lưu id vào App Group (chỉ số, không bao giờ nội dung). Cùng logic với
// Android PasteOfferOnce.kt.
import Foundation

final class PasteOfferOnce {
    /// Id mục đã HIỆN lời mời (lưu bền qua lần khởi động bàn phím).
    private(set) var offeredID: Int?
    /// Id đang hiện trên bar NGAY LÚC NÀY — vẫn được vẽ lại (bar tính lại nhiều lượt)
    /// cho tới khi rời bar lần đầu.
    private(set) var showingID: Int?
    /// Ghi id mới (chỉ gọi khi đổi — một lần mỗi lần copy).
    var persist: (Int) -> Void

    init(offeredID: Int? = nil, persist: @escaping (Int) -> Void = { _ in }) {
        self.offeredID = offeredID
        self.persist = persist
    }

    /// Nạp lại id đã lưu (process bàn phím khác có thể vừa ghi). Không đụng mục đang hiện.
    func reload(offeredID id: Int?) {
        if showingID == nil { offeredID = id }
    }

    /// Được mời dán mục `id` không: chưa từng hiện, hoặc đang hiện dở.
    func canOffer(_ id: Int) -> Bool { id != offeredID || id == showingID }

    /// Bar vừa vẽ lời mời cho `id` (đã thực sự hiện).
    func displayed(_ id: Int) {
        showingID = id
        if offeredID != id { offeredID = id; persist(id) }
    }

    /// Lời mời rời bar (gõ chữ, bị thay, ẩn bàn phím, đổi ô) — từ đây không mời lại.
    func ended() { showingID = nil }

    /// Đã chạm Dán / chip: cũng tính là đã mời, không mời lại.
    func used(_ id: Int) {
        showingID = nil
        if offeredID != id { offeredID = id; persist(id) }
    }
}
