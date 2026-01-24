package li.cil.oc.client.renderer.tileentity

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.driver.item.UpgradeRenderer
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.Robot
import li.cil.oc.common.tileentity.RobotProxy
import li.cil.oc.util.RenderState
import li.cil.oc.util.StackOption
import li.cil.oc.util.SomeStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType
import net.minecraft.client.renderer.entity.RenderLivingBase
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.client.renderer.vertex.VertexFormatElement
import net.minecraft.init.Items
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.client.MinecraftForgeClient
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.opengl.GL11
import kotlin.math.max
import kotlin.math.sin

object RobotRenderer : TileEntitySpecialRenderer<RobotProxy>() {
    private val displayList = GLAllocation.generateDisplayLists(2)

    private val mountPoints = Array(7) { i ->
        RobotRenderEvent.MountPoint(when (i) {
            0 -> MountPointName.TopLeft
            1 -> MountPointName.TopRight
            2 -> MountPointName.TopBack
            3 -> MountPointName.BottomLeft
            4 -> MountPointName.BottomRight
            5 -> MountPointName.BottomBack
            else -> MountPointName.BottomFront
        })
    }

    private val slotNameMapping = mapOf(
        MountPointName.TopLeft to 0,
        MountPointName.TopRight to 1,
        MountPointName.TopBack to 2,
        MountPointName.BottomLeft to 3,
        MountPointName.BottomRight to 4,
        MountPointName.BottomBack to 5,
        MountPointName.BottomFront to 6
    )

    private const val size = 0.4f
    private const val l = 0.5f - size
    private const val h = 0.5f + size
    private const val gap = 1.0f / 28.0f
    private const val gt = 0.5f + gap
    private const val gb = 0.5f - gap

    // https://github.com/MinecraftForge/MinecraftForge/issues/2321
    val POSITION_TEX_NORMALF = VertexFormat()
    val NORMAL_3F = VertexFormatElement(0, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.NORMAL, 3)

    init {
        POSITION_TEX_NORMALF.addElement(DefaultVertexFormats.POSITION_3F)
        POSITION_TEX_NORMALF.addElement(DefaultVertexFormats.TEX_2F)
        POSITION_TEX_NORMALF.addElement(NORMAL_3F)
    }

    private fun BufferBuilder.normal(normal: Vec3d): BufferBuilder {
        val normalized = normal.normalize()
        return this.normal(normalized.x.toFloat(), normalized.y.toFloat(), normalized.z.toFloat())
    }

    private fun drawTop() {
        val t = Tessellator.getInstance()
        val r = t.buffer

        r.begin(GL11.GL_TRIANGLE_FAN, POSITION_TEX_NORMALF)

        r.pos(0.5, 1.0, 0.5).tex(0.25, 0.25).normal(Vec3d(0.0, 0.2, 1.0)).endVertex()
        r.pos(l.toDouble(), gt.toDouble(), h.toDouble()).tex(0.0, 0.5).normal(Vec3d(0.0, 0.2, 1.0)).endVertex()
        r.pos(h.toDouble(), gt.toDouble(), h.toDouble()).tex(0.5, 0.5).normal(Vec3d(0.0, 0.2, 1.0)).endVertex()
        r.pos(h.toDouble(), gt.toDouble(), l.toDouble()).tex(0.5, 0.0).normal(Vec3d(1.0, 0.2, 0.0)).endVertex()
        r.pos(l.toDouble(), gt.toDouble(), l.toDouble()).tex(0.0, 0.0).normal(Vec3d(0.0, 0.2, -1.0)).endVertex()
        r.pos(l.toDouble(), gt.toDouble(), h.toDouble()).tex(0.0, 0.5).normal(Vec3d(-1.0, 0.2, 0.0)).endVertex()

        t.draw()

        r.begin(GL11.GL_QUADS, POSITION_TEX_NORMALF)

        r.pos(l.toDouble(), gt.toDouble(), h.toDouble()).tex(0.0, 1.0).normal(0f, -1f, 0f).endVertex()
        r.pos(l.toDouble(), gt.toDouble(), l.toDouble()).tex(0.0, 0.5).normal(0f, -1f, 0f).endVertex()
        r.pos(h.toDouble(), gt.toDouble(), l.toDouble()).tex(0.5, 0.5).normal(0f, -1f, 0f).endVertex()
        r.pos(h.toDouble(), gt.toDouble(), h.toDouble()).tex(0.5, 1.0).normal(0f, -1f, 0f).endVertex()

        t.draw()
    }

