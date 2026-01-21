package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.text.ITextComponent
import org.apache.commons.lang3.tuple.Pair as ApachePair

abstract class Template {
    protected open val suggestedComponents: Array<kotlin.Pair<String, (IInventory) -> Boolean>> = arrayOf(
        "BIOS" to { inv -> hasComponent(Constants.ItemName.EEPROM)(inv) },
        "Screen" to { inv -> hasComponent(Constants.BlockName.ScreenTier1)(inv) },
        "Keyboard" to { inv -> hasComponent(Constants.BlockName.Keyboard)(inv) },
        "GraphicsCard" to { inventory ->
            arrayOf(
                Constants.ItemName.APUCreative,
                Constants.ItemName.APUTier1,
                Constants.ItemName.APUTier2,
                Constants.ItemName.GraphicsCardTier1,
                Constants.ItemName.GraphicsCardTier2,
                Constants.ItemName.GraphicsCardTier3
            ).any { name -> hasComponent(name)(inventory) }
        },
        "Inventory" to { inv -> hasInventory(inv) },
        "OS" to { inv -> hasFileSystem(inv) }
    )

    protected abstract val hostClass: Class<out api.network.EnvironmentHost>

    protected fun validateComputer(inventory: IInventory): Array<Any> {
        val hasCase = caseTier(inventory) != Tier.None
        val hasCPU = this.hasCPU(inventory)
        val hasRAM = this.hasRAM(inventory)
        val requiresRAM = this.requiresRAM(inventory)
        val complexity = this.complexity(inventory)
        val maxComplexity = this.maxComplexity(inventory)

        val valid = hasCase && hasCPU && (hasRAM || !requiresRAM) && complexity <= maxComplexity

        val progress: ITextComponent = when {
            !hasCPU -> Localization.Assembler.InsertCPU()
            !hasRAM && requiresRAM -> Localization.Assembler.InsertRAM()
            else -> Localization.Assembler.Complexity(complexity, maxComplexity)
        }

        val warnings = mutableListOf<ITextComponent>()
        for ((name, check) in suggestedComponents) {
            if (!check(inventory)) {
                warnings.add(Localization.Assembler.Warning(name))
            }
        }
        if (warnings.isNotEmpty()) {
            warnings.add(0, Localization.Assembler.Warnings())
        }

        return arrayOf(valid as java.lang.Boolean, progress, warnings.toTypedArray())
    }

    protected fun exists(inventory: IInventory, p: (ItemStack) -> Boolean): Boolean {
        return (0 until inventory.sizeInventory).any { slot ->
            val stack = inventory.getStackInSlot(slot)
            !stack.isEmpty && p(stack)
        }
    }

    protected fun hasCPU(inventory: IInventory): Boolean = exists(inventory) { stack ->
        api.Driver.driverFor(stack, hostClass) is api.driver.item.Processor
    }

    protected fun hasRAM(inventory: IInventory): Boolean = exists(inventory) { stack ->
        api.Driver.driverFor(stack, hostClass) is api.driver.item.Memory
    }

    protected fun requiresRAM(inventory: IInventory): Boolean {
        return !(0 until inventory.sizeInventory).map { inventory.getStackInSlot(it) }.any { stack ->
            val driver = api.Driver.driverFor(stack, hostClass)
            if (driver is api.driver.item.Processor) {
                val architecture = driver.architecture(stack)
                architecture != null && architecture.getAnnotation(api.machine.Architecture.NoMemoryRequirements::class.java) != null
            } else false
        }
    }

    protected fun hasComponent(name: String): (IInventory) -> Boolean = { inventory ->
        exists(inventory) { stack ->
            api.Items.get(stack)?.name() == name
        }
    }

    protected fun hasInventory(inventory: IInventory): Boolean = exists(inventory) { stack ->
        api.Driver.driverFor(stack, hostClass) is api.driver.item.Inventory
    }

    protected fun hasFileSystem(inventory: IInventory): Boolean = exists(inventory) { stack ->
        val driver = api.Driver.driverFor(stack, hostClass)
        driver != null && (driver.slot(stack) == Slot.Floppy || driver.slot(stack) == Slot.HDD)
    }

    protected fun complexity(inventory: IInventory): Int {
        var acc = 0
        for (slot in 1 until inventory.sizeInventory) {
            val stack = inventory.getStackInSlot(slot)
            val driver = api.Driver.driverFor(stack, hostClass)
            acc += when {
                driver is api.driver.item.Processor -> 0 // CPUs are exempt, since they control the limit.
                driver is api.driver.item.Container -> (1 + driver.tier(stack)) * 2
                driver != null && driver.slot(stack) != Slot.EEPROM -> 1 + driver.tier(stack)
                else -> 0
            }
        }
        return acc
    }

    protected open fun maxComplexity(inventory: IInventory): Int {
        val caseTier = this.caseTier(inventory)
        val cpuTier = (0 until inventory.sizeInventory).fold(0) { acc, slot ->
            val stack = inventory.getStackInSlot(slot)
            val driver = api.Driver.driverFor(stack, hostClass)
            acc + if (driver is api.driver.item.Processor) driver.tier(stack) else 0
        }
        return if (caseTier >= Tier.One && cpuTier >= Tier.One) {
            Settings.deviceComplexityByTier(caseTier) - (minOf(2, caseTier) - cpuTier) * 6
        } else 0
    }

    protected abstract fun caseTier(inventory: IInventory): Int

    protected fun toPair(t: kotlin.Pair<String, Int>?): ApachePair<String, Int>? =
        t?.let { ApachePair.of(it.first, it.second) }
}
