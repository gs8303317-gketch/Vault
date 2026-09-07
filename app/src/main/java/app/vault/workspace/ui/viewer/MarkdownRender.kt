package app.vault.workspace.ui.viewer

/**
 * Tiny Markdown → display blocks (headers, lists, code fences, paragraphs).
 * Not a full CommonMark parser — enough for a premium in-vault preview.
 */
object MarkdownRender {

    sealed class Block {
        data class Heading(val level: Int, val text: String) : Block()
        data class Paragraph(val text: String) : Block()
        data class Bullet(val text: String) : Block()
        data class Ordered(val index: Int, val text: String) : Block()
        data class Code(val language: String, val code: String) : Block()
        data object Rule : Block()
        data class Quote(val text: String) : Block()
    }

    fun parse(source: String, maxBlocks: Int = 4_000): List<Block> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val out = ArrayList<Block>(minOf(lines.size, 256))
        var i = 0
        var inFence = false
        var fenceLang = ""
        val fenceBuf = StringBuilder()

        fun flushPara(buf: StringBuilder) {
            val t = buf.toString().trimEnd()
            if (t.isNotEmpty()) out.add(Block.Paragraph(t))
            buf.clear()
        }

        val para = StringBuilder()
        while (i < lines.size && out.size < maxBlocks) {
            val line = lines[i]
            if (inFence) {
                if (line.trimStart().startsWith("```")) {
                    out.add(Block.Code(fenceLang, fenceBuf.toString().trimEnd()))
                    fenceBuf.clear()
                    inFence = false
                    fenceLang = ""
                } else {
                    if (fenceBuf.isNotEmpty()) fenceBuf.append('\n')
                    fenceBuf.append(line)
                }
                i++
                continue
            }

            val trimmed = line.trimEnd()
            val t = trimmed.trimStart()
            when {
                t.startsWith("```") -> {
                    flushPara(para)
                    inFence = true
                    fenceLang = t.removePrefix("```").trim()
                }
                t.matches(Regex("^#{1,6}\\s+.+")) -> {
                    flushPara(para)
                    val level = t.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    out.add(Block.Heading(level, t.drop(level).trim()))
                }
                t == "---" || t == "***" || t == "___" -> {
                    flushPara(para)
                    out.add(Block.Rule)
                }
                t.startsWith("> ") || t == ">" -> {
                    flushPara(para)
                    out.add(Block.Quote(t.removePrefix(">").trimStart()))
                }
                t.matches(Regex("^[-*+]\\s+.+")) -> {
                    flushPara(para)
                    out.add(Block.Bullet(t.drop(2).trimStart().ifEmpty { t.drop(1).trimStart() }))
                }
                t.matches(Regex("^\\d+\\.\\s+.+")) -> {
                    flushPara(para)
                    val dot = t.indexOf('.')
                    val idx = t.substring(0, dot).toIntOrNull() ?: 1
                    out.add(Block.Ordered(idx, t.substring(dot + 1).trimStart()))
                }
                t.isEmpty() -> flushPara(para)
                else -> {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(trimmed)
                }
            }
            i++
        }
        if (inFence && fenceBuf.isNotEmpty() && out.size < maxBlocks) {
            out.add(Block.Code(fenceLang, fenceBuf.toString().trimEnd()))
        }
        flushPara(para)
        return out
    }

    /** Strip a few inline markers for display (bold/italic/code) → plain-ish string. */
    fun stripInline(text: String): String {
        var s = text
        s = s.replace(Regex("`([^`]+)`"), "$1")
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        s = s.replace(Regex("__([^_]+)__"), "$1")
        s = s.replace(Regex("\\*([^*]+)\\*"), "$1")
        s = s.replace(Regex("_([^_]+)_"), "$1")
        s = s.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        return s
    }
}
