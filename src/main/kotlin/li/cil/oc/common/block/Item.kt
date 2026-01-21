package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Item(value: Block) : ItemBlock(value) {
    init {
        setHasSubtypes(true)
    }

    override fun addInformation(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super.addInformation(stack, world, tooltip, flag)
        val block = this.block
        if (block is SimpleBlock && world != null) {
            block.addInformation(getMetadata(stack.itemDamage), stack, world, tooltip, flag)
        }
    }

    override fun getRarity(stack: ItemStack): EnumRarity {
        val block = this.block
        return if (block is SimpleBlock) block.rarity(stack) else EnumRarity.COMMON
    }

    override fun getMetadata(itemDamage: Int): Int = itemDamage

    override fun getItemStackDisplayName(stack: ItemStack): String {
        if (api.Items.get(stack) == api.Items.get(Constants.BlockName.Print)) {
            val data = PrintData(stack)
            return data.label ?: super.getItemStackDisplayName(stack)
        }
        return super.getItemStackDisplayName(stack)
    }

    override fun getTranslationKey(): String {
        val block = this.block
        return if (block is SimpleBlock) block.translationKey else Settings.namespace + "tile"
    }

    override fun isBookEnchantable(a: ItemStack, b: ItemStack): Boolean = false

    override fun placeBlockAt(stack: ItemStack, player: EntityPlayer, world: World, pos: BlockPos, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, newState: IBlockState): Boolean {
        // When placing robots in creative mode, we have to copy the stack
        // manually before it's placed to ensure different component addresses
        // in the different robots, to avoid interference of screens e.g.
        val needsCopying = player.capabilities.isCreativeMode && api.Items.get(stack) == api.Items.get(Constants.BlockName.Robot)
        val stackToUse = if (needsCopying) RobotData(stack).copyItemStack() else stack
        if (super.placeBlockAt(stackToUse, player, world, pos, side, hitX, hitY, hitZ, newState)) {
            // If it's a rotatable block try to make it face the player.
            val tileEntity = world.getTileEntity(pos)
            when (tileEntity) {
                is tileentity.Keyboard -> {
                    tileEntity.setFromEntityPitchAndYaw(player)
                    tileEntity.setFromFacing(side)
                }
                is tileentity.traits.Rotatable -> {
                    tileEntity.setFromEntityPitchAndYaw(player)
                    if (!tileEntity.validFacings.contains(tileEntity.pitch)) {
                        tileEntity.pitch = tileEntity.validFacings.firstOrNull() ?: EnumFacing.NORTH
                    }
                    if (tileEntity !is tileentity.RobotProxy) {
                        tileEntity.invertRotation()
                    }
                }
            }
            return true
        }
        return false
    }
}
