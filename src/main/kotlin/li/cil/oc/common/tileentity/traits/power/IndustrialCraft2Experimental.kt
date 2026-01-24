package li.cil.oc.common.tileentity.traits.power

import ic2.api.energy.tile.IEnergyEmitter
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.common.asm.Injectable
import li.cil.oc.common.tileentity.traits.Tickable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Optional
import net.minecraftforge.fml.common.eventhandler.Event

@Injectable.Interface(value = "ic2.api.energy.tile.IEnergySink", modid = Mods.IDs.IndustrialCraft2)
interface IndustrialCraft2Experimental : Common, IndustrialCraft2Common, Tickable {
    val world: net.minecraft.world.World?

    fun getPos(): net.minecraft.util.math.BlockPos

    fun isInvalid(): Boolean

    fun readFromNBTForServer(nbt: NBTTagCompound)

    fun writeToNBTForServer(nbt: NBTTagCompound)

    // Mixin-like property for conversion buffer - implementations need to provide storage
    var ic2ConversionBuffer: Double

    private fun useIndustrialCraft2Power(): Boolean = isServer && Mods.IndustrialCraft2.isModAvailable

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        if (useIndustrialCraft2Power() && world != null && Settings.get.isTickMultiple(world!!)) {
            updateIC2Energy()
        }
    }

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun updateIC2Energy() {
        tryAllSides({ demand, _ ->
            val result = minOf(demand, ic2ConversionBuffer)
            ic2ConversionBuffer -= result
            result
        }, Power::fromEU, Power::toEU)
    }

    fun validateIC2() {
        if (useIndustrialCraft2Power() && !addedToIC2PowerGrid) {
            EventHandler.scheduleIC2Add(this as net.minecraft.tileentity.TileEntity)
        }
    }

    fun invalidateIC2() {
        if (useIndustrialCraft2Power() && addedToIC2PowerGrid) {
            removeFromIC2Grid()
        }
    }

    fun onChunkUnloadIC2() {
        if (useIndustrialCraft2Power() && addedToIC2PowerGrid) {
            removeFromIC2Grid()
        }
    }

    private fun removeFromIC2Grid() {
        try {
            val event = Class.forName("ic2.api.energy.event.EnergyTileUnloadEvent")
                .getConstructor(Class.forName("ic2.api.energy.tile.IEnergyTile"))
                .newInstance(this) as Event
            MinecraftForge.EVENT_BUS.post(event)
        } catch (t: Throwable) {
            OpenComputers.log.warn("Error removing node from IC2 grid.", t)
        }
        addedToIC2PowerGrid = false
    }

    // ----------------------------------------------------------------------- //

    fun readIC2FromNBTForServer(nbt: NBTTagCompound) {
        ic2ConversionBuffer = nbt.getDouble(Settings.namespace + "ic2power")
    }

    fun writeIC2ToNBTForServer(nbt: NBTTagCompound) {
        nbt.setDouble(Settings.namespace + "ic2power", ic2ConversionBuffer)
    }

    // ----------------------------------------------------------------------- //

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun getSinkTier(): Int = Int.MAX_VALUE

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun acceptsEnergyFrom(emitter: IEnergyEmitter, direction: EnumFacing): Boolean =
        useIndustrialCraft2Power() && canConnectPower(direction)

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun injectEnergy(directionFrom: EnumFacing, amount: Double, voltage: Double): Double {
        ic2ConversionBuffer += amount
        return 0.0
    }

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    fun getDemandedEnergy(): Double {
        if (!useIndustrialCraft2Power()) return 0.0
        return if (ic2ConversionBuffer < energyThroughput * Settings.get.tickFrequency) {
            minOf(EnumFacing.VALUES.map { globalDemand(it) }.maxOrNull() ?: 0.0, Power.toEU(energyThroughput))
        } else 0.0
    }
}
