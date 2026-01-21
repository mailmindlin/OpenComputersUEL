package li.cil.oc.common.item

import li.cil.oc.api.internal.Wrench as ApiWrench
import li.cil.oc.common.asm.Injectable
import li.cil.oc.common.item.traits.SimpleItem
import li.cil.oc.integration.Mods
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

@Injectable.InterfaceList(
    Injectable.Interface(value = "ic2.api.item.IBoxable", modid = Mods.IDs.IndustrialCraft2)
)
class Wrench : SimpleItem(), ApiWrench {
    init {
        setHarvestLevel("wrench", 1)
        maxStackSize = 1
    }

    override fun doesSneakBypassUse(stack: ItemStack, world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean = true

    override fun onItemUseFirst(player: EntityPlayer, world: World, pos: BlockPos, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, hand: EnumHand): EnumActionResult {
        if (world.isBlockLoaded(pos) && world.isBlockModifiable(player, pos)) {
            val block = world.getBlockState(pos).block
            if (block.rotateBlock(world, pos, side)) {
                block.neighborChanged(world.getBlockState(pos), world, pos, Blocks.AIR, pos)
                player.swingArm(hand)
                return if (!world.isRemote) EnumActionResult.SUCCESS else EnumActionResult.PASS
            }
        }
        return super.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand)
    }

    override fun useWrenchOnBlock(player: EntityPlayer, world: World, pos: BlockPos, simulate: Boolean): Boolean {
        if (!simulate) player.swingArm(EnumHand.MAIN_HAND)
        return true
    }

    // IndustrialCraft 2
    fun canBeStoredInToolbox(stack: ItemStack): Boolean = true
}
