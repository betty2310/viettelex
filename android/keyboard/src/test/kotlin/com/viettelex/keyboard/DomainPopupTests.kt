package com.viettelex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Popup nhiều lựa chọn (giữ "." ô URL/email ra đuôi tên miền; luật trượt-chọn/huỷ) và chip
 * đuôi mail sau "@" ở ô email. Song sinh iOS/KeyboardTests/DomainPopupTests.swift.
 */
class DomainPopupTests {
    // MARK: lựa chọn theo loại ô

    @Test fun choicesPerField() {
        val t = DomainPopup.tlds
        assertEquals(".com mặc định, .vn/.com.vn kế (#113)", listOf(".com", ".vn", ".com.vn", ".net", ".org", ".edu"), t)
        assertEquals(t, DomainPopup.choices(DomainPopup.Field.URL, ".", lettersPlane = true))
        assertEquals(t, DomainPopup.choices(DomainPopup.Field.EMAIL, ".", lettersPlane = true))
        assertEquals("không còn phím .com", emptyList<String>(), DomainPopup.choices(DomainPopup.Field.URL, ".com", true))
        assertEquals(emptyList<String>(), DomainPopup.choices(DomainPopup.Field.URL, "/", true))
        assertEquals(emptyList<String>(), DomainPopup.choices(DomainPopup.Field.EMAIL, "@", true))
        assertEquals("ô thường: không", emptyList<String>(), DomainPopup.choices(DomainPopup.Field.NORMAL, ".", true))
        assertEquals("chỉ bàn chữ", emptyList<String>(), DomainPopup.choices(DomainPopup.Field.URL, ".", false))
    }

    // MARK: bố cục + chỉ số dưới ngón

    private fun idx(l: DomainPopup.Layout, x: Float, y: Float = 20f, startX: Float) =
        DomainPopup.index(x, y, startX, l, top = 0f, bottom = 60f)

    @Test fun layoutLeftToRightWhenRoomOnRight() {
        val l = DomainPopup.layout(keyMidX = 60f, itemWidth = 56f, count = 6, containerWidth = 390f)
        assertFalse(l.mirrored)
        assertEquals(32f, l.originX, 0.001f)
        assertEquals(32f, l.slotMinX(0), 0.001f)
        assertEquals("ô dưới ngón lúc mở = .com", 0, idx(l, 60f, startX = 60f))
        assertEquals(1, idx(l, 60f + 56, startX = 60f))
        assertEquals(5, idx(l, 60f + 56 * 5, startX = 60f))
        assertEquals("lố mép trong ngần: kẹp ô cuối", 5, idx(l, l.originX + l.width + 10, startX = 60f))
        assertEquals(0, idx(l, l.originX - 10, startX = 60f))
        assertNull("trượt xa ngang ⇒ huỷ", idx(l, l.originX + l.width + DomainPopup.CANCEL_SLACK_X + 1, startX = 60f))
        assertNull(idx(l, l.originX - DomainPopup.CANCEL_SLACK_X - 1, startX = 60f))
        assertNull("trượt lên quá ⇒ huỷ", idx(l, 60f, -DomainPopup.CANCEL_SLACK_Y - 1, 60f))
        assertNull("trượt xuống quá ⇒ huỷ", idx(l, 60f, 60 + DomainPopup.CANCEL_SLACK_Y + 1, 60f))
        assertEquals("ngón vẫn trên phím ⇒ chọn", 0, idx(l, 60f, 70f, 60f))
    }

    @Test fun layoutMirrorsNearRightEdge() {
        val l = DomainPopup.layout(keyMidX = 350f, itemWidth = 56f, count = 6, containerWidth = 390f)
        assertTrue(l.mirrored)
        assertEquals(".com vẫn ngay trên phím", 350f - 28, l.slotMinX(0), 0.001f)
        assertTrue(l.originX >= 2f)
        assertEquals(0, idx(l, 350f, startX = 350f))
        assertEquals("loe sang trái: trượt trái ra .vn", 1, idx(l, 350f - 56, startX = 350f))
        assertEquals(5, idx(l, 350f - 56 * 5, startX = 350f))
    }

    @Test fun layoutClampedInsideNarrowContainer() {
        val l = DomainPopup.layout(keyMidX = 150f, itemWidth = 56f, count = 5, containerWidth = 300f)
        assertTrue(l.originX >= 2f)
        assertTrue(l.originX + l.width <= 298f)
        val dir = if (l.mirrored) -1 else 1
        assertEquals("hàng bị kẹp: lúc mở vẫn là ô 0", 0, idx(l, 150f, startX = 150f))
        assertEquals(1, idx(l, 150f + dir * 56, startX = 150f))
        assertEquals("dời chưa nửa ô: giữ nguyên", 0, idx(l, 150f + dir * 20, startX = 150f))
        // đúng nửa ô: làm tròn xa 0 như Swift .rounded() (cả hai chiều)
        assertEquals(1, idx(l, 150f + dir * 28, startX = 150f))
    }

    // MARK: lượt giữ (KeyCommitQueue) — chạm thường / giữ / trượt / huỷ / ngón khác

    private class Rig {
        val out = ArrayList<String>()
        val commits = KeyCommitQueue()
        val key = Any()
        val layout = DomainPopup.layout(60f, 56f, DomainPopup.tlds.size, 390f)
        val hold = DomainPopup.Hold(DomainPopup.tlds)
        fun down() = commits.arm(key) { out += "." }
        fun fire(x: Float = 60f, y: Float = 80f) = hold.fire(commits, key, layout, 0f, 100f, x, y) { out += it }
        fun up() = commits.release(key)
    }

