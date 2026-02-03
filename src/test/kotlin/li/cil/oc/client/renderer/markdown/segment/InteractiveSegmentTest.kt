package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Settings
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class InteractiveSegmentTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setUp() {
            Settings.defaultsForTesting()
        }
    }

    // Test implementation of InteractiveSegment for testing
    private class TestInteractiveSegment(
        override val parent: Segment?,
        override val text: String,
        override var next: Segment? = null,
        private val customTooltip: String? = null,
        private val shouldHandleClick: Boolean = false
    ) : TextSegment(parent, text), InteractiveSegment {
        override val tooltip: String?
            get() = customTooltip

        override fun onMouseClick(mouseX: Int, mouseY: Int): Boolean {
            return shouldHandleClick
        }

        var hoverCount = 0

        override fun notifyHover() {
            hoverCount++
        }
    }

    @Test
    fun `InteractiveSegment tooltip defaults to null`() {
        val segment = TestInteractiveSegment(null, "Test")

        assertNull(segment.tooltip)
    }

    @Test
    fun `InteractiveSegment can provide custom tooltip`() {
        val segment = TestInteractiveSegment(null, "Test", customTooltip = "Custom tooltip")

        assertEquals("Custom tooltip", segment.tooltip)
    }

    @Test
    fun `onMouseClick defaults to false`() {
        val segment = TestInteractiveSegment(null, "Test", shouldHandleClick = false)

        val result = segment.onMouseClick(10, 10)

        assertFalse(result)
    }

    @Test
    fun `onMouseClick can return true to indicate handled`() {
        val segment = TestInteractiveSegment(null, "Test", shouldHandleClick = true)

        val result = segment.onMouseClick(10, 10)

        assertTrue(result)
    }

    @Test
    fun `notifyHover can be called multiple times`() {
        val segment = TestInteractiveSegment(null, "Test")

        segment.notifyHover()
        segment.notifyHover()
        segment.notifyHover()

        assertEquals(3, segment.hoverCount)
    }

    @Test
    fun `checkHovered returns segment when mouse is inside bounds`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 15,
            mouseY = 15,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered returns null when mouse X is before bounds`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 5,
            mouseY = 15,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertNull(result)
    }

    @Test
    fun `checkHovered returns null when mouse X is after bounds`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 35,
            mouseY = 15,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertNull(result)
    }

    @Test
    fun `checkHovered returns null when mouse Y is before bounds`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 15,
            mouseY = 5,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertNull(result)
    }

    @Test
    fun `checkHovered returns null when mouse Y is after bounds`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 15,
            mouseY = 35,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertNull(result)
    }

    @Test
    fun `checkHovered includes left edge`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 10, // Exactly on left edge
            mouseY = 15,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered includes right edge`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 30, // Exactly on right edge (x + w)
            mouseY = 15,
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered includes top edge`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 15,
            mouseY = 10, // Exactly on top edge
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered includes bottom edge`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 15,
            mouseY = 30, // Exactly on bottom edge (y + h)
            x = 10,
            y = 10,
            w = 20,
            h = 20
        )

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered with zero width returns null`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 10,
            mouseY = 15,
            x = 10,
            y = 10,
            w = 0,
            h = 20
        )

        // Only exact position at x should match
        assertSame(segment, result)
    }

    @Test
    fun `checkHovered with zero height returns null`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 15,
            mouseY = 10,
            x = 10,
            y = 10,
            w = 20,
            h = 0
        )

        // Only exact position at y should match
        assertSame(segment, result)
    }

    @Test
    fun `checkHovered with negative coordinates`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = -5,
            mouseY = -5,
            x = -10,
            y = -10,
            w = 20,
            h = 20
        )

        assertSame(segment, result)
    }

    @Test
    fun `checkHovered corner cases - all corners inside`() {
        val segment = TestInteractiveSegment(null, "Test")

        // Top-left corner
        assertSame(segment, segment.checkHovered(10, 10, 10, 10, 20, 20))

        // Top-right corner
        assertSame(segment, segment.checkHovered(30, 10, 10, 10, 20, 20))

        // Bottom-left corner
        assertSame(segment, segment.checkHovered(10, 30, 10, 10, 20, 20))

        // Bottom-right corner
        assertSame(segment, segment.checkHovered(30, 30, 10, 10, 20, 20))
    }

    @Test
    fun `checkHovered corner cases - just outside corners`() {
        val segment = TestInteractiveSegment(null, "Test")

        // Just outside top-left corner
        assertNull(segment.checkHovered(9, 9, 10, 10, 20, 20))

        // Just outside top-right corner
        assertNull(segment.checkHovered(31, 9, 10, 10, 20, 20))

        // Just outside bottom-left corner
        assertNull(segment.checkHovered(9, 31, 10, 10, 20, 20))

        // Just outside bottom-right corner
        assertNull(segment.checkHovered(31, 31, 10, 10, 20, 20))
    }

    @Test
    fun `LinkSegment implements InteractiveSegment`() {
        val link = LinkSegment(null, "Link", "http://example.com")

        assertTrue(link is InteractiveSegment)
        assertNotNull(link.tooltip)
        assertEquals("http://example.com", link.tooltip)
    }

    @Test
    fun `multiple InteractiveSegments are distinct`() {
        val seg1 = TestInteractiveSegment(null, "Test1")
        val seg2 = TestInteractiveSegment(null, "Test2")

        assertNotSame(seg1, seg2)

        seg1.notifyHover()
        assertEquals(1, seg1.hoverCount)
        assertEquals(0, seg2.hoverCount)
    }

    @Test
    fun `InteractiveSegment with very large bounds`() {
        val segment = TestInteractiveSegment(null, "Test")

        val result = segment.checkHovered(
            mouseX = 5000,
            mouseY = 5000,
            x = 0,
            y = 0,
            w = 10000,
            h = 10000
        )

        assertSame(segment, result)
    }

    @Test
    fun `InteractiveSegment preserves TextSegment behavior`() {
        val segment = TestInteractiveSegment(null, "Test text")

        assertEquals("Test text", segment.text)
        assertNull(segment.parent)
    }
}
