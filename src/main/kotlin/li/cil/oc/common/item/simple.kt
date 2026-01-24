package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.GPULike
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Rarity
import net.minecraft.item.ItemStack

class ALU(parent: Delegator) : AbstractDelegate(parent)
class ArrowKeys(parent: Delegator) : AbstractDelegate(parent) {
    override val tooltipName: String? get() = null
}
class ButtonGroup(parent: Delegator) : AbstractDelegate(parent) {
    override val tooltipName: String? get() = null
}
class CardBase(parent: Delegator) : AbstractDelegate(parent)
class CircuitBoard(parent: Delegator) : AbstractDelegate(parent)
class ControlUnit(parent: Delegator) : AbstractDelegate(parent)
class CuttingWire(parent: Delegator) : AbstractDelegate(parent)
class DiamondChip(parent: Delegator) : AbstractDelegate(parent)
class Disk(parent: Delegator) : AbstractDelegate(parent)
class DroneCase(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override fun tierFromDriver(stack: ItemStack): Int = tier
}
class GraphicsCard(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier, GPULike {
    override val gpuTier: Int get() = tier
}
class InkCartridgeEmpty(parent: Delegator) : AbstractDelegate(parent) {
    override val maxStackSize: Int = 1
}
class InternetCard(parent: Delegator) : AbstractDelegate(parent), ItemTier
class Interweb(parent: Delegator) : AbstractDelegate(parent)
abstract class AbstractTieredDelegate(parent: Delegator, internal val tier: Int): AbstractDelegate(parent) {
    override val unlocalizedName: String get() = super.unlocalizedName + tier
    override val tooltipName: String get() = super.unlocalizedName
}
class Memory(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier
class Microchip(parent: Delegator, tier: Int): AbstractTieredDelegate(parent, tier) {
    override fun rarity(stack: ItemStack) = Rarity.byTier(tier)
}
class MicrocontrollerCase(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override fun tierFromDriver(stack: ItemStack): Int = tier
}
class NetworkCard(parent: Delegator) : AbstractDelegate(parent), ItemTier
class NumPad(parent: Delegator) : AbstractDelegate(parent)
class PrintedCircuitBoard(parent: Delegator) : AbstractDelegate(parent)
class RawCircuitBoard(parent: Delegator) : AbstractDelegate(parent)
class TabletCase(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override fun tierFromDriver(stack: ItemStack): Int = tier
}
class TerminalServer(parent: Delegator) : AbstractDelegate(parent) {
    override val tooltipData: Array<Any> get() = arrayOf(Settings.get.terminalsPerServer)
}
class Transistor(parent: Delegator) : AbstractDelegate(parent)
class UpgradeAngel(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeChunkloader(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeContainerCard(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override val tooltipData: Array<Any> get() = arrayOf(tier + 1)
}
class UpgradeContainerUpgrade(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override val tooltipData: Array<Any> get() = arrayOf(tier + 1)
}
class UpgradeCrafting(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeGenerator(parent: Delegator) : AbstractDelegate(parent), ItemTier {
    override val tooltipData: Array<Any> get() = arrayOf((Settings.get.generatorEfficiency * 100).toInt())
}
class UpgradeHover(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override val tooltipData: Array<Any> get() = arrayOf(Settings.get.upgradeFlightHeight[tier])
}
class UpgradeInventory(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeInventoryController(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeLeash(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeNavigation(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradePiston(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeSign(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeSolarGenerator(parent: Delegator) : AbstractDelegate(parent), ItemTier {
    override val tooltipData: Array<Any> get() = arrayOf((Settings.get.solarGeneratorEfficiency * 100).toInt())
}
class UpgradeStickyPiston(parent: Delegator) : AbstractDelegate(parent), ItemTier {
    override val tooltipName: String get() = super<AbstractDelegate>.unlocalizedName
}
class UpgradeTankController(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeTractorBeam(parent: Delegator) : AbstractDelegate(parent), ItemTier
class UpgradeTrading(parent: Delegator) : AbstractDelegate(parent), ItemTier {
    override val tooltipName: String get() = super<AbstractDelegate>.unlocalizedName
}
class WirelessNetworkCard(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier
