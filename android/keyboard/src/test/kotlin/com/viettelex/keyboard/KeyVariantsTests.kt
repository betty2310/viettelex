package com.viettelex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Giữ phím bàn số / ký hiệu ra hàng biến thể như stock iOS. Song sinh iOS KeyVariantsTests. */
class KeyVariantsTests {
    /**
     * Phím trên bàn 123 / ký hiệu Android (android/app KeyLayout NUM1/NUM2/NUM3/SYM1/SYM2/SYM3 +
     * "," "." hàng đáy) — KeyLayoutTest kiểm lại từ layout thật.
     */
    private val numbersKeys = listOf("1","2","3","4","5","6","7","8","9","0",
        "@","#","₫","_","&","-","+","(",")","/", "*","\"","'",":",";","!","?", ",",".")
    private val symbolsKeys = listOf("~","`","|","•","√","π","÷","×","¶","∆",
        "/","£","€","¥","^","°","=","{","}","\\", "%","©","®","™","✓","[","]","<",">", ",",".")

    @Test fun everyEntryStartsWithItsBaseAndIsOnAPlane() {
        val onPlanes = (numbersKeys + symbolsKeys).toSet()
        for ((key, list) in KeyVariants.table) {
            assertEquals("ô 0 = ký tự gốc — $key", key, list.first())
            assertTrue(key, list.size >= 2)
            assertEquals("$key: trùng biến thể", list.size, list.toSet().size)
            assertTrue("$key không có trên bàn số/ký hiệu", key in onPlanes)
        }
    }

    @Test fun stockOrdering() {
        val t = KeyVariants.table
        assertEquals(listOf("\"", "”", "“", "„", "»", "«"), t["\""])
        assertEquals(listOf("'", "‘", "’", "`"), t["'"])
        assertEquals(listOf("-", "–", "—", "•"), t["-"])
        assertEquals(listOf("0", "°"), t["0"])
        assertEquals(listOf(".", "…"), t["."])
        assertEquals(listOf("?", "¿"), t["?"])
        assertEquals(listOf("!", "¡"), t["!"])
        assertEquals(listOf("/", "\\"), t["/"])
        assertEquals(listOf("&", "§"), t["&"])
        assertEquals(listOf("%", "‰"), t["%"])
        // Android: ₫ là phím gốc (#113), giữ ⇒ $ € £ ¥ ₩ ₹ ¢.
        assertEquals(listOf("₫", "\$", "€", "£", "¥", "₩", "₹", "¢"), t["₫"])
        assertEquals(listOf("=", "≠", "≈"), t["="])
        assertEquals(listOf("+", "±"), t["+"])
        assertEquals(listOf("*", "×"), t["*"])
        assertEquals("\$ không còn là phím Android", null, t["\$"])
    }

    @Test fun gating() {
        assertEquals(KeyVariants.table["\""], KeyVariants.variants("\"", symbolPlane = true))
        assertEquals("bàn chữ: không", emptyList<String>(), KeyVariants.variants("\"", symbolPlane = false))
        assertEquals("không đè giữ q…p", emptyList<String>(), KeyVariants.variants("e", symbolPlane = false))
        assertEquals("ô số: không", emptyList<String>(), KeyVariants.variants("0", symbolPlane = true, numericField = true))
        assertEquals("phím không có biến thể", emptyList<String>(), KeyVariants.variants("@", symbolPlane = true))
    }

    /**
     * Hai bảng (Kotlin / Swift) trùng ở mọi phím có trên CẢ HAI nền tảng — đọc thẳng file Swift.
     * Bàn 123 Android theo Gboard (#113) nên được lệch đúng ở [KeyVariants.ANDROID_ONLY] /
     * [KeyVariants.IOS_ONLY]; lệch thêm phím nào phải khai báo ở đó.
     */
    @Test fun matchesSwiftTable() {
        val f = listOf("../../iOS", "../iOS", "iOS").map { File(it, "Keyboard/KeyVariants.swift") }
            .firstOrNull { it.exists() } ?: error("không thấy iOS/Keyboard/KeyVariants.swift")
        val row = Regex("""^\s*"((?:\\.|[^"\\])*)": \[(.*)],\s*$""")
        val item = Regex(""""((?:\\.|[^"\\])*)"""")
        fun unesc(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
        val swift = f.readLines().mapNotNull { row.find(it) }.associate { m ->
            unesc(m.groupValues[1]) to item.findAll(m.groupValues[2]).map { unesc(it.groupValues[1]) }.toList()
        }
        val android = KeyVariants.table
        assertEquals("chỉ Android", KeyVariants.ANDROID_ONLY, android.keys - swift.keys)
        assertEquals("chỉ iOS", KeyVariants.IOS_ONLY, swift.keys - android.keys)
        val shared = android.keys intersect swift.keys
        assertTrue(shared.size >= 20)
        for (k in shared) assertEquals("biến thể \"$k\" lệch iOS", swift[k], android[k])
    }
}
