package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTableTest {
    @Test
    fun parseCommaQuoted() {
        val p = CsvTable.parse("name,age\n\"Doe, Jane\",42\nbob,7")
        assertEquals(',', p.delimiter)
        assertEquals(listOf("name", "age"), p.headers)
        assertEquals(2, p.rows.size)
        assertEquals("Doe, Jane", p.rows[0][0])
        assertEquals("42", p.rows[0][1])
    }

    @Test
    fun parseTsv() {
        val p = CsvTable.parse("a\tb\nc\td")
        assertEquals('\t', p.delimiter)
        assertEquals(listOf("a", "b"), p.headers)
        assertEquals(listOf("c", "d"), p.rows[0])
    }

    @Test
    fun capsRows() {
        val lines = (1..(CsvTable.MAX_ROWS + 20)).joinToString("\n") { "r$it,x" }
        val p = CsvTable.parse("h1,h2\n$lines")
        assertTrue(p.truncatedRows)
        assertEquals(CsvTable.MAX_ROWS - 1, p.rows.size) // header consumed
    }
}
