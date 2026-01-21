package li.cil.oc.common.tileentity.traits.power

/* TODO RotaryCraft
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.Optional

@Injectable.Interface(value = "Reika.RotaryCraft.API.Power.ShaftPowerReceiver", modid = Mods.IDs.RotaryCraft)
interface RotaryCraft : Common {
    var rotaryCraftOmega: Int
    var rotaryCraftTorque: Int
    var rotaryCraftPower: Long
    var rotaryCraftAlpha: Int

    private fun useRotaryCraftPower(): Boolean = isServer && Mods.RotaryCraft.isAvailable

    // ----------------------------------------------------------------------- //

    fun updateRotaryCraftEntity() {
        if (useRotaryCraftPower()) updateRotaryCraftEnergy()
    }

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    private fun updateRotaryCraftEnergy() {
        if (world.getTotalWorldTime() % Settings.get.tickFrequency == 0L) {
            tryAllSides({ demand, _ ->
                val consumed = minOf(demand.toLong(), rotaryCraftPower)
                rotaryCraftPower -= consumed
                consumed.toDouble()
            }, Power::fromWA, Power::toWA)
        }
    }

    // ----------------------------------------------------------------------- //
    // ShaftMachine

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun getOmega(): Int = rotaryCraftOmega

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun getTorque(): Int = rotaryCraftTorque

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun getPower(): Long = rotaryCraftPower

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun getName(): String = OpenComputers.Name

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun getIORenderAlpha(): Int = rotaryCraftAlpha

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun setIORenderAlpha(value: Int) { rotaryCraftAlpha = value }

    // ----------------------------------------------------------------------- //
    // ShaftPowerReceiver

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun setOmega(value: Int) { rotaryCraftOmega = value }

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun setTorque(value: Int) { rotaryCraftTorque = value }

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun setPower(value: Long) { rotaryCraftPower = value }

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun noInputMachine() {
        rotaryCraftOmega = 0
        rotaryCraftTorque = 0
        rotaryCraftPower = 0
    }

    // ----------------------------------------------------------------------- //
    // PowerAcceptor

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun canReadFrom(forgeDirection: EnumFacing): Boolean = true

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun isReceiving(): Boolean = true

    @Optional.Method(modid = Mods.IDs.RotaryCraft)
    fun getMinTorque(available: Int): Int = 0
}
*/

// Stub interface for when RotaryCraft is not available
interface RotaryCraft : Common
