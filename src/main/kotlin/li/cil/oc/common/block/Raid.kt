package li.cil.oc.common.block

import li.cil.oc.client.KeyBindings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.item.data.RaidData
import li.cil.oc.common.tileentity
import net.minecraft.block.Block
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.reflect.KClass

class Raid(protected val tileTag: KClass<tileentity.Raid> = tileentity.Raid::class) : SimpleBlock(), traits.GUI, traits.CustomDrops<tileentity.Raid> {
    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Facing)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(PropertyRotatable.Facing).horizontalIndex

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, advanced: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, advanced)
        if (KeyBindings.showExtendedTooltips) {
            val data = RaidData(stack)
            for (disk in data.disks) {
                if (!disk.isEmpty) {
                    tooltip.add("- " + disk.displayName)
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override val guiType = GuiType.Raid

    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.Raid()

    // ----------------------------------------------------------------------- //

    override fun hasComparatorInputOverride(state: IBlockState): Boolean = true

    override fun getComparatorInputOverride(state: IBlockState, world: World, pos: BlockPos): Int {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is tileentity.Raid && tileEntity.presence.all { it }) 15 else 0
    }

    override fun doCustomInit(tileEntity: tileentity.Raid, player: EntityLivingBase, stack: ItemStack) {
        super.doCustomInit(tileEntity, player, stack)
        if (!tileEntity.world.isRemote) {
            val data = RaidData(stack)
            for (i in 0 until minOf(data.disks.size, tileEntity.sizeInventory)) {
                tileEntity.setInventorySlotContents(i, data.disks[i])
            }
            data.label?.let { tileEntity.label.setLabel(it) }
            if (!data.filesystem.isEmpty) {
                tileEntity.tryCreateRaid(data.filesystem.getCompoundTag("node").getString("address"))
                tileEntity.filesystem?.load(data.filesystem)
            }
        }
    }

    override fun doCustomDrops(tileEntity: tileentity.Raid, player: EntityPlayer, willHarvest: Boolean) {
        super.doCustomDrops(tileEntity, player, willHarvest)
        val stack = createItemStack()
        if (tileEntity.items.any { !it.isEmpty }) {
            val data = RaidData()
            data.disks = tileEntity.items.clone()
            tileEntity.filesystem?.save(data.filesystem)
            data.label = tileEntity.label.getLabel()
            data.save(stack)
        }
        Block.spawnAsEntity(tileEntity.world, tileEntity.pos, stack)
    }

    override val tileEntityClass: Class<tileentity.Raid> get() = tileentity.Raid::class.java
}
