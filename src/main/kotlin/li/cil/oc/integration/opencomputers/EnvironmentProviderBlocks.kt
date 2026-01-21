package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.Environment
import li.cil.oc.common
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.component
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
        isOneOf(item.block, Constants.BlockName.Assembler) -> tileentity.Assembler::class.java
        isOneOf(item.block, Constants.BlockName.CaseTier1, Constants.BlockName.CaseTier2, Constants.BlockName.CaseTier3, Constants.BlockName.CaseCreative, Constants.BlockName.Microcontroller) -> Machine::class.java
        isOneOf(item.block, Constants.BlockName.HologramTier1, Constants.BlockName.HologramTier2) -> tileentity.Hologram::class.java
        isOneOf(item.block, Constants.BlockName.Printer) -> tileentity.Printer::class.java
        isOneOf(item.block, Constants.BlockName.Relay) -> tileentity.Relay::class.java
        isOneOf(item.block, Constants.BlockName.Redstone) -> if (BundledRedstone.isAvailable) component.Redstone.Bundled::class.java else component.Redstone.Vanilla::class.java
        isOneOf(item.block, Constants.BlockName.ScreenTier1) -> common.component.TextBuffer::class.java as Class<out Environment>
        isOneOf(item.block, Constants.BlockName.ScreenTier2, Constants.BlockName.ScreenTier3) -> common.component.Screen::class.java
        isOneOf(item.block, Constants.BlockName.Robot) -> component.Robot::class.java as Class<out Environment>
        isOneOf(item.block, Constants.BlockName.Waypoint) -> tileentity.Waypoint::class.java as Class<out Environment>
        else -> null
      }
    } else null
    else -> {
      if (api.Items.get(stack) == api.Items.get(Constants.ItemName.Drone)) component.Drone::class.java as Class<out Environment>
      else null
    }
  }

  private fun isOneOf(block: Block, vararg names: String) = names.any { api.Items.get(it).block() == block }
}
