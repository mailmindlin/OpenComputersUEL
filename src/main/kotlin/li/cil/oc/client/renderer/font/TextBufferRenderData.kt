package li.cil.oc.client.renderer.font

import li.cil.oc.util.TextBuffer

interface TextBufferRenderData {
    var dirty: Boolean

    val data: TextBuffer

    val viewport: Pair<Int, Int>
}