    private fun drawBottom() {
        val t = Tessellator.getInstance()
        val r = t.buffer

        r.begin(GL11.GL_TRIANGLE_FAN, POSITION_TEX_NORMALF)

        r.pos(0.5, 0.03, 0.5).tex(0.75, 0.25).normal(Vec3d(0.0, -0.2, 1.0)).endVertex()
        r.pos(l.toDouble(), gb.toDouble(), l.toDouble()).tex(0.5, 0.0).normal(Vec3d(0.0, -0.2, 1.0)).endVertex()
        r.pos(h.toDouble(), gb.toDouble(), l.toDouble()).tex(1.0, 0.0).normal(Vec3d(0.0, -0.2, 1.0)).endVertex()
        r.pos(h.toDouble(), gb.toDouble(), h.toDouble()).tex(1.0, 0.5).normal(Vec3d(1.0, -0.2, 0.0)).endVertex()
        r.pos(l.toDouble(), gb.toDouble(), h.toDouble()).tex(0.5, 0.5).normal(Vec3d(0.0, -0.2, -1.0)).endVertex()
        r.pos(l.toDouble(), gb.toDouble(), l.toDouble()).tex(0.5, 0.0).normal(Vec3d(-1.0, -0.2, 0.0)).endVertex()

        t.draw()

        r.begin(GL11.GL_QUADS, POSITION_TEX_NORMALF)

        r.pos(l.toDouble(), gb.toDouble(), l.toDouble()).tex(0.0, 0.5).normal(0f, 1f, 0f).endVertex()
        r.pos(l.toDouble(), gb.toDouble(), h.toDouble()).tex(0.0, 1.0).normal(0f, 1f, 0f).endVertex()
        r.pos(h.toDouble(), gb.toDouble(), h.toDouble()).tex(0.5, 1.0).normal(0f, 1f, 0f).endVertex()
        r.pos(h.toDouble(), gb.toDouble(), l.toDouble()).tex(0.5, 0.5).normal(0f, 1f, 0f).endVertex()

        t.draw()
    }

    fun compileList() {
        GL11.glNewList(displayList, GL11.GL_COMPILE)

        drawTop()

        GL11.glEndList()

        GL11.glNewList(displayList + 1, GL11.GL_COMPILE)

        drawBottom()

        GL11.glEndList()
    }

    init {
        compileList()
    }

