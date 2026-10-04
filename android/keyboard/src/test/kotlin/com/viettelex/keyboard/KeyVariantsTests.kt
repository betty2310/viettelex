package com.viettelex.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Giữ phím bàn số / ký hiệu ra hàng biến thể như stock iOS. Song sinh iOS KeyVariantsTests. */
class KeyVariantsTests {
    /** Phím trên bàn 123 / #+= (android/app KeyLayout NUM1/NUM2/SYM1/SYM2/PUNCT3 + hàng đáy). */
    private val numbersKeys = listOf("1","2","3","4","5","6","7","8","9","0",
        "-","/",":",";","(",")","\$","&","@","\"", ".",",","?","!","'")
    private val symbolsKeys = listOf("[","]","{","}","#","%","^","*","+","=",
        "_","\\","|","~","<",">","€","¥","₫","•", ".",",","?","!","'")

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
        assertEquals("₫ ngay sau \$ cho người dùng Việt", listOf("\$", "₫"), t["\$"]!!.take(2))
        assertEquals(setOf("\$", "₫", "€", "£", "¥", "₩", "₹", "₽", "¢"), t["\$"]!!.toSet())
    }

    @Test fun gating() {
        assertEquals(KeyVariants.table["\""], KeyVariants.variants("\"", symbolPlane = true))
        assertEquals("bàn chữ: không", emptyList<String>(), KeyVariants.variants("\"", symbolPlane = false))
        assertEquals("không đè giữ q…p", emptyList<String>(), KeyVariants.variants("e", symbolPlane = false))
        assertEquals("ô số: không", emptyList<String>(), KeyVariants.variants("0", symbolPlane = true, numericField = true))
        assertEquals("phím không có biến thể", emptyList<String>(), KeyVariants.variants("@", symbolPlane = true))
    }

    /** Hai bảng (Kotlin / Swift) phải cùng dữ liệu — đọc thẳng file Swift. */
    @Test fun matchesSwiftTable() {
        val f = listOf("../../iOS", "../iOS", "iOS").map { File(it, "Keyboard/KeyVariants.swift") }
            .firstOrNull { it.exists() } ?: error("không thấy iOS/Keyboard/KeyVariants.swift")
        val row = Regex("""^\s*"((?:\\.|[^"\\])*)": \[(.*)],\s*$""")
        val item = Regex(""""((?:\\.|[^"\\])*)"""")
        fun unesc(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
        val swift = f.readLines().mapNotNull { row.find(it) }.associate { m ->
            unesc(m.groupValues[1]) to item.findAll(m.groupValues[2]).map { unesc(it.groupValues[1]) }.toList()
        }
        assertEquals(KeyVariants.table.size, swift.size)
        assertEquals(swift, KeyVariants.table)
    }
}
