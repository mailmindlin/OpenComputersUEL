package li.cil.oc.client.renderer.font

import com.google.common.base.Charsets
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

/**
 * Font renderer using a user specified texture file, meaning the list of
 * supported characters is fixed. But at least this one works.
 */
class StaticFontRenderer : TextureFontRenderer() {
    private val fontData: Triple<String, Int, Int> = try {
        val lines = Minecraft.getMinecraft().resourceManager
            .getResource(ResourceLocation(Settings.resourceDomain, "textures/font/chars.txt"))
            .inputStream
            .bufferedReader(Charsets.UTF_8)
            .lineSequence()
            .iterator()
        val chars = lines.next()
        val (w, h) = if (lines.hasNext()) {
            val size = lines.next().split(" ", limit = 2)
            Pair(size[0].toInt(), size[1].toInt())
        } else Pair(10, 18)
        Triple(chars, w, h)
    } catch (t: Throwable) {
        OpenComputers.log.warn("Failed reading font metadata, using defaults.", t)
        Triple(basicChars, 10, 18)
    }

    private val chars = fontData.first
    override val charWidth = fontData.second
    override val charHeight = fontData.third

    private val cols = 256 / charWidth
    private val uStep = charWidth / 256.0
    private val uSize = uStep
    private val vStep = (charHeight + 1) / 256.0
    private val vSize = charHeight / 256.0
    private val s = Settings.get.fontCharScale
    private val dw = charWidth * s - charWidth
    private val dh = charHeight * s - charHeight

    override val textureCount = 1

    override fun bindTexture(index: Int) {
        if (Settings.get.textAntiAlias) {
            Textures.bind(Textures.Font.AntiAliased)
        } else {
            Textures.bind(Textures.Font.Aliased)
        }
    }

    override fun drawChar(tx: Float, ty: Float, char: Int) {
        val index = 1 + when (val i = chars.indexOf(char.toChar())) {
            -1 -> chars.indexOf('?')
            else -> i
        }
        val x = (index - 1) % cols
        val y = (index - 1) / cols
        val u = x * uStep
        val v = y * vStep
        GL11.glTexCoord2d(u, v + vSize)
        GL11.glVertex3d(tx - dw, ty + charHeight * s, 0.0)
        GL11.glTexCoord2d(u + uSize, v + vSize)
        GL11.glVertex3d(tx + charWidth * s, ty + charHeight * s, 0.0)
        GL11.glTexCoord2d(u + uSize, v)
        GL11.glVertex3d(tx + charWidth * s, ty - dh, 0.0)
        GL11.glTexCoord2d(u, v)
        GL11.glVertex3d(tx - dw, ty - dh, 0.0)
    }

    override fun generateChar(char: Int) {}
}
