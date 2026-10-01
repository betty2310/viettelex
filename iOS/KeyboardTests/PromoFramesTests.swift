import XCTest
import UIKit

/// Xuất ảnh KeyboardView thật (offscreen) cho video giới thiệu trên App Store / Play:
/// bàn phím trống, các thanh gợi ý, balloon từng phím chữ, bảng Mẫu câu + toạ độ phím (JSON).
/// Chỉ chạy khi có VT_PROMO_DIR (xcodebuild: TEST_RUNNER_VT_PROMO_DIR=…).
final class PromoFramesTests: XCTestCase {
    @MainActor func testWritePromoFrames() throws {
        guard let dir = ProcessInfo.processInfo.environment["VT_PROMO_DIR"] else {
            throw XCTSkip("đặt VT_PROMO_DIR để xuất ảnh")
        }
        try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
        let w: CGFloat = 402
        var meta: [String: Any] = [:]
        func shot(_ name: String, _ configure: (KeyboardView) -> Void) throws {
            let kb = KeyboardView(needsGlobe: false, inputController: nil, onKey: { _ in })
            let host = UIView(frame: CGRect(x: 0, y: 0, width: w, height: 300))
            host.backgroundColor = UIColor(red: 0.82, green: 0.83, blue: 0.86, alpha: 1)
            host.overrideUserInterfaceStyle = .light
            host.addSubview(kb); kb.frame = host.bounds
            kb.applyAppearance(.light, style: .light)
            kb.setSuggestionsEnabled(true)
            kb.setNeedsLayout(); kb.layoutIfNeeded()
            configure(kb)
            kb.setNeedsLayout(); kb.layoutIfNeeded()
            let h = kb.systemLayoutSizeFitting(CGSize(width: w, height: 0)).height
            host.frame.size.height = max(h, 200); kb.frame = host.bounds
            host.layoutIfNeeded(); kb.layoutIfNeeded()
            if meta["size"] == nil {
                var letters: [String: [CGFloat]] = [:]
                for c in "qwertyuiopasdfghjklzxcvbnm" {
                    if let f = kb.debugLetterFrame(String(c)) { letters[String(c)] = [f.minX, f.minY, f.width, f.height] }
                }
                meta["letters"] = letters
                meta["rows"] = kb.debugRowFrames().map { [$0.minX, $0.minY, $0.width, $0.height] }
                meta["size"] = [host.bounds.width, host.bounds.height]
                meta["scale"] = UIScreen.main.scale
            }
            let img = UIGraphicsImageRenderer(bounds: host.bounds).image { ctx in host.layer.render(in: ctx.cgContext) }
            try XCTUnwrap(img.pngData()).write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
        let bars: [String: [String]] = [
            "thu": ["thử", "thử gõ", "thư"], "tieng": ["tiếng", "tiếng Việt", "tiếp"],
            "chao": ["chào", "chào bạn", "cháu"], "viet": ["tiếng", "tiếng Việt", "Việt Nam"],
            "next": ["Em", "Anh", "Tôi"],
        ]
        for (name, words) in bars {
            try shot("bar-\(name)") { $0.showSuggestions(.init(nextWords: words)) }
            for c in "thuwrgoxiensvjcha" {
                try shot("bar-\(name)-key-\(c)") { kb in
                    kb.showSuggestions(.init(nextWords: words))
                    _ = kb.debugBalloon(letter: String(c), text: String(c))
                }
            }
        }
        try shot("templates") { kb in
            kb.showSuggestions(.init(nextWords: bars["next"]!))
            kb.debugTapBurger()
        }
        let data = try JSONSerialization.data(withJSONObject: meta, options: [.prettyPrinted, .sortedKeys])
        try data.write(to: URL(fileURLWithPath: dir).appendingPathComponent("meta.json"))
    }
}
