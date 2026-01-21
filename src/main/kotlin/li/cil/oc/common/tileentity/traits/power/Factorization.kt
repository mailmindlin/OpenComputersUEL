package li.cil.oc.common.tileentity.traits.power

/* TODO Factorization

import cpw.mods.fml.common.Optional
import factorization.api.Charge
import factorization.api.Coord
import factorization.api.IChargeConductor
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.nbt.NBTTagCompound

@Injectable.Interface(value = "factorization.api.IChargeConductor", modid = Mods.IDs.Factorization)
interface Factorization : Common {
    private fun useFactorizationPower(): Boolean = isServer && Mods.Factorization.isAvailable

    @Optional.Method(modid = Mods.IDs.Factorization)
    fun getCharge(): Charge?

    @Optional.Method(modid = Mods.IDs.Factorization)
    fun getInfo(): String = ""

    @Optional.Method(modid = Mods.IDs.Factorization)
    fun getCoord(): Coord

    // ----------------------------------------------------------------------- //

    fun updateFactorizationEntity() {
        if (useFactorizationPower()) updateFactorizationEnergy()
    }

    @Optional.Method(modid = Mods.IDs.Factorization)
    private fun updateFactorizationEnergy() {
        getCharge()?.update()
        if (world.getTotalWorldTime() % Settings.get.tickFrequency == 0L) {
            tryAllSides({ demand, _ -> getCharge()?.deplete(demand.toInt())?.toDouble() ?: 0.0 }, Power::fromCharge, Power::toCharge)
        }
    }

    fun invalidateFactorization() {
        if (useFactorizationPower()) invalidateCharge()
    }

    @Optional.Method(modid = Mods.IDs.Factorization)
    private fun invalidateCharge() {
        getCharge()?.invalidate()
    }

    fun onChunkUnloadFactorization() {
        if (useFactorizationPower()) removeCharge()
    }

    @Optional.Method(modid = Mods.IDs.Factorization)
    private fun removeCharge() {
        if (!isInvalid()) getCharge()?.remove()
    }

    // ----------------------------------------------------------------------- //

    fun readFactorizationFromNBTForServer(nbt: NBTTagCompound) {
        if (useFactorizationPower()) loadCharge(nbt)
    }

    @Optional.Method(modid = Mods.IDs.Factorization)
    private fun loadCharge(nbt: NBTTagCompound) {
        getCharge()?.readFromNBT(nbt, "fzpower")
    }

    fun writeFactorizationToNBTForServer(nbt: NBTTagCompound) {
        if (useFactorizationPower()) saveCharge(nbt)
    }

    @Optional.Method(modid = Mods.IDs.Factorization)
    private fun saveCharge(nbt: NBTTagCompound) {
        getCharge()?.writeToNBT(nbt, "fzpower")
    }
}
*/

// Stub interface for when Factorization is not available
interface Factorization : Common
