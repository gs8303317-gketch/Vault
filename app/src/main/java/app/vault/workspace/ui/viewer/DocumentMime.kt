package app.vault.workspace.ui.viewer

/**
 * Document mime / extension routing for Phase 3 viewers.
 * Pure JVM helpers — unit-tested without Compose / Android framework.
 */
object DocumentMime {

    enum class ViewerKind {
        PDF,
        MARKDOWN,
        CSV,
        HTML,
        /** OOXML (docx/pptx/xlsx) — dependency-free zip+xml text extract. */
        OFFICE_TEXT,
        PLAIN_TEXT,
        /** No in-app viewer — premium OtherFile shell + Export. */
        OTHER,
    }

    /** Friendly category label for OtherFile premium card. */
    fun categoryLabel(mime: String, displayName: String = ""): String {
        return when (viewerKind(mime, displayName)) {
            ViewerKind.PDF -> "PDF document"
            ViewerKind.MARKDOWN -> "Markdown"
            ViewerKind.CSV -> "Spreadsheet (CSV/TSV)"
            ViewerKind.HTML -> "HTML document"
            ViewerKind.OFFICE_TEXT -> officeLabel(mime, displayName)
            ViewerKind.PLAIN_TEXT -> "Text document"
            ViewerKind.OTHER -> otherLabel(mime, displayName)
        }
    }

    fun viewerKind(mime: String, displayName: String = ""): ViewerKind {
        val m = mime.lowercase().trim()
        val ext = extensionOf(displayName)

        if (m == "application/pdf" || ext == "pdf") return ViewerKind.PDF

        if (isMarkdown(m, ext)) return ViewerKind.MARKDOWN
        if (isCsv(m, ext)) return ViewerKind.CSV
        if (isHtml(m, ext)) return ViewerKind.HTML
        if (isOoxmlOffice(m, ext)) return ViewerKind.OFFICE_TEXT

        // Legacy binary Office / EPUB / archives — export-only shell.
        if (isLegacyOffice(m, ext) || isEpub(m, ext)) return ViewerKind.OTHER

        if (TextEncoding.isPlainTextDocumentMime(m) || isPlainTextExt(ext)) {
            return ViewerKind.PLAIN_TEXT
        }

        // text/* that somehow wasn't caught (defensive)
        if (m.startsWith("text/")) return ViewerKind.PLAIN_TEXT

        return ViewerKind.OTHER
    }

    fun isImmersiveDocument(kind: ViewerKind): Boolean =
        kind == ViewerKind.PDF ||
            kind == ViewerKind.MARKDOWN ||
            kind == ViewerKind.CSV ||
            kind == ViewerKind.HTML ||
            kind == ViewerKind.OFFICE_TEXT ||
            kind == ViewerKind.PLAIN_TEXT

    /**
     * When ContentResolver returns a generic mime, prefer extension from [displayName].
     * Never invent a mime when the resolver already gave a specific type.
     */
    fun resolveImportMime(resolverMime: String?, displayName: String?): String {
        val raw = resolverMime?.trim().orEmpty()
        val generic = raw.isEmpty() ||
            raw.equals("application/octet-stream", ignoreCase = true) ||
            raw.equals("application/zip", ignoreCase = true) || // OOXML is zip underneath
            raw.equals("*/*", ignoreCase = true)
        if (!generic) return raw
        val fromExt = mimeFromExtension(extensionOf(displayName.orEmpty()))
        return fromExt ?: (if (raw.isEmpty()) "application/octet-stream" else raw)
    }

