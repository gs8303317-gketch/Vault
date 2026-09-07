package app.vault.workspace.ui.viewer

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Dependency-free OOXML text extract (docx / pptx / xlsx) via ZipInputStream + XML tag strip.
 * Read-only preview — not a full Office engine.
 */
object OfficeTextExtract {

    data class Result(
        val text: String,
        val formatLabel: String,
        val truncated: Boolean,
    )

    private const val MAX_OUT_CHARS = 500_000
    private const val MAX_ENTRY_BYTES = 2_000_000

    fun extract(bytes: ByteArray, mime: String, displayName: String = ""): Result {
        val kind = detectKind(mime, displayName)
        return when (kind) {
            Kind.DOCX -> extractMatching(bytes, "Word (.docx)") { path ->
                path == "word/document.xml"
            }
            Kind.PPTX -> extractMatching(bytes, "PowerPoint (.pptx)") { path ->
                path.startsWith("ppt/slides/slide") && path.endsWith(".xml")
            }
            Kind.XLSX -> extractXlsx(bytes)
            Kind.UNKNOWN -> Result(
                text = "",
                formatLabel = "Office",
                truncated = false,
            )
        }
    }

    fun detectKind(mime: String, displayName: String): Kind {
        val m = mime.lowercase()
        val ext = DocumentMime.extensionOf(displayName)
        return when {
            ext == "docx" || m.contains("wordprocessingml") -> Kind.DOCX
            ext == "pptx" || m.contains("presentationml") -> Kind.PPTX
            ext == "xlsx" || m.contains("spreadsheetml") -> Kind.XLSX
            else -> Kind.UNKNOWN
        }
    }

    enum class Kind { DOCX, PPTX, XLSX, UNKNOWN }

    private fun extractMatching(
        bytes: ByteArray,
        label: String,
        match: (String) -> Boolean,
    ): Result {
        val parts = ArrayList<String>()
        var truncated = false
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory && match(name)) {
                    val raw = readEntryLimited(zis)
                    if (raw.size >= MAX_ENTRY_BYTES) truncated = true
                    val stripped = stripXmlText(String(raw, Charsets.UTF_8))
                    if (stripped.isNotBlank()) {
                        parts.add(stripped)
                        total += stripped.length
                        if (total >= MAX_OUT_CHARS) {
                            truncated = true
                            break
                        }
                    }
                }
                zis.closeEntry()
            }
        }
        val joined = parts.joinToString("\n\n")
        val text = if (joined.length > MAX_OUT_CHARS) {
            truncated = true
            joined.take(MAX_OUT_CHARS)
        } else {
            joined
        }
        return Result(text = text, formatLabel = label, truncated = truncated)
    }

    private fun extractXlsx(bytes: ByteArray): Result {
        var shared = ""
        val sheetTexts = ArrayList<String>()
        var truncated = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                when {
                    name == "xl/sharedStrings.xml" -> {
                        val raw = readEntryLimited(zis)
                        if (raw.size >= MAX_ENTRY_BYTES) truncated = true
                        shared = stripXmlText(String(raw, Charsets.UTF_8))
                    }
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        val raw = readEntryLimited(zis)
                        if (raw.size >= MAX_ENTRY_BYTES) truncated = true
                        // Inline strings + cell values (numbers) appear as text nodes after strip.
                        val t = stripXmlText(String(raw, Charsets.UTF_8))
                        if (t.isNotBlank()) sheetTexts.add(t)
                    }
                }
                zis.closeEntry()
            }
        }
        val combined = buildString {
            if (shared.isNotBlank()) {
                append("Shared strings:\n")
                append(shared)
                append("\n\n")
            }
            sheetTexts.forEachIndexed { idx, t ->
                append("Sheet ${idx + 1}:\n")
                append(t)
                append("\n\n")
            }
        }.trim()
        val text = if (combined.length > MAX_OUT_CHARS) {
            truncated = true
            combined.take(MAX_OUT_CHARS)
        } else {
            combined
        }
        return Result(text = text, formatLabel = "Excel (.xlsx)", truncated = truncated)
    }

    private fun readEntryLimited(zis: ZipInputStream): ByteArray {
        val buf = ByteArray(8_192)
        val out = java.io.ByteArrayOutputStream()
        while (out.size() < MAX_ENTRY_BYTES) {
            val n = zis.read(buf)
            if (n <= 0) break
            val allow = minOf(n, MAX_ENTRY_BYTES - out.size())
            out.write(buf, 0, allow)
            if (allow < n) break
        }
        return out.toByteArray()
    }

    /**
     * Very small XML text extractor: concatenate character data outside tags,
     * collapse whitespace, decode a few common entities.
     */
    fun stripXmlText(xml: String): String {
        val sb = StringBuilder(xml.length / 4)
        var i = 0
        var inTag = false
        while (i < xml.length) {
            val c = xml[i]
            when {
                c == '<' -> inTag = true
                c == '>' -> inTag = false
                !inTag -> sb.append(c)
            }
            i++
        }
        val decoded = decodeEntities(sb.toString())
        return decoded
            .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
            .replace(Regex(" *\\n+ *"), "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun decodeEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
            }
}
