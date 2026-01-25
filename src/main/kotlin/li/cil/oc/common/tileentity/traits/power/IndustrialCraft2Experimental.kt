package li.cil.oc.common.tileentity.traits.power

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.behaviors.BehaviorLifecycle
import li.cil.oc.common.tileentity.behaviors.BehaviorUpdate
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.common.tileentity.traits.Tickable
import li.cil.oc.common.tileentity.traits.isServer
import li.cil.oc.common.tileentity.traits.world
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Optional
import net.minecraftforge.fml.common.eventhandler.Event

private val IndustrialCraft2Experimental.useIndustrialCraft2Power: Boolean get() = this.isServer && Mods.IndustrialCraft2.isModAvailable

//@Injectable.Interface(value = "ic2.api.energy.tile.IEnergySink", modid = Mods.IDs.IndustrialCraft2)
@Optional.Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = Mods.IDs.IndustrialCraft2)
interface IndustrialCraft2Experimental : Common, IndustrialCraft2Common, Tickable, ic2.api.energy.tile.IEnergySink {
    val ic2Delegate: Delegate

    @Optional.Interface(iface = "li.cil.oc.common.tileentity.behaviors.BehaviorUpdate", modid = Mods.IDs.IndustrialCraft2)
    @Optional.Interface(iface = "li.cil.oc.common.tileentity.behaviors.BehaviorLifecycle", modid = Mods.IDs.IndustrialCraft2)
    class Delegate(private val tile: IndustrialCraft2Experimental): NbtSeriailzable, BehaviorUpdate, BehaviorLifecycle {
        private var conversionBuffer: Double = 0.0
        private var addedToIC2PowerGrid = false

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            super.readFromNBTForServer(nbt)
            conversionBuffer = nbt.getDouble(Settings.namespace + "ic2power")
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            super.writeToNBTForServer(nbt)
            nbt.setDouble(Settings.namespace + "ic2power", conversionBuffer)
        }

        fun injectEnergy(directionFrom: EnumFacing, amount: Double, voltage: Double): Double {
            conversionBuffer += amount
            return 0.0
        }

        fun getDemandedEnergy(): Double {
            if (!tile.useIndustrialCraft2Power) return 0.0
            val energyThroughput = tile.energyThroughput
            if (conversionBuffer >= energyThroughput * Settings.get.tickFrequency)
                // Satisfied
                return 0.0
            return EnumFacing.VALUES.maxOf { tile.globalDemand(it) }.coerceAtMost(Power.toEU(energyThroughput))
        }

        private fun updateEnergy() {
            tile.tryAllSides({ demand, _ ->
                val result = demand.coerceAtMost(conversionBuffer)
                conversionBuffer -= result
                result
            }, Power::fromEU, Power::toEU)
        }

        @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
        override fun update() {
            if (!tile.useIndustrialCraft2Power) return
            if (Settings.get.isTickMultiple(tile.world))
                updateEnergy()
        }

        @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
        private fun removeFromIC2Grid() {
            try {
                MinecraftForge.EVENT_BUS.post(
                    Class.forName("ic2.api.energy.event.EnergyTileUnloadEvent")
                        .getConstructor(Class.forName("ic2.api.energy.tile.IEnergyTile"))
                        .newInstance(this)
                    as Event
                )
            } catch (e: Exception) {
                OpenComputers.log.warn("Error removing node from IC2 grid.", e)
            }
        }

        @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
        override fun initialize() {
            if (tile.useIndustrialCraft2Power && !addedToIC2PowerGrid)
                EventHandler.scheduleIC2Add(this)
        }

        @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
        override fun dispose() {
            if (tile.useIndustrialCraft2Power && addedToIC2PowerGrid)
                removeFromIC2Grid()
        }
    }

    // ----------------------------------------------------------------------- //

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    override fun getSinkTier(): Int = Int.MAX_VALUE

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    override fun acceptsEnergyFrom(emitter: ic2.api.energy.tile.IEnergyEmitter, direction: EnumFacing): Boolean =
        useIndustrialCraft2Power && canConnectPower(direction)

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    override fun injectEnergy(directionFrom: EnumFacing, amount: Double, voltage: Double): Double
        = ic2Delegate.injectEnergy(directionFrom, amount, voltage)

    @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
    override fun getDemandedEnergy(): Double
        = ic2Delegate.getDemandedEnergy()
}
