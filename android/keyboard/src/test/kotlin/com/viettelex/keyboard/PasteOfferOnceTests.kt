package com.viettelex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "Dán …" chỉ mời một lần mỗi mục clipboard — cùng kịch bản với iOS PasteOfferOnceTests. */
class PasteOfferOnceTests {
    @Test fun newItemOfferedThenNotReofferedAfterIgnore() {
        val saved = mutableListOf<Long>()
        val o = PasteOfferOnce { saved += it }
        assertTrue(o.canOffer(5))
        o.displayed(5)
        assertTrue(o.canOffer(5))            // đang hiện: các lượt vẽ lại vẫn giữ lời mời
        o.displayed(5)                       // vẽ lại không ghi lại
        assertEquals(listOf(5L), saved)
        o.ended()                            // gõ chữ / bị thay / ẩn bàn phím / đổi ô
        assertFalse(o.canOffer(5))
        o.reload(5)                          // hiện lại bàn phím
        assertFalse(o.canOffer(5))
    }

    @Test fun notReofferedInNewSessionFromPersistedId() {
        var stored: Long? = null
        val a = PasteOfferOnce { stored = it }
        a.displayed(9); a.ended()
        val b = PasteOfferOnce(stored)       // process IME mới / app khác
        assertFalse(b.canOffer(9))
        assertTrue(b.canOffer(10))
    }

    @Test fun newCopyOfferedOnceAgain() {
        val o = PasteOfferOnce()
        o.displayed(1); o.ended()
        assertFalse(o.canOffer(1))
        assertTrue(o.canOffer(2))
        o.displayed(2); o.ended()
        assertFalse(o.canOffer(2))
    }

    @Test fun newCopyWhileShowingIsTracked() {
        val o = PasteOfferOnce()
        o.displayed(1)
        assertTrue(o.canOffer(2))
        o.displayed(2); o.ended()
        assertFalse(o.canOffer(2))           // chỉ nhớ mục mới nhất (mục cũ đã rời clipboard)
    }

    @Test fun usedNotOfferedAgain() {
        val saved = mutableListOf<Long>()
        val o = PasteOfferOnce { saved += it }
        o.displayed(3); o.used(3)
        assertFalse(o.canOffer(3))
        assertEquals(listOf(3L), saved)
        o.used(4)
        assertFalse(o.canOffer(4))
    }

    @Test fun reloadDoesNotKillOfferOnScreen() {
        val o = PasteOfferOnce()
        o.displayed(7); o.reload(6)
        assertTrue(o.canOffer(7))
    }

    @Test fun computedButNeverDisplayedStaysOfferable() {
        val o = PasteOfferOnce(1)
        assertTrue(o.canOffer(2))            // tính ra nhưng bar thu gọn → chưa hiện
        o.ended()
        assertTrue(o.canOffer(2))
    }

    // MARK: qua KeyboardSession (bar báo hiện/rời bằng pasteOfferVisible)

    private class FakeClip : ClipboardSource {
        override var changeCount = 0
        var text: String? = null
        var id = 100L
        override fun hasText() = text != null
        override fun readText() = text
        override fun clipId() = id
        fun copy(s: String) { text = s; changeCount++; id++ }
    }

    private var now = 1_000_000L
    private val clip = FakeClip()
    private var stored: Long? = null

    @Before fun setUp() { TestAssets.install() }

    private fun session(): KeyboardSession {
        val s = KeyboardSession(UserLangModel(), clip) { now }
        s.pasteOnce.reload(stored)
        s.pasteOnce.persist = { stored = it }
        s.startInput(KeyboardSettings(), FieldTraits())
        return s
    }

    private fun KeyboardSession.offered(p: TextProxy): Boolean {
        invalidatePasteCache()
        return suggestionsNow(p)!!.paste
    }

    @Test fun sessionIgnoredOfferNotRepeated() {
        val s = session(); val p = MockProxy()
        clip.copy("hello")
        assertTrue(s.offered(p))
        s.pasteOfferVisible(true)                    // bar vẽ thẻ Dán
        assertTrue(s.offered(p))                     // lượt vẽ lại khi còn đang hiện
        s.pasteOfferVisible(false)                   // gõ chữ / bị thay
        assertFalse(s.offered(p))
        now += 1000
        assertFalse(s.suggestionsNow(p)!!.paste)     // cả nhánh cache 2 s
        // ẩn bàn phím + hiện lại / process mới / app khác: không mời lại
        val s2 = session()
        assertFalse(s2.offered(MockProxy()))
        // copy mới ⇒ mời lại một lần
        clip.copy("world")
        assertTrue(s2.offered(MockProxy()))
        s2.pasteOfferVisible(true); s2.pasteOfferVisible(false)
        assertFalse(s2.offered(MockProxy()))
    }

    @Test fun sessionComputedButHiddenStillOffered() {
        val s = session(); val p = MockProxy()
        clip.copy("x")
        assertTrue(s.offered(p))                     // tính ra nhưng chưa vẽ (bar thu gọn)
        s.pasteOfferVisible(false)
        assertTrue(s.offered(p))
    }

    @Test fun sessionUsedNotOfferedAgain() {
        val s = session(); val p = MockProxy()
        clip.copy("copied")
        assertTrue(s.offered(p))
        s.pasteOfferVisible(true)
        s.acceptSuggestion(SuggestionSet.PASTE_TOKEN, p)
        assertEquals("copied", p.text)
        assertFalse(session().offered(MockProxy()))  // phiên mới, ô trống: vẫn không mời
    }
}
