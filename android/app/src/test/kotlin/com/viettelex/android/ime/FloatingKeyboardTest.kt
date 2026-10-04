package com.viettelex.android.ime

import com.viettelex.keyboard.Keys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bàn phím thả nổi (#112): kẹp vị trí, lưu theo chiều màn hình, vùng chạm / insets, thanh kéo. */
class FloatingKeyboardTest {
    @Test fun phoneWidthIsComfortableFraction() {
        val w = FloatingKeyboard.panelWidth(1080, tablet = false, landscape = false)
        assertTrue(w in (1080 * 0.75).toInt()..(1080 * 0.85).toInt())
        // Ngang / tablet hẹp hơn (không tràn bề ngang rộng); không bao giờ vượt màn hình.
        assertTrue(FloatingKeyboard.panelWidth(2400, false, true) < 2400 * 0.82)
        assertTrue(FloatingKeyboard.panelWidth(1600, true, false) < 1600 * 0.82)
        assertEquals(0, FloatingKeyboard.panelWidth(0, false, false))
        assertEquals(500, FloatingKeyboard.panelHeight(100, 376, 24))
    }

    @Test fun placeClampsPanelOnScreen() {
        // Vùng dọc [80, 2200) (dưới thanh trạng thái, trên nav bar), khung cao 700.
        assertEquals(80, FloatingKeyboard.place(0f, 80, 2200, 700))
        assertEquals(1500, FloatingKeyboard.place(1f, 80, 2200, 700))
        assertEquals(790, FloatingKeyboard.place(0.5f, 80, 2200, 700))
        // Phân số ngoài 0…1 / NaN bị kẹp.
        assertEquals(1500, FloatingKeyboard.place(3f, 80, 2200, 700))
        assertEquals(80, FloatingKeyboard.place(-1f, 80, 2200, 700))
        assertEquals(80, FloatingKeyboard.place(Float.NaN, 80, 2200, 700))
        // Khung lớn hơn vùng (ngang điện thoại, strip mở) ⇒ dính mép đầu, không âm.
        assertEquals(80, FloatingKeyboard.place(1f, 80, 600, 700))
    }

    @Test fun dragFractionRoundTripsAndClamps() {
        val x = FloatingKeyboard.place(0.3f, 0, 1080, 886)
        assertEquals(0.3f, FloatingKeyboard.fraction(x, 0, 1080, 886), 0.01f)
        // Kéo quá mép ⇒ kẹp 0 / 1 (khung không ra khỏi màn hình).
        assertEquals(0f, FloatingKeyboard.fraction(-500, 0, 1080, 886))
        assertEquals(1f, FloatingKeyboard.fraction(5000, 0, 1080, 886))
        assertEquals(0f, FloatingKeyboard.fraction(10, 0, 500, 886))      // không có khoảng trống
        assertEquals(0, FloatingKeyboard.clamp(-20, 0, 1080, 886))
        assertEquals(194, FloatingKeyboard.clamp(400, 0, 1080, 886))
        assertEquals(50, FloatingKeyboard.clamp(50, 0, 1080, 886))
    }

    @Test fun positionPersistsPerOrientation() {
        val store = HashMap<String, String>()
        val get: (String) -> String? = { store[it] }
        val put: (String, String) -> Unit = { k, v -> store[k] = v }
        // Chưa lưu ⇒ giữa ngang, sát đáy.
        assertEquals(FloatingKeyboard.DEFAULT_FX to FloatingKeyboard.DEFAULT_FY, FloatingKeyboard.load(false, get))
        FloatingKeyboard.save(landscape = false, fx = 0.25f, fy = 0.4f, put = put)
        FloatingKeyboard.save(landscape = true, fx = 0.9f, fy = 0.1f, put = put)
        assertEquals(setOf(Keys.FLOATING_POS_PORTRAIT, Keys.FLOATING_POS_LANDSCAPE), store.keys)
        val p = FloatingKeyboard.load(false, get)
        val l = FloatingKeyboard.load(true, get)
        assertEquals(0.25f, p.first, 1e-4f); assertEquals(0.4f, p.second, 1e-4f)
        assertEquals(0.9f, l.first, 1e-4f); assertEquals(0.1f, l.second, 1e-4f)
        // Ghi một chiều không đụng chiều kia.
        FloatingKeyboard.save(false, 1f, 1f, put)
        assertEquals(0.9f, FloatingKeyboard.load(true, get).first, 1e-4f)
    }

    @Test fun decodeRejectsGarbageAndClamps() {
        assertNull(FloatingKeyboard.decode(null))
        assertNull(FloatingKeyboard.decode(""))
        assertNull(FloatingKeyboard.decode("0.5"))
        assertNull(FloatingKeyboard.decode("a,b"))
        assertNull(FloatingKeyboard.decode("NaN,0.2"))
        assertEquals(1f to 0f, FloatingKeyboard.decode("7,-2"))
        // Locale-independent (dấu chấm thập phân).
        assertEquals("0.5000,1.0000", FloatingKeyboard.encode(0.5f, 1f))
        // Giá trị hỏng ⇒ vị trí mặc định.
        assertEquals(FloatingKeyboard.DEFAULT_FX to FloatingKeyboard.DEFAULT_FY, FloatingKeyboard.load(false) { "garbage" })
    }

    @Test fun touchRegionInWindowCoordinates() {
        // View gốc ở (0, 0) trong cửa sổ toàn màn hình: vùng = khung.
        assertArrayEquals(intArrayOf(97, 1200, 983, 1900), FloatingKeyboard.touchRegion(0, 0, 97, 1200, 983, 1900))
        // View gốc dời xuống (cửa sổ có phần trên khác) ⇒ cộng offset.
        assertArrayEquals(intArrayOf(97, 1263, 983, 1963), FloatingKeyboard.touchRegion(0, 63, 97, 1200, 983, 1900))
        // App không co: content/visible top = đáy cửa sổ.
        assertEquals(2340, FloatingKeyboard.contentTopInsets(2340))
        assertEquals(0, FloatingKeyboard.contentTopInsets(-1))
    }

    @Test fun handleHitDragVsDockButton() {
        // Khung x 100…900, thanh kéo y 1876…1900, nút gắn lại rộng 126 ở đầu phải.
        assertEquals(0, FloatingKeyboard.handleHit(100, 900, 1876, 1900, 126, 500f, 1888f))
        assertEquals(1, FloatingKeyboard.handleHit(100, 900, 1876, 1900, 126, 880f, 1888f))
        assertEquals(-1, FloatingKeyboard.handleHit(100, 900, 1876, 1900, 126, 500f, 1800f))   // vùng phím
        assertEquals(-1, FloatingKeyboard.handleHit(100, 900, 1876, 1900, 126, 50f, 1888f))    // ngoài khung
        assertEquals(-1, FloatingKeyboard.handleHit(100, 900, 1876, 1900, 126, 900f, 1888f))
    }
}
