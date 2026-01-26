package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.Environment
import li.cil.oc.common.component.Screen as ComponentScreen
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.tileentity.Assembler
import li.cil.oc.common.tileentity.Hologram
import li.cil.oc.common.tileentity.Printer
import li.cil.oc.common.tileentity.Relay
import li.cil.oc.common.tileentity.Waypoint
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.component.Drone as ComponentDrone
import li.cil.oc.server.component.Redstone as ComponentRedstone
import li.cil.oc.server.component.Robot as ComponentRobot
import li.cil.oc.server.machine.Machine
import net.minecraft.block.Block
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack

/**
 * Provide static environment lookup for blocks that are components.
 * This allows showing their documentation in NEI, for example. Not
 * all blocks are present here, because some also serve as upgrades
 * and therefore have item drivers.
 */
object EnvironmentProviderBlocks : EnvironmentProvider {
  override fun getEnvironment(stack: ItemStack): Class<*>? = when (val item = stack.item) {
    is ItemBlock -> if (item.block != null) {
      when {
        isOneOf(item.block, Constants.BlockName.Assembler) -> Assembler::class.java
        isOneOf(item.block, Constants.BlockName.CaseTier1, Constants.BlockName.CaseTier2, Constants.BlockName.CaseTier3, Constants.BlockName.CaseCreative, Constants.BlockName.Microcontroller) -> Machine::class.java
        isOneOf(item.block, Constants.BlockName.HologramTier1, Constants.BlockName.HologramTier2) -> Hologram::class.java
        isOneOf(item.block, Constants.BlockName.Printer) -> Printer::class.java
        isOneOf(item.block, Constants.BlockName.Relay) -> Relay::class.java
        isOneOf(item.block, Constants.BlockName.Redstone) -> if (BundledRedstone.isAvailable) ComponentRedstone.Bundled::class.java else ComponentRedstone.Vanilla::class.java
        isOneOf(item.block, Constants.BlockName.ScreenTier1) -> TextBuffer::class.java as Class<out Environment>
        isOneOf(item.block, Constants.BlockName.ScreenTier2, Constants.BlockName.ScreenTier3) -> ComponentScreen::class.java
        isOneOf(item.block, Constants.BlockName.Robot) -> ComponentRobot::class.java as Class<out Environment>
        isOneOf(item.block, Constants.BlockName.Waypoint) -> Waypoint::class.java as Class<out Environment>
        else -> null
      }
    } else null
    else -> {
      if (ApiItems.get(stack) == Constants.ItemInfo.Drone) ComponentDrone::class.java as Class<out Environment>
      else null
    }
  }

  private fun isOneOf(block: Block, name: String) = ApiItems.get(name)?.block() == block
  private fun isOneOf(block: Block, vararg names: String) = names.any { ApiItems.get(it)?.block() == block }
}
