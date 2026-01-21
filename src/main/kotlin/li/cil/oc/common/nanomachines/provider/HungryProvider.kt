package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.integration.util.DamageSourceWithRandomCause
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound

object HungryProvider : ScalaProvider("d697c24a-014c-4773-a288-23084a59e9e8") {
    const val FillCount = 10 // Create a bunch of these to have a higher chance of one being picked / available.

    @JvmField
    val HungryDamage = DamageSourceWithRandomCause("oc.nanomachinesHungry", 3)
        .setDamageBypassesArmor()
        .setDamageIsAbsolute()

    override fun createScalaBehaviors(player: EntityPlayer): Iterable<Behavior> =
        (1..FillCount).map { HungryBehavior(player) }

    override fun readBehaviorFromNBT(player: EntityPlayer, nbt: NBTTagCompound): Behavior = HungryBehavior(player)

    class HungryBehavior(player: EntityPlayer) : AbstractBehavior(player) {
        override fun onDisable(reason: DisableReason) {
            if (reason == DisableReason.OutOfEnergy) {
                player.attackEntityFrom(HungryDamage, Settings.get.nanomachinesHungryDamage)
                api.Nanomachines.getController(player).changeBuffer(Settings.get.nanomachinesHungryEnergyRestored)
            }
        }
    }
}
