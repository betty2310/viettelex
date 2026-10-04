// DomainPopup — giữ phím "." ở ô địa chỉ / URL / email để chọn đuôi tên miền như stock
// iOS (issue #113: ô URL bỏ phím ".com" riêng — space rộng như stock, đuôi gom vào giữ "."). Phần THUẦN (không UIKit): danh sách lựa chọn theo loại ô, bố cục hàng ô, chỉ số ô
// dưới ngón theo toạ độ x, luật huỷ khi trượt xa. View chỉ dựng popup khi hết giờ giữ.
import CoreGraphics

enum DomainPopup {
    /// Đuôi tên miền — .com mặc định như stock, .vn / .com.vn kế tiếp cho người dùng Việt
    /// (issue #113). Bản Kotlin song sinh: android/keyboard/…/DomainPopup.kt.
    static let tlds = [".com", ".vn", ".com.vn", ".net", ".org", ".edu"]

    /// Phím `title` ở bàn CHỮ của ô `kind` có popup không. Chỉ phím "." của thanh địa
    /// chỉ/tìm kiếm, ô URL và ô email; ô khác ⇒ rỗng (0 chi phí).
    static func choices(kind: KeyboardView.InputKind, key title: String, lettersPlane: Bool) -> [String] {
        guard lettersPlane else { return [] }
        switch kind {
        case .search: return title == "." ? tlds : []
        case .url: return title == "." ? tlds : []
        case .email: return title == "." ? tlds : []
        default: return []
        }
    }

    /// Bố cục hàng ô (toạ độ vùng chứa). Ô đầu (mặc định) nằm ngay trên tâm phím; không đủ
    /// chỗ bên phải thì lật ngược (ô đầu ở bên phải, hàng loe sang trái) như stock, rồi kẹp
    /// trong [margin, width − margin].
    struct Layout: Equatable {
        let originX: CGFloat
        let itemWidth: CGFloat
        let count: Int
        /// true ⇒ ô 0 ở bên PHẢI (hàng đọc từ phải sang trái).
        let mirrored: Bool
        var width: CGFloat { itemWidth * CGFloat(count) }

        /// Khung x của lựa chọn `index` (toạ độ vùng chứa).
        func slotMinX(_ index: Int) -> CGFloat {
            let slot = mirrored ? count - 1 - index : index
            return originX + CGFloat(slot) * itemWidth
        }
    }

    static func layout(keyMidX: CGFloat, itemWidth: CGFloat, count: Int,
                       containerWidth: CGFloat, margin: CGFloat = 2) -> Layout {
        let w = itemWidth * CGFloat(count)
        let rightFits = keyMidX - itemWidth / 2 + w <= containerWidth - margin
        let raw = rightFits ? keyMidX - itemWidth / 2 : keyMidX + itemWidth / 2 - w
        let x = min(max(raw, margin), max(margin, containerWidth - margin - w))
        return Layout(originX: x, itemWidth: itemWidth, count: count, mirrored: !rightFits)
    }

    /// Ngón trượt ra ngoài hàng quá ngần này (ngang) ⇒ huỷ.
    static let cancelSlackX: CGFloat = 30
    /// Ngón trượt lên trên đỉnh popup / xuống dưới đáy phím quá ngần này ⇒ huỷ.
    static let cancelSlackY: CGFloat = 40

    /// Lựa chọn theo độ dời ngang của ngón so với lúc mở (`startX`): lúc mở luôn là ô 0
    /// (.com) — kể cả khi hàng bị kẹp mép không nằm đúng dưới ngón; mỗi bề rộng ô trượt
    /// theo hướng hàng loe ⇒ sang ô kế. nil = huỷ (ra ngoài hàng/phím quá ngần slack).
    /// `top` = đỉnh popup, `bottom` = đáy phím. Quay lại gần hàng thì chọn lại được (như stock).
    static func index(at p: CGPoint, startX: CGFloat, layout l: Layout, top: CGFloat, bottom: CGFloat) -> Int? {
        guard l.count > 0,
              p.x >= l.originX - cancelSlackX, p.x <= l.originX + l.width + cancelSlackX,
              p.y >= top - cancelSlackY, p.y <= bottom + cancelSlackY else { return nil }
        let step = Int(((p.x - startX) / l.itemWidth).rounded())
        let i = l.mirrored ? -step : step
        return min(max(i, 0), l.count - 1)
    }
}

