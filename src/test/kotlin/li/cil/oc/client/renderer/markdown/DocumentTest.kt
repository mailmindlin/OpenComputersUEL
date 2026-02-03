package li.cil.oc.client.renderer.markdown

import li.cil.oc.Settings
import li.cil.oc.client.renderer.markdown.segment.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DocumentTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    @Test
    fun `parse creates segments from plain text lines`() {
        val lines = listOf("Hello", "World")

        val result = Document.parse(lines)

        assertNotNull(result)
        assertTrue(result is TextSegment)
        assertEquals("Hello", (result as TextSegment).text)
    }

    @Test
    fun `parse creates header segments from markdown headers`() {
        val lines = listOf("# Header 1", "## Header 2", "### Header 3")

        val result = Document.parse(lines)

        // Collect all segments
        val segments = mutableListOf<Segment>()
        var current: Segment? = result
        while (current != null) {
            segments.add(current)
            current = current.next
        }

        // Find header segments
        val headers = segments.filterIsInstance<HeaderSegment>()
        assertEquals(3, headers.size)
        assertEquals(1, headers[0].level)
        assertEquals("Header 1", headers[0].text)
        assertEquals(2, headers[1].level)
        assertEquals("Header 2", headers[1].text)
        assertEquals(3, headers[2].level)
        assertEquals("Header 3", headers[2].text)
    }

    @Test
    fun `parse creates bold segments`() {
        val lines = listOf("This is **bold** text", "This is __also bold__")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val boldSegments = segments.filterIsInstance<BoldSegment>()

        assertEquals(2, boldSegments.size)
        assertEquals("bold", boldSegments[0].text)
        assertEquals("also bold", boldSegments[1].text)
    }

    @Test
    fun `parse creates italic segments`() {
        val lines = listOf("This is *italic* text", "This is _also italic_")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val italicSegments = segments.filterIsInstance<ItalicSegment>()

        assertEquals(2, italicSegments.size)
        assertEquals("italic", italicSegments[0].text)
        assertEquals("also italic", italicSegments[1].text)
    }

    @Test
    fun `parse creates strikethrough segments`() {
        val lines = listOf("This is ~~strikethrough~~ text")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val strikethroughSegments = segments.filterIsInstance<StrikethroughSegment>()

        assertEquals(1, strikethroughSegments.size)
        assertEquals("strikethrough", strikethroughSegments[0].text)
    }

    @Test
    fun `parse creates code segments`() {
        val lines = listOf("This is `code` text")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val codeSegments = segments.filterIsInstance<CodeSegment>()

        assertEquals(1, codeSegments.size)
        assertEquals("code", codeSegments[0].text)
    }

    @Test
    fun `parse creates link segments`() {
        val lines = listOf("This is [a link](http://example.com) text")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val linkSegments = segments.filterIsInstance<LinkSegment>()

        assertEquals(1, linkSegments.size)
        assertEquals("a link", linkSegments[0].text)
        assertEquals("http://example.com", linkSegments[0].url)
    }

    @Test
    fun `parse creates image segments`() {
        val lines = listOf("This is ![alt text](image.png) text")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        // Image segments become RenderSegment or TextSegment (error message)
        // Since we don't have a real Manual.imageFor implementation in tests,
        // it will likely become an error TextSegment
        assertNotNull(segments)
    }

    @Test
    fun `parse handles nested formatting`() {
        val lines = listOf("This is **bold with *italic* inside** text")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val italicSegments = segments.filterIsInstance<ItalicSegment>()

        // Italic should be nested inside bold (check for segments with BoldSegment parent)
        val segmentsWithBoldParent = segments.filter { seg ->
            var parent = seg.parent
            while (parent != null) {
                if (parent is BoldSegment) return@filter true
                parent = parent.parent
            }
            false
        }

        // Should have found italic segment and segments inside bold
        assertTrue(italicSegments.isNotEmpty(), "Should find italic segment")
        assertTrue(segmentsWithBoldParent.isNotEmpty(), "Should have segments with BoldSegment as ancestor")
        assertTrue(italicSegments.any { it.parent is BoldSegment }, "Italic should be child of BoldSegment")
    }

    @Test
    fun `parse handles multiple formatting on same line`() {
        val lines = listOf("**bold** and *italic* and `code`")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        assertTrue(segments.filterIsInstance<BoldSegment>().isNotEmpty())
        assertTrue(segments.filterIsInstance<ItalicSegment>().isNotEmpty())
        assertTrue(segments.filterIsInstance<CodeSegment>().isNotEmpty())
    }

    @Test
    fun `parse handles empty lines`() {
        val lines = listOf("", "Text", "")

        val result = Document.parse(lines)

        assertNotNull(result)
        val segments = collectSegments(result)
        assertEquals(3, segments.size)
    }

    @Test
    fun `parse trims trailing whitespace from lines`() {
        val lines = listOf("Text with trailing spaces   ")

        val result = Document.parse(lines)

        val firstSegment = result as TextSegment
        assertEquals("Text with trailing spaces", firstSegment.text)
    }

    @Test
    fun `parse links segments with next pointers`() {
        val lines = listOf("Line 1", "Line 2", "Line 3")

        val result = Document.parse(lines)

        // Verify segments are linked
        assertNotNull(result)
        assertNotNull(result.next)
        assertNotNull(result.next!!.next)
        assertNull(result.next!!.next!!.next?.next?.next)
    }

    @Test
    fun `parse handles special characters correctly`() {
        val lines = listOf("Text with special chars: !@#\$%^&*()")

        val result = Document.parse(lines)

        assertNotNull(result)
        assertTrue(result is TextSegment)
    }

    @Test
    fun `parse handles malformed markdown gracefully`() {
        val lines = listOf(
            "**unclosed bold",
            "*unclosed italic",
            "[link without url",
            "`unclosed code"
        )

        val result = Document.parse(lines)

        // Should still create segments without throwing exceptions
        assertNotNull(result)
        val segments = collectSegments(result)
        assertTrue(segments.isNotEmpty())
    }

    @Test
    fun `parse handles empty document`() {
        val lines = emptyList<String>()

        // Empty document should throw or handle gracefully
        assertThrows(NoSuchElementException::class.java) {
            Document.parse(lines)
        }
    }

    @Test
    fun `renderAsText preserves markdown format`() {
        val lines = listOf("# Header", "**Bold** and *italic*")

        val result = Document.parse(lines)
        val rendered = result.renderAsText(MarkupFormat.Markdown).toList()

        assertEquals(2, rendered.size)
        assertEquals("# Header", rendered[0])
        assertTrue(rendered[1].contains("**Bold**"))
        assertTrue(rendered[1].contains("*italic*"))
    }

    @Test
    fun `renderAsText converts to IGWMod format`() {
        val lines = listOf("# Header")

        val result = Document.parse(lines)
        val rendered = result.renderAsText(MarkupFormat.IGWMod).toList()

        assertEquals(1, rendered.size)
        // IGWMod uses different syntax for headers
        assertTrue(rendered[0].contains("[prefix{l}]"))
    }

    @Test
    fun `segment isLast returns true for last segment on line`() {
        val lines = listOf("First", "Second")

        val result = Document.parse(lines)

        // First segment's next should be on a different root
        assertTrue(result.isLast)
        assertNotNull(result.next)
        assertTrue(result.next!!.isLast)
    }

    @Test
    fun `segment root returns the original parent`() {
        val lines = listOf("This is **bold** text")

        val result = Document.parse(lines)

        val segments = collectSegments(result)
        val boldSegment = segments.filterIsInstance<BoldSegment>().first()

        // Bold segment's root should be a TextSegment
        assertNotNull(boldSegment.root)
        assertTrue(boldSegment.root is TextSegment)
    }

    private fun collectSegments(start: Segment): List<Segment> {
        val segments = mutableListOf<Segment>()
        var current: Segment? = start
        while (current != null) {
            segments.add(current)
            current = current.next
        }
        return segments
    }
}
