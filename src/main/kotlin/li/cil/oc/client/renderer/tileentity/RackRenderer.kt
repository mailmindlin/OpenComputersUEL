package li.cil.oc.client.renderer.tileentity

import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.common.tileentity.Rack
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge

object RackRenderer : TileEntitySpecialRenderer<Rack>() {
    private const val vOffset = 2 / 16f
    private const val vSize = 3 / 16f

    override fun render(rack: Rack, x: Double, y: Double, z: Double, partialTicks: Float, destroyStage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        RenderState.pushAttrib()

        GlStateManager.pushMatrix()

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        when (rack.yaw) {
            EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }

        GlStateManager.translate(-0.5, 0.5, 0.505 - 0.5f / 16f)
        GlStateManager.scale(1.0, -1.0, 1.0)

        // Note: we manually sync the rack inventory for this to work.
        for (i in 0 until rack.sizeInventory) {
            if (!rack.getStackInSlot(i).isEmpty) {
                GlStateManager.pushMatrix()
                RenderState.pushAttrib()

                val v0 = vOffset + i * vSize
                val v1 = vOffset + (i + 1) * vSize
                val event = RackMountableRenderEvent.TileEntity(rack, i, rack.lastData[i], v0, v1)
                MinecraftForge.EVENT_BUS.post(event)

                RenderState.popAttrib()
                GlStateManager.popMatrix()
            }
        }

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }
}