    @Test fun tapStillTypesBase() {
        val r = Rig(); r.down(); r.up()
        assertEquals(listOf("."), r.out)
        assertFalse("đã chốt ⇒ không mở", r.fire())
    }

    @Test fun holdAndReleaseInsertsPreselected() {
        val r = Rig(); r.down()
        assertTrue(r.fire())
        assertEquals(".com", r.hold.chosen)
        assertEquals("chưa nhấc: chưa chèn", emptyList<String>(), r.out)
        r.up()
        assertEquals(listOf(".com"), r.out)
    }

    @Test fun slideSelectsNext() {
        val r = Rig(); r.down(); r.fire()
        assertTrue(r.hold.move(60f + 56, 80f))
        assertFalse("cùng ô: không đổi", r.hold.move(60f + 60, 80f))
        assertEquals(".vn", r.hold.chosen)
        assertTrue(r.hold.move(60f + 112, 80f))
        r.up()
        assertEquals(listOf(".com.vn"), r.out)
    }

    @Test fun slideFarCancelsAndCanComeBack() {
        val r = Rig(); r.down(); r.fire()
        assertTrue(r.hold.move(60f, -300f))
        assertNull(r.hold.chosen)
        r.up()
        assertEquals("trượt xa ⇒ không chèn gì", emptyList<String>(), r.out)
        val r2 = Rig(); r2.down(); r2.fire()
        r2.hold.move(60f, -300f); r2.hold.move(60f + 56, 80f)
        r2.up()
        assertEquals("quay lại hàng thì chọn lại được", listOf(".vn"), r2.out)
    }

    @Test fun otherFingerCommitsSelectionFirst() {
        val r = Rig(); r.down(); r.fire()
        // ngón khác chạm: view chốt popup (release/flush) trước khi chèn phím mới
        r.commits.flush(); r.out += "a"
        assertEquals(listOf(".com", "a"), r.out)
    }

    @Test fun moveBeforeFireIsIgnored() {
        val r = Rig(); r.down()
        assertFalse(r.hold.move(200f, 80f))
        assertNull(r.hold.chosen)
    }

    // MARK: chip đuôi mail

    @Test fun emailChipsAfterAt() {
        val c = EmailDomains.chips("phuc@")
        assertEquals("gmail trước, tối đa 3", listOf("@gmail.com", "@icloud.com", "@yahoo.com"), c.map { it.label })
        assertEquals("không lặp \"@\"", "gmail.com", c.first().insert)
        assertEquals("@outlook.com", EmailDomains.chips("phuc@", limit = 4).last().label)
        assertEquals("gmail.com", EmailDomains.chips("To: a, phuc@").first().insert)
    }

    @Test fun emailChipsCompletePrefix() {
        assertEquals(listOf(EmailDomains.Chip("@gmail.com", "ail.com")), EmailDomains.chips("phuc@gm"))
        assertEquals("ail.com", EmailDomains.chips("phuc@GM").first().insert)
        assertEquals(listOf("@outlook.com"), EmailDomains.chips("phuc@o").map { it.label })
        assertEquals("com", EmailDomains.chips("phuc@gmail.").first().insert)
        assertEquals("m", EmailDomains.chips("phuc@gmail.co").first().insert)
    }

    @Test fun emailChipsGone() {
        for (s in listOf("", "phuc", "@", "hi @", "phuc@gmail.com", "phuc@abc.", "ban@congty", "a@b@", "phuc@ "))
            assertEquals(s, emptyList<EmailDomains.Chip>(), EmailDomains.chips(s))
        assertEquals(emptyList<EmailDomains.Chip>(), EmailDomains.chips("phuc@", limit = 0))
    }

    // MARK: session — ô email có thanh chỉ chứa chip đuôi mail; ô khác không đổi

    private fun session(traits: FieldTraits, settings: KeyboardSettings = KeyboardSettings()) =
        KeyboardSession(UserLangModel(), null) { 1_000_000L }.also { it.startInput(settings, traits) }

    private val email = FieldTraits(passthrough = true, suggestionsAllowed = false, emailField = true)

    @Test fun emailFieldShowsChipsAndInsertsMissingPart() {
        TestAssets.install()
        val s = session(email)
        assertTrue("ô email luôn có thanh", s.suggestionsActive)
        val p = MockProxy()
        p.insertText("phuc@")
        assertEquals(listOf("@gmail.com", "@icloud.com", "@yahoo.com"), s.suggestionsNow(p)?.nextWords)
        s.acceptSuggestion("@gmail.com", p)
        assertEquals("phuc@gmail.com", p.text)
        assertEquals(emptyList<String>(), s.suggestionsNow(p)?.nextWords)
        // context đổi giữa chừng ⇒ không chèn mù
        val q = MockProxy(); q.insertText("phuc@gm")
        s.acceptSuggestion("@yahoo.com", q)
        assertEquals("phuc@gm", q.text)
    }

    @Test fun emailBarOffWhenSuggestionsOffOrSecure() {
        TestAssets.install()
        assertFalse(session(email, KeyboardSettings(showSuggestions = false)).suggestionsActive)
        assertFalse(session(email.copy(isSecure = true)).suggestionsActive)
        assertFalse("ô URL literal: không thanh", session(FieldTraits(passthrough = true, suggestionsAllowed = false,
            urlField = true)).suggestionsActive)
    }
}
