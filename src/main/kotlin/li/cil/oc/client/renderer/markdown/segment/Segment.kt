package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.FontRenderer
import java.util.regex.Pattern

sealed interface Segment {
    /**
     * Parent segment, i.e. the segment this segment was refined from.
     * Each line starts as a TextSegment that is refined based into segments
     * based on the handled formatting rules / patterns.
     */
    val parent: Segment?

    /**
     * The root segment, i.e. the original parent of this segment.
     */
    val root: Segment
        get() = if (parent == null) this else parent!!.root

    /**
     * Get the X coordinate at which to render the next segment.
     *
     * For flowing/inline segments this will be to the right of the last line
     * this segment renders, for block segments it will be at the start of
     * the next line below this segment.
     *
     * The coordinates in this context are relative to (0,0).
     */
    fun nextX(indent: Int, maxWidth: Int, renderer: FontRenderer): Int

    /**
     * Get the Y coordinate at which to render the next segment.
     *
     * For flowing/inline segments this will be the same level as the last line
     * this segment renders, unless it's the last segment on its line. For block
     * segments and last-on-line segments this will be the next line after.
     *
     * The coordinates in this context are relative to (0,0).
     */
    fun nextY(indent: Int, maxWidth: Int, renderer: FontRenderer): Int

    /**
     * Render the segment at the specified coordinates with the specified
     * properties.
     */
    fun render(x: Int, y: Int, indent: Int, maxWidth: Int, renderer: FontRenderer, mouseX: Int, mouseY: Int): InteractiveSegment? = null

    fun renderAsText(format: MarkupFormat): Iterable<String> {
        var segment: Segment? = this
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        while (segment != null) {
            builder.append(segment.toString(format))
            if (segment.isLast) {
                result.add(builder.toString())
                builder.clear()
            }
            segment = segment.next
        }
        return result
    }

    fun toString(format: MarkupFormat): String

    // ----------------------------------------------------------------------- //

    // Used during construction, checks a segment for inner segments.
    fun refine(pattern: Regex, factory: (Segment, MatchResult) -> Segment): Iterable<Segment> = listOf(this)

    // Set after construction of document, used for formatting, specifically
    // to compute the height for last segment on a line (to force a new line).
    var next: Segment?

    // Utility method to check if the segment is the last on a line.
    val isLast: Boolean
        get() = next == null || root != next!!.root
}
