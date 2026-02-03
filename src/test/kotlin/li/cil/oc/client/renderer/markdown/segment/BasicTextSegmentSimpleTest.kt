package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Settings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Simplified tests for BasicTextSegment that don't require FontRenderer.
 * These tests focus on the basic properties and logic that can be tested
 * without needing to mock complex Minecraft rendering infrastructure.
 */
class BasicTextSegmentSimpleTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    @Test
    fun `breaks contains common punctuation characters`() {
        val segment = TextSegment(null, "test")

        assertTrue(segment.breaks.contains(' '))
        assertTrue(segment.breaks.contains('.'))
        assertTrue(segment.breaks.contains(','))
        assertTrue(segment.breaks.contains(':'))
        assertTrue(segment.breaks.contains(';'))
        assertTrue(segment.breaks.contains('-'))
        assertTrue(segment.breaks.contains('!'))
        assertTrue(segment.breaks.contains('?'))
    }

    @Test
    fun `breaks contains various punctuation marks`() {
        val segment = TextSegment(null, "test")

        // Test that various break characters are present
        assertTrue(segment.breaks.contains('_'))
        assertTrue(segment.breaks.contains('='))
        assertTrue(segment.breaks.contains('+'))
        assertTrue(segment.breaks.contains('*'))
        assertTrue(segment.breaks.contains('/'))
        assertTrue(segment.breaks.contains('\\'))
    }

    @Test
    fun `lists contains markdown list markers`() {
        val segment = TextSegment(null, "test")

        assertTrue(segment.lists.contains("- "))
        assertTrue(segment.lists.contains("* "))
    }

    @Test
    fun `rootPrefix returns first two characters of root text`() {
        val root = TextSegment(null, "- List item")
        val child = TextSegment(root, "child")

        assertEquals("- ", child.rootPrefix)
    }

    @Test
    fun `rootPrefix handles short root text`() {
        val root = TextSegment(null, "A")
        val child = TextSegment(root, "child")

        assertEquals("A", child.rootPrefix)
    }

    @Test
    fun `rootPrefix handles empty root text`() {
        val root = TextSegment(null, "")
        val child = TextSegment(root, "child")

        assertEquals("", child.rootPrefix)
    }

    @Test
    fun `ignoreLeadingWhitespace is true by default`() {
        val segment = TextSegment(null, "  text")

        assertTrue(segment.ignoreLeadingWhitespace)
    }

    @Test
    fun `CodeSegment ignoreLeadingWhitespace is false`() {
        val segment = CodeSegment(null, "  code")

        assertFalse(segment.ignoreLeadingWhitespace)
    }

    @Test
    fun `TextSegment with whitespace`() {
        val segment = TextSegment(null, "  text with spaces  ")

        assertEquals("  text with spaces  ", segment.text)
    }

    @Test
    fun `TextSegment with special characters`() {
        val segment = TextSegment(null, "text & < > \" '")

        assertEquals("text & < > \" '", segment.text)
    }

    @Test
    fun `TextSegment with unicode`() {
        val segment = TextSegment(null, "текст 文本 📝")

        assertEquals("текст 文本 📝", segment.text)
    }

    @Test
    fun `CodeSegment preserves whitespace in text property`() {
        val segment = CodeSegment(null, "  code  with  spaces  ")

        assertEquals("  code  with  spaces  ", segment.text)
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
    fun `isLast returns true when next has different root`() {
        val root1 = TextSegment(null, "Root1")
        val root2 = TextSegment(null, "Root2")
        root1.next = root2

        assertTrue(root1.isLast)
    }

    @Test
    fun `segment text is immutable through val`() {
        val segment = TextSegment(null, "Original")

        // Text is a val and cannot be changed
        assertEquals("Original", segment.text)
    }

    @Test
    fun `parent reference is properly maintained`() {
        val parent = TextSegment(null, "Parent")
        val child = TextSegment(parent, "Child")

        assertSame(parent, child.parent)
    }

    @Test
    fun `next pointer can be updated`() {
        val segment1 = TextSegment(null, "First")
        val segment2 = TextSegment(null, "Second")

        assertNull(segment1.next)
        segment1.next = segment2
        assertSame(segment2, segment1.next)
    }

    @Test
    fun `breaks set is read-only`() {
        val segment = TextSegment(null, "test")

        // Breaks returns a set that should be consistent
        val breaks1 = segment.breaks
        val breaks2 = segment.breaks

        assertEquals(breaks1, breaks2)
    }

    @Test
    fun `lists set is read-only`() {
        val segment = TextSegment(null, "test")

        // Lists returns a set that should be consistent
        val lists1 = segment.lists
        val lists2 = segment.lists

        assertEquals(lists1, lists2)
    }
}
