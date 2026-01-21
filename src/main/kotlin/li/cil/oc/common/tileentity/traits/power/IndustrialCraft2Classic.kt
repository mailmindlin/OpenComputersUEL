package li.cil.oc.common.tileentity.traits.power

/* TODO IC2 Classic

import cpw.mods.fml.common.Optional
import cpw.mods.fml.common.eventhandler.Event
import ic2classic.api.Direction
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.ForgeDirection

@Injectable.Interface(value = "ic2classic.api.energy.tile.IEnergySink", modid = Mods.IDs.IndustrialCraft2Classic)
interface IndustrialCraft2Classic : Common, IndustrialCraft2Common {
    var ic2ClassicConversionBuffer: Double

    private fun useIndustrialCraft2ClassicPower(): Boolean = isServer && Mods.IndustrialCraft2Classic.isAvailable

    // ----------------------------------------------------------------------- //

    fun updateIC2ClassicEntity() {
        if (useIndustrialCraft2ClassicPower() && world.getTotalWorldTime() % Settings.get.tickFrequency == 0L) {
            updateIC2ClassicEnergy()
        }
    }

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2Classic)
    private fun updateIC2ClassicEnergy() {
        tryAllSides({ demand, _ ->
            val result = minOf(demand, ic2ClassicConversionBuffer)
            ic2ClassicConversionBuffer -= result
            result
        }, Power::fromEU, Power::toEU)
    }

    fun validateIC2Classic() {
        if (useIndustrialCraft2ClassicPower() && !addedToIC2PowerGrid) EventHandler.scheduleIC2Add(this as TileEntity)
    }

    fun invalidateIC2Classic() {
        if (useIndustrialCraft2ClassicPower() && addedToIC2PowerGrid) removeFromIC2ClassicGrid()
    }

    fun onChunkUnloadIC2Classic() {
        if (useIndustrialCraft2ClassicPower() && addedToIC2PowerGrid) removeFromIC2ClassicGrid()
    }

    private fun removeFromIC2ClassicGrid() {
        try {
            MinecraftForge.EVENT_BUS.post(
                Class.forName("ic2classic.api.energy.event.EnergyTileUnloadEvent")
                    .getConstructor(Class.forName("ic2classic.api.energy.tile.IEnergyTile"))
                    .newInstance(this) as Event
            )
        } catch (t: Throwable) {
            OpenComputers.log.warn("Error removing node from IC2 grid.", t)
        }
        addedToIC2PowerGrid = false
    }

    // ----------------------------------------------------------------------- //

    fun readIC2ClassicFromNBTForServer(nbt: NBTTagCompound) {
        ic2ClassicConversionBuffer = nbt.getDouble(Settings.namespace + "ic2cpower")
    }

    fun writeIC2ClassicToNBTForServer(nbt: NBTTagCompound) {
        nbt.setDouble(Settings.namespace + "ic2cpower", ic2ClassicConversionBuffer)
    }

    // ----------------------------------------------------------------------- //

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2Classic)
    fun isAddedToEnergyNet(): Boolean = addedToIC2PowerGrid

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2Classic)
    fun getMaxSafeInput(): Int = Int.MAX_VALUE

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2Classic)
    fun acceptsEnergyFrom(emitter: TileEntity, direction: Direction): Boolean =
        useIndustrialCraft2ClassicPower() && canConnectPower(direction.toForgeDirection())

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2Classic)
    fun injectEnergy(directionFrom: Direction, amount: Int): Boolean {
        ic2ClassicConversionBuffer += amount
        return true
    }

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2Classic)
    fun demandsEnergy(): Int {
        if (!useIndustrialCraft2ClassicPower()) return 0
        return if (ic2ClassicConversionBuffer < energyThroughput * Settings.get.tickFrequency) {
            minOf(ForgeDirection.VALID_DIRECTIONS.map { globalDemand(it) }.max() ?: 0.0, Power.toEU(energyThroughput)).toInt()
        } else 0
    }
}
*/

// Stub interface for when IC2 Classic is not available
interface IndustrialCraft2Classic : Common, IndustrialCraft2Common
