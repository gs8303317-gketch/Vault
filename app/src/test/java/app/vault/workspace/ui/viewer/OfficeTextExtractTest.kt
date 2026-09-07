package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficeTextExtractTest {
    @Test
    fun stripXmlTextBasic() {
        val xml = "<w:t>Hello&amp;world</w:t><w:t> next</w:t>"
        assertEquals("Hello&world next", OfficeTextExtract.stripXmlText(xml).replace("\n", " "))
    }

    @Test
    fun extractDocxFromZip() {
        val xml = """<?xml version="1.0"?><w:document><w:body><w:p><w:r><w:t>Vault secret</w:t></w:r></w:p></w:body></w:document>"""
        val bytes = zipOf("word/document.xml" to xml.toByteArray())
        val r = OfficeTextExtract.extract(
            bytes,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "note.docx",
        )
        assertTrue(r.text.contains("Vault secret"))
        assertEquals("Word (.docx)", r.formatLabel)
    }

    @Test
    fun detectKind() {
        assertEquals(
            OfficeTextExtract.Kind.XLSX,
            OfficeTextExtract.detectKind("application/octet-stream", "book.xlsx"),
        )
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
