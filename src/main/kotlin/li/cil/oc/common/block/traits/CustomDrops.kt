package li.cil.oc.common.block.traits

import li.cil.oc.common.block.SimpleBlock
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

interface CustomDrops<Tile : TileEntity> {
    val tileClass: Class<Tile>

    fun customDropsGetDrops(world: IBlockAccess, pos: BlockPos, state: IBlockState, fortune: Int): List<ItemStack> = emptyList()

    fun customDropsBreakBlock(world: World, pos: BlockPos, state: IBlockState) {}

    fun customDropsRemovedByPlayer(state: IBlockState, world: World, pos: BlockPos, player: EntityPlayer, willHarvest: Boolean): Boolean {
        if (!world.isRemote) {
            val tileEntity = world.getTileEntity(pos)
            if (tileClass.isInstance(tileEntity)) {
                @Suppress("UNCHECKED_CAST")
                doCustomDrops(tileEntity as Tile, player, willHarvest)
            }
        }
        return true // Let the caller handle super call
    }

    fun customDropsOnBlockPlacedBy(world: World, pos: BlockPos, state: IBlockState, placer: EntityLivingBase, stack: ItemStack) {
        val tileEntity = world.getTileEntity(pos)
        if (tileClass.isInstance(tileEntity)) {
            @Suppress("UNCHECKED_CAST")
            doCustomInit(tileEntity as Tile, placer, stack)
        }
    }

    fun doCustomInit(tileEntity: Tile, player: EntityLivingBase, stack: ItemStack) {}

    fun doCustomDrops(tileEntity: Tile, player: EntityPlayer, willHarvest: Boolean) {}
}
