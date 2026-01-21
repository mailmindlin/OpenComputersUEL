package li.cil.oc.client.renderer.font

import li.cil.oc.Settings
import li.cil.oc.util.FontUtils
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.IReloadableResourceManager
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.IResourceManagerReloadListener
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11

/**
 * Font renderer that dynamically generates lookup textures by rendering a font
 * to it. It's pretty broken right now, and font rendering looks crappy as hell.
 */
class DynamicFontRenderer : TextureFontRenderer(), IResourceManagerReloadListener {
    private val glyphProvider: IGlyphProvider = when (Settings.get().fontRenderer) {
        else -> FontParserHex()
    }

    private val textures = mutableListOf<CharTexture>()
    private val charMap = mutableMapOf<Int, CharIcon>()
    private var activeTexture: CharTexture? = null

    init {
        initialize()

        when (val manager = Minecraft.getMinecraft().resourceManager) {
            is IReloadableResourceManager -> manager.registerReloadListener(this)
        }
    }

    fun initialize() {
        for (texture in textures) {
            texture.delete()
        }
        textures.clear()
        charMap.clear()
        textures += CharTexture(this)
        activeTexture = textures.first()
        generateChars(basicChars.toCharArray())
    }

    override fun onResourceManagerReload(manager: IResourceManager) {
        glyphProvider.initialize()
        initialize()
    }

    override val charWidth: Int
        get() = glyphProvider.glyphWidth

    override val charHeight: Int
        get() = glyphProvider.glyphHeight

    override val textureCount: Int
        get() = textures.size

    override fun bindTexture(index: Int) {
        activeTexture = textures[index]
        activeTexture?.bind()
        RenderState.checkError(javaClass.name + ".bindTexture")
    }

    override fun generateChar(char: Int) {
        charMap.getOrPut(char) { createCharIcon(char) }
    }

    override fun drawChar(tx: Float, ty: Float, char: Int) {
        charMap[char]?.let { icon ->
            if (icon.texture == activeTexture) {
                icon.draw(tx, ty)
            }
        }
    }

    private fun createCharIcon(char: Int): CharIcon? {
        return if (FontUtils.wcwidth(char) < 1 || glyphProvider.getGlyph(char) == null) {
            if (char == '?'.code) null
            else charMap.getOrPut('?'.code) { createCharIcon('?'.code) }
        } else {
            if (textures.last().isFull(char)) {
                textures += CharTexture(this)
                textures.last().bind()
            }
            textures.last().add(char)
        }
    }

    class CharTexture(val owner: DynamicFontRenderer) {
        private val id = GlStateManager.generateTexture()

        init {
            RenderState.bindTexture(id)
            if (Settings.get().textLinearFiltering) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            } else {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(SIZE * SIZE * 4))
            RenderState.bindTexture(0)

            RenderState.checkError(javaClass.name + ".<init>: create texture")
        }

        // Some padding to avoid bleeding.
        private val cellWidth = owner.charWidth + 2
        private val cellHeight = owner.charHeight + 2
        private val cols = SIZE / cellWidth
        private val rows = SIZE / cellHeight
        private val uStep = cellWidth / SIZE.toDouble()
        private val vStep = cellHeight / SIZE.toDouble()
        private val pad = 1.0 / SIZE
        private val capacity = cols * rows

        private var chars = 0

        fun delete() {
            GlStateManager.deleteTexture(id)
        }

        fun bind() {
            RenderState.bindTexture(id)
        }

        fun isFull(char: Int) = chars + FontUtils.wcwidth(char) > capacity

        fun add(char: Int): CharIcon {
            val glyphWidth = FontUtils.wcwidth(char)
            val w = owner.charWidth * glyphWidth
            val h = owner.charHeight
            // Force line break if we have a char that's wider than what space remains in this row.
            if (chars % cols + glyphWidth > cols) {
                chars += 1
            }
            val x = chars % cols
            val y = chars / cols

            RenderState.bindTexture(id)
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 1 + x * cellWidth, 1 + y * cellHeight, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, owner.glyphProvider.getGlyph(char))

            chars += glyphWidth

            return CharIcon(this, w, h, pad + x * uStep, pad + y * vStep, (x + glyphWidth) * uStep - pad, (y + 1) * vStep - pad)
        }

        companion object {
            private const val SIZE = 256
        }
    }

    class CharIcon(val texture: CharTexture, val w: Int, val h: Int, val u1: Double, val v1: Double, val u2: Double, val v2: Double) {
        fun draw(tx: Float, ty: Float) {
            GL11.glTexCoord2d(u1, v2)
            GL11.glVertex2f(tx, ty + h)
            GL11.glTexCoord2d(u2, v2)
            GL11.glVertex2f(tx + w, ty + h)
            GL11.glTexCoord2d(u2, v1)
            GL11.glVertex2f(tx + w, ty)
            GL11.glTexCoord2d(u1, v1)
            GL11.glVertex2f(tx, ty)
        }
    }
}
