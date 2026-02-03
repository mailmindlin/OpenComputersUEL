package li.cil.oc.client.renderer.font

import li.cil.oc.util.ScreenResolution
import li.cil.oc.util.TextBufferData

interface TextBufferRenderData {
    var dirty: Boolean

    val data: TextBufferData

    val viewport: ScreenResolution
}