    fun resetMountPoints(running: Boolean) {
        val offset = if (running) 0f else -0.06f

        // Left top.
        mountPoints[0].offset.setX(0f)
        mountPoints[0].offset.setY(0.2f)
        mountPoints[0].offset.setZ(0.24f)
        mountPoints[0].rotation.setX(0f)
        mountPoints[0].rotation.setY(1f)
        mountPoints[0].rotation.setZ(0f)
        mountPoints[0].rotation.setW(90f)

        // Right top.
        mountPoints[1].offset.setX(0f)
        mountPoints[1].offset.setY(0.2f)
        mountPoints[1].offset.setZ(0.24f)
        mountPoints[1].rotation.setX(0f)
        mountPoints[1].rotation.setY(1f)
        mountPoints[1].rotation.setZ(0f)
        mountPoints[1].rotation.setW(-90f)

        // Back top.
        mountPoints[2].offset.setX(0f)
        mountPoints[2].offset.setY(0.2f)
        mountPoints[2].offset.setZ(0.24f)
        mountPoints[2].rotation.setX(0f)
        mountPoints[2].rotation.setY(1f)
        mountPoints[2].rotation.setZ(0f)
        mountPoints[2].rotation.setW(180f)

        // Left bottom.
        mountPoints[3].offset.setX(0f)
        mountPoints[3].offset.setY(-0.2f - offset)
        mountPoints[3].offset.setZ(0.24f)
        mountPoints[3].rotation.setX(0f)
        mountPoints[3].rotation.setY(1f)
        mountPoints[3].rotation.setZ(0f)
        mountPoints[3].rotation.setW(90f)

        // Right bottom.
        mountPoints[4].offset.setX(0f)
        mountPoints[4].offset.setY(-0.2f - offset)
        mountPoints[4].offset.setZ(0.24f)
        mountPoints[4].rotation.setX(0f)
        mountPoints[4].rotation.setY(1f)
        mountPoints[4].rotation.setZ(0f)
        mountPoints[4].rotation.setW(-90f)

        // Back bottom.
        mountPoints[5].offset.setX(0f)
        mountPoints[5].offset.setY(-0.2f - offset)
        mountPoints[5].offset.setZ(0.24f)
        mountPoints[5].rotation.setX(0f)
        mountPoints[5].rotation.setY(1f)
        mountPoints[5].rotation.setZ(0f)
        mountPoints[5].rotation.setW(180f)

        // Front bottom.
        mountPoints[6].offset.setX(0f)
        mountPoints[6].offset.setY(-0.2f - offset)
        mountPoints[6].offset.setZ(0.24f)
        mountPoints[6].rotation.setX(0f)
        mountPoints[6].rotation.setY(1f)
        mountPoints[6].rotation.setZ(0f)
        mountPoints[6].rotation.setW(0f)
    }

