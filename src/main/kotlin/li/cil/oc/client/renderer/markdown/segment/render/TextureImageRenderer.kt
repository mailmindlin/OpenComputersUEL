package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.client.Textures
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.client.resources.IResourceManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.io.InputStream
import javax.imageio.ImageIO

open class TextureImageRenderer(val location: ResourceLocation) : ImageRenderer {
    private val texture: ImageTexture

    init {
        val manager = Minecraft.getMinecraft().textureManager
        val existing = manager.getTexture(location)
        texture = if (existing is ImageTexture) {
            existing
        } else {
            if (existing != null && existing.glTextureId != -1) {
                TextureUtil.deleteTexture(existing.glTextureId)
            }
            val image = ImageTexture(location)
            manager.loadTexture(location, image)
            image
        }
    }

    override fun getWidth(): Int = texture.width

    override fun getHeight(): Int = texture.height

    override fun render(mouseX: Int, mouseY: Int) {
        Textures.bind(location)
        GlStateManager.color(1f, 1f, 1f, 1f)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(0f, 0f)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(0f, texture.height.toFloat())
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(texture.width.toFloat(), texture.height.toFloat())
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(texture.width.toFloat(), 0f)
        GL11.glEnd()
    }

    private class ImageTexture(val location: ResourceLocation) : AbstractTexture() {
        var width = 0
        var height = 0

        override fun loadTexture(manager: IResourceManager) {
            deleteGlTexture()

            var inputStream: InputStream? = null
            try {
                val resource = manager.getResource(location)
                inputStream = resource.inputStream
                val bi = ImageIO.read(inputStream)
                TextureUtil.uploadTextureImageAllocate(glTextureId, bi, false, false)
                width = bi.width
                height = bi.height
            } finally {
                inputStream?.close()
            }
        }
    }
}