/// Ô email: vừa gõ "@" (hoặc tiền tố tên miền sau "@") ⇒ chip đuôi mail trên thanh gợi ý
/// ("@gmail.com" trước). Chạm chip chèn PHẦN CÒN THIẾU (không lặp "@" / tiền tố đã gõ).
/// Thuần — controller chỉ gọi ở ô `.email` (ô khác 0 việc).
enum EmailDomains {
    static let providers = ["gmail.com", "icloud.com", "yahoo.com", "outlook.com"]

    struct Chip: Equatable {
        /// Nhãn trên thanh gợi ý, vd "@gmail.com".
        let label: String
        /// Chữ chèn sau con trỏ, vd "gmail.com" sau "phuc@", "ail.com" sau "phuc@gm".
        let insert: String
    }

    /// Chip cho chữ trước con trỏ. Token cuối (sau khoảng trắng / "," / ";") phải có đúng
    /// một "@", phần trước "@" không rỗng; phần sau là tiền tố (không phân biệt hoa thường)
    /// của tên miền và chưa gõ đủ.
    static func chips(before: String, limit: Int = 3) -> [Chip] {
        guard limit > 0, let at = before.lastIndex(of: "@") else { return [] }
        let partial = before[before.index(after: at)...]
        guard partial.count < 12,
              partial.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "." || $0 == "-") })
        else { return [] }
        let local = before[..<at].reversed().prefix { !$0.isWhitespace && $0 != "," && $0 != ";" && $0 != "<" }
        guard !local.isEmpty, !local.contains("@") else { return [] }
        let p = partial.lowercased()
        return Array(providers.lazy
            .filter { $0.hasPrefix(p) && $0 != p }
            .prefix(limit)
            .map { Chip(label: "@" + $0, insert: String($0.dropFirst(p.count))) })
    }
}

/// Ô URL / thanh địa chỉ (StripMode.tools + FieldTraits.wantsURLChips): chip gõ nhanh địa chỉ
/// thay cho dải trống. Chạm chèn TẠI CON TRỎ. Thuần — controller tính lại từ context lúc chạm
/// (con trỏ có thể đã dời), lệch thì bỏ.
/// - ô trống (trước + sau con trỏ đều rỗng): `https://` `www.` `.com`
/// - đầu một địa chỉ (trước con trỏ rỗng / khoảng trắng / "://"): `www.` `.com` `.vn`
/// - đang gõ tên miền: `.com` `.vn` (bỏ đuôi đã có; sau "." chỉ chèn "com"/"vn")
/// - đã sang đường dẫn ("/" sau tên miền): không chip
enum URLChips {
    typealias Chip = EmailDomains.Chip
    static let https = "https://", www = "www.", tlds = [".com", ".vn"]

    static func chips(before: String, after: String) -> [Chip] {
        if before.isEmpty && after.isEmpty {
            return [Chip(label: https, insert: https), Chip(label: www, insert: www),
                    Chip(label: tlds[0], insert: tlds[0])]
        }
        let token = before.reversed().prefix { !$0.isWhitespace }.reversed()
        var host = Substring(String(token))
        if let r = host.range(of: "://") { host = host[r.upperBound...] }
        if host.isEmpty {
            return [Chip(label: www, insert: www)] + tlds.map { Chip(label: $0, insert: $0) }
        }
        guard !host.contains("/") else { return [] }
        let h = host.lowercased()
        if tlds.contains(where: { h.hasSuffix($0) }) || h == "www." { return [] }
        return tlds.map { t in Chip(label: t, insert: h.hasSuffix(".") ? String(t.dropFirst()) : t) }
    }
}
