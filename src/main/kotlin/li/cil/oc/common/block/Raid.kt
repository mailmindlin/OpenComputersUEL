package li.cil.oc.common.block

import li.cil.oc.client.KeyBindings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.block.traits.CustomDrops
import li.cil.oc.common.block.traits.GUI
import li.cil.oc.common.item.data.RaidData
import li.cil.oc.common.tileentity.Raid as TERaid
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

class Raid(protected val tileTag: KClass<TERaid> = TERaid::class) : SimpleBlock(), GUI, CustomDrops<TERaid> {
    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Facing)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(PropertyRotatable.Facing).horizontalIndex

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
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

    override val tileClass: Class<TERaid> get() = TERaid::class.java
    override fun createNewTileEntity(world: World, metadata: Int) = TERaid()

    // ----------------------------------------------------------------------- //

    override fun hasComparatorInputOverride(state: IBlockState): Boolean = true

    override fun getComparatorInputOverride(state: IBlockState, world: World, pos: BlockPos): Int {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TERaid && tileEntity.presence.all { it }) 15 else 0
    }

    override fun doCustomInit(tileEntity: TERaid, player: EntityLivingBase, stack: ItemStack) {
        super.doCustomInit(tileEntity, player, stack)
        if (!tileEntity.world.isRemote) {
            val data = RaidData(stack)
            for (i in 0 until minOf(data.disks.size, tileEntity.getSizeInventory())) {
                tileEntity.setInventorySlotContents(i, data.disks[i])
            }
            data.label?.let { tileEntity.label.setLabel(it) }
            if (!data.filesystem.isEmpty) {
                tileEntity.tryCreateRaid(data.filesystem.getCompoundTag("node").getString("address"))
                tileEntity.filesystem?.load(data.filesystem)
            }
        }
    }

    override fun doCustomDrops(tileEntity: TERaid, player: EntityPlayer, willHarvest: Boolean) {
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
}
