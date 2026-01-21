package li.cil.oc.client.renderer

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.init.Items
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Print
import li.cil.oc.common.tileentity.Cable as TileEntityCable
import li.cil.oc.common.block.Cable as BlockCable
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.rotateTowards
import li.cil.oc.util.ExtendedWorld.extendedWorld
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderGlobal
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.DrawBlockHighlightEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import kotlin.math.abs
import kotlin.random.Random

object HighlightRenderer {
    private val random = Random.Default

    val tablet by lazy { Items.get(Constants.ItemName.Tablet) }

    @SubscribeEvent
    @Suppress("unused")
    fun onDrawBlockHighlight(e: DrawBlockHighlightEvent) {
        if (e.target == null || e.target.blockPos == null) return

        val hitInfo = e.target
        val world = e.player.entityWorld
        val blockPos = BlockPosition(hitInfo.blockPos, world)

        if (hitInfo.typeOfHit == RayTraceResult.Type.BLOCK && Items.get(e.player.heldItemMainhand) == tablet) {
            val isAir = world.isAirBlock(blockPos)
            if (!isAir) {
                val block = world.getBlock(blockPos)
                val bounds = block.getSelectedBoundingBox(world.getBlockState(hitInfo.blockPos), world, hitInfo.blockPos)
                    .offset(-blockPos.x.toDouble(), -blockPos.y.toDouble(), -blockPos.z.toDouble())
                val sideHit = hitInfo.sideHit
                val playerPos = Vec3d(
                    e.player.prevPosX + (e.player.posX - e.player.prevPosX) * e.partialTicks,
                    e.player.prevPosY + (e.player.posY - e.player.prevPosY) * e.partialTicks,
                    e.player.prevPosZ + (e.player.posZ - e.player.prevPosZ) * e.partialTicks
                )
                val renderPos = blockPos.offset(-playerPos.x, -playerPos.y, -playerPos.z)

                GlStateManager.pushMatrix()
                RenderState.pushAttrib()
                RenderState.makeItBlend()
                Textures.bind(Textures.Model.HologramEffect)

                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
                GlStateManager.color(0.0f, 1.0f, 0.0f, 0.4f)

                GlStateManager.translate(renderPos.x, renderPos.y, renderPos.z)
                GlStateManager.scale(1.002, 1.002, 1.002)

                if (Settings.get.hologramFlickerFrequency > 0 && random.nextDouble() < Settings.get.hologramFlickerFrequency) {
                    val sx = 1 - abs(sideHit.xOffset)
                    val sy = 1 - abs(sideHit.yOffset)
                    val sz = 1 - abs(sideHit.zOffset)
                    GlStateManager.scale(1 + random.nextGaussian() * 0.01, 1 + random.nextGaussian() * 0.001, 1 + random.nextGaussian() * 0.01)
                    GlStateManager.translate(random.nextGaussian() * 0.01 * sx, random.nextGaussian() * 0.01 * sy, random.nextGaussian() * 0.01 * sz)
                }

                val t = Tessellator.getInstance()
                val r = t.buffer
                r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
                when (sideHit) {
                    EnumFacing.UP -> {
                        r.pos(bounds.maxX, bounds.maxY + 0.002, bounds.maxZ).tex(bounds.maxZ * 16, bounds.maxX * 16).endVertex()
                        r.pos(bounds.maxX, bounds.maxY + 0.002, bounds.minZ).tex(bounds.minZ * 16, bounds.maxX * 16).endVertex()
                        r.pos(bounds.minX, bounds.maxY + 0.002, bounds.minZ).tex(bounds.minZ * 16, bounds.minX * 16).endVertex()
                        r.pos(bounds.minX, bounds.maxY + 0.002, bounds.maxZ).tex(bounds.maxZ * 16, bounds.minX * 16).endVertex()
                    }
                    EnumFacing.DOWN -> {
                        r.pos(bounds.maxX, bounds.minY - 0.002, bounds.minZ).tex(bounds.minZ * 16, bounds.maxX * 16).endVertex()
                        r.pos(bounds.maxX, bounds.minY - 0.002, bounds.maxZ).tex(bounds.maxZ * 16, bounds.maxX * 16).endVertex()
                        r.pos(bounds.minX, bounds.minY - 0.002, bounds.maxZ).tex(bounds.maxZ * 16, bounds.minX * 16).endVertex()
                        r.pos(bounds.minX, bounds.minY - 0.002, bounds.minZ).tex(bounds.minZ * 16, bounds.minX * 16).endVertex()
                    }
                    EnumFacing.EAST -> {
                        r.pos(bounds.maxX + 0.002, bounds.maxY, bounds.minZ).tex(bounds.minZ * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.maxX + 0.002, bounds.maxY, bounds.maxZ).tex(bounds.maxZ * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.maxX + 0.002, bounds.minY, bounds.maxZ).tex(bounds.maxZ * 16, bounds.minY * 16).endVertex()
                        r.pos(bounds.maxX + 0.002, bounds.minY, bounds.minZ).tex(bounds.minZ * 16, bounds.minY * 16).endVertex()
                    }
                    EnumFacing.WEST -> {
                        r.pos(bounds.minX - 0.002, bounds.maxY, bounds.maxZ).tex(bounds.maxZ * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.minX - 0.002, bounds.maxY, bounds.minZ).tex(bounds.minZ * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.minX - 0.002, bounds.minY, bounds.minZ).tex(bounds.minZ * 16, bounds.minY * 16).endVertex()
                        r.pos(bounds.minX - 0.002, bounds.minY, bounds.maxZ).tex(bounds.maxZ * 16, bounds.minY * 16).endVertex()
                    }
                    EnumFacing.SOUTH -> {
                        r.pos(bounds.maxX, bounds.maxY, bounds.maxZ + 0.002).tex(bounds.maxX * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.minX, bounds.maxY, bounds.maxZ + 0.002).tex(bounds.minX * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.minX, bounds.minY, bounds.maxZ + 0.002).tex(bounds.minX * 16, bounds.minY * 16).endVertex()
                        r.pos(bounds.maxX, bounds.minY, bounds.maxZ + 0.002).tex(bounds.maxX * 16, bounds.minY * 16).endVertex()
                    }
                    else -> {
                        r.pos(bounds.minX, bounds.maxY, bounds.minZ - 0.002).tex(bounds.minX * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.maxX, bounds.maxY, bounds.minZ - 0.002).tex(bounds.maxX * 16, bounds.maxY * 16).endVertex()
                        r.pos(bounds.maxX, bounds.minY, bounds.minZ - 0.002).tex(bounds.maxX * 16, bounds.minY * 16).endVertex()
                        r.pos(bounds.minX, bounds.minY, bounds.minZ - 0.002).tex(bounds.minX * 16, bounds.minY * 16).endVertex()
                    }
                }
                t.draw()

                RenderState.disableBlend()
                RenderState.popAttrib()
                GlStateManager.popMatrix()
            }
        }

        if (hitInfo.typeOfHit == RayTraceResult.Type.BLOCK) {
            when (val te = e.player.entityWorld.getTileEntity(hitInfo.blockPos)) {
                is Print -> {
                    if (te.shapes.isNotEmpty()) {
                        val pos = Vec3d(
                            e.player.prevPosX + (e.player.posX - e.player.prevPosX) * e.partialTicks,
                            e.player.prevPosY + (e.player.posY - e.player.prevPosY) * e.partialTicks,
                            e.player.prevPosZ + (e.player.posZ - e.player.prevPosZ) * e.partialTicks
                        )
                        val expansion = 0.002f

                        // See RenderGlobal.drawSelectionBox.
                        GlStateManager.enableBlend()
                        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 1)
                        GlStateManager.color(0f, 0f, 0f, 0.4f)
                        GlStateManager.glLineWidth(2f)
                        GlStateManager.disableTexture2D()
                        GlStateManager.depthMask(false)

                        for (shape in te.shapes) {
                            val bounds = shape.bounds.rotateTowards(te.facing)
                            RenderGlobal.drawSelectionBoundingBox(
                                bounds.grow(expansion.toDouble(), expansion.toDouble(), expansion.toDouble())
                                    .offset(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
                                    .offset(-pos.x, -pos.y, -pos.z),
                                0f, 0f, 0f, 0x66 / 0xFFf.toFloat()
                            )
                        }

                        GlStateManager.depthMask(true)
                        GlStateManager.enableTexture2D()
                        GlStateManager.disableBlend()

                        e.isCanceled = true
                    }
                }
                is TileEntityCable -> {
                    // See RenderGlobal.drawSelectionBox.
                    GlStateManager.enableBlend()
                    OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 1)
                    GlStateManager.color(0f, 0f, 0f, 0.4f)
                    GlStateManager.glLineWidth(2f)
                    GlStateManager.disableTexture2D()
                    GlStateManager.depthMask(false)
                    GlStateManager.pushMatrix()

                    val player = e.player
                    GlStateManager.translate(
                        blockPos.x - (player.lastTickPosX + (player.posX - player.lastTickPosX) * e.partialTicks),
                        blockPos.y - (player.lastTickPosY + (player.posY - player.lastTickPosY) * e.partialTicks),
                        blockPos.z - (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * e.partialTicks)
                    )

                    val mask = BlockCable.neighbors(world, hitInfo.blockPos)
                    val tesselator = Tessellator.getInstance()
                    val buffer = tesselator.buffer

                    buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION)
                    Cable.drawOverlay(buffer, mask)
                    tesselator.draw()

                    GlStateManager.popMatrix()
                    GlStateManager.depthMask(true)
                    GlStateManager.enableTexture2D()
                    GlStateManager.disableBlend()

                    e.isCanceled = true
                }
            }
        }
    }

    private object Cable {
        private const val EXPAND = 0.002f
        private val MIN = BlockCable.MIN - EXPAND
        private val MAX = BlockCable.MAX + EXPAND

        fun drawOverlay(buffer: BufferBuilder, mask: Int) {
            // Draw the cable arms
            for (side in EnumFacing.values()) {
                if (((1 shl side.index) and mask) != 0) {
                    val offset = if (side.axisDirection == EnumFacing.AxisDirection.NEGATIVE) -EXPAND else 1 + EXPAND
                    val centre = if (side.axisDirection == EnumFacing.AxisDirection.NEGATIVE) MIN else MAX

                    // Draw the arm end quad
                    drawLineAdjacent(buffer, side.axis, offset.toDouble(), MIN.toDouble(), MIN.toDouble(), MIN.toDouble(), MAX.toDouble())
                    drawLineAdjacent(buffer, side.axis, offset.toDouble(), MIN.toDouble(), MAX.toDouble(), MAX.toDouble(), MAX.toDouble())
                    drawLineAdjacent(buffer, side.axis, offset.toDouble(), MAX.toDouble(), MAX.toDouble(), MAX.toDouble(), MIN.toDouble())
                    drawLineAdjacent(buffer, side.axis, offset.toDouble(), MAX.toDouble(), MIN.toDouble(), MIN.toDouble(), MIN.toDouble())

                    // Draw the connecting lines to the middle
                    drawLineAlong(buffer, side.axis, MIN.toDouble(), MIN.toDouble(), offset.toDouble(), centre.toDouble())
                    drawLineAlong(buffer, side.axis, MAX.toDouble(), MIN.toDouble(), offset.toDouble(), centre.toDouble())
                    drawLineAlong(buffer, side.axis, MAX.toDouble(), MAX.toDouble(), offset.toDouble(), centre.toDouble())
                    drawLineAlong(buffer, side.axis, MIN.toDouble(), MAX.toDouble(), offset.toDouble(), centre.toDouble())
                }
            }

            // Draw the cable core
            drawCore(buffer, mask, EnumFacing.WEST, EnumFacing.DOWN, EnumFacing.Axis.Z)
            drawCore(buffer, mask, EnumFacing.WEST, EnumFacing.UP, EnumFacing.Axis.Z)
            drawCore(buffer, mask, EnumFacing.EAST, EnumFacing.DOWN, EnumFacing.Axis.Z)
            drawCore(buffer, mask, EnumFacing.EAST, EnumFacing.UP, EnumFacing.Axis.Z)

            drawCore(buffer, mask, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.Axis.Y)
            drawCore(buffer, mask, EnumFacing.WEST, EnumFacing.SOUTH, EnumFacing.Axis.Y)
            drawCore(buffer, mask, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.Axis.Y)
            drawCore(buffer, mask, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.Axis.Y)

            drawCore(buffer, mask, EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.Axis.X)
            drawCore(buffer, mask, EnumFacing.DOWN, EnumFacing.SOUTH, EnumFacing.Axis.X)
            drawCore(buffer, mask, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.Axis.X)
            drawCore(buffer, mask, EnumFacing.UP, EnumFacing.SOUTH, EnumFacing.Axis.X)
        }

        /** Draw part of the core object */
        private fun drawCore(buffer: BufferBuilder, mask: Int, a: EnumFacing, b: EnumFacing, other: EnumFacing.Axis) {
            if (((mask shr a.ordinal) and 1) != ((mask shr b.ordinal) and 1)) return

            val offA = if (a.axisDirection == EnumFacing.AxisDirection.NEGATIVE) MIN else MAX
            val offB = if (b.axisDirection == EnumFacing.AxisDirection.NEGATIVE) MIN else MAX
            drawLineAlong(buffer, other, offA.toDouble(), offB.toDouble(), MIN.toDouble(), MAX.toDouble())
        }

        /** Draw a line parallel to an axis */
        private fun drawLineAlong(buffer: BufferBuilder, axis: EnumFacing.Axis, offA: Double, offB: Double, start: Double, end: Double) {
            when (axis) {
                EnumFacing.Axis.X -> {
                    buffer.pos(start, offA, offB).endVertex()
                    buffer.pos(end, offA, offB).endVertex()
                }
                EnumFacing.Axis.Y -> {
                    buffer.pos(offA, start, offB).endVertex()
                    buffer.pos(offA, end, offB).endVertex()
                }
                EnumFacing.Axis.Z -> {
                    buffer.pos(offA, offB, start).endVertex()
                    buffer.pos(offA, offB, end).endVertex()
                }
            }
        }

        /** Draw a line perpendicular to an axis */
        private fun drawLineAdjacent(buffer: BufferBuilder, axis: EnumFacing.Axis, offset: Double, startA: Double, startB: Double, endA: Double, endB: Double) {
            when (axis) {
                EnumFacing.Axis.X -> {
                    buffer.pos(offset, startA, startB).endVertex()
                    buffer.pos(offset, endA, endB).endVertex()
                }
                EnumFacing.Axis.Y -> {
                    buffer.pos(startA, offset, startB).endVertex()
                    buffer.pos(endA, offset, endB).endVertex()
                }
                EnumFacing.Axis.Z -> {
                    buffer.pos(startA, startB, offset).endVertex()
                    buffer.pos(endA, endB, offset).endVertex()
                }
            }
        }
    }
}
