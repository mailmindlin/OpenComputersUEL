package li.cil.oc.client.renderer

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.init.Items
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11

object MFUTargetRenderer {
    private const val COLOR = 0x00FF00

    @SubscribeEvent
    @Suppress("unused")
    fun onRenderWorldLastEvent(e: RenderWorldLastEvent) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return

        val stack = player.heldItemMainhand
        if (stack is ItemStack && Items.get(stack) == Constants.ItemInfo.MFU && stack.hasTagCompound()) {
            val data = stack.tagCompound ?: return
            if (data.hasKey(Settings.namespace + "coord", NBT.TAG_INT_ARRAY)) {
                val coords = data.getIntArray(Settings.namespace + "coord")
                val (x, y, z, dimension, side) = coords
                if (player.entityWorld.provider.dimension != dimension) return
                if (player.getDistance(x.toDouble(), y.toDouble(), z.toDouble()) > 64) return

                val bounds = BlockPosition(x, y, z).bounds.grow(0.1, 0.1, 0.1)

                val px = player.lastTickPosX + (player.posX - player.lastTickPosX) * e.partialTicks
                val py = player.lastTickPosY + (player.posY - player.lastTickPosY) * e.partialTicks
                val pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * e.partialTicks

                RenderState.checkError(javaClass.name + ".onRenderWorldLastEvent: entering (aka: wasntme)")

                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
                GL11.glPushMatrix()
                GL11.glTranslated(-px, -py, -pz)
                RenderState.makeItBlend()
                GL11.glDisable(GL11.GL_LIGHTING)
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                GL11.glDisable(GL11.GL_DEPTH_TEST)
                GL11.glDisable(GL11.GL_CULL_FACE)

                GL11.glColor4f(
                    ((COLOR shr 16) and 0xFF) / 255f,
                    ((COLOR shr 8) and 0xFF) / 255f,
                    ((COLOR shr 0) and 0xFF) / 255f,
                    0.25f
                )
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE)
                drawBox(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
                drawFace(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, side)

                GL11.glPopMatrix()
                GL11.glPopAttrib()

                RenderState.checkError(javaClass.name + ".onRenderWorldLastEvent: leaving")
            }
        }
    }

    private fun drawBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
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

    private fun drawFace(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, side: Int) {
        when (side) {
            0 -> { // Down
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex3d(minX, minY, minZ)
                GL11.glVertex3d(minX, minY, maxZ)
                GL11.glVertex3d(maxX, minY, maxZ)
                GL11.glVertex3d(maxX, minY, minZ)
                GL11.glEnd()
            }
            1 -> { // Up
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex3d(maxX, maxY, minZ)
                GL11.glVertex3d(maxX, maxY, maxZ)
                GL11.glVertex3d(minX, maxY, maxZ)
                GL11.glVertex3d(minX, maxY, minZ)
                GL11.glEnd()
            }
            2 -> { // North
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex3d(minX, minY, minZ)
                GL11.glVertex3d(maxX, minY, minZ)
                GL11.glVertex3d(maxX, maxY, minZ)
                GL11.glVertex3d(minX, maxY, minZ)
                GL11.glEnd()
            }
            3 -> { // South
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex3d(maxX, maxY, maxZ)
                GL11.glVertex3d(maxX, minY, maxZ)
                GL11.glVertex3d(minX, minY, maxZ)
                GL11.glVertex3d(minX, maxY, maxZ)
                GL11.glEnd()
            }
            4 -> { // East
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex3d(minX, minY, minZ)
                GL11.glVertex3d(minX, maxY, minZ)
                GL11.glVertex3d(minX, maxY, maxZ)
                GL11.glVertex3d(minX, minY, maxZ)
                GL11.glEnd()
            }
            5 -> { // West
                GL11.glBegin(GL11.GL_QUADS)
                GL11.glVertex3d(maxX, minY, minZ)
                GL11.glVertex3d(maxX, minY, maxZ)
                GL11.glVertex3d(maxX, maxY, maxZ)
                GL11.glVertex3d(maxX, maxY, minZ)
                GL11.glEnd()
            }
        }
    }
}
