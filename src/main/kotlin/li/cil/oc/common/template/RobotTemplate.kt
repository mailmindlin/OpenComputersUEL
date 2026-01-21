package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.internal.Robot
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.util.ItemUtils
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

object RobotTemplate : Template() {
    override val hostClass: Class<Robot> = Robot::class.java

    @JvmStatic
    fun selectTier1(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.BlockName.CaseTier1)

    @JvmStatic
    fun selectTier2(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.BlockName.CaseTier2)

    @JvmStatic
    fun selectTier3(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.BlockName.CaseTier3)

    @JvmStatic
    fun selectCreative(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.BlockName.CaseCreative)

    @JvmStatic
    fun validate(inventory: IInventory): Array<Any> = validateComputer(inventory)

    @JvmStatic
    fun assemble(inventory: IInventory): Array<Any> {
        val items = (1 until inventory.sizeInventory).map { inventory.getStackInSlot(it) }
        val data = RobotData()
        data.tier = caseTier(inventory)
        data.name = RobotData.randomName
        data.robotEnergy = Settings.get.bufferRobot.toInt()
        data.totalEnergy = data.robotEnergy
        data.containers = items.take(3).filter { !it.isEmpty }.toTypedArray()
        data.components = items.drop(3).filter { !it.isEmpty }.toTypedArray()
        val stack = data.createItemStack()
        val energy = Settings.get.robotBaseCost + complexity(inventory) * Settings.get.robotComplexityCost

        return arrayOf(stack, energy)
    }

    @JvmStatic
    fun selectDisassembler(stack: ItemStack): Boolean = api.Items.get(stack) == api.Items.get(Constants.BlockName.Robot)

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<ItemStack> {
        val info = RobotData(stack)
        val itemName = Constants.BlockName.Case(info.tier)

        return arrayOf(api.Items.get(itemName).createItemStack(1)) + info.containers + info.components
    }

    @JvmStatic
    fun register() {
        // Tier 1
        api.IMC.registerAssemblerTemplate(
            "Robot (Tier 1)",
            "li.cil.oc.common.template.RobotTemplate.selectTier1",
            "li.cil.oc.common.template.RobotTemplate.validate",
            "li.cil.oc.common.template.RobotTemplate.assemble",
            hostClass,
            intArrayOf(Tier.Two, Tier.One, Tier.One),
            intArrayOf(Tier.One, Tier.One, Tier.One),
            listOf(
                Slot.Card to Tier.One,
                null,
                null,
                Slot.CPU to Tier.One,
                Slot.Memory to Tier.One,
                Slot.Memory to Tier.One,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.One
            ).map { toPair(it) }
        )

        // Tier 2
        api.IMC.registerAssemblerTemplate(
            "Robot (Tier 2)",
            "li.cil.oc.common.template.RobotTemplate.selectTier2",
            "li.cil.oc.common.template.RobotTemplate.validate",
            "li.cil.oc.common.template.RobotTemplate.assemble",
            hostClass,
            intArrayOf(Tier.Three, Tier.Two, Tier.One),
            intArrayOf(Tier.Two, Tier.Two, Tier.Two, Tier.One, Tier.One, Tier.One),
            listOf(
                Slot.Card to Tier.Two,
                Slot.Card to Tier.One,
                null,
                Slot.CPU to Tier.Two,
                Slot.Memory to Tier.Two,
                Slot.Memory to Tier.Two,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.Two
            ).map { toPair(it) }
        )

        // Tier 3
        api.IMC.registerAssemblerTemplate(
            "Robot (Tier 3)",
            "li.cil.oc.common.template.RobotTemplate.selectTier3",
            "li.cil.oc.common.template.RobotTemplate.validate",
            "li.cil.oc.common.template.RobotTemplate.assemble",
            hostClass,
            intArrayOf(Tier.Three, Tier.Two, Tier.Two),
            intArrayOf(Tier.Three, Tier.Three, Tier.Three, Tier.Two, Tier.Two, Tier.Two, Tier.One, Tier.One, Tier.One),
            listOf(
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Two,
                Slot.Card to Tier.Two,
                Slot.CPU to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.Three,
                Slot.HDD to Tier.Two
            ).map { toPair(it) }
        )

        // Creative
        api.IMC.registerAssemblerTemplate(
            "Robot (Creative)",
            "li.cil.oc.common.template.RobotTemplate.selectCreative",
            "li.cil.oc.common.template.RobotTemplate.validate",
            "li.cil.oc.common.template.RobotTemplate.assemble",
            hostClass,
            intArrayOf(Tier.Three, Tier.Three, Tier.Three),
            intArrayOf(Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three),
            listOf(
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Three,
                Slot.CPU to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.Three,
                Slot.HDD to Tier.Three
            ).map { toPair(it) }
        )

        // Disassembler
        api.IMC.registerDisassemblerTemplate(
            "Robot",
            "li.cil.oc.common.template.RobotTemplate.selectDisassembler",
            "li.cil.oc.common.template.RobotTemplate.disassemble"
        )
    }

    override fun caseTier(inventory: IInventory): Int = ItemUtils.caseTier(inventory.getStackInSlot(0))
}