    fun mimeFromExtension(ext: String): String? = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "md", "markdown", "mdown" -> "text/markdown"
        "csv" -> "text/csv"
        "tsv", "tab" -> "text/tab-separated-values"
        "html", "htm" -> "text/html"
        "txt", "log", "text" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "js", "mjs" -> "application/javascript"
        "css" -> "text/css"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "doc" -> "application/msword"
        "ppt" -> "application/vnd.ms-powerpoint"
        "xls" -> "application/vnd.ms-excel"
        "epub" -> "application/epub+zip"
        "rtf" -> "application/rtf"
        else -> null
    }

    /**
     * Heuristic: first [sample] bytes look like text (high printable / whitespace ratio,
     * few NULs). Used by OtherFile "Open as text".
     */
    fun looksLikeText(bytes: ByteArray, sample: Int = 4096): Boolean {
        if (bytes.isEmpty()) return true
        val n = minOf(bytes.size, sample)
        var printable = 0
        var nul = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == 0 -> nul++
                b == 9 || b == 10 || b == 13 -> printable++
                b in 32..126 -> printable++
                b >= 0xC0 -> printable++ // likely UTF-8 lead
                b in 0x80..0xBF -> printable++ // UTF-8 cont
                else -> { /* control */ }
            }
        }
        if (nul > 0) return false
        return printable.toFloat() / n >= 0.85f
    }

    fun extensionOf(name: String): String {
        val base = name.substringAfterLast('/', name)
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.lastIndex) return ""
        return base.substring(dot + 1).lowercase()
    }

    private fun isMarkdown(m: String, ext: String): Boolean =
        m == "text/markdown" ||
            m == "text/x-markdown" ||
            ext == "md" ||
            ext == "markdown" ||
            ext == "mdown"

    private fun isCsv(m: String, ext: String): Boolean =
        m == "text/csv" ||
            m == "text/comma-separated-values" ||
            m == "text/tab-separated-values" ||
            m == "application/csv" ||
            ext == "csv" ||
            ext == "tsv" ||
            ext == "tab"

    private fun isHtml(m: String, ext: String): Boolean =
        m == "text/html" ||
            m == "application/xhtml+xml" ||
            ext == "html" ||
            ext == "htm"

    private fun isOoxmlOffice(m: String, ext: String): Boolean {
        if (ext == "docx" || ext == "pptx" || ext == "xlsx") return true
        if (!m.contains("officedocument")) return false
        return m.contains("wordprocessingml") ||
            m.contains("presentationml") ||
            m.contains("spreadsheetml")
    }

    private fun isLegacyOffice(m: String, ext: String): Boolean =
        ext == "doc" ||
            ext == "ppt" ||
            ext == "xls" ||
            ext == "rtf" ||
            m == "application/msword" ||
            m == "application/vnd.ms-powerpoint" ||
            m == "application/vnd.ms-excel" ||
            m == "application/rtf" ||
            m == "text/rtf"

    private fun isEpub(m: String, ext: String): Boolean =
        ext == "epub" || m == "application/epub+zip"

    private fun isPlainTextExt(ext: String): Boolean =
        ext in setOf(
            "txt", "log", "text", "json", "xml", "js", "mjs", "css",
            "kt", "java", "py", "c", "h", "cpp", "rs", "go", "sh", "yml", "yaml",
            "toml", "ini", "cfg", "conf", "properties", "gradle", "kts", "sql",
        )

    private fun officeLabel(mime: String, displayName: String): String {
        val ext = extensionOf(displayName)
        val m = mime.lowercase()
        return when {
            ext == "docx" || m.contains("wordprocessingml") -> "Word document (text preview)"
            ext == "pptx" || m.contains("presentationml") -> "PowerPoint (text preview)"
            ext == "xlsx" || m.contains("spreadsheetml") -> "Excel (text preview)"
            else -> "Office document (text preview)"
        }
    }

    private fun otherLabel(mime: String, displayName: String): String {
        val ext = extensionOf(displayName)
        val m = mime.lowercase()
        return when {
            isEpub(m, ext) -> "EPUB (export only — no in-app reader)"
            ext == "doc" || m == "application/msword" -> "Word (.doc) — no in-app viewer"
            ext == "ppt" || m.contains("ms-powerpoint") -> "PowerPoint (.ppt) — no in-app viewer"
            ext == "xls" || m.contains("ms-excel") -> "Excel (.xls) — no in-app viewer"
            ext == "rtf" || m.contains("rtf") -> "RTF — no in-app viewer"
            m.startsWith("application/") -> "File"
            else -> "File"
        }
    }
}
