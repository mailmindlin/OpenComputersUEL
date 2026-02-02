package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.traits.GUI
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import li.cil.oc.common.tileentity.Relay as TERelay
import net.minecraft.world.World
import li.cil.oc.common.block.traits.GUI as TraitGUI
import li.cil.oc.common.block.traits.PowerAcceptor as TraitPowerAcceptor

class Relay : SimpleBlock(), TraitGUI, TraitPowerAcceptor {
    override val guiType = GuiType.Relay

    override fun localOnBlockActivated(
        world: World, pos: BlockPos,
        player: EntityPlayer, hand: EnumHand, heldItem: ItemStack,
        side: EnumFacing,
        hitX: Float, hitY: Float, hitZ: Float)
            : Boolean = super<TraitGUI>.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)

    override val energyThroughput: Double get() = Settings.get.accessPointRate

    override fun createNewTileEntity(world: World, metadata: Int) = TERelay()
}
