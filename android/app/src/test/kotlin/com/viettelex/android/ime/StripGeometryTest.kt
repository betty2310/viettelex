package com.viettelex.android.ime

import com.viettelex.keyboard.BarLayout
import com.viettelex.keyboard.SuggestionSet
import com.viettelex.keyboard.SuggestionSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lỗi iOS 27/09/2026: thẻ Dán vẽ chồng chữ gợi ý, slot đổi bề rộng theo nội dung. */
class StripGeometryTest {
    @Test fun threeEqualContiguousSlots() {
        val l = FloatArray(3); val r = FloatArray(3)
        StripGeometry.thirds(100f, 400f, l, r)
        assertEquals(100f, l[0]); assertEquals(400f, r[2])
        for (i in 0..2) assertEquals(100f, r[i] - l[i], 0.001f)
        assertEquals(r[0], l[1]); assertEquals(r[1], l[2])
    }

    @Test fun gboardProportions() {
        // #112: strip 42 dp ≈ 0.75 hàng phím chuẩn (56 dp); pill / ô nhấn nằm gọn trong dải.
        val row = KeyLayout.keyAreaDp(false, false, 0) / 4f
        assertEquals(0.75f, KeyLayout.OPEN_STRIP / row, 0.01f)
        val half = StripGeometry.PILL_H / 2
        assertTrue(KeyLayout.BAR_CY - half >= 0f && KeyLayout.BAR_CY + half <= KeyLayout.OPEN_STRIP)
        assertTrue(StripGeometry.WORD_SP >= 16f)
        // Icon: thu gọn 14 dp, mở 22 dp (⌄ bớt 2), nội suy theo độ mở và kẹp 0…1.
        assertEquals(14f, StripGeometry.iconDp(0f), 0.001f)
        assertEquals(22f, StripGeometry.iconDp(1f), 0.001f)
        assertEquals(20f, StripGeometry.iconDp(1f, delta = 2f), 0.001f)
        assertEquals(18f, StripGeometry.iconDp(0.5f), 0.001f)
        assertEquals(22f, StripGeometry.iconDp(3f), 0.001f)
    }

    @Test fun pasteCardReplacesWholeBar() {
        // Có chữ gợi ý + thẻ Dán ⇒ chỉ thẻ Dán (không slot nào vẽ chồng).
        val set = SuggestionSet(literal = "xin", word = "xin", word2 = "xinh", paste = true)
        assertSame(BarLayout.PasteCard, SuggestionSlots.arrange(set, undoAsPill = true, bestInMiddle = true))
    }
}
