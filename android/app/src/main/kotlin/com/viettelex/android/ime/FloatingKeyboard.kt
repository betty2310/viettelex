package com.viettelex.android.ime

import com.viettelex.keyboard.Keys
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bàn phím thả nổi (#112, kiểu Gboard) — phần THUẦN (không Android API), pinned by
 * FloatingKeyboardTest.
 *
 * Cách làm: cửa sổ IME cao gần hết màn hình, nền TRONG SUỐT; khung bàn phím (thanh gợi ý +
 * phím + thanh kéo ở đáy) vẽ bo góc ở vị trí tuỳ ý. onComputeInsets:
 * contentTopInsets = visibleTopInsets = chiều cao cửa sổ ⇒ app KHÔNG bị co (như overlay);
 * touchableInsets = REGION đúng khung ⇒ chạm ngoài khung rơi xuống app.
 *
 * Vị trí lưu dạng phân số (fx, fy) ∈ [0, 1] của khoảng trống còn lại theo mỗi trục ⇒ đổi cỡ
 * khung / cửa sổ (đổi strip, chia màn hình) vẫn nằm trong màn hình mà không phải kẹp lại số
 * pixel cũ. Lưu riêng dọc / ngang.
 */
object FloatingKeyboard {
    /** Chiều cao thanh kéo ở đáy khung (dp). */
    const val HANDLE_DP = 24f
    /** Bo góc khung (dp). */
    const val RADIUS_DP = 14f
    /** Bề rộng vùng nút "gắn lại" ở đầu phải thanh kéo (dp). */
    const val EXIT_W_DP = 48f
    /** Vị trí mặc định: giữa ngang, sát đáy (như bàn phím thường, chỉ hẹp hơn). */
    const val DEFAULT_FX = 0.5f
    const val DEFAULT_FY = 1f

    /** Tỉ lệ bề ngang khung / màn hình: điện thoại dọc 0.82 (Gboard ≈ 0.8), ngang + tablet hẹp hơn. */
    fun widthRatio(tablet: Boolean, landscape: Boolean): Float = when {
        tablet && landscape -> 0.45f
        tablet -> 0.6f
        landscape -> 0.55f
        else -> 0.82f
    }

    fun panelWidth(screenW: Int, tablet: Boolean, landscape: Boolean): Int =
        (screenW * widthRatio(tablet, landscape)).roundToInt().coerceIn(0, maxOf(screenW, 0))

    /** Khung cao = strip + vùng phím + thanh kéo. */
    fun panelHeight(strip: Int, keyArea: Int, handle: Int): Int = strip + keyArea + handle

    /** Khoảng trống còn lại theo một trục (≥ 0). */
    private fun slack(areaStart: Int, areaEnd: Int, size: Int): Int = maxOf(areaEnd - areaStart - size, 0)

    /**
     * Toạ độ trái/trên của khung kích thước [size] trong vùng [areaStart, areaEnd) theo phân
     * số [f] (kẹp 0…1). Khung lớn hơn vùng ⇒ dính mép đầu.
     */
    fun place(f: Float, areaStart: Int, areaEnd: Int, size: Int): Int =
        areaStart + (slack(areaStart, areaEnd, size) * clampFraction(f)).roundToInt()

    /** Ngược của [place]: toạ độ (có thể ngoài vùng khi đang kéo) → phân số đã kẹp. */
    fun fraction(pos: Int, areaStart: Int, areaEnd: Int, size: Int): Float {
        val s = slack(areaStart, areaEnd, size)
        if (s == 0) return 0f
        return clampFraction((pos - areaStart).toFloat() / s)
    }

    /** Kẹp toạ độ trái/trên để khung nằm trọn trong vùng (khung to hơn vùng ⇒ mép đầu). */
    fun clamp(pos: Int, areaStart: Int, areaEnd: Int, size: Int): Int =
        pos.coerceIn(areaStart, areaStart + slack(areaStart, areaEnd, size))

    fun clampFraction(f: Float): Float = if (f.isNaN()) 0f else f.coerceIn(0f, 1f)

    // --- lưu vị trí (theo chiều màn hình) ---

    fun prefKey(landscape: Boolean): String =
        if (landscape) Keys.FLOATING_POS_LANDSCAPE else Keys.FLOATING_POS_PORTRAIT

    fun encode(fx: Float, fy: Float): String =
        String.format(Locale.US, "%.4f,%.4f", clampFraction(fx), clampFraction(fy))

    /** "fx,fy" → cặp đã kẹp; hỏng / vắng ⇒ null (dùng mặc định). */
    fun decode(s: String?): Pair<Float, Float>? {
        val parts = s?.split(',') ?: return null
        if (parts.size != 2) return null
        val x = parts[0].trim().toFloatOrNull() ?: return null
        val y = parts[1].trim().toFloatOrNull() ?: return null
        if (x.isNaN() || y.isNaN()) return null
        return clampFraction(x) to clampFraction(y)
    }

    /** Đọc vị trí đã lưu của chiều [landscape] ([get] = prefs.getString). */
    fun load(landscape: Boolean, get: (String) -> String?): Pair<Float, Float> =
        decode(get(prefKey(landscape))) ?: (DEFAULT_FX to DEFAULT_FY)

    /** Ghi vị trí cho chiều [landscape] ([put] = prefs.edit().putString(...).apply()). */
    fun save(landscape: Boolean, fx: Float, fy: Float, put: (String, String) -> Unit) =
        put(prefKey(landscape), encode(fx, fy))

    // --- insets / chạm ---

    /**
     * Vùng nhận chạm (toạ độ CỬA SỔ) = khung trong view gốc dời theo vị trí view gốc trong cửa
     * sổ. Trả [left, top, right, bottom].
     */
    fun touchRegion(rootX: Int, rootY: Int, left: Int, top: Int, right: Int, bottom: Int): IntArray =
        intArrayOf(rootX + left, rootY + top, rootX + right, rootY + bottom)

    /** contentTop / visibleTop khi nổi = đáy cửa sổ ⇒ app không bị co, không bị đẩy lên. */
    fun contentTopInsets(windowHeight: Int): Int = maxOf(windowHeight, 0)

    /** Chạm ở thanh kéo: 0 = kéo, 1 = nút gắn lại (đầu phải), -1 = ngoài thanh. */
    fun handleHit(panelLeft: Int, panelRight: Int, handleTop: Int, handleBottom: Int,
                  exitW: Int, x: Float, y: Float): Int {
        if (x < panelLeft || x >= panelRight || y < handleTop || y >= handleBottom) return -1
        return if (x >= panelRight - exitW) 1 else 0
    }
}
