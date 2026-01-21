package li.cil.oc.common.template

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api.Driver
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.IMC
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.common.util.Constants.NBT
import java.lang.reflect.Method

object AssemblerTemplates {
    @JvmField
    val NoSlot = Slot(Slot.None, Tier.None, null, null)

    private val templates = mutableListOf<Template>()
    private val templateFilters = mutableListOf<Method>()

    @JvmStatic
    fun add(template: NBTTagCompound) {
        val selector = IMC.getStaticMethod(template.getString("select"), ItemStack::class.java)
        val validator = IMC.getStaticMethod(template.getString("validate"), IInventory::class.java)
        val assembler = IMC.getStaticMethod(template.getString("assemble"), IInventory::class.java)
        val hostClass = tryGetHostClass(template.getString("hostClass"))

        val containerSlots = mutableListOf<Slot>()
        val containerList = template.getTagList("containerSlots", NBT.TAG_COMPOUND)
        for (i in 0 until minOf(3, containerList.tagCount())) {
            containerSlots.add(parseSlot(containerList.getCompoundTagAt(i), Slot.Container, hostClass))
        }
        while (containerSlots.size < 3) containerSlots.add(NoSlot)

        val upgradeSlots = mutableListOf<Slot>()
        val upgradeList = template.getTagList("upgradeSlots", NBT.TAG_COMPOUND)
        for (i in 0 until minOf(9, upgradeList.tagCount())) {
            upgradeSlots.add(parseSlot(upgradeList.getCompoundTagAt(i), Slot.Upgrade, hostClass))
        }
        while (upgradeSlots.size < 9) upgradeSlots.add(NoSlot)

        val componentSlots = mutableListOf<Slot>()
        val componentList = template.getTagList("componentSlots", NBT.TAG_COMPOUND)
        for (i in 0 until minOf(9, componentList.tagCount())) {
            componentSlots.add(parseSlot(componentList.getCompoundTagAt(i), null, hostClass))
        }
        while (componentSlots.size < 9) componentSlots.add(NoSlot)

        templates.add(Template(selector, validator, assembler, containerSlots.toTypedArray(), upgradeSlots.toTypedArray(), componentSlots.toTypedArray()))
    }

    @JvmStatic
    fun addFilter(method: String) {
        templateFilters.add(IMC.getStaticMethod(method, ItemStack::class.java))
    }

    @JvmStatic
    fun select(stack: ItemStack): Template? {
        if (!stack.isEmpty && templateFilters.all { IMC.tryInvokeStatic(it, stack, true) as Boolean }) {
            return templates.find { it.select(stack) }
        }
        return null
    }

    class Template(
        val selector: Method,
        val validator: Method,
        val assembler: Method,
        val containerSlots: Array<Slot>,
        val upgradeSlots: Array<Slot>,
        val componentSlots: Array<Slot>
    ) {
        fun select(stack: ItemStack): Boolean = IMC.tryInvokeStatic(selector, stack, false) as Boolean

        fun validate(inventory: IInventory): Triple<Boolean, ITextComponent?, Array<ITextComponent>> {
            return when (val result = IMC.tryInvokeStatic(validator, inventory, null as Array<Any>?)) {
                is Array<*> -> when {
                    result.size >= 3 && result[0] is Boolean && result[1] is ITextComponent && result[2] is Array<*> ->
                        @Suppress("UNCHECKED_CAST")
                        Triple(result[0] as Boolean, result[1] as ITextComponent, result[2] as Array<ITextComponent>)
                    result.size >= 2 && result[0] is Boolean && result[1] is ITextComponent ->
                        Triple(result[0] as Boolean, result[1] as ITextComponent, emptyArray())
                    result.size >= 1 && result[0] is Boolean ->
                        Triple(result[0] as Boolean, null, emptyArray())
                    else -> Triple(false, null, emptyArray())
                }
                else -> Triple(false, null, emptyArray())
            }
        }

        fun assemble(inventory: IInventory): Pair<ItemStack, Double> {
            return when (val result = IMC.tryInvokeStatic(assembler, inventory, null as Array<Any>?)) {
                is Array<*> -> when {
                    result.size >= 2 && result[0] is ItemStack && result[1] is Number ->
                        Pair(result[0] as ItemStack, (result[1] as Number).toDouble())
                    result.size >= 1 && result[0] is ItemStack ->
                        Pair(result[0] as ItemStack, 0.0)
                    else -> Pair(ItemStack.EMPTY, 0.0)
                }
                else -> Pair(ItemStack.EMPTY, 0.0)
            }
        }
    }

    class Slot(
        val kind: String,
        val tier: Int,
        val validator: Method?,
        val hostClass: Class<out EnvironmentHost>?
    ) {
        fun validate(inventory: IInventory, slot: Int, stack: ItemStack): Boolean {
            return if (validator != null) {
                IMC.tryInvokeStatic(validator, inventory, slot, tier, stack, false) as Boolean
            } else {
                val driver = if (hostClass != null) Driver.driverFor(stack, hostClass) else Driver.driverFor(stack)
                if (driver != null) {
                    try {
                        driver.slot(stack) == kind && driver.tier(stack) <= tier
                    } catch (e: AbstractMethodError) {
                        OpenComputers.log.warn("Error trying to query driver '${driver.javaClass.name}' for slot and/or tier information. Probably their fault. Yell at them before coming to OpenComputers for support. :P")
                        false
                    }
                } else false
            }
        }
    }

    private fun parseSlot(nbt: NBTTagCompound, kindOverride: String?, hostClass: Class<out EnvironmentHost>?): Slot {
        val kind = kindOverride ?: if (nbt.hasKey("type")) nbt.getString("type") else Slot.None
        val tier = if (nbt.hasKey("tier")) nbt.getInteger("tier") else Tier.Any
        val validator = if (nbt.hasKey("validate")) IMC.getStaticMethod(nbt.getString("validate"), IInventory::class.java, Int::class.java, Int::class.java, ItemStack::class.java) else null
        return Slot(kind, tier, validator, hostClass)
    }

    private fun tryGetHostClass(name: String): Class<out EnvironmentHost>? {
        return if (Strings.isNullOrEmpty(name)) null
        else Class.forName(name).asSubclass(EnvironmentHost::class.java)
    }
}
