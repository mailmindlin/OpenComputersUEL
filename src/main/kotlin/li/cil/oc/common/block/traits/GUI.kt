package li.cil.oc.common.block.traits

import li.cil.oc.OpenComputers
import li.cil.oc.common.GuiType
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

interface GUI {
    val guiType: GuiType

    fun guiLocalOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (!player.isSneaking) {
            if (!world.isRemote) {
                player.openGui(OpenComputers, guiType.id, world, pos.x, pos.y, pos.z)
            }
            return true
        }
        return false
    }
}
