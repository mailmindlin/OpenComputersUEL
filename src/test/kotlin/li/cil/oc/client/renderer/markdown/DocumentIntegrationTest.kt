package li.cil.oc.client.renderer.markdown

import li.cil.oc.Settings
import li.cil.oc.client.renderer.markdown.segment.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Integration tests for the markdown Document parser.
 * Tests complete document parsing and segment relationships.
 */
class DocumentIntegrationTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }

        fun collectSegments(start: Segment): List<Segment> {
            val segments = mutableListOf<Segment>()
            var current: Segment? = start
            while (current != null) {
                segments.add(current)
                current = current.next
            }
            return segments
        }

        fun collectSegmentTypes(start: Segment): Map<String, Int> {
            val types = mutableMapOf<String, Int>()
            var current: Segment? = start
            while (current != null) {
                val typeName = current::class.simpleName ?: "Unknown"
                types[typeName] = types.getOrDefault(typeName, 0) + 1
                current = current.next
            }
            return types
        }
    }

    @Test
    fun `complete markdown document parsing`() {
        val document = listOf(
            "# Main Title",
            "",
            "This is a paragraph with **bold** and *italic* text.",
            "",
            "## Subsection",
            "",
            "Here's a [link](http://example.com) and some `code`.",
            "",
            "- List item 1",
            "- List item 2"
        )

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Should have created multiple segments
        assertTrue(segments.size > 10)

        // Check for specific segment types
        assertTrue(segments.any { it is HeaderSegment && it.level == 1 })
        assertTrue(segments.any { it is HeaderSegment && it.level == 2 })
        assertTrue(segments.any { it is BoldSegment })
        assertTrue(segments.any { it is ItalicSegment })
        assertTrue(segments.any { it is LinkSegment })
        assertTrue(segments.any { it is CodeSegment })
    }

    @Test
    fun `nested formatting in complex document`() {
        val document = listOf(
            "This has **bold with *italic* inside** and more.",
            "Also *italic with **bold** inside* text."
        )

        val result = Document.parse(document)
        val segments = collectSegments(result)

        val boldSegments = segments.filterIsInstance<BoldSegment>()
        val italicSegments = segments.filterIsInstance<ItalicSegment>()

        // Should have found both bold and italic
        assertTrue(boldSegments.isNotEmpty())
        assertTrue(italicSegments.isNotEmpty())

        // Check that some segments have parents
        assertTrue(boldSegments.any { it.parent != null })
        assertTrue(italicSegments.any { it.parent != null })
    }

    @Test
    fun `segment chain maintains proper parent relationships`() {
        val document = listOf("Plain **bold** plain again")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Find the bold segment
        val boldSegment = segments.filterIsInstance<BoldSegment>().first()

        // Bold segment should have a parent (the root TextSegment)
        assertNotNull(boldSegment.parent)
        assertTrue(boldSegment.parent is TextSegment)

        // All segments on this line should share the same root
        val roots = segments.map { it.root }.distinct()
        assertEquals(1, roots.size)
    }

    @Test
    fun `document with multiple lines maintains line separation`() {
        val document = listOf(
            "Line 1",
            "Line 2",
            "Line 3"
        )

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Each line should be marked as last (isLast = true)
        val lastSegments = segments.filter { it.isLast }
        assertEquals(3, lastSegments.size)

        // Different lines should have different roots
        val roots = segments.map { it.root }.distinct()
        assertEquals(3, roots.size)
    }

    @Test
    fun `links and formatting on same line`() {
        val document = listOf("**Bold** and [link](http://example.com) and *italic*")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        val types = collectSegmentTypes(result)

        assertTrue(types.getOrDefault("BoldSegment", 0) >= 1)
        assertTrue(types.getOrDefault("LinkSegment", 0) >= 1)
        assertTrue(types.getOrDefault("ItalicSegment", 0) >= 1)
    }

    @Test
    fun `code segments preserve whitespace`() {
        val document = listOf("Text with `code  with  spaces` inline")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        val codeSegment = segments.filterIsInstance<CodeSegment>().firstOrNull()
        assertNotNull(codeSegment)
        assertEquals("code  with  spaces", codeSegment!!.text)
    }

    @Test
    fun `headers of different levels are parsed correctly`() {
        val document = listOf(
            "# Level 1",
            "## Level 2",
            "### Level 3",
            "#### Level 4"
        )

        val result = Document.parse(document)
        val segments = collectSegments(result)
        val headers = segments.filterIsInstance<HeaderSegment>()

        assertEquals(4, headers.size)

        // Verify levels are parsed correctly
        assertEquals(1, headers[0].level)
        assertEquals(2, headers[1].level)
        assertEquals(3, headers[2].level)
        assertEquals(4, headers[3].level)
    }

    @Test
    fun `multiple links on same line`() {
        val document = listOf("First [link1](url1) and [link2](url2) and [link3](url3)")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        val links = segments.filterIsInstance<LinkSegment>()
        assertEquals(3, links.size)
        assertEquals("link1", links[0].text)
        assertEquals("url1", links[0].url)
        assertEquals("link2", links[1].text)
        assertEquals("url2", links[1].url)
        assertEquals("link3", links[2].text)
        assertEquals("url3", links[2].url)
    }

    @Test
    fun `alternating formatting types`() {
        val document = listOf("**b1** *i1* **b2** *i2* ~~s1~~ `c1`")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        assertTrue(segments.filterIsInstance<BoldSegment>().size >= 2)
        assertTrue(segments.filterIsInstance<ItalicSegment>().size >= 2)
        assertTrue(segments.filterIsInstance<StrikethroughSegment>().size >= 1)
        assertTrue(segments.filterIsInstance<CodeSegment>().size >= 1)
    }

    @Test
    fun `renderAsText reconstructs markdown correctly`() {
        val original = listOf(
            "# Header",
            "**Bold** text with *italic*",
            "[Link](http://example.com)"
        )

        val parsed = Document.parse(original)
        val reconstructed = parsed.renderAsText(MarkupFormat.Markdown).toList()

        assertEquals(3, reconstructed.size)
        assertEquals("# Header", reconstructed[0])
        assertTrue(reconstructed[1].contains("**Bold**"))
        assertTrue(reconstructed[1].contains("*italic*"))
        assertTrue(reconstructed[2].contains("[Link](http://example.com)"))
    }

    @Test
    fun `renderAsText converts between formats`() {
        val markdown = listOf("# Header", "**Bold** and *Italic*")

        val parsed = Document.parse(markdown)
        val igwMod = parsed.renderAsText(MarkupFormat.IGWMod).toList()

        // IGWMod uses different syntax
        assertTrue(igwMod[0].contains("[prefix{l}]"))
        assertFalse(igwMod[1].contains("**"))
    }

    @Test
    fun `empty lines create empty segments`() {
        val document = listOf("Line 1", "", "", "Line 2")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Should have 4 root segments (one per line)
        val roots = segments.filter { it.parent == null }
        assertEquals(4, roots.size)
    }

    @Test
    fun `segment next pointers form complete chain`() {
        val document = listOf("Line 1", "Line 2", "Line 3")

        val result = Document.parse(document)

        // Verify chain is complete
        var count = 0
        var current: Segment? = result
        while (current != null) {
            count++
            current = current.next
            // Prevent infinite loops in test
            if (count > 100) return fail("Segment chain appears to be circular")
        }

        assertTrue(count >= 3)
    }

    @Test
    fun `complex nesting with multiple levels`() {
        // This tests the refinement process with overlapping patterns
        // Note: Code pattern is processed BEFORE bold pattern in segmentTypes order,
        // so code inside bold won't work. Test bold with italic instead (italic comes after bold).
        val document = listOf("Start **bold with *italic* inside** end")

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Italic should be nested inside bold
        val italicSegments = segments.filterIsInstance<ItalicSegment>()

        // Check that italic segment has BoldSegment as parent
        assertTrue(italicSegments.isNotEmpty(), "Should find italic segment")
        assertTrue(
            italicSegments.any { it.parent is BoldSegment },
            "Italic should be nested inside BoldSegment"
        )
    }

    @Test
    fun `malformed markdown does not crash parser`() {
        val document = listOf(
            "**unclosed bold",
            "[unclosed link",
            "`unclosed code",
            "***multiple asterisks***",
            "[[nested [brackets]]]"
        )

        // Should not throw exception
        assertDoesNotThrow {
            val result = Document.parse(document)
            collectSegments(result)
        }
    }

    @Test
    fun `very long document`() {
        val document = (1..100).map { "Line $it with **bold** and *italic*" }

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Should have many segments
        assertTrue(segments.size > 300)

        // Each line should be properly terminated
        val lastSegments = segments.filter { it.isLast }
        assertEquals(100, lastSegments.size)
    }

    @Test
    fun `special characters in various contexts`() {
        val document = listOf(
            "Text with <html> tags",
            "**Bold with & ampersand**",
            "[Link with spaces](http://example.com/path with spaces)",
            "`Code with \"quotes\"`"
        )

        val result = Document.parse(document)
        val segments = collectSegments(result)

        // Should handle special characters without errors
        assertTrue(segments.isNotEmpty())

        val linkSegment = segments.filterIsInstance<LinkSegment>().firstOrNull()
        assertNotNull(linkSegment)
        assertTrue(linkSegment!!.url.contains(" "))
    }

    @Test
    fun `unicode and emoji in markdown`() {
        val document = listOf(
            "# 标题 🎉",
            "**粗体** and *斜体* with 😀 emoji",
            "[链接](http://example.com)"
        )

        val result = Document.parse(document)
        val segments = collectSegments(result)

        assertTrue(segments.any { it is HeaderSegment && it.text.contains("标题") })
        assertTrue(segments.any { it is BoldSegment && it.text.contains("粗体") })
        assertTrue(segments.any { it is ItalicSegment && it.text.contains("斜体") })
    }

    @Test
    fun `all segment types in one document`() {
        val document = listOf(
            "# Header",
            "**Bold** *Italic* ~~Strike~~ `Code` [Link](url) Normal"
        )

        val result = Document.parse(document)
        val types = collectSegmentTypes(result)

        assertTrue(types.containsKey("HeaderSegment"))
        assertTrue(types.containsKey("BoldSegment"))
        assertTrue(types.containsKey("ItalicSegment"))
        assertTrue(types.containsKey("StrikethroughSegment"))
        assertTrue(types.containsKey("CodeSegment"))
        assertTrue(types.containsKey("LinkSegment"))
        assertTrue(types.containsKey("TextSegment"))
    }
}
