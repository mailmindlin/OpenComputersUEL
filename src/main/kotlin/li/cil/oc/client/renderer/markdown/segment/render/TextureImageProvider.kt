package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.InteractiveImageRenderer
import li.cil.oc.client.Textures
import net.minecraft.util.ResourceLocation

object TextureImageProvider : ImageProvider {
    override fun getImage(data: String): ImageRenderer {
        return try {
            TextureImageRenderer(ResourceLocation(data))
        } catch (t: Throwable) {
            object : TextureImageRenderer(Textures.GUI.ManualMissingItem), InteractiveImageRenderer {
                override fun getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.ImageMissing"

                override fun onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
            }
        }
    }
}
