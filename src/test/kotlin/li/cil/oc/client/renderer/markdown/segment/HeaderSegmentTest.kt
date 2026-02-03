package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Settings
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.util.text.TextFormatting
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HeaderSegmentTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    @Test
    fun `constructor creates header with correct properties`() {
        val segment = HeaderSegment(null, "My Header", 1)

        assertEquals("My Header", segment.text)
        assertEquals(1, segment.level)
    }

    @Test
    fun `header level 1 is correctly initialized`() {
        val segment = HeaderSegment(null, "Header", 1)

        // Level 1 header should be created successfully
        assertEquals(1, segment.level)
        assertNotNull(segment)
    }

    @Test
    fun `header level 2 is correctly initialized`() {
        val segment = HeaderSegment(null, "Header", 2)

        // Level 2 header should be created successfully
        assertEquals(2, segment.level)
        assertNotNull(segment)
    }

    @Test
    fun `header level 3 is correctly initialized`() {
        val segment = HeaderSegment(null, "Header", 3)

        // Level 3 header should be created successfully
        assertEquals(3, segment.level)
        assertNotNull(segment)
    }

    @Test
    fun `header level 4 and above are correctly initialized`() {
        val segment4 = HeaderSegment(null, "Header", 4)
        val segment5 = HeaderSegment(null, "Header", 5)
        val segment6 = HeaderSegment(null, "Header", 6)

        // All headers should be created successfully
        assertEquals(4, segment4.level)
        assertEquals(5, segment5.level)
        assertEquals(6, segment6.level)
    }

    @Test
    fun `toString with Markdown format includes hash marks`() {
        val segment = HeaderSegment(null, "My Header", 1)

        val result = segment.toString(MarkupFormat.Markdown)

        assertEquals("# My Header", result)
    }

    @Test
    fun `toString with Markdown format includes correct number of hashes`() {
        val h1 = HeaderSegment(null, "Header 1", 1)
        val h2 = HeaderSegment(null, "Header 2", 2)
        val h3 = HeaderSegment(null, "Header 3", 3)

        assertEquals("# Header 1", h1.toString(MarkupFormat.Markdown))
        assertEquals("## Header 2", h2.toString(MarkupFormat.Markdown))
        assertEquals("### Header 3", h3.toString(MarkupFormat.Markdown))
    }

    @Test
    fun `toString with IGWMod format uses prefix syntax`() {
        val segment = HeaderSegment(null, "Header", 1)

        val result = segment.toString(MarkupFormat.IGWMod)

        assertEquals("[prefix{l}]Header [prefix{}]", result)
    }

    @Test
    fun `toString returns formatted debug string`() {
        val segment = HeaderSegment(null, "Header", 2)

        val result = segment.toString()

        assertTrue(result.contains("HeaderSegment"))
        assertTrue(result.contains("level=2"))
        assertTrue(result.contains("Header"))
    }

    @Test
    fun `header with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val header = HeaderSegment(parent, "Header", 1)

        assertEquals(parent, header.parent)
    }

    @Test
    fun `header with empty text`() {
        val segment = HeaderSegment(null, "", 1)

        assertEquals("", segment.text)
        assertEquals(1, segment.level)
    }

    @Test
    fun `header with very long text`() {
        val longText = "A".repeat(1000)
        val segment = HeaderSegment(null, longText, 1)

        assertEquals(longText, segment.text)
    }

    @Test
    fun `header with special characters in text`() {
        val segment = HeaderSegment(null, "Header with *special* chars!", 1)

        assertEquals("Header with *special* chars!", segment.text)
    }

    @Test
    fun `multiple headers with different levels are distinct`() {
        val h1 = HeaderSegment(null, "Same Text", 1)
        val h2 = HeaderSegment(null, "Same Text", 2)

        assertNotSame(h1, h2)
        assertEquals(h1.text, h2.text)
        assertNotEquals(h1.level, h2.level)
    }

    @Test
    fun `header level 0 or negative work correctly`() {
        val h0 = HeaderSegment(null, "Header", 0)
        val hNeg = HeaderSegment(null, "Header", -1)

        // Headers with unusual levels should still be created
        assertEquals(0, h0.level)
        assertEquals(-1, hNeg.level)
    }

    @Test
    fun `header toString with Markdown handles zero level`() {
        val segment = HeaderSegment(null, "Header", 0)

        val result = segment.toString(MarkupFormat.Markdown)

        // Should have no # marks for level 0
        assertEquals(" Header", result)
    }

    @Test
    fun `headers with unicode text`() {
        val segment = HeaderSegment(null, "Заголовок 📝", 1)

        assertEquals("Заголовок 📝", segment.text)
        assertEquals("# Заголовок 📝", segment.toString(MarkupFormat.Markdown))
    }
}