    fun renderChassis(robot: Robot? = null, offset: Double = 0.0, isRunningOverride: Boolean = false) {
        val isRunning = robot?.isRunning ?: isRunningOverride

        val vStep = 1.0f / 32.0f

        val offsetV = ((offset - offset.toInt()) * 16).toInt() * vStep
        val (u0, u1, v0, v1) = if (isRunning) {
            Tuple4(0.5f, 1f, 0.5f + offsetV, 0.5f + vStep + offsetV)
        } else {
            Tuple4(0.25f - vStep, 0.25f + vStep, 0.75f - vStep, 0.75f + vStep)
        }

        resetMountPoints(robot != null && robot.isRunning)
        val event = RobotRenderEvent(robot, mountPoints)
        MinecraftForge.EVENT_BUS.post(event)
        if (!event.isCanceled) {
            bindTexture(Textures.Model.Robot)
            if (!isRunning) {
                GlStateManager.translate(0.0, (-2 * gap).toDouble(), 0.0)
            }
            //GlStateManager.callList(displayList + 1)
            drawBottom()
            if (!isRunning) {
                GlStateManager.translate(0.0, (-2 * gap).toDouble(), 0.0)
            }

            if (MinecraftForgeClient.getRenderPass() > 0) return

            //GlStateManager.callList(displayList)
            drawTop()
            GlStateManager.color(1f, 1f, 1f)

            if (isRunning) {
                RenderState.disableEntityLighting()

                run {
                    // Additive blending for the light.
                    RenderState.makeItBlend()
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
                    // Light color.
                    val lightColor = robot?.info?.lightColor ?: 0xF23030
                    val r = (lightColor ushr 16) and 0xFF
                    val g = (lightColor ushr 8) and 0xFF
                    val b = (lightColor ushr 0) and 0xFF
                    GlStateManager.color(r / 255f, g / 255f, b / 255f)
                }

                val t = Tessellator.getInstance()
                val r = t.buffer
                r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
                r.pos(l.toDouble(), gt.toDouble(), l.toDouble()).tex(u0.toDouble(), v0.toDouble()).endVertex()
                r.pos(l.toDouble(), gb.toDouble(), l.toDouble()).tex(u0.toDouble(), v1.toDouble()).endVertex()
                r.pos(l.toDouble(), gb.toDouble(), h.toDouble()).tex(u1.toDouble(), v1.toDouble()).endVertex()
                r.pos(l.toDouble(), gt.toDouble(), h.toDouble()).tex(u1.toDouble(), v0.toDouble()).endVertex()

                r.pos(l.toDouble(), gt.toDouble(), h.toDouble()).tex(u0.toDouble(), v0.toDouble()).endVertex()
                r.pos(l.toDouble(), gb.toDouble(), h.toDouble()).tex(u0.toDouble(), v1.toDouble()).endVertex()
                r.pos(h.toDouble(), gb.toDouble(), h.toDouble()).tex(u1.toDouble(), v1.toDouble()).endVertex()
                r.pos(h.toDouble(), gt.toDouble(), h.toDouble()).tex(u1.toDouble(), v0.toDouble()).endVertex()

                r.pos(h.toDouble(), gt.toDouble(), h.toDouble()).tex(u0.toDouble(), v0.toDouble()).endVertex()
                r.pos(h.toDouble(), gb.toDouble(), h.toDouble()).tex(u0.toDouble(), v1.toDouble()).endVertex()
                r.pos(h.toDouble(), gb.toDouble(), l.toDouble()).tex(u1.toDouble(), v1.toDouble()).endVertex()
                r.pos(h.toDouble(), gt.toDouble(), l.toDouble()).tex(u1.toDouble(), v0.toDouble()).endVertex()

                r.pos(h.toDouble(), gt.toDouble(), l.toDouble()).tex(u0.toDouble(), v0.toDouble()).endVertex()
                r.pos(h.toDouble(), gb.toDouble(), l.toDouble()).tex(u0.toDouble(), v1.toDouble()).endVertex()
                r.pos(l.toDouble(), gb.toDouble(), l.toDouble()).tex(u1.toDouble(), v1.toDouble()).endVertex()
                r.pos(l.toDouble(), gt.toDouble(), l.toDouble()).tex(u1.toDouble(), v0.toDouble()).endVertex()
                t.draw()

                RenderState.disableBlend()
                RenderState.enableEntityLighting()
            }
            GlStateManager.color(1f, 1f, 1f, 1f)
        }
    }

