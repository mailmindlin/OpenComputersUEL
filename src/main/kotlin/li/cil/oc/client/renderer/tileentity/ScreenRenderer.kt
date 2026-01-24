package li.cil.oc.client.renderer.tileentity

import li.cil.oc.Settings
import li.cil.oc.common.init.Items
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Screen
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GLContext
import kotlin.math.max

object ScreenRenderer : TileEntitySpecialRenderer<Screen>() {
    private val maxRenderDistanceSq = Settings.get.maxScreenTextRenderDistance * Settings.get.maxScreenTextRenderDistance

    private val fadeDistanceSq = Settings.get.screenTextFadeStartDistance * Settings.get.screenTextFadeStartDistance

    private val fadeRatio = 1.0 / (maxRenderDistanceSq - fadeDistanceSq)

    private var screen: Screen? = null

    private val canUseBlendColor = GLContext.getCapabilities().OpenGL14

    // ----------------------------------------------------------------------- //
    // Rendering
    // ----------------------------------------------------------------------- //

    override fun render(screen: Screen, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        this.screen = screen
        if (!screen.isOrigin()) {
            return
        }

        val distance = playerDistanceSq() / minOf(screen.width, screen.height)
        if (distance > maxRenderDistanceSq) {
            return
        }

        // y = block.bottom - player.feet
        // eye is higher, so the y delta should be more negative
        val eyeDelta = y - Minecraft.getMinecraft().player.getEyeHeight()

        // Crude check whether screen text can be seen by the local player based
        // on the player's position -> angle relative to screen.
        val screenFacing = screen.facing()!!.opposite
        if (screenFacing.xOffset * (x + 0.5) + screenFacing.yOffset * (eyeDelta + 0.5) + screenFacing.zOffset * (z + 0.5) < 0) {
            return
        }

        RenderState.checkError(javaClass.name + ".render: checks")

        RenderState.pushAttrib()

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0xFF.toFloat(), 0xFF.toFloat())
        RenderState.disableEntityLighting()
        RenderState.makeItBlend()
        GlStateManager.color(1f, 1f, 1f, 1f)

        GlStateManager.pushMatrix()

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        RenderState.checkError(javaClass.name + ".render: setup")

        drawOverlay()

        RenderState.checkError(javaClass.name + ".render: overlay")

