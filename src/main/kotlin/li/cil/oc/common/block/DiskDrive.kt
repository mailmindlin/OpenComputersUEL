package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.tileentity.DiskDrive as TEDiskDrive
import li.cil.oc.integration.Mods
import li.cil.oc.util.Tooltip
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DiskDrive : SimpleBlock(), traits.GUI {
    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Facing)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(PropertyRotatable.Facing).horizontalIndex

    // ----------------------------------------------------------------------- //

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, flag: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, flag)
        if (Mods.ComputerCraft.isModAvailable) {
            tooltip.addAll(Tooltip.get(javaClass.simpleName + ".CC"))
        }
    }

    // ----------------------------------------------------------------------- //

    override val guiType = GuiType.DiskDrive

    override fun createNewTileEntity(world: World, metadata: Int) = TEDiskDrive()

    // ----------------------------------------------------------------------- //

    override fun hasComparatorInputOverride(state: IBlockState): Boolean = true

    override fun getComparatorInputOverride(state: IBlockState, world: World, pos: BlockPos): Int {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEDiskDrive && !tileEntity.getStackInSlot(0).isEmpty) 15 else 0
    }

    // ----------------------------------------------------------------------- //

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        // Behavior: sneaking -> Insert[+Eject], not sneaking -> GUI.
        if (player.isSneaking) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is TEDiskDrive) {
                val isDiskInDrive = tileEntity.getStackInSlot(0) != null
                val isHoldingDisk = tileEntity.isItemValidForSlot(0, heldItem)
                if (isDiskInDrive) {
                    if (!world.isRemote) {
                        tileEntity.dropSlot(0, 1, tileEntity.facing)
                    }
                }
                if (isHoldingDisk) {
                    // Insert the disk.
                    tileEntity.setInventorySlotContents(0, heldItem.copy().splitStack(1))
                    if (hand == EnumHand.MAIN_HAND)
                        player.inventory.decrStackSize(player.inventory.currentItem, 1)
                    else
                        player.inventory.offHandInventory[0].shrink(1)
                }
                return isDiskInDrive || isHoldingDisk
            }
            return false
        }
        return super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
    }
}
