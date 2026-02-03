package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Settings
import li.cil.oc.client.renderer.markdown.MarkupFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import net.minecraft.client.gui.FontRenderer

class TextSegmentTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    @Test
    fun `constructor creates segment with correct properties`() {
        val segment = TextSegment(null, "Hello World")

        assertEquals("Hello World", segment.text)
        assertNull(segment.parent)
        assertNull(segment.next)
    }

    @Test
    fun `segment with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val child = TextSegment(parent, "Child")

        assertEquals(parent, child.parent)
    }

    @Test
    fun `refine splits text on pattern matches`() {
        val segment = TextSegment(null, "This is **bold** text")
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        assertEquals(3, segments.size)
        assertTrue(segments[0] is TextSegment)
        assertEquals("This is ", (segments[0] as TextSegment).text)
        assertTrue(segments[1] is BoldSegment)
        assertEquals("bold", (segments[1] as BoldSegment).text)
        assertTrue(segments[2] is TextSegment)
        assertEquals(" text", (segments[2] as TextSegment).text)
    }

    @Test
    fun `refine returns self when no pattern matches`() {
        val segment = TextSegment(null, "Plain text")
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        assertEquals(1, segments.size)
        assertSame(segment, segments[0])
    }

    @Test
    fun `refine handles multiple pattern matches`() {
        val segment = TextSegment(null, "**bold1** and **bold2**")
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        val boldSegments = segments.filterIsInstance<BoldSegment>()
        assertEquals(2, boldSegments.size)
        assertEquals("bold1", boldSegments[0].text)
        assertEquals("bold2", boldSegments[1].text)
    }

    @Test
    fun `refine handles pattern at start of text`() {
        val segment = TextSegment(null, "**bold** text")
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        assertEquals(2, segments.size)
        assertTrue(segments[0] is BoldSegment)
        assertTrue(segments[1] is TextSegment)
    }

    @Test
    fun `refine handles pattern at end of text`() {
        val segment = TextSegment(null, "text **bold**")
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        assertEquals(2, segments.size)
        assertTrue(segments[0] is TextSegment)
        assertTrue(segments[1] is BoldSegment)
    }

    @Test
    fun `toString returns formatted string`() {
        val segment = TextSegment(null, "Test")

        val result = segment.toString()

        assertTrue(result.contains("TextSegment"))
        assertTrue(result.contains("Test"))
    }

    @Test
    fun `toString with Markdown format returns plain text`() {
        val segment = TextSegment(null, "Plain text")

        val result = segment.toString(MarkupFormat.Markdown)

        assertEquals("Plain text", result)
    }

    @Test
    fun `segment root returns self when no parent`() {
        val segment = TextSegment(null, "Root")

        assertEquals(segment, segment.root)
    }

    @Test
    fun `segment root returns top-level parent`() {
        val root = TextSegment(null, "Root")
        val child = TextSegment(root, "Child")
        val grandchild = TextSegment(child, "Grandchild")

        assertEquals(root, grandchild.root)
        assertEquals(root, child.root)
    }

    @Test
    fun `isLast returns true when next is null`() {
        val segment = TextSegment(null, "Last")

        assertTrue(segment.isLast)
    }

    @Test
    fun `isLast returns false when next is from same root`() {
        val root = TextSegment(null, "Root")
        val child1 = TextSegment(root, "Child1")
        val child2 = TextSegment(root, "Child2")
        child1.next = child2

        // If they share the same root, child1 is not last
        // But since child1's root is 'root' and child2's root is also 'root',
        // they are considered on the same line
        assertFalse(child1.isLast)
    }

    @Test
    fun `isLast returns true when next is from different root`() {
        val root1 = TextSegment(null, "Root1")
        val root2 = TextSegment(null, "Root2")
        root1.next = root2

        assertTrue(root1.isLast)
    }

    @Test
    fun `refine handles empty match groups`() {
        val segment = TextSegment(null, "Test `` text")
        val pattern = """(`)(.*?)\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            CodeSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        val codeSegments = segments.filterIsInstance<CodeSegment>()
        assertEquals(1, codeSegments.size)
        assertEquals("", codeSegments[0].text)
    }

    @Test
    fun `refine handles overlapping patterns by processing first match`() {
        val segment = TextSegment(null, "***bolditalic***")
        // This pattern matches bold
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = segment.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        val segments = refined.toList()
        // Should have created bold segment, with remaining * characters as text
        assertTrue(segments.any { it is BoldSegment })
    }

    @Test
    fun `segment text is immutable`() {
        val segment = TextSegment(null, "Original")

        // Text cannot be changed (it's a val)
        assertEquals("Original", segment.text)
    }

    @Test
    fun `refine preserves parent reference in new segments`() {
        val parent = TextSegment(null, "Parent with **bold** text")
        val pattern = """(\*\*)(\S.*?\S|${'$'})\1""".toRegex()

        val refined = parent.refine(pattern) { s, m ->
            BoldSegment(s, m.groupValues[2])
        }

        refined.forEach { segment ->
            if (segment != parent) {
                assertEquals(parent, segment.parent)
            }
        }
    }
}
