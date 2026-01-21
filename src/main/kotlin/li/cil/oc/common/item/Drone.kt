package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.client.KeyBindings
import li.cil.oc.client.renderer.block.DroneModel
import li.cil.oc.common.entity.Drone as EntityDrone
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Rarity
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.client.event.ModelBakeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Drone(parent: Delegator) : AbstractDelegate(parent), CustomModel {
    init {
        ItemBlacklist.hide(this)
        showInItemList = false
    }

    @SideOnly(Side.CLIENT)
    override fun getModelLocation(stack: ItemStack): ModelResourceLocation =
        ModelResourceLocation(Settings.resourceDomain + ":" + Constants.ItemName.Drone, "inventory")

    @SideOnly(Side.CLIENT)
    override fun bakeModels(bakeEvent: ModelBakeEvent) {
        bakeEvent.modelRegistry.putObject(getModelLocation(createItemStack()), DroneModel)
    }

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        if (KeyBindings.showExtendedTooltips) {
            val info = DroneData(stack)
            for (component in info.components) {
                if (!component.isEmpty) {
                    tooltip.add("- " + component.displayName)
                }
            }
        }
    }

    override fun rarity(stack: ItemStack) = Rarity.byTier(DroneData(stack).tier)

    override fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val world = position.world ?: return false
        if (!world.isRemote) {
            val drone = EntityDrone(world)
            when (player) {
                is agent.Player -> {
                    drone.ownerName = player.agent.ownerName()
                    drone.ownerUUID = player.agent.ownerUUID()
                }
                else -> {
                    drone.ownerName = player.name
                    drone.ownerUUID = player.gameProfile.id
                }
            }
            drone.initializeAfterPlacement(stack, player, position.offset(hitX * 1.1f, hitY * 1.1f, hitZ * 1.1f))
            world.spawnEntity(drone)
        }
        stack.shrink(1)
        return true
    }
}
