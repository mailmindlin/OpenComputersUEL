package li.cil.oc.client.renderer

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import li.cil.oc.Settings
import li.cil.oc.client.renderer.font.DynamicFontRenderer
import li.cil.oc.client.renderer.font.StaticFontRenderer
import li.cil.oc.client.renderer.font.TextBufferRenderData
import li.cil.oc.client.renderer.font.TextureFontRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.lwjgl.opengl.GL11
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

object TextBufferRenderCache : Callable<Int>, RemovalListener<TextBufferRenderData, Int> {
    val renderer: TextureFontRenderer = if (Settings.get.fontRenderer == "texture") {
        StaticFontRenderer()
    } else {
        DynamicFontRenderer()
    }

    private val cache = CacheBuilder.newBuilder()
        .expireAfterAccess(2, TimeUnit.SECONDS)
        .removalListener(this)
        .build<TextBufferRenderData, Int>()

    // To allow access in cache entry init.
    private var currentBuffer: TextBufferRenderData? = null

    // ----------------------------------------------------------------------- //
    // Rendering
    // ----------------------------------------------------------------------- //

    fun render(buffer: TextBufferRenderData) {
        currentBuffer = buffer
        compileOrDraw(cache.get(currentBuffer!!, this))
    }

    private fun compileOrDraw(list: Int): Boolean {
        val buffer = currentBuffer!!
        return if (buffer.dirty) {
            RenderState.checkError(javaClass.name + ".compileOrDraw: entering (aka: wasntme)")

            for (line in buffer.data.buffer) {
                renderer.generateChars(line)
            }

            val doCompile = !RenderState.compilingDisplayList()
            if (doCompile) {
                buffer.dirty = false
                GL11.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE)

                RenderState.checkError(javaClass.name + ".compileOrDraw: glNewList")
            }

            renderer.drawBuffer(buffer.data, buffer.viewport.width, buffer.viewport.height)

            RenderState.checkError(javaClass.name + ".compileOrDraw: drawString")

            if (doCompile) {
                GL11.glEndList()

                RenderState.checkError(javaClass.name + ".compileOrDraw: glEndList")
            }

            RenderState.checkError(javaClass.name + ".compileOrDraw: leaving")

            true
        } else {
            GL11.glCallList(list)
            GlStateManager.enableTexture2D()
            GlStateManager.depthMask(true)
            GlStateManager.color(1f, 1f, 1f, 1f)

            // Because display lists and the GlStateManager don't like each other, apparently.
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            RenderState.bindTexture(0)
            GL11.glDepthMask(true)
            GL11.glColor4f(1f, 1f, 1f, 1f)

            RenderState.disableBlend()

            RenderState.checkError(javaClass.name + ".compileOrDraw: glCallList")
            false
        }
    }

    // ----------------------------------------------------------------------- //
    // Cache
    // ----------------------------------------------------------------------- //

    override fun call(): Int {
        RenderState.checkError(javaClass.name + ".call: entering (aka: wasntme)")

        val list = GLAllocation.generateDisplayLists(1)
        currentBuffer!!.dirty = true // Force compilation.

        RenderState.checkError(javaClass.name + ".call: leaving")

        return list
    }

    override fun onRemoval(e: RemovalNotification<TextBufferRenderData, Int>) {
        RenderState.checkError(javaClass.name + ".onRemoval: entering (aka: wasntme)")

        GLAllocation.deleteDisplayLists(e.value)

        RenderState.checkError(javaClass.name + ".onRemoval: leaving")
    }

    // ----------------------------------------------------------------------- //
    // ITickHandler
    // ----------------------------------------------------------------------- //

    @SubscribeEvent
    @Suppress("unused")
    fun onTick(e: ClientTickEvent) {
        cache.cleanUp()
    }
}
