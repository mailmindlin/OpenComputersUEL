package li.cil.oc.integration.minecraftforge

import li.cil.oc.OpenComputers
import li.cil.oc.common.tileentity.traits.PowerAcceptor
import li.cil.oc.integration.util.Power
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.energy.CapabilityEnergy
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object EventHandlerMinecraftForge {

    @SubscribeEvent
    fun onAttachCapabilities(event: AttachCapabilitiesEvent<TileEntity>) {
        when (val obj = event.`object`) {
            is PowerAcceptor -> event.addCapability(ProviderEnergy, Provider(obj))
        }
    }

    @JvmStatic
    fun canCharge(stack: ItemStack): Boolean {
        if (stack.hasCapability(CapabilityEnergy.ENERGY, null)) {
            val storage = stack.getCapability(CapabilityEnergy.ENERGY, null)
            if (storage is IEnergyStorage) {
                return storage.canReceive()
            }
        }
        return false
    }

    @JvmStatic
    fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double {
        if (stack.hasCapability(CapabilityEnergy.ENERGY, null)) {
            val storage = stack.getCapability(CapabilityEnergy.ENERGY, null)
            if (storage is IEnergyStorage) {
                return amount - Power.fromRF(storage.receiveEnergy(Power.toRF(amount), simulate))
            }
        }
        return amount
    }

    val ProviderEnergy: ResourceLocation = ResourceLocation(OpenComputers.ID, "forgeenergy")

    class Provider(private val tile: PowerAcceptor) : ICapabilityProvider {

        private val providers = EnumFacing.VALUES.map { side -> EnergyStorageImpl(tile, side) }
        private val nullProvider = EnergyStorageImpl(tile, null)

        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean =
            capability == CapabilityEnergy.ENERGY

        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if (capability == CapabilityEnergy.ENERGY) {
                @Suppress("UNCHECKED_CAST")
                (if (facing == null) nullProvider else providers[facing.index]) as T
            } else {
                null
            }
        }

        inner class EnergyStorageImpl(val tile: PowerAcceptor, val side: EnumFacing?) : IEnergyStorage {

            override fun getEnergyStored(): Int = Power.toRF(tile.globalBuffer(side))

            override fun getMaxEnergyStored(): Int = Power.toRF(tile.globalBufferSize(side))

            override fun canReceive(): Boolean = tile.canConnectPower(side)

            override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
                return Power.toRF(tile.tryChangeBuffer(side, Power.fromRF(maxReceive), !simulate))
            }

            override fun canExtract(): Boolean = false

            override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int = 0
        }
    }
}
