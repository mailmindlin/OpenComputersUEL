package li.cil.oc.client.renderer

import li.cil.oc.Settings
import li.cil.oc.server.network.WirelessNetwork
import li.cil.oc.util.OCObfuscationReflectionHelper
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.world.World
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11

object WirelessNetworkDebugRenderer {
    val colors = intArrayOf(0xFF0000, 0x00FFFF, 0x00FF00, 0x0000FF, 0xFF00FF, 0xFFFF00, 0xFFFFFF, 0x000000)

    @SubscribeEvent
    @Suppress("unused")
    fun onRenderWorldLastEvent(e: RenderWorldLastEvent) {
        if (Settings.rTreeDebugRenderer) {
            RenderState.checkError(javaClass.name + ".onRenderWorldLastEvent: entering (aka: wasntme)")

            val world = OCObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.client.renderer.RenderGlobal::class.java,
                e.context,
                "field_72769_h"
            ) as World

            WirelessNetwork.dimensions[world.provider.dimension]?.let { tree ->
                val mc = Minecraft.getMinecraft()
                val player = mc.player
                val px = player.lastTickPosX + (player.posX - player.lastTickPosX) * e.partialTicks
                val py = player.lastTickPosY + (player.posY - player.lastTickPosY) * e.partialTicks
                val pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * e.partialTicks

                RenderState.pushAttrib()
                GlStateManager.pushMatrix()
                GL11.glTranslated(-px, -py, -pz)
                RenderState.makeItBlend()
                GL11.glDisable(GL11.GL_LIGHTING)
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                GL11.glDisable(GL11.GL_DEPTH_TEST)
                GL11.glDisable(GL11.GL_CULL_FACE)

                fun drawBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
                    GL11.glBegin(GL11.GL_QUADS)
                    GL11.glVertex3d(minX, minY, minZ)
                    GL11.glVertex3d(minX, minY, maxZ)
                    GL11.glVertex3d(maxX, minY, maxZ)
                    GL11.glVertex3d(maxX, minY, minZ)
                    GL11.glEnd()
                    GL11.glBegin(GL11.GL_QUADS)
                    GL11.glVertex3d(minX, minY, minZ)
                    GL11.glVertex3d(maxX, minY, minZ)
                    GL11.glVertex3d(maxX, maxY, minZ)
                    GL11.glVertex3d(minX, maxY, minZ)
                    GL11.glEnd()
                    GL11.glBegin(GL11.GL_QUADS)
                    GL11.glVertex3d(maxX, maxY, minZ)
                    GL11.glVertex3d(maxX, maxY, maxZ)
                    GL11.glVertex3d(minX, maxY, maxZ)
                    GL11.glVertex3d(minX, maxY, minZ)
                    GL11.glEnd()
                    GL11.glBegin(GL11.GL_QUADS)
                    GL11.glVertex3d(maxX, maxY, maxZ)
                    GL11.glVertex3d(maxX, minY, maxZ)
                    GL11.glVertex3d(minX, minY, maxZ)
                    GL11.glVertex3d(minX, maxY, maxZ)
                    GL11.glEnd()
                    GL11.glBegin(GL11.GL_QUADS)
                    GL11.glVertex3d(minX, minY, minZ)
                    GL11.glVertex3d(minX, maxY, minZ)
                    GL11.glVertex3d(minX, maxY, maxZ)
                    GL11.glVertex3d(minX, minY, maxZ)
                    GL11.glEnd()
                    GL11.glBegin(GL11.GL_QUADS)
                    GL11.glVertex3d(maxX, minY, minZ)
                    GL11.glVertex3d(maxX, minY, maxZ)
                    GL11.glVertex3d(maxX, maxY, maxZ)
                    GL11.glVertex3d(maxX, maxY, minZ)
                    GL11.glEnd()
                }

                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
                for ((bounds, level) in tree.allBounds) {
                    val (min, max) = bounds
                    val (minX, minY, minZ) = min
                    val (maxX, maxY, maxZ) = max
                    val color = colors[level % colors.size]
                    GL11.glColor4f(
                        ((color shr 16) and 0xFF) / 255f,
                        ((color shr 8) and 0xFF) / 255f,
                        ((color shr 0) and 0xFF) / 255f,
                        0.25f
                    )
                    val size = 0.5 - level * 0.05
                    drawBox(minX - size, minY - size, minZ - size, maxX + size, maxY + size, maxZ + size)
                }
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)

                RenderState.popAttrib()
                GlStateManager.popMatrix()
            }

            RenderState.checkError(javaClass.name + ".onRenderWorldLastEvent: leaving")
        }
    }
}
