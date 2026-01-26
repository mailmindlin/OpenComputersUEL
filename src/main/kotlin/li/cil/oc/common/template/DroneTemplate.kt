package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.IMC
import li.cil.oc.api.Items
import li.cil.oc.api.internal.Drone
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.util.ItemUtils
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

object DroneTemplate : Template() {
    override val suggestedComponents = arrayOf(
        "BIOS" to { inv: IInventory -> hasComponent(Constants.ItemName.EEPROM)(inv) }
    )

    override val hostClass: Class<Drone> = Drone::class.java

    @JvmStatic
    fun selectTier1(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.DroneCaseTier1

    @JvmStatic
    fun selectTier2(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.DroneCaseTier2

    @JvmStatic
    fun selectTierCreative(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.DroneCaseCreative

    @JvmStatic
    fun validate(inventory: IInventory): Array<Any> = validateComputer(inventory)

    @JvmStatic
    fun assemble(inventory: IInventory): Array<Any> {
        val items = (0 until inventory.sizeInventory).map { inventory.getStackInSlot(it) }
        val data = DroneData()
        data.tier = caseTier(inventory)
        data.name = RobotData.randomName
        data.components = items.drop(1).filter { !it.isEmpty }.toTypedArray()
        data.storedEnergy = Settings.get.bufferDrone.toInt()
        val stack = Constants.ItemInfo.Drone!!.createItemStack(1)
        data.save(stack)
        val energy = Settings.get.droneBaseCost + complexity(inventory) * Settings.get.droneComplexityCost

        return arrayOf(stack, energy)
    }

    @JvmStatic
    @Suppress("unused") // Used via reflection
    fun selectDisassembler(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.Drone

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<ItemStack> {
        val info = MicrocontrollerData(stack)
        val itemInfo = Constants.ItemInfo.DroneCase(info.tier)

        return arrayOf(itemInfo.createItemStack(1)) + info.components
    }

    @JvmStatic
    fun register() {
        // Tier 1
        IMC.registerAssemblerTemplate(
            "Drone (Tier 1)",
            "li.cil.oc.common.template.DroneTemplate.selectTier1",
            "li.cil.oc.common.template.DroneTemplate.validate",
            "li.cil.oc.common.template.DroneTemplate.assemble",
            hostClass,
            null,
            intArrayOf(Tier.Two, Tier.One),
            listOf(
                Slot.Card to Tier.Two,
                Slot.Card to Tier.One,
                null,
                Slot.CPU to Tier.One,
                Slot.Memory to Tier.One,
                null,
                Slot.EEPROM to Tier.Any
            ).map { toPair(it) }
        )

        // Tier 2
        IMC.registerAssemblerTemplate(
            "Drone (Tier 2)",
            "li.cil.oc.common.template.DroneTemplate.selectTier2",
            "li.cil.oc.common.template.DroneTemplate.validate",
            "li.cil.oc.common.template.DroneTemplate.assemble",
            hostClass,
            null,
            intArrayOf(Tier.Three, Tier.Two, Tier.One),
            listOf(
                Slot.Card to Tier.Two,
                Slot.Card to Tier.Two,
                null,
                Slot.CPU to Tier.One,
                Slot.Memory to Tier.One,
                Slot.Memory to Tier.One,
                Slot.EEPROM to Tier.Any
            ).map { toPair(it) }
        )

        // Creative
        IMC.registerAssemblerTemplate(
            "Drone (Creative)",
            "li.cil.oc.common.template.DroneTemplate.selectTierCreative",
            "li.cil.oc.common.template.DroneTemplate.validate",
            "li.cil.oc.common.template.DroneTemplate.assemble",
            hostClass,
            null,
            intArrayOf(Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three),
            listOf(
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Three,
                Slot.CPU to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.EEPROM to Tier.Any
            ).map { toPair(it) }
        )

        // Disassembler
        IMC.registerDisassemblerTemplate(
            "Drone",
            "li.cil.oc.common.template.DroneTemplate.selectDisassembler",
            "li.cil.oc.common.template.DroneTemplate.disassemble"
        )
    }

    override fun maxComplexity(inventory: IInventory): Int {
        return when (caseTier(inventory)) {
            Tier.Two -> 8
            Tier.Four -> 9001 // Creative
            else -> 5
        }
    }

    override fun caseTier(inventory: IInventory): Int = ItemUtils.caseTier(inventory.getStackInSlot(0))
}
