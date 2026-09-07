package app.vault.workspace.ui.viewer

/**
 * Lightweight CSV / TSV parser for an in-app table preview (first N rows / cols).
 */
object CsvTable {
    const val MAX_ROWS = 200
    const val MAX_COLS = 40

    data class Preview(
        val headers: List<String>,
        val rows: List<List<String>>,
        val totalRowsApprox: Int,
        val truncatedRows: Boolean,
        val truncatedCols: Boolean,
        val delimiter: Char,
    )

    fun detectDelimiter(sample: String): Char {
        val first = sample.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val tabs = first.count { it == '\t' }
        val commas = first.count { it == ',' }
        val semis = first.count { it == ';' }
        return when {
            tabs > 0 && tabs >= commas && tabs >= semis -> '\t'
            semis > commas -> ';'
            else -> ','
        }
    }

    fun parse(text: String, delimiter: Char? = null): Preview {
        val delim = delimiter ?: detectDelimiter(text)
        val allLines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val nonEmpty = allLines.filter { it.isNotEmpty() }
        val truncatedRows = nonEmpty.size > MAX_ROWS
        val take = nonEmpty.take(MAX_ROWS)
        if (take.isEmpty()) {
            return Preview(
                headers = emptyList(),
                rows = emptyList(),
                totalRowsApprox = 0,
                truncatedRows = false,
                truncatedCols = false,
                delimiter = delim,
            )
        }
        val parsed = take.map { parseLine(it, delim) }
        val maxCols = parsed.maxOf { it.size }.coerceAtMost(MAX_COLS)
        val truncatedCols = parsed.any { it.size > MAX_COLS }
        val normalized = parsed.map { row ->
            List(maxCols) { idx -> row.getOrElse(idx) { "" } }
        }
        // Use first row as header when it looks non-numeric-ish; always treat as header for preview.
        val headers = normalized.first()
        val body = normalized.drop(1)
        return Preview(
            headers = headers,
            rows = body,
            totalRowsApprox = nonEmpty.size,
            truncatedRows = truncatedRows,
            truncatedCols = truncatedCols,
            delimiter = delim,
        )
    }

    fun parseLine(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val cell = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    out.add(cell.toString())
                    cell.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        out.add(cell.toString())
        return out
    }
}
