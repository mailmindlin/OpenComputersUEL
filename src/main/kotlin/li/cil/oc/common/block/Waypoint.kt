package li.cil.oc.common.block

import li.cil.oc.OpenComputers
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.tileentity.Screen as TEScreen
import li.cil.oc.common.tileentity.Waypoint as TEWaypoint
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.property.ExtendedBlockState
import net.minecraftforge.common.property.IExtendedBlockState

class Waypoint : RedstoneAware() {
    override fun createBlockState() = ExtendedBlockState(this, arrayOf(PropertyRotatable.Pitch, PropertyRotatable.Yaw), arrayOf(PropertyTile))

    override fun getMetaFromState(state: IBlockState): Int = (state.getValue(PropertyRotatable.Pitch).ordinal shl 2) or state.getValue(PropertyRotatable.Yaw).horizontalIndex

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Pitch, EnumFacing.byIndex(meta shr 2)).withProperty(PropertyRotatable.Yaw, EnumFacing.byHorizontalIndex(meta and 0x3))

    override fun getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = world.getTileEntity(pos)
        return if (state is IExtendedBlockState && tileEntity is TEScreen) {
            state
                .withProperty(PropertyTile, tileEntity)
                .withProperty(PropertyRotatable.Pitch, tileEntity.pitch)
                .withProperty(PropertyRotatable.Yaw, tileEntity.yaw)
        } else state
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = TEWaypoint()

    // ----------------------------------------------------------------------- //

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (!player.isSneaking) {
            if (world.isRemote) {
                player.openGui(OpenComputers, GuiType.Waypoint.id, world, pos.x, pos.y, pos.z)
            }
            return true
        }
        return super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
    }

    override fun getValidRotations(world: World, pos: BlockPos): Array<EnumFacing> {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEWaypoint) {
            val facing = tileEntity.facing() ?: return super.getValidRotations(world, pos)
            EnumFacing.values().filter { d -> d != facing && d != facing.opposite }.toTypedArray()
        } else super.getValidRotations(world, pos)
    }
}
