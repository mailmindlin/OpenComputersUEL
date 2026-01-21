package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api.Nanomachines as ApiNanomachines
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect

object PotionProvider : ScalaProvider("c29e4eec-5a46-479a-9b3d-ad0f06da784a") {
    // Lazy to give other mods a chance to register their potions.
    val PotionWhitelist: Set<Potion> by lazy { filterPotions(Settings.get.nanomachinePotionWhitelist) }

    fun <T> filterPotions(list: Iterable<T>): Set<Potion> {
        return list.mapNotNull { entry ->
            when (entry) {
                is String -> Potion.getPotionFromResourceLocation(entry)
                is Number -> Potion.getPotionById(entry.toInt())
                else -> null
            }
        }.toSet()
    }

    fun isPotionEligible(potion: Potion?): Boolean = potion != null && PotionWhitelist.contains(potion)

    override fun createScalaBehaviors(player: EntityPlayer): Iterable<Behavior> {
        return Potion.REGISTRY.filter { isPotionEligible(it) }.map { PotionBehavior(it, player) }
    }

    override fun writeBehaviorToNBT(behavior: Behavior, nbt: NBTTagCompound) {
        if (behavior is PotionBehavior) {
            nbt.setString("potionId", Potion.REGISTRY.getNameForObject(behavior.potion).toString())
        }
    }

    override fun readBehaviorFromNBT(player: EntityPlayer, nbt: NBTTagCompound): Behavior {
        val potionId = nbt.getString("potionId")
        return PotionBehavior(Potion.getPotionFromResourceLocation(potionId), player)
    }

    class PotionBehavior(val potion: Potion, player: EntityPlayer) : AbstractBehavior(player) {
        companion object {
            const val Duration = 600
        }

        fun amplifier(player: EntityPlayer): Int = ApiNanomachines.getController(player).getInputCount(this) - 1

        override fun getNameHint(): String = potion.name.removePrefix("potion.")

        override fun onDisable(reason: DisableReason) {
            player.removePotionEffect(potion)
        }

        override fun update() {
            player.addPotionEffect(PotionEffect(potion, Duration, amplifier(player), true, Settings.get.enableNanomachinePfx))
        }
    }
}
