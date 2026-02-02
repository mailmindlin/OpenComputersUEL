package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import net.minecraft.client.renderer.block.model.ModelBakery
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Terminal(parent: Delegator) : AbstractDelegate(parent), CustomModel {
    override val maxStackSize: Int = 1

    fun hasServer(stack: ItemStack): Boolean = stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "server")

    @SideOnly(Side.CLIENT)
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super.tooltipLines(stack, world, tooltip, flag)
        if (hasServer(stack)) {
            val server = stack.tagCompound!!.getString(Settings.namespace + "server")
            tooltip.add("\u00a78${server.substring(0, 13)}...\u00a77")
        }
    }

    @SideOnly(Side.CLIENT)
    private fun modelLocationFromState(running: Boolean): ModelResourceLocation =
        ModelResourceLocation(Settings.resourceDomain + ":" + Constants.ItemName.Terminal + (if (running) "_on" else "_off"), "inventory")

    @SideOnly(Side.CLIENT)
    override fun getModelLocation(stack: ItemStack): ModelResourceLocation =
        modelLocationFromState(hasServer(stack))

    @SideOnly(Side.CLIENT)
    override fun registerModelLocations() {
        for (state in listOf(true, false)) {
            val location = modelLocationFromState(state)
            ModelBakery.registerItemVariants(parent, ResourceLocation(location.namespace + ":" + location.path))
        }
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (!player.isSneaking && stack.hasTagCompound()) {
            val key = stack.tagCompound!!.getString(Settings.namespace + "key")
            val server = stack.tagCompound!!.getString(Settings.namespace + "server")
            if (!key.isNullOrEmpty() && !server.isNullOrEmpty()) {
                if (world.isRemote) {
                    player.openGui(OpenComputers.INSTANCE, GuiType.Terminal.id, world, 0, 0, 0)
                }
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
        return super.onItemRightClick(stack, world, player)
    }
}
