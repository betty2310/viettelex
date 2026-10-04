import XCTest

/// "Dán …" chỉ mời một lần mỗi mục clipboard — cùng kịch bản với Android PasteOfferOnceTests.
final class PasteOfferOnceTests: XCTestCase {
    func testNewItemOfferedThenNotReofferedAfterIgnore() {
        var saved: [Int] = []
        let o = PasteOfferOnce { saved.append($0) }
        XCTAssertTrue(o.canOffer(5))
        o.displayed(5)
        XCTAssertTrue(o.canOffer(5), "đang hiện: các lượt vẽ lại vẫn giữ lời mời")
        o.displayed(5)                       // vẽ lại không ghi lại
        XCTAssertEqual(saved, [5])
        o.ended()                            // gõ chữ / bị thay / ẩn bàn phím / đổi ô
        XCTAssertFalse(o.canOffer(5))
        o.reload(offeredID: 5)               // hiện lại bàn phím
        XCTAssertFalse(o.canOffer(5))
    }

    func testNotReofferedInNewSessionFromPersistedID() {
        var stored: Int?
        let a = PasteOfferOnce { stored = $0 }
        a.displayed(9); a.ended()
        let b = PasteOfferOnce(offeredID: stored)   // process bàn phím mới / app khác
        XCTAssertFalse(b.canOffer(9))
        XCTAssertTrue(b.canOffer(10))
    }

    func testNewCopyOfferedOnceAgain() {
        let o = PasteOfferOnce()
        o.displayed(1); o.ended()
        XCTAssertFalse(o.canOffer(1))
        XCTAssertTrue(o.canOffer(2))
        o.displayed(2); o.ended()
        XCTAssertFalse(o.canOffer(2))
    }

    func testNewCopyWhileShowingIsTracked() {
        let o = PasteOfferOnce()
        o.displayed(1)
        XCTAssertTrue(o.canOffer(2))
        o.displayed(2)                       // copy mới trong lúc bar còn hiện lời mời
        o.ended()
        // chỉ nhớ mục mới nhất (mục cũ đã rời clipboard)
        XCTAssertFalse(o.canOffer(2))
    }

    func testUsedNotOfferedAgain() {
        var saved: [Int] = []
        let o = PasteOfferOnce { saved.append($0) }
        o.displayed(3)
        o.used(3)
        XCTAssertFalse(o.canOffer(3))
        XCTAssertEqual(saved, [3])
        o.used(4)                            // chạm chip mà chưa ghi nhận hiện (phòng hờ)
        XCTAssertFalse(o.canOffer(4))
    }

    func testReloadDoesNotKillOfferOnScreen() {
        let o = PasteOfferOnce()
        o.displayed(7)
        o.reload(offeredID: 6)               // process khác ghi trước đó
        XCTAssertTrue(o.canOffer(7))
    }

    func testComputedButNeverDisplayedStaysOfferable() {
        let o = PasteOfferOnce(offeredID: 1)
        XCTAssertTrue(o.canOffer(2))         // tính ra nhưng bar thu gọn → chưa hiện
        o.ended()
        XCTAssertTrue(o.canOffer(2))
    }
}
