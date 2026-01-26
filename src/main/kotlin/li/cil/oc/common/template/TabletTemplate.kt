package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.IMC
import li.cil.oc.api.Items
import li.cil.oc.api.internal.Tablet
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.util.ItemUtils
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

object TabletTemplate : Template() {
    override val suggestedComponents = arrayOf(
        "BIOS" to { inv: IInventory -> hasComponent(Constants.ItemName.EEPROM)(inv) },
        "Keyboard" to { inv: IInventory -> hasComponent(Constants.BlockName.Keyboard)(inv) },
        "GraphicsCard" to { inventory: IInventory ->
            arrayOf(
                Constants.ItemName.APUCreative,
                Constants.ItemName.APUTier1,
                Constants.ItemName.APUTier2,
                Constants.ItemName.GraphicsCardTier1,
                Constants.ItemName.GraphicsCardTier2,
                Constants.ItemName.GraphicsCardTier3
            ).any { name -> hasComponent(name)(inventory) }
        },
        "OS" to { inv: IInventory -> hasFileSystem(inv) }
    )

    override val hostClass: Class<Tablet> = Tablet::class.java

    @JvmStatic
    fun selectTier1(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.TabletCaseTier1

    @JvmStatic
    fun selectTier2(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.TabletCaseTier2

    @JvmStatic
    fun selectCreative(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.TabletCaseCreative

    @JvmStatic
    fun validate(inventory: IInventory): Array<Any> = validateComputer(inventory)

    @JvmStatic
    fun assemble(inventory: IInventory): Array<Any> {
        val items = (1 until inventory.sizeInventory).map { slot -> inventory.getStackInSlot(slot) }
        val data = TabletData()
        data.tier = ItemUtils.caseTier(inventory.getStackInSlot(0))
        data.container = items.firstOrNull() ?: ItemStack.EMPTY
        data.items = arrayOf(Constants.BlockInfo.ScreenTier1.createItemStack(1)) +
                items.drop(if (data.tier == Tier.One) 0 else 1).filter { !it.isEmpty }
        data.energy = Settings.get.bufferTablet
        data.maxEnergy = data.energy
        val stack = Constants.ItemInfo.Tablet.createItemStack(1)
        data.save(stack)
        val energy = Settings.get.tabletBaseCost + complexity(inventory) * Settings.get.tabletComplexityCost

        return arrayOf(stack, energy)
    }

    @JvmStatic
    @Suppress("unused")
    fun selectDisassembler(stack: ItemStack): Boolean = Items.get(stack) == Constants.ItemInfo.Tablet

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<ItemStack> {
        val info = TabletData(stack)
        val itemInfo = Constants.ItemInfo.TabletCase(info.tier)
        return (arrayOf(itemInfo.createItemStack(1), info.container) +
                info.items.filter { !it.isEmpty }.drop(1) /* Screen */).filter { !it.isEmpty }.toTypedArray()
    }

    @JvmStatic
    fun register() {
        // Tier 1
        IMC.registerAssemblerTemplate(
            "Tablet (Tier 1)",
            "li.cil.oc.common.template.TabletTemplate.selectTier1",
            "li.cil.oc.common.template.TabletTemplate.validate",
            "li.cil.oc.common.template.TabletTemplate.assemble",
            hostClass,
            null,
            intArrayOf(Tier.Three, Tier.Two, Tier.One),
            listOf(
                Slot.Card to Tier.Two,
                Slot.Card to Tier.Two,
                null,
                Slot.CPU to Tier.Two,
                Slot.Memory to Tier.Two,
                Slot.Memory to Tier.Two,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.Two
            ).map { toPair(it) }
        )

        // Tier 2
        IMC.registerAssemblerTemplate(
            "Tablet (Tier 2)",
            "li.cil.oc.common.template.TabletTemplate.selectTier2",
            "li.cil.oc.common.template.TabletTemplate.validate",
            "li.cil.oc.common.template.TabletTemplate.assemble",
            hostClass,
            intArrayOf(Tier.Two),
            intArrayOf(Tier.Three, Tier.Two, Tier.Two),
            listOf(
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Two,
                null,
                Slot.CPU to Tier.Three,
                Slot.Memory to Tier.Two,
                Slot.Memory to Tier.Two,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.Two
            ).map { toPair(it) }
        )

        // Creative
        IMC.registerAssemblerTemplate(
            "Tablet (Creative)",
            "li.cil.oc.common.template.TabletTemplate.selectCreative",
            "li.cil.oc.common.template.TabletTemplate.validate",
            "li.cil.oc.common.template.TabletTemplate.assemble",
            hostClass,
            intArrayOf(Tier.Three),
            intArrayOf(Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three, Tier.Three),
            listOf(
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Three,
                Slot.Card to Tier.Three,
                Slot.CPU to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.Memory to Tier.Three,
                Slot.EEPROM to Tier.Any,
                Slot.HDD to Tier.Three
            ).map { toPair(it) }
        )

        // Disassembler
        IMC.registerDisassemblerTemplate(
            "Tablet",
            "li.cil.oc.common.template.TabletTemplate.selectDisassembler",
            "li.cil.oc.common.template.TabletTemplate.disassemble"
        )
    }

    override fun maxComplexity(inventory: IInventory): Int = super.maxComplexity(inventory) / 2 + 5

    override fun caseTier(inventory: IInventory): Int = ItemUtils.caseTier(inventory.getStackInSlot(0))
}
