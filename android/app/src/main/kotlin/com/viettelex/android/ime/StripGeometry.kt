package com.viettelex.android.ime

/**
 * Hình học slot thanh gợi ý — THUẦN. 3 slot BẰNG NHAU, cố định theo bề rộng bar (không
 * theo nội dung) ⇒ không "lúc rộng lúc hẹp" như lỗi iOS 27/09/2026. Thẻ Dán thay CẢ bar
 * (StripView không vẽ slot khi thẻ hiện). Pinned by StripGeometryTest.
 */
object StripGeometry {
    /** Chiều cao pill (thẻ Dán, chip số, Khôi phục) và ô nhấn slot, dp. */
    const val PILL_H = 32f
    /** Icon thanh công cụ khi mở (☰ mẫu câu, con trỏ, 📋), dp; ⌄ nhỏ hơn 2 dp. */
    const val ICON_OPEN = 22f
    /** Icon khi thu gọn (hàng nổi 14 dp). */
    const val ICON_COLLAPSED = 14f
    /** Cỡ chữ gợi ý (sp) — Gboard ~16–17; giữa đậm vừa. */
    const val WORD_SP = 17f

    /** Cỡ icon nội suy theo độ mở [o] (0 thu gọn … 1 mở); [delta] = bớt so với [ICON_OPEN]. */
    fun iconDp(o: Float, delta: Float = 0f): Float =
        ICON_COLLAPSED + (ICON_OPEN - delta - ICON_COLLAPSED) * o.coerceIn(0f, 1f)

    /** Mép trái/phải slot i (0..2) trong [barL, barR] → ghi vào [l]/[r]. */
    fun thirds(barL: Float, barR: Float, l: FloatArray, r: FloatArray) {
        val third = (barR - barL) / 3f
        for (i in 0..2) { l[i] = barL + i * third; r[i] = if (i == 2) barR else l[i] + third }
    }
}
