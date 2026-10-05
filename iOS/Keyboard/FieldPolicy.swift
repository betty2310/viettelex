import UIKit

/// Khi nào gõ LITERAL (bỏ Telex) theo trait của ô nhập.
///
/// Trước đây: mọi ô `autocorrectionType == .no` ⇒ literal. Sai với ô tìm kiếm —
/// thanh địa chỉ Safari/Chrome và Spotlight đều tắt autocorrect nhưng người dùng
/// cần gõ tiếng Việt ở đó. Giờ chỉ literal ở ô có NGỮ NGHĨA không phải chữ Việt:
/// email, URL thuần, username, mã OTP, mật khẩu (giống Android FieldMapping).
enum FieldPolicy {
    static func passthrough(keyboardType: UIKeyboardType,
                            contentType: UITextContentType?) -> Bool {
        switch keyboardType {
        case .emailAddress, .URL: return true
        default: break
        }
        guard let c = contentType else { return false }
        let literal: Set<UITextContentType> = [
            .username, .emailAddress, .URL, .oneTimeCode, .password, .newPassword,
        ]
        return literal.contains(c)
    }
}

extension FieldPolicy {
    /// Auto-shift theo kiểu viết hoa của ô: true = bật shift, false = tắt, nil =
    /// để nguyên. Host KHÔNG khai báo (nil) = mặc định UITextInputTraits là
    /// `.sentences` — trước đây nil bị coi như "không viết hoa" nên ô trống vẫn
    /// hiện phím chữ thường (feedback iPad 26/09/2026).
    static func autoShift(autocap: UITextAutocapitalizationType?, before: String) -> Bool? {
        switch autocap ?? .sentences {
        case .none: return nil
        case .allCharacters: return true
        case .words:
            return before.isEmpty || before.last?.isWhitespace == true
        case .sentences:
            let t = before.trimmingCharacters(in: .whitespaces)
            return before.isEmpty
                || (before.hasSuffix(" ") && (t.hasSuffix(".") || t.hasSuffix("!") || t.hasSuffix("?")))
                || before.hasSuffix("\n")
        @unknown default: return nil
        }
    }

    /// Như trên + công tắc "Tự động viết hoa đầu câu": tắt ⇒ nil (không đụng shift tay)
    /// và KHÔNG gọi `before` (documentContextBeforeInput là XPC).
    static func autoShift(enabled: Bool, autocap: UITextAutocapitalizationType?,
                          before: () -> String) -> Bool? {
        guard enabled else { return nil }
        return autoShift(autocap: autocap, before: before())
    }
}

/// Ảnh chụp trait của ô nhập — phần THUẦN của refreshFieldTraits(). Host đổi ô trong
/// cùng app (ô tìm kiếm ↔ ô bình luận…) KHÔNG gọi lại viewWillAppear, nên controller
/// đọc lại trait ở textDidChange/selectionDidChange và chỉ cấu hình lại bàn phím khi
/// ảnh chụp KHÁC lần trước (configureInputKind còn nhảy plane — gọi thừa là mất plane
/// số/ký hiệu đang mở). KHÔNG dùng documentIdentifier: nil trước khi phiên nhập thiết
/// lập → bẫy crash.
struct FieldTraits: Equatable {
    var keyboardType: UIKeyboardType = .default
    var returnKeyType: UIReturnKeyType = .default
    var appearance: UIKeyboardAppearance = .default
    var autocorrection: UITextAutocorrectionType = .default
    var contentType: UITextContentType?
    var secure = false

    /// Gõ literal (bỏ Telex)?
    var passthrough: Bool {
        FieldPolicy.passthrough(keyboardType: keyboardType, contentType: contentType)
    }

    /// Chip kết quả phép tính (MathResults): không ở ô mật khẩu / URL / email / username /
    /// OTP (ô literal); ô số vẫn có.
    var allowsMathResults: Bool { !passthrough && !secure }

    /// Ô cho phép thanh gợi ý (stock tắt ở ô mật khẩu / autocorrection = .no). Ô email
    /// luôn có (chỉ hiện chip đuôi mail — EmailDomains), vì ô email hầu như đều tắt autocorrect.
    var allowsSuggestions: Bool { !secure && (autocorrection != .no || inputKind == .email) }

    /// Tự thêm dấu cách sau dấu câu: chỉ ô chữ thường (không email/URL/số/mật khẩu/omnibox).
    var allowsAutoSpace: Bool {
        !passthrough && !secure && inputKind == .normal
    }

    /// Loại layout (web input type=number/email/url ánh xạ sang keyboardType).
    var inputKind: KeyboardView.InputKind {
        switch keyboardType {
        case .numberPad, .numbersAndPunctuation, .decimalPad,
             .phonePad, .asciiCapableNumberPad:
            return .number
        case .emailAddress: return .email
        case .URL: return .url
        case .webSearch: return .search
        default: return .normal
        }
    }

    /// Dòng header log chẩn đoán: chỉ loại trait, không có nội dung người dùng.
    var logDescription: String {
        "keyboardType=\(keyboardType.rawValue) returnKeyType=\(returnKeyType.rawValue)"
            + " autocorrectionType=\(autocorrection.rawValue)"
            + " textContentType=\(contentType?.rawValue ?? "nil") secure=\(secure ? 1 : 0)"
    }

