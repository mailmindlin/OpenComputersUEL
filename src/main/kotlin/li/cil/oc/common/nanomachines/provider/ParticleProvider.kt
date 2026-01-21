package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.util.PlayerUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumParticleTypes

object ParticleProvider : ScalaProvider("b48c4bbd-51bb-4915-9367-16cff3220e4b") {
    @JvmField
    val ParticleTypes: Array<EnumParticleTypes> = arrayOf(
        EnumParticleTypes.FIREWORKS_SPARK,
        EnumParticleTypes.TOWN_AURA,
        EnumParticleTypes.SMOKE_NORMAL,
        EnumParticleTypes.SPELL_WITCH,
        EnumParticleTypes.NOTE,
        EnumParticleTypes.ENCHANTMENT_TABLE,
        EnumParticleTypes.FLAME,
        EnumParticleTypes.LAVA,
        EnumParticleTypes.WATER_SPLASH,
        EnumParticleTypes.REDSTONE,
        EnumParticleTypes.SLIME,
        EnumParticleTypes.HEART,
        EnumParticleTypes.VILLAGER_HAPPY
    )

    override fun createScalaBehaviors(player: EntityPlayer): Iterable<Behavior> =
        ParticleTypes.map { ParticleBehavior(it, player) }

    override fun writeBehaviorToNBT(behavior: Behavior, nbt: NBTTagCompound) {
        if (behavior is ParticleBehavior) {
            nbt.setInteger("effectName", behavior.effectType.particleID)
        }
    }

    override fun readBehaviorFromNBT(player: EntityPlayer, nbt: NBTTagCompound): Behavior {
        val effectType = EnumParticleTypes.getParticleFromId(nbt.getInteger("effectName"))
        return ParticleBehavior(effectType, player)
    }

    class ParticleBehavior(var effectType: EnumParticleTypes, player: EntityPlayer) : AbstractBehavior(player) {
        override fun getNameHint(): String = "particles." + effectType.particleName

        override fun update() {
            val world = player.entityWorld
            if (world.isRemote && Settings.get.enableNanomachinePfx) {
                PlayerUtils.spawnParticleAround(player, effectType, api.Nanomachines.getController(player).getInputCount(this) * 0.25)
            }
        }
    }
}
