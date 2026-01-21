package li.cil.oc.client.renderer.item

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent.MountPoint
import li.cil.oc.client.Textures
import li.cil.oc.integration.opencomputers.Item
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.item.ItemStack
import net.minecraft.util.math.AxisAlignedBB
import org.lwjgl.opengl.GL11

object UpgradeRenderer {
    val craftingUpgrade by lazy { ApiItems.get(Constants.ItemName.CraftingUpgrade) }
    val generatorUpgrade by lazy { ApiItems.get(Constants.ItemName.GeneratorUpgrade) }
    val inventoryUpgrade by lazy { ApiItems.get(Constants.ItemName.InventoryUpgrade) }

    fun preferredMountPoint(stack: ItemStack, availableMountPoints: Set<String>): String {
        val descriptor = ApiItems.get(stack)

        return if (descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade) {
            if (descriptor == generatorUpgrade && availableMountPoints.contains(MountPointName.BottomBack)) MountPointName.BottomBack
            else if (descriptor == inventoryUpgrade && availableMountPoints.contains(MountPointName.TopBack)) MountPointName.TopBack
            else MountPointName.Any
        } else MountPointName.None
    }

    fun canRender(stack: ItemStack): Boolean {
        val descriptor = ApiItems.get(stack)
        return descriptor == craftingUpgrade || descriptor == generatorUpgrade || descriptor == inventoryUpgrade
    }

    fun render(stack: ItemStack, mountPoint: MountPoint) {
        val descriptor = ApiItems.get(stack)

        if (descriptor == ApiItems.get(Constants.ItemName.CraftingUpgrade)) {
            Textures.bind(Textures.Model.UpgradeCrafting)
            drawSimpleBlock(mountPoint)

            RenderState.checkError(javaClass.name + ".renderItem: crafting upgrade")
        } else if (descriptor == ApiItems.get(Constants.ItemName.GeneratorUpgrade)) {
            Textures.bind(Textures.Model.UpgradeGenerator)
            drawSimpleBlock(mountPoint, if (Item.dataTag(stack).getInteger("remainingTicks") > 0) 0.5f else 0f)

            RenderState.checkError(javaClass.name + ".renderItem: generator upgrade")
        } else if (descriptor == ApiItems.get(Constants.ItemName.InventoryUpgrade)) {
            Textures.bind(Textures.Model.UpgradeInventory)
            drawSimpleBlock(mountPoint)

            RenderState.checkError(javaClass.name + ".renderItem: inventory upgrade")
        }
    }

    private val bounds = AxisAlignedBB(-0.1, -0.1, -0.1, 0.1, 0.1, 0.1)

    private fun drawSimpleBlock(mountPoint: MountPoint, frontOffset: Float = 0f) {
        GlStateManager.rotate(mountPoint.rotation.w, mountPoint.rotation.x, mountPoint.rotation.y, mountPoint.rotation.z)
        GlStateManager.translate(mountPoint.offset.x, mountPoint.offset.y, mountPoint.offset.z)

        val t = Tessellator.getInstance()
        val r = t.buffer
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL)

        // Front.
        r.pos(bounds.minX, bounds.minY, bounds.maxZ).tex(frontOffset.toDouble(), 0.5).normal(0f, 0f, 1f).endVertex()
        r.pos(bounds.maxX, bounds.minY, bounds.maxZ).tex((frontOffset + 0.5f).toDouble(), 0.5).normal(0f, 0f, 1f).endVertex()
        r.pos(bounds.maxX, bounds.maxY, bounds.maxZ).tex((frontOffset + 0.5f).toDouble(), 0.0).normal(0f, 0f, 1f).endVertex()
        r.pos(bounds.minX, bounds.maxY, bounds.maxZ).tex(frontOffset.toDouble(), 0.0).normal(0f, 0f, 1f).endVertex()

        // Top.
        r.pos(bounds.maxX, bounds.maxY, bounds.maxZ).tex(1.0, 0.5).normal(0f, 1f, 0f).endVertex()
        r.pos(bounds.maxX, bounds.maxY, bounds.minZ).tex(1.0, 1.0).normal(0f, 1f, 0f).endVertex()
        r.pos(bounds.minX, bounds.maxY, bounds.minZ).tex(0.5, 1.0).normal(0f, 1f, 0f).endVertex()
        r.pos(bounds.minX, bounds.maxY, bounds.maxZ).tex(0.5, 0.5).normal(0f, 1f, 0f).endVertex()

        // Bottom.
        r.pos(bounds.minX, bounds.minY, bounds.maxZ).tex(0.5, 0.5).normal(0f, -1f, 0f).endVertex()
        r.pos(bounds.minX, bounds.minY, bounds.minZ).tex(0.5, 1.0).normal(0f, -1f, 0f).endVertex()
        r.pos(bounds.maxX, bounds.minY, bounds.minZ).tex(1.0, 1.0).normal(0f, -1f, 0f).endVertex()
        r.pos(bounds.maxX, bounds.minY, bounds.maxZ).tex(1.0, 0.5).normal(0f, -1f, 0f).endVertex()

        // Left.
        r.pos(bounds.maxX, bounds.maxY, bounds.maxZ).tex(0.0, 0.5).normal(1f, 0f, 0f).endVertex()
        r.pos(bounds.maxX, bounds.minY, bounds.maxZ).tex(0.0, 1.0).normal(1f, 0f, 0f).endVertex()
        r.pos(bounds.maxX, bounds.minY, bounds.minZ).tex(0.5, 1.0).normal(1f, 0f, 0f).endVertex()
        r.pos(bounds.maxX, bounds.maxY, bounds.minZ).tex(0.5, 0.5).normal(1f, 0f, 0f).endVertex()

        // Right.
        r.pos(bounds.minX, bounds.minY, bounds.maxZ).tex(0.0, 1.0).normal(-1f, 0f, 0f).endVertex()
        r.pos(bounds.minX, bounds.maxY, bounds.maxZ).tex(0.0, 0.5).normal(-1f, 0f, 0f).endVertex()
        r.pos(bounds.minX, bounds.maxY, bounds.minZ).tex(0.5, 0.5).normal(-1f, 0f, 0f).endVertex()
        r.pos(bounds.minX, bounds.minY, bounds.minZ).tex(0.5, 1.0).normal(-1f, 0f, 0f).endVertex()

        t.draw()
    }
}
