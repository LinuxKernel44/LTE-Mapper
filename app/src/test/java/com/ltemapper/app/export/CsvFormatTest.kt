package com.ltemapper.app.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvFormatTest {

    @Test
    fun `plain values are not quoted`() {
        assertEquals("hello", CsvFormat.escape("hello"))
    }

    @Test
    fun `values with commas are quoted`() {
        assertEquals("\"a,b\"", CsvFormat.escape("a,b"))
    }

    @Test
    fun `internal quotes are doubled`() {
        assertEquals("\"a\"\"b\"", CsvFormat.escape("a\"b"))
    }

    @Test
    fun `splitLine handles simple comma separated values`() {
        assertEquals(listOf("a", "b", "c"), CsvFormat.splitLine("a,b,c"))
    }

    @Test
    fun `splitLine handles quoted fields containing commas`() {
        assertEquals(listOf("a", "b,c", "d"), CsvFormat.splitLine("a,\"b,c\",d"))
    }

    @Test
    fun `splitLine handles escaped quotes inside quoted fields`() {
        assertEquals(listOf("a\"b"), CsvFormat.splitLine("\"a\"\"b\""))
    }

    @Test
    fun `splitLine handles empty fields`() {
        assertEquals(listOf("a", "", "c"), CsvFormat.splitLine("a,,c"))
    }

    @Test
    fun `escape and splitLine round trip`() {
        val original = listOf("has,comma", "has\"quote", "plain", "")
        val line = original.joinToString(",") { CsvFormat.escape(it) }
        assertEquals(original, CsvFormat.splitLine(line))
    }

    @Test
    fun `plain escape does not neutralize formula-like values`() {
        // escape() alone is used for numeric fields (e.g. negative dBm) which legitimately start
        // with '-'; only escapeUntrustedText should touch the leading character.
        assertEquals("-87", CsvFormat.escape("-87"))
        assertEquals("=cmd", CsvFormat.escape("=cmd"))
    }

    @Test
    fun `escapeUntrustedText neutralizes leading formula-trigger characters`() {
        assertEquals("'=cmd|'/C calc'!A1", CsvFormat.escapeUntrustedText("=cmd|'/C calc'!A1"))
        assertEquals("'+1+1", CsvFormat.escapeUntrustedText("+1+1"))
        assertEquals("'-1+1", CsvFormat.escapeUntrustedText("-1+1"))
        assertEquals("'@SUM(A1)", CsvFormat.escapeUntrustedText("@SUM(A1)"))
    }

    @Test
    fun `escapeUntrustedText leaves ordinary operator names untouched`() {
        assertEquals("Orange", CsvFormat.escapeUntrustedText("Orange"))
        assertEquals("T-Mobile", CsvFormat.escapeUntrustedText("T-Mobile"))
    }
}
