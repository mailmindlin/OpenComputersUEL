package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class UpgradeTank(parent: Delegator) : AbstractDelegate(parent), ItemTier {
    @SideOnly(Side.CLIENT)
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        if (stack.hasTagCompound()) {
            val fluidStack = FluidStack.loadFluidStackFromNBT(stack.tagCompound!!.getCompoundTag(Settings.namespace + "data"))
            if (fluidStack != null) {
                tooltip.add("${fluidStack.fluid.getLocalizedName(fluidStack)}: ${fluidStack.amount}/16000")
            }
        }
        super<ItemTier>.tooltipLines(stack, world, tooltip, flag)
    }
}
