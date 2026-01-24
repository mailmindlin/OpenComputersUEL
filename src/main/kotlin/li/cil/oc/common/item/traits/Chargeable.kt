package li.cil.oc.common.item.traits

import ic2.api.item.IElectricItemManager
import li.cil.oc.Settings
import li.cil.oc.api.driver.item.Chargeable as ApiChargeable
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.ic2.ElectricItemManager
import li.cil.oc.integration.opencomputers.ModOpenComputers
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.Optional
import net.minecraft.item.ItemStack
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.energy.CapabilityEnergy
import net.minecraftforge.energy.IEnergyStorage

// TODO Forge power capabilities.
@Injectable.InterfaceList(
    Injectable.Interface(value = "ic2.api.item.ISpecialElectricItem", modid = Mods.IDs.IndustrialCraft2)
)
interface Chargeable : ApiChargeable {

    fun maxCharge(stack: ItemStack): Double

    fun getCharge(stack: ItemStack): Double

    fun setCharge(stack: ItemStack, amount: Double)

    fun canExtract(stack: ItemStack): Boolean = false

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun getManager(stack: ItemStack): IElectricItemManager = ElectricItemManager
    companion object {
        @JvmField
        val KEY = ResourceLocation(ModOpenComputers.mod.id, "chargeable")

        @JvmStatic
        fun convertForgeEnergyToOpenComputers(fe: Int): Double = fe / Settings.get.ratioForgeEnergy

        @JvmStatic
        fun convertOpenComputersToForgeEnergy(oc: Double): Int = (oc * Settings.get.ratioForgeEnergy).toInt()

        @JvmStatic
        fun applyCharge(amount: Double, current: Double, maximum: Double, save: (Double) -> Unit): Double {
            val target = current + amount
            val result = target.coerceIn(0.0, maximum)
            val used = result - current
            val unused = amount - used
            if (used > Double.MIN_VALUE || used < -Double.MIN_VALUE) {
                save(used)
            }
            return unused
        }
    }

    class Provider(private val stack: ItemStack, private val item: Chargeable) : ICapabilityProvider, IEnergyStorage {
        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            return capability == CapabilityEnergy.ENERGY
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if (hasCapability(capability, facing)) this as T else null
        }

        override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
            // Chargeable.charge() returns the amount UNUSED
            // IEnergyStorage wants the amount USED
            return maxReceive - convertOpenComputersToForgeEnergy(item.charge(stack, convertForgeEnergyToOpenComputers(maxReceive), simulate))
        }

        override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
            return if (canExtract()) {
                -receiveEnergy(-maxExtract, simulate)
            } else {
                0
            }
        }

        override fun getEnergyStored(): Int = convertOpenComputersToForgeEnergy(item.getCharge(stack))

        override fun getMaxEnergyStored(): Int = convertOpenComputersToForgeEnergy(item.maxCharge(stack))

        override fun canExtract(): Boolean = item.canExtract(stack)

        override fun canReceive(): Boolean = item.canCharge(stack)
    }
}
