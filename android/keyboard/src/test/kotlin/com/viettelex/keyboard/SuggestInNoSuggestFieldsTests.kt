package com.viettelex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * #113 "Gợi ý cả khi ứng dụng tắt gợi ý" (mặc định BẬT, như Gboard/Laban): ô chữ NO_SUGGESTIONS
 * không nhạy cảm ⇒ gợi ý chữ đầy đủ, KHÔNG tự sửa, KHÔNG học. Bảng quyết định thuần + luồng session.
 */
class SuggestInNoSuggestFieldsTests {
    @Before fun setUp() { TestAssets.install(); PlusGate.paywallEnabled = false }
    @org.junit.After fun tearDown() { PlusGate.paywallEnabled = PlusConfig.PAYWALL_ENABLED }

    /** Ô chat NO_SUGGESTIONS (Messenger chat với Trang) — như FieldMapping.map trả về. */
    private val appNo = FieldTraits(suggestionsAllowed = false, stripTools = true, appNoSuggestions = true)

    private class Row(val name: String, val field: FieldTraits, val incognito: Boolean,
                      val stripOn: StripMode, val stripOff: StripMode)

    private val table = listOf(
        Row("ô thường", FieldTraits(), false, StripMode.FULL, StripMode.FULL),
        Row("ô app tắt gợi ý", appNo, false, StripMode.FULL, StripMode.TOOLS),
        Row("ô địa chỉ tắt gợi ý (Chrome omnibox)", appNo.copy(urlField = true, lowercaseSuggestions = true), false,
            StripMode.FULL, StripMode.TOOLS),
        Row("app tắt gợi ý + NO_PERSONALIZED_LEARNING", appNo.copy(noLearning = true), true, StripMode.BLANK, StripMode.BLANK),
        Row("app tắt gợi ý + ẩn danh thủ công", appNo, true, StripMode.BLANK, StripMode.BLANK),
        Row("mật khẩu", FieldTraits(isSecure = true, passthrough = true, suggestionsAllowed = false), false,
            StripMode.BLANK, StripMode.BLANK),
        // FieldMapping không bao giờ đặt appNoSuggestions cho mật khẩu/passthrough — phòng thủ vẫn chặn.
        Row("mật khẩu (cờ lạc)", appNo.copy(isSecure = true), false, StripMode.BLANK, StripMode.BLANK),
        Row("FORCE_ASCII (passthrough)", FieldTraits(passthrough = true, suggestionsAllowed = false, stripTools = true), false,
            StripMode.TOOLS, StripMode.TOOLS),
        Row("passthrough (cờ lạc)", appNo.copy(passthrough = true), false, StripMode.TOOLS, StripMode.TOOLS),
        Row("bàn số / SĐT / ngày", FieldTraits(passthrough = true, suggestionsAllowed = false), false,
            StripMode.BLANK, StripMode.BLANK),
        Row("email (chip đuôi mail)", FieldTraits(passthrough = true, suggestionsAllowed = false, emailField = true, stripTools = true),
            false, StripMode.FULL, StripMode.FULL),
    )

    @Test fun decisionTable() {
        for (r in table) {
            assertEquals("${r.name} / BẬT", r.stripOn, StripMode.of(true, r.field, r.incognito, suggestAnyway = true))
            assertEquals("${r.name} / TẮT", r.stripOff, StripMode.of(true, r.field, r.incognito, suggestAnyway = false))
            assertEquals("${r.name} / thanh gợi ý tắt", StripMode.OFF, StripMode.of(false, r.field, r.incognito, suggestAnyway = true))
            // Tự sửa không bao giờ chạy ở ô app tắt gợi ý (suggestionsAllowed vẫn false).
            if (r.field.appNoSuggestions) assertFalse(r.name, AutoCorrect.fieldAllows(r.field))
            // Không học ở ô app tắt gợi ý, dù cài đặt bật hay tắt.
            if (r.field.appNoSuggestions) assertFalse(r.name, StripMode.learns(true, r.field, r.incognito))
        }
        assertTrue(StripMode.learns(true, FieldTraits(), incognito = false))
        assertFalse(StripMode.learns(true, FieldTraits(), incognito = true))
        assertFalse(StripMode.learns(false, FieldTraits(), incognito = false))
    }

    @Test fun defaultOnAndBackedUp() {
        assertTrue(KeyboardSettings().suggestInNoSuggestFields)
        assertFalse(KeyboardSettings.load { if (it == Keys.SUGGEST_IN_NO_SUGGEST_FIELDS) false else null }.suggestInNoSuggestFields)
        assertEquals(SettingKind.Bool(true), BackupSettings.byKey[Keys.SUGGEST_IN_NO_SUGGEST_FIELDS]?.kind)
    }

    @Test fun sessionSuggestsButNoAutoCorrectNoLearning() {
        val s = KeyboardSession(UserLangModel(), null)
        s.startInput(KeyboardSettings(autoCorrect = true), appNo)
        assertTrue(s.suggestionsActive)
        assertEquals(StripMode.FULL, s.stripMode)
        assertFalse("không tự sửa", s.wantsTouches)
        val p = MockProxy()
        val before = s.langModel.count("người")
        for (c in "nguoi") s.handle(Key.Letter(c), p)
        val set = s.suggestionsNow(p)
        assertNotNull(set)
        assertTrue("có gợi ý chữ", set!!.word != null || set.word2 != null || set.literal != null)
        s.handle(Key.Space, p)
        assertEquals("không học", before, s.langModel.count("người"))
    }

    @Test fun sessionOffKeepsToolsOnly() {
        val s = KeyboardSession(UserLangModel(), null)
        s.startInput(KeyboardSettings(suggestInNoSuggestFields = false), appNo)
        assertFalse(s.suggestionsActive)
        assertEquals(StripMode.TOOLS, s.stripMode)
        val p = MockProxy()
        val before = s.langModel.count("người")
        for (c in "nguoi ") s.handle(if (c == ' ') Key.Space else Key.Letter(c), p)
        assertEquals("không học (cả khi tắt)", before, s.langModel.count("người"))
    }

    @Test fun addressFieldSuggestsLowercase() {
        val s = KeyboardSession(UserLangModel(), null)
        s.startInput(KeyboardSettings(), appNo.copy(capSentences = true, lowercaseSuggestions = true))
        val p = MockProxy()
        for (c in "binh") s.handle(Key.Letter(c), p)
        val set = s.suggestionsNow(p)!!
        for (w in listOfNotNull(set.word, set.word2) + set.nextWords) assertEquals(w.lowercase(), w)
    }
}
