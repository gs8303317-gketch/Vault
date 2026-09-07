package app.vault.workspace.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRenderTest {
    @Test
    fun parsesHeadersListsCode() {
        val src = """
            # Title
            Intro paragraph
            - one
            - two
            ```kotlin
            val x = 1
            ```
            ## Sub
            1. first
            > quote
            ---
        """.trimIndent()
        val blocks = MarkdownRender.parse(src)
        assertTrue(blocks.any { it is MarkdownRender.Block.Heading && it.level == 1 && it.text == "Title" })
        assertTrue(blocks.any { it is MarkdownRender.Block.Bullet && it.text == "one" })
        assertTrue(blocks.any { it is MarkdownRender.Block.Code && it.language == "kotlin" && it.code.contains("val x") })
        assertTrue(blocks.any { it is MarkdownRender.Block.Ordered && it.index == 1 })
        assertTrue(blocks.any { it is MarkdownRender.Block.Quote })
        assertTrue(blocks.any { it is MarkdownRender.Block.Rule })
    }

    @Test
    fun stripInline() {
        assertEquals("bold", MarkdownRender.stripInline("**bold**"))
        assertEquals("code", MarkdownRender.stripInline("`code`"))
        assertEquals("link", MarkdownRender.stripInline("[link](http://x)"))
    }
}