    /// Cần cấu hình lại bàn phím? Lần đầu (old nil) luôn cần; sau đó chỉ khi trait đổi.
    static func needsReconfigure(old: FieldTraits?, new: FieldTraits) -> Bool {
        old != new
    }
}

extension FieldTraits {
    /// Ô nhạy cảm: mật khẩu / mã OTP — dải gợi ý để TRỐNG (không ☰, không 📋, không mời Dán).
    var sensitive: Bool {
        if secure { return true }
        guard let c = contentType else { return false }
        return [UITextContentType.password, .newPassword, .oneTimeCode].contains(c)
    }

    /// Ô URL / thanh "tìm hoặc nhập địa chỉ" (Safari, Chrome — keyboardType .webSearch):
    /// chip `www.` `.com` `.vn` (+ `https://` khi ô trống) ở dải công cụ (URLChips).
    var wantsURLChips: Bool { inputKind == .url || inputKind == .search }

    /// App tắt gợi ý (autocorrection = .no) ở ô CHỮ không nhạy cảm, không literal, không bàn số
    /// (thanh địa chỉ Safari .webSearch, ô chat…) — song sinh Android `FieldTraits.appNoSuggestions`.
    /// Cài đặt "Gợi ý cả khi ứng dụng tắt gợi ý" bật ⇒ vẫn gợi ý chữ (StripMode.full) nhưng
    /// `allowsSuggestions` vẫn false ⇒ AutoCorrect.fieldAllows giữ TẮT; và không học từ.
    var appNoSuggestions: Bool {
        autocorrection == .no && !sensitive && !passthrough && inputKind != .number
    }

    /// Ô địa chỉ / tìm kiếm ⇒ gợi ý chữ thường (không DisplayCase tên riêng, không hoa đầu câu)
    /// — song sinh Android `FieldMapping.isAddressOrSearch` (#113 thanh địa chỉ Firefox gõ
    /// "bình" ra "Thường"). .URL / .webSearch, hoặc Enter = Go / Search / Google / Yahoo.
    var lowercaseSuggestions: Bool {
        guard !sensitive else { return false }
        if inputKind == .url || inputKind == .search { return true }
        switch returnKeyType {
        case .go, .search, .google, .yahoo: return true
        default: return false
        }
    }
}

/// Dải gợi ý hiện gì ở ô hiện tại — song sinh Android StripMode (#113). Chiều cao dải KHÔNG
/// phụ thuộc chế độ này (giữ theo công tắc toàn cục, e72ae43) — chỉ nội dung đổi.
enum StripMode: Equatable {
    /// Tắt "Thanh gợi ý" trong cài đặt: không có dải (chỉ headroom balloon).
    case off
    /// Mật khẩu / OTP / ẩn danh / bàn số: dải trống giữ chiều cao.
    case blank
    /// Ô từ chối gợi ý chữ (autocorrection = .no: thanh địa chỉ Safari, ô URL…) nhưng không
    /// nhạy cảm: ☰ / 📋 / ⌄ + lời mời Dán (mời một lần, chip = Plus) + chip URL — không chữ.
    case tools
    /// Gợi ý đầy đủ.
    case full

    /// Dải có vẽ nội dung (thanh công cụ ± gợi ý).
    var shown: Bool { self == .tools || self == .full }

    /// THUẦN: công tắc + trait ô + ẩn danh → dải hiện gì. `suggestAnyway` = cài đặt "Gợi ý cả
    /// khi ứng dụng tắt gợi ý" (#113): ô `appNoSuggestions` (không ẩn danh) ⇒ .full.
    static func of(showSuggestions: Bool, traits t: FieldTraits, incognito: Bool,
                   suggestAnyway: Bool = false) -> StripMode {
        guard showSuggestions else { return .off }
        if t.allowsSuggestions { return .full }
        if overridesAppNoSuggest(suggestAnyway, traits: t, incognito: incognito) { return .full }
        if t.sensitive || incognito || t.inputKind == .number { return .blank }
        return .tools
    }

    /// Ô app tắt gợi ý mà VẪN gợi ý chữ (cài đặt bật, không nhạy cảm/literal/bàn số/ẩn danh).
    static func overridesAppNoSuggest(_ suggestAnyway: Bool, traits t: FieldTraits, incognito: Bool) -> Bool {
        suggestAnyway && t.appNoSuggestions && !t.allowsSuggestions && !incognito
    }

    /// THUẦN: học từ ở ô này? Ô app tắt gợi ý VẪN học như ô thường (Phil 05/10 — ô chat là nơi
    /// gõ nhiều nhất); ô nhạy cảm không bao giờ là appNoSuggestions; ẩn danh thì không học.
    /// Song sinh Android `StripMode.learns`.
    static func learns(learnWords: Bool, traits t: FieldTraits?, incognito: Bool) -> Bool {
        learnWords && !incognito
    }

    /// Ô URL / tìm kiếm ở chế độ .full: chip URL (URLChips) thay gợi ý chữ CHỈ ở "vị trí tên
    /// miền" — ô trống, hoặc token trước con trỏ đã có "." / "://" (đang gõ địa chỉ); còn lại
    /// gợi ý chữ như ô thường. Chế độ .tools: luôn chip URL như cũ.
    static func prefersURLChips(before: String, after: String) -> Bool {
        if before.isEmpty && after.isEmpty { return true }
        let token = before.reversed().prefix { !$0.isWhitespace }
        return token.contains(".") || String(token.reversed()).contains("://")
    }
}
