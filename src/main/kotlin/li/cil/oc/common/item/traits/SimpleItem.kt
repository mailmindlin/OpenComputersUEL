package li.cil.oc.common.item.traits

import li.cil.oc.CreativeTab
import li.cil.oc.Settings
import li.cil.oc.common.tileentity
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class SimpleItem : Item() {
    init {
        creativeTab = CreativeTab
    }

    fun createItemStack(amount: Int = 1): ItemStack = ItemStack(this, amount)

    override fun isBookEnchantable(stack: ItemStack, book: ItemStack): Boolean = false

    override fun doesSneakBypassUse(stack: ItemStack, world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean {
        val te = world.getTileEntity(pos)
        return if (te is tileentity.DiskDrive) {
            true
        } else {
            super.doesSneakBypassUse(stack, world, pos, player)
        }
    }

    @SideOnly(Side.CLIENT)
    override fun addInformation(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase()))

        if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "data")) {
            val data = stack.tagCompound!!.getCompoundTag(Settings.namespace + "data")
            if (data.hasKey("node") && data.getCompoundTag("node").hasKey("address")) {
                tooltip.add("\u00a78${data.getCompoundTag("node").getString("address").substring(0, 13)}...\u00a77")
            }
        }
    }
}
