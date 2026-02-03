package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Settings
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.util.text.TextFormatting
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class FormattingSegmentsTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    // BoldSegment Tests
    @Test
    fun `BoldSegment constructor creates segment with correct properties`() {
        val segment = BoldSegment(null, "Bold Text")

        assertEquals("Bold Text", segment.text)
        assertNull(segment.parent)
    }

    @Test
    fun `BoldSegment is created successfully`() {
        val segment = BoldSegment(null, "Bold")

        // BoldSegment should be a valid TextSegment
        assertTrue(segment is TextSegment)
        assertEquals("Bold", segment.text)
    }

    @Test
    fun `BoldSegment toString with Markdown format`() {
        val segment = BoldSegment(null, "Bold Text")

        assertEquals("**Bold Text**", segment.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `BoldSegment toString with IGWMod format`() {
        val segment = BoldSegment(null, "Bold Text")

        assertEquals("[prefix{l}]Bold Text [prefix{}]", segment.toString(MarkupFormat.IGWMod))
    }

    @Test
    fun `BoldSegment with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val bold = BoldSegment(parent, "Bold")

        assertEquals(parent, bold.parent)
    }

    @Test
    fun `BoldSegment with empty text`() {
        val segment = BoldSegment(null, "")

        assertEquals("", segment.text)
        assertEquals("****", segment.toString(MarkupFormat.Markdown))
    }

    // ItalicSegment Tests
    @Test
    fun `ItalicSegment constructor creates segment with correct properties`() {
        val segment = ItalicSegment(null, "Italic Text")

        assertEquals("Italic Text", segment.text)
        assertNull(segment.parent)
    }

    @Test
    fun `ItalicSegment is created successfully`() {
        val segment = ItalicSegment(null, "Italic")

        // ItalicSegment should be a valid TextSegment
        assertTrue(segment is TextSegment)
        assertEquals("Italic", segment.text)
    }

    @Test
    fun `ItalicSegment toString with Markdown format`() {
        val segment = ItalicSegment(null, "Italic Text")

        assertEquals("*Italic Text*", segment.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `ItalicSegment toString with IGWMod format`() {
        val segment = ItalicSegment(null, "Italic Text")

        assertEquals("[prefix{o}]Italic Text [prefix{}]", segment.toString(MarkupFormat.IGWMod))
    }

    @Test
    fun `ItalicSegment with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val italic = ItalicSegment(parent, "Italic")

        assertEquals(parent, italic.parent)
    }

    // StrikethroughSegment Tests
    @Test
    fun `StrikethroughSegment constructor creates segment with correct properties`() {
        val segment = StrikethroughSegment(null, "Struck Text")

        assertEquals("Struck Text", segment.text)
        assertNull(segment.parent)
    }

    @Test
    fun `StrikethroughSegment is created successfully`() {
        val segment = StrikethroughSegment(null, "Struck")

        // StrikethroughSegment should be a valid TextSegment
        assertTrue(segment is TextSegment)
        assertEquals("Struck", segment.text)
    }

    @Test
    fun `StrikethroughSegment toString with Markdown format`() {
        val segment = StrikethroughSegment(null, "Struck Text")

        assertEquals("~~Struck Text~~", segment.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `StrikethroughSegment toString with IGWMod format`() {
        val segment = StrikethroughSegment(null, "Struck Text")

        assertEquals("[prefix{m}]Struck Text [prefix{}]", segment.toString(MarkupFormat.IGWMod))
    }

    @Test
    fun `StrikethroughSegment with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val struck = StrikethroughSegment(parent, "Struck")

        assertEquals(parent, struck.parent)
    }

    // CodeSegment Tests
    @Test
    fun `CodeSegment constructor creates segment with correct properties`() {
        val segment = CodeSegment(null, "code()")

        assertEquals("code()", segment.text)
        assertNull(segment.parent)
    }

    @Test
    fun `CodeSegment toString with Markdown format`() {
        val segment = CodeSegment(null, "code()")

        assertEquals("`code()`", segment.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `CodeSegment toString with IGWMod format`() {
        val segment = CodeSegment(null, "code()")

        assertEquals("[prefix{1}]code() [prefix{}]", segment.toString(MarkupFormat.IGWMod))
    }

    @Test
    fun `CodeSegment with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val code = CodeSegment(parent, "code")

        assertEquals(parent, code.parent)
    }

    @Test
    fun `CodeSegment ignoreLeadingWhitespace is false`() {
        val segment = CodeSegment(null, "  code")

        assertFalse(segment.ignoreLeadingWhitespace)
    }

    @Test
    fun `CodeSegment with whitespace preserves it`() {
        val segment = CodeSegment(null, "  code  ")

        assertEquals("  code  ", segment.text)
    }

    @Test
    fun `CodeSegment with special characters`() {
        val segment = CodeSegment(null, "a && b || c")

        assertEquals("`a && b || c`", segment.toString(MarkupFormat.Markdown))
    }

    // Mixed Formatting Tests
    @Test
    fun `formatting segments can be nested via parent`() {
        val parent = BoldSegment(null, "parent")
        val child = ItalicSegment(parent, "child")

        assertSame(parent, child.parent)
        assertTrue(parent is BoldSegment)
        assertTrue(child is ItalicSegment)
    }

    @Test
    fun `formatting segments with unicode text`() {
        val bold = BoldSegment(null, "粗体")
        val italic = ItalicSegment(null, "斜体")
        val code = CodeSegment(null, "编码")

        assertEquals("**粗体**", bold.toString(MarkupFormat.Markdown))
        assertEquals("*斜体*", italic.toString(MarkupFormat.Markdown))
        assertEquals("`编码`", code.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `formatting segments with emoji`() {
        val bold = BoldSegment(null, "Hello 🎉")
        val italic = ItalicSegment(null, "World 🌍")

        assertEquals("**Hello 🎉**", bold.toString(MarkupFormat.Markdown))
        assertEquals("*World 🌍*", italic.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `formatting segments preserve markdown special characters in text`() {
        val bold = BoldSegment(null, "text with * asterisk")
        val italic = ItalicSegment(null, "text with _ underscore")
        val code = CodeSegment(null, "text with ` backtick")

        // These should still wrap properly
        assertEquals("**text with * asterisk**", bold.toString(MarkupFormat.Markdown))
        assertEquals("*text with _ underscore*", italic.toString(MarkupFormat.Markdown))
        assertEquals("`text with ` backtick`", code.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `all formatting segments extend appropriate base classes`() {
        val bold = BoldSegment(null, "test")
        val italic = ItalicSegment(null, "test")
        val struck = StrikethroughSegment(null, "test")
        val code = CodeSegment(null, "test")

        assertTrue(bold is TextSegment)
        assertTrue(italic is TextSegment)
        assertTrue(struck is TextSegment)
        assertTrue(code is BasicTextSegment)
    }

    @Test
    fun `formatting segments can be distinguished by type`() {
        val segments: List<Segment> = listOf(
            BoldSegment(null, "bold"),
            ItalicSegment(null, "italic"),
            StrikethroughSegment(null, "struck"),
            CodeSegment(null, "code")
        )

        assertEquals(1, segments.filterIsInstance<BoldSegment>().size)
        assertEquals(1, segments.filterIsInstance<ItalicSegment>().size)
        assertEquals(1, segments.filterIsInstance<StrikethroughSegment>().size)
        assertEquals(1, segments.filterIsInstance<CodeSegment>().size)
    }

    @Test
    fun `formatting segments with very long text`() {
        val longText = "A".repeat(1000)

        val bold = BoldSegment(null, longText)
        val italic = ItalicSegment(null, longText)
        val code = CodeSegment(null, longText)

        assertEquals(longText, bold.text)
        assertEquals(longText, italic.text)
        assertEquals(longText, code.text)
    }
}