    override fun render(proxy: RobotProxy, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        val robot = proxy.robot
        val worldTime = robot.world!!.totalWorldTime + f

        GlStateManager.pushMatrix()
        RenderState.pushAttrib()
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        // If the move started while we were rendering and we have a reference to
        // the *old* proxy the robot would be rendered at the wrong position, so we
        // correct for the offset.
        if (robot.proxy != proxy) {
            GlStateManager.translate((robot.proxy.x - proxy.x).toDouble(), (robot.proxy.y - proxy.y).toDouble(), (robot.proxy.z - proxy.z).toDouble())
        }

        if (robot.isAnimatingMove) {
            val remaining = (robot.animationTicksLeft - f) / robot.animationTicksTotal.toDouble()
            val delta = robot.moveFrom.get().subtract(robot.pos)
            GlStateManager.translate(delta.x * remaining, delta.y * remaining, delta.z * remaining)
        }

        val timeJitter = robot.hashCode() xor 0xFF
        val hover = if (robot.isRunning) sin(timeJitter + worldTime / 20.0).toFloat() * 0.03f else -0.03f
        GlStateManager.translate(0.0, hover.toDouble(), 0.0)

        GlStateManager.pushMatrix()

        GlStateManager.depthMask(true)
        RenderState.enableEntityLighting()
        GlStateManager.disableBlend()

        if (robot.isAnimatingTurn) {
            val remaining = (robot.animationTicksLeft - f) / robot.animationTicksTotal.toFloat()
            GlStateManager.rotate(90 * remaining, 0f, robot.turnAxis.toFloat(), 0f)
        }

        when (robot.yaw()) {
            EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }

        GlStateManager.translate(-0.5f, -0.5f, -0.5f)

        val offset = timeJitter + worldTime / 20.0
        renderChassis(robot, offset)

        if (MinecraftForgeClient.getRenderPass() == 0 && !robot.renderingErrored && x * x + y * y + z * z < 24 * 24) {
            when (val stackOpt = StackOption(robot.getStackInSlot(0))) {
                is SomeStack -> {
                    val stack = stackOpt.value

                    RenderState.pushAttrib()
                    GlStateManager.pushMatrix()
                    try {
                        // Copy-paste from player render code, with minor adjustments for
                        // robot scale.

                        GlStateManager.disableCull()
                        GlStateManager.enableRescaleNormal()

                        GlStateManager.scale(1.0, -1.0, -1.0)
                        GlStateManager.translate(0.0, (-8 * 0.0625 - 0.0078125).toDouble(), -0.5)

                        if (robot.isAnimatingSwing) {
                            val wantedTicksPerCycle = 10
                            val cycles = max(robot.animationTicksTotal / wantedTicksPerCycle, 1)
                            val ticksPerCycle = robot.animationTicksTotal / cycles
                            val remaining = (robot.animationTicksLeft - f) / ticksPerCycle.toDouble()
                            GlStateManager.rotate((sin((remaining - remaining.toInt()) * Math.PI) * 45).toFloat(), 1f, 0f, 0f)
                        }

                        val item = stack.item
                        if (item is ItemBlock) {
                            GlStateManager.rotate(-90.0f, 1.0f, 0.0f, 0.0f)
                            GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f)
                            val scale = 0.625f
                            GlStateManager.scale(scale, scale, scale)
                        } else if (item == Items.BOW) {
                            GlStateManager.translate((1.5f / 16f).toDouble(), -0.125, -0.125)
                            GlStateManager.rotate(10.0f, 0.0f, 0.0f, 1.0f)
                            val scale = 0.625f
                            GlStateManager.scale(scale, -scale, scale)
                        } else if (item.isFull3D) {
                            if (item.shouldRotateAroundWhenRendering()) {
                                GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)
                                GlStateManager.translate(0.0, -0.0625, 0.0)
                            }

                            GlStateManager.translate(0.0, 0.1875, 0.0)
                            GlStateManager.translate(0.0625, -0.125, (-2 / 16.0))
                            val scale = 0.625f
                            GlStateManager.scale(scale, -scale, scale)
                            GlStateManager.rotate(0.0f, 1.0f, 0.0f, 0.0f)
                            GlStateManager.rotate(0.0f, 0.0f, 1.0f, 0.0f)
                        } else {
                            GlStateManager.translate(0.0, (2f / 16f).toDouble(), 0.0)
                            val scale = 0.875f
                            GlStateManager.scale(scale, scale, scale)
                            GlStateManager.rotate(90.0f, 0.0f, 1.0f, 0.0f)
                            GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)
                        }

                        Minecraft.getMinecraft().itemRenderer.renderItem(Minecraft.getMinecraft().player, stack, TransformType.THIRD_PERSON_RIGHT_HAND)
                    } catch (e: Throwable) {
                        OpenComputers.log.warn("Failed rendering equipped item.", e)
                        robot.proxy().renderingErrored = true
                    }
                    GlStateManager.enableCull()
                    GlStateManager.disableRescaleNormal()
                    GlStateManager.popMatrix()
                    RenderState.popAttrib()
                }
                else -> {}
            }

            if (MinecraftForgeClient.getRenderPass() == 0) {
                val availableSlots = slotNameMapping.keys.toMutableSet()
                val wildcardRenderers = mutableListOf<Pair<ItemStack, UpgradeRenderer>>()
                val slotMapping = arrayOfNulls<Pair<ItemStack, UpgradeRenderer>>(mountPoints.size)

                val renderers = (robot.componentSlots() + robot.containerSlots()).map { robot.getStackInSlot(it) }
                    .filter { !it.isEmpty() && it.item is UpgradeRenderer }
                    .map { Pair(it, it.item as UpgradeRenderer) }

                for ((stack, renderer) in renderers) {
                    val preferredSlot = renderer.computePreferredMountPoint(stack, robot, availableSlots)
                    if (availableSlots.remove(preferredSlot)) {
                        slotMapping[slotNameMapping[preferredSlot]!!] = stack to renderer
                    } else if (preferredSlot == MountPointName.Any) {
                        wildcardRenderers += (stack to renderer)
                    }
                }

                var firstEmpty = slotMapping.indexOf(null)
                for (entry in wildcardRenderers) {
                    if (firstEmpty < 0) break
                    slotMapping[firstEmpty] = entry
                    firstEmpty = slotMapping.indexOf(null)
                }

                for ((info, mountPoint) in slotMapping.zip(mountPoints)) {
                    if (info != null) {
                        try {
                            val (stack, renderer) = info
                            GlStateManager.pushMatrix()
                            GlStateManager.translate(0.5, 0.5, 0.5)
                            renderer.render(stack, mountPoint, robot, f)
                            GlStateManager.popMatrix()
                        } catch (e: Throwable) {
                            OpenComputers.log.warn("Failed rendering equipped upgrade.", e)
                            robot.proxy().renderingErrored = true
                        }
                    }
                }
            }
        }
        GlStateManager.popMatrix()

        val name = robot.name
        if (Settings.get.robotLabels && MinecraftForgeClient.getRenderPass() == 1 && !Strings.isNullOrEmpty(name) && x * x + y * y + z * z < RenderLivingBase.NAME_TAG_RANGE) {
            GlStateManager.pushMatrix()

            // This is pretty much copy-pasta from the entity's label renderer.
            val t = Tessellator.getInstance()
            val r = t.buffer
            val f = fontRenderer
            val scale = 1.6f / 60f
            val width = f.getStringWidth(name)
            val halfWidth = width / 2

            GlStateManager.translate(0.0, 0.8, 0.0)
            GL11.glNormal3f(0f, 1f, 0f)
            GlStateManager.color(1f, 1f, 1f)

            GlStateManager.rotate(-rendererDispatcher.entityYaw, 0f, 1f, 0f)
            GlStateManager.rotate(rendererDispatcher.entityPitch, 1f, 0f, 0f)
            GlStateManager.scale(-scale, -scale, scale)

            RenderState.makeItBlend()
            GlStateManager.depthMask(false)
            GlStateManager.disableLighting()
            GlStateManager.disableTexture2D()

            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
            r.pos((-halfWidth - 1).toDouble(), -1.0, 0.0).color(0f, 0f, 0f, 0.5f).endVertex()
            r.pos((-halfWidth - 1).toDouble(), 8.0, 0.0).color(0f, 0f, 0f, 0.5f).endVertex()
            r.pos((halfWidth + 1).toDouble(), 8.0, 0.0).color(0f, 0f, 0f, 0.5f).endVertex()
            r.pos((halfWidth + 1).toDouble(), -1.0, 0.0).color(0f, 0f, 0f, 0.5f).endVertex()
            t.draw()

            GlStateManager.enableTexture2D() // For the font.
            f.drawString((if (EventHandler.isItTime) TextFormatting.OBFUSCATED.toString() else "") + name, -halfWidth, 0, 0xFFFFFFFF.toInt())

            GlStateManager.depthMask(true)
            GlStateManager.enableLighting()
            RenderState.disableBlend()

            GlStateManager.popMatrix()
        }

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
