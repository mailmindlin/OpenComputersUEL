package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.Tier
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.tileentity.Microcontroller as TEMicrocontroller
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.Rarity
import li.cil.oc.util.StackOption
import net.minecraft.block.Block
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.world.World
import kotlin.reflect.KClass
import li.cil.oc.common.block.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.block.traits.StateAware as TraitStateAware
import li.cil.oc.common.block.traits.CustomDrops as TraitCustomDrops

class Microcontroller(protected val tileTag: KClass<TEMicrocontroller> = TEMicrocontroller::class) : RedstoneAware(), TraitPowerAcceptor, TraitStateAware, TraitCustomDrops<TEMicrocontroller> {
    init {
        setCreativeTab(null)
        ItemBlacklist.hide(this)
    }

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Facing)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(PropertyRotatable.Facing).horizontalIndex

    // ----------------------------------------------------------------------- //

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEMicrocontroller) tileEntity.info.copyItemStack() else ItemStack.EMPTY
    }

    // ----------------------------------------------------------------------- //

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, advanced)
        if (KeyBindings.showExtendedTooltips) {
            val info = MicrocontrollerData(stack)
            for (component in info.components) {
                if (!component.isEmpty) {
                    tooltip.add("- " + component.displayName)
                }
            }
        }
    }

    override fun rarity(stack: ItemStack): EnumRarity {
        val data = MicrocontrollerData(stack)
        return Rarity.byTier(data.tier)
    }

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.caseRate[Tier.One]

    override fun createNewTileEntity(world: World, metadata: Int) = TEMicrocontroller()

    // ----------------------------------------------------------------------- //

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (!Wrench.holdsApplicableWrench(player, pos)) {
            if (!player.isSneaking) {
                if (!world.isRemote) {
                    val tileEntity = world.getTileEntity(pos)
                    if (tileEntity is TEMicrocontroller) {
                        val machine = tileEntity.machine!!
                        if (machine.isRunning) machine.stop()
                        else machine.start()
                    }
                }
                return true
            } else if (Items.get(heldItem) == Items.get(Constants.ItemName.EEPROM)) {
                if (!world.isRemote) {
                    val tileEntity = world.getTileEntity(pos)
                    if (tileEntity is TEMicrocontroller) {
                        val newEeprom = player.inventory.decrStackSize(player.inventory.currentItem, 1)
                        val result = tileEntity.changeEEPROM(newEeprom)
                        if (result != null) {
                            InventoryUtils.addToPlayerInventory(result, player)
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    override fun doCustomInit(tileEntity: TEMicrocontroller, player: EntityLivingBase, stack: ItemStack) {
        super.doCustomInit(tileEntity, player, stack)
        if (!tileEntity.world.isRemote) {
            tileEntity.info.load(stack)
            tileEntity.snooperNode.changeBuffer(tileEntity.info.storedEnergy - tileEntity.snooperNode.localBuffer())
        }
    }

    override fun doCustomDrops(tileEntity: TEMicrocontroller, player: EntityPlayer, willHarvest: Boolean) {
        super.doCustomDrops(tileEntity, player, willHarvest)
        tileEntity.saveComponents()
        tileEntity.info.storedEnergy = tileEntity.snooperNode.localBuffer().toInt()
        Block.spawnAsEntity(tileEntity.world, tileEntity.pos, tileEntity.info.createItemStack())
    }

    override val tileClass: Class<TEMicrocontroller> get() = TEMicrocontroller::class.java
}
