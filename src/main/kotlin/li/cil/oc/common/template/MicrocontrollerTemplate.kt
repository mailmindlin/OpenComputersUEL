package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.IMC
import li.cil.oc.api.internal.Microcontroller
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.util.ItemUtils
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

object MicrocontrollerTemplate : Template() {
    override val suggestedComponents = arrayOf(
        "BIOS" to { inv: IInventory -> hasComponent("eeprom")(inv) }
    )

    override val hostClass: Class<Microcontroller> = Microcontroller::class.java

    @JvmStatic
    fun selectTier1(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier1)

    @JvmStatic
    fun selectTier2(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.ItemName.MicrocontrollerCaseTier2)

    @JvmStatic
    fun selectTierCreative(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.ItemName.MicrocontrollerCaseCreative)

    @JvmStatic
    fun validate(inventory: IInventory): Array<Any> = validateComputer(inventory)

    @JvmStatic
    fun assemble(inventory: IInventory): Array<Any> {
        val items = (0 until inventory.sizeInventory).map { inventory.getStackInSlot(it) }
        val data = MicrocontrollerData()
        data.tier = caseTier(inventory)
        data.components = items.drop(1).filter { !it.isEmpty }.toTypedArray()
        data.storedEnergy = Settings.get.bufferMicrocontroller.toInt()
        val stack = data.createItemStack()
        val energy = Settings.get.microcontrollerBaseCost + complexity(inventory) * Settings.get.microcontrollerComplexityCost

        return arrayOf(stack, energy)
    }

    @JvmStatic
    fun selectDisassembler(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.BlockName.Microcontroller)

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<ItemStack> {
        val info = MicrocontrollerData(stack)
        val itemName = Constants.ItemName.MicrocontrollerCase(info.tier)

        return arrayOf(api.Items.get(itemName).createItemStack(1)) + info.components
    }

    @JvmStatic
    fun register() {
        // Tier 1
        IMC.registerAssemblerTemplate(
            "Microcontroller (Tier 1)",
            "li.cil.oc.common.template.MicrocontrollerTemplate.selectTier1",
            "li.cil.oc.common.template.MicrocontrollerTemplate.validate",
            "li.cil.oc.common.template.MicrocontrollerTemplate.assemble",
            hostClass,
            null,
            intArrayOf(Tier.Two),
            listOf(
                Slot.Card to Tier.One,
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
            "Microcontroller (Tier 2)",
            "li.cil.oc.common.template.MicrocontrollerTemplate.selectTier2",
            "li.cil.oc.common.template.MicrocontrollerTemplate.validate",
            "li.cil.oc.common.template.MicrocontrollerTemplate.assemble",
            hostClass,
            null,
            intArrayOf(Tier.Three),
            listOf(
                Slot.Card to Tier.Two,
                Slot.Card to Tier.One,
                null,
                Slot.CPU to Tier.One,
                Slot.Memory to Tier.One,
                Slot.Memory to Tier.One,
                Slot.EEPROM to Tier.Any
            ).map { toPair(it) }
        )

        // Creative
        IMC.registerAssemblerTemplate(
            "Microcontroller (Creative)",
            "li.cil.oc.common.template.MicrocontrollerTemplate.selectTierCreative",
            "li.cil.oc.common.template.MicrocontrollerTemplate.validate",
            "li.cil.oc.common.template.MicrocontrollerTemplate.assemble",
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
            "Microcontroller",
            "li.cil.oc.common.template.MicrocontrollerTemplate.selectDisassembler",
            "li.cil.oc.common.template.MicrocontrollerTemplate.disassemble"
        )
    }

    override fun maxComplexity(inventory: IInventory): Int {
        return when (caseTier(inventory)) {
            Tier.Two -> 5
            Tier.Four -> 9001 // Creative
            else -> 4
        }
    }

    override fun caseTier(inventory: IInventory): Int = ItemUtils.caseTier(inventory.getStackInSlot(0))
}
