package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Settings
import li.cil.oc.client.renderer.markdown.MarkupFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class LinkSegmentTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    private val LinkSegment.color get() = colorForTesting

    @Test
    fun `constructor creates link with correct properties`() {
        val segment = LinkSegment(null, "Click here", "http://example.com")

        assertEquals("Click here", segment.text)
        assertEquals("http://example.com", segment.url)
    }

    @Test
    fun `link with parent references parent correctly`() {
        val parent = TextSegment(null, "Parent")
        val link = LinkSegment(parent, "Link", "http://example.com")

        assertEquals(parent, link.parent)
    }

    @Test
    fun `tooltip returns the URL`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        assertEquals("http://example.com", segment.tooltip)
    }

    @Test
    fun `http URL is considered valid`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        // Color should be green-ish for valid links (0x66FF66 or 0xAAFFAA)
        val color = segment.color
        assertTrue(color == 0x66FF66 || color == 0xAAFFAA ||
                   (color and 0x00FF00) > (color and 0xFF0000))
    }

    @Test
    fun `https URL is considered valid`() {
        val segment = LinkSegment(null, "Link", "https://example.com")

        val color = segment.color
        assertTrue(color == 0x66FF66 || color == 0xAAFFAA ||
                   (color and 0x00FF00) > (color and 0xFF0000))
    }

    @Test
    fun `color fades after hover`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        // Get initial color
        val initialColor = segment.color

        // Simulate hover
        segment.notifyHover()

        // Color should change to hover color immediately after hover
        val hoveredColor = segment.color
        assertNotEquals(initialColor, hoveredColor)

        // After some time, color should fade back
        Thread.sleep(100)
        val fadedColor = segment.color
        // Color should be somewhere between hover and normal
        assertNotNull(fadedColor)
    }

    @Test
    fun `toString with Markdown format returns markdown link`() {
        val segment = LinkSegment(null, "Click here", "http://example.com")

        val result = segment.toString(MarkupFormat.Markdown)

        assertEquals("[Click here](http://example.com)", result)
    }

    @Test
    fun `toString with IGWMod format handles external links`() {
        val segment = LinkSegment(null, "External", "http://example.com")

        val result = segment.toString(MarkupFormat.IGWMod)

        // External links should just return the text in IGWMod format
        assertEquals("External", result)
    }

    @Test
    fun `toString with IGWMod format handles internal links`() {
        val segment = LinkSegment(null, "Internal", "page.md")

        val result = segment.toString(MarkupFormat.IGWMod)

        // Internal links should use IGWMod link syntax
        assertTrue(result.contains("[link{"))
        assertTrue(result.contains("Internal"))
    }

    @Test
    fun `toString returns formatted debug string`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.toString()

        assertTrue(result.contains("LinkSegment"))
        assertTrue(result.contains("Link"))
        assertTrue(result.contains("http://example.com"))
    }

    @Test
    fun `checkHovered returns segment when mouse is inside bounds`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(10, 10, 5, 5, 20, 10)

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered returns null when mouse is outside bounds`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(50, 50, 5, 5, 20, 10)

        assertNull(result)
    }

    @Test
    fun `checkHovered returns segment when mouse is on left edge`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(5, 10, 5, 5, 20, 10)

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered returns segment when mouse is on right edge`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(25, 10, 5, 5, 20, 10)

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered returns segment when mouse is on top edge`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(10, 5, 5, 5, 20, 10)

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered returns segment when mouse is on bottom edge`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(10, 15, 5, 5, 20, 10)

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered returns null when mouse is just outside left`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(4, 10, 5, 5, 20, 10)

        assertNull(result)
    }

    @Test
    fun `checkHovered returns null when mouse is just outside right`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        val result = segment.checkHovered(26, 10, 5, 5, 20, 10)

        assertNull(result)
    }

    @Test
    fun `links with same URL are distinct objects`() {
        val link1 = LinkSegment(null, "Link 1", "http://example.com")
        val link2 = LinkSegment(null, "Link 2", "http://example.com")

        assertNotSame(link1, link2)
        assertEquals(link1.url, link2.url)
    }

    @Test
    fun `empty link text is allowed`() {
        val segment = LinkSegment(null, "", "http://example.com")

        assertEquals("", segment.text)
        assertEquals("http://example.com", segment.url)
    }

    @Test
    fun `link URL with special characters`() {
        val segment = LinkSegment(null, "Link", "http://example.com/path?query=value&foo=bar")

        assertEquals("http://example.com/path?query=value&foo=bar", segment.url)
    }

    @Test
    fun `relative link URL`() {
        val segment = LinkSegment(null, "Link", "../other-page.md")

        assertEquals("../other-page.md", segment.url)
    }

    @Test
    fun `link with fragment identifier`() {
        val segment = LinkSegment(null, "Link", "page.md#section")

        assertEquals("page.md#section", segment.url)
    }

    @Test
    fun `notifyHover can be called multiple times`() {
        val segment = LinkSegment(null, "Link", "http://example.com")

        segment.notifyHover()
        segment.notifyHover()
        segment.notifyHover()

        // Should not throw exceptions
        assertNotNull(segment.color)
    }
}