        if (distance > fadeDistanceSq) {
            val alpha = max(0.0, 1 - ((distance - fadeDistanceSq) * fadeRatio)).toFloat()
            if (canUseBlendColor) {
                GL14.glBlendColor(0f, 0f, 0f, alpha)
                GlStateManager.blendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE)
            }
        }

        RenderState.checkError(javaClass.name + ".render: fade")

        if (screen.buffer.isRenderingEnabled) {
            val profiler = Minecraft.getMinecraft().profiler
            profiler.startSection("opencomputers:screen_text")
            draw()
            profiler.endSection()
        }

        RenderState.disableBlend()
        RenderState.enableEntityLighting()

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }

    private fun transform() {
        when (screen!!.yaw) {
            EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }
        when (screen!!.pitch) {
            EnumFacing.DOWN -> GlStateManager.rotate(90f, 1f, 0f, 0f)
            EnumFacing.UP -> GlStateManager.rotate(-90f, 1f, 0f, 0f)
            else -> {} // No pitch.
        }

        // Fit area to screen (bottom left = bottom left).
        GlStateManager.translate(-0.5f, -0.5f, 0.5f)
        GlStateManager.translate(0.0, screen!!.height.toDouble(), 0.0)

        // Flip text upside down.
        GlStateManager.scale(1.0, -1.0, 1.0)
    }

    private fun isScreen(stack: ItemStack): Boolean = when (val item = Items.get(stack)) {
        is ItemInfo -> item.block() is li.cil.oc.common.block.Screen
        else -> false
    }

    private fun drawOverlay() {
        if (screen!!.facing() == EnumFacing.UP || screen!!.facing() == EnumFacing.DOWN) {
            // Show up vector overlay when holding same screen block.
            val stack = Minecraft.getMinecraft().player.heldItemMainhand
            if (!stack.isEmpty) {
                if (Wrench.holdsApplicableWrench(Minecraft.getMinecraft().player, screen!!.pos) || isScreen(stack)) {
                    GlStateManager.pushMatrix()
                    transform()
                    GlStateManager.depthMask(false)
                    GlStateManager.translate((screen!!.width / 2f - 0.5f).toDouble(), (screen!!.height / 2f - 0.5f).toDouble(), 0.05)

                    val t = Tessellator.getInstance()
                    val r = t.buffer

                    Textures.Block.bind()
                    r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

                    val icon = Textures.getSprite(Textures.Block.ScreenUpIndicator)
                    r.pos(0.0, 1.0, 0.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                    r.pos(1.0, 1.0, 0.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                    r.pos(1.0, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                    r.pos(0.0, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

                    t.draw()

                    GlStateManager.depthMask(true)
                    GlStateManager.popMatrix()
                }
            }
        }
    }

    private fun draw() {
        RenderState.checkError(javaClass.name + ".draw: entering (aka: wasntme)")

        val sx = screen!!.width
        val sy = screen!!.height
        val tw = sx * 16f
        val th = sy * 16f

        transform()

        // Offset from border.
        GlStateManager.translate((sx * 2.25f / tw).toDouble(), (sy * 2.25f / th).toDouble(), 0.0)

        // Inner size (minus borders).
        val isx = sx - (4.5f / 16)
        val isy = sy - (4.5f / 16)

        // Scale based on actual buffer size.
        val sizeX = screen!!.buffer.renderWidth()
        val sizeY = screen!!.buffer.renderHeight()
        val scaleX = isx / sizeX
        val scaleY = isy / sizeY
        if (true) {
            if (scaleX > scaleY) {
                GlStateManager.translate((sizeX * 0.5f * (scaleX - scaleY)).toDouble(), 0.0, 0.0)
                GlStateManager.scale(scaleY.toDouble(), scaleY.toDouble(), 1.0)
            } else {
                GlStateManager.translate(0.0, (sizeY * 0.5f * (scaleY - scaleX)).toDouble(), 0.0)
                GlStateManager.scale(scaleX.toDouble(), scaleX.toDouble(), 1.0)
            }
        } else {
            // Stretch to fit.
            GlStateManager.scale(scaleX.toDouble(), scaleY.toDouble(), 1.0)
        }

        // Slightly offset the text so it doesn't clip into the screen.
        GlStateManager.translate(0.0, 0.0, 0.01)

        RenderState.checkError(javaClass.name + ".draw: setup")

        // Render the actual text.
        screen!!.buffer.renderText()

        RenderState.checkError(javaClass.name + ".draw: text")
    }

    private fun playerDistanceSq(): Double {
        val player = Minecraft.getMinecraft().player
        val bounds = screen!!.renderBoundingBox

        val px = player.posX
        val py = player.posY
        val pz = player.posZ

        val ex = bounds.maxX - bounds.minX
        val ey = bounds.maxY - bounds.minY
        val ez = bounds.maxZ - bounds.minZ
        val cx = bounds.minX + ex * 0.5
        val cy = bounds.minY + ey * 0.5
        val cz = bounds.minZ + ez * 0.5
        val dx = px - cx
        val dy = py - cy
        val dz = pz - cz

        return (if (dx < -ex) {
            val d = dx + ex
            d * d
        } else if (dx > ex) {
            val d = dx - ex
            d * d
        } else 0.0) + (if (dy < -ey) {
            val d = dy + ey
            d * d
        } else if (dy > ey) {
            val d = dy - ey
            d * d
        } else 0.0) + (if (dz < -ez) {
            val d = dz + ez
            d * d
        } else if (dz > ez) {
            val d = dz - ez
            d * d
        } else 0.0)
    }
}
