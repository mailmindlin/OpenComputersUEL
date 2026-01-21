package li.cil.oc.util

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumParticleTypes

object PlayerUtils {
    @JvmStatic
    fun persistedData(player: EntityPlayer): NBTTagCompound {
        val nbt = player.entityData
        if (!nbt.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            nbt.setTag(EntityPlayer.PERSISTED_NBT_TAG, NBTTagCompound())
        }
        return nbt.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
    }

    @JvmStatic
    @JvmOverloads
    fun spawnParticleAround(player: EntityPlayer, effectType: EnumParticleTypes, chance: Double = 1.0) {
        val rng = player.entityWorld.rand
        if (chance >= 1 || rng.nextDouble() < chance) {
            val bounds = player.entityBoundingBox
            val x = bounds.minX + (bounds.maxX - bounds.minX) * rng.nextDouble() * 1.5
            val y = bounds.minY + (bounds.maxY - bounds.minY) * rng.nextDouble() * 0.5
            val z = bounds.minZ + (bounds.maxZ - bounds.minZ) * rng.nextDouble() * 1.5
            player.entityWorld.spawnParticle(effectType, x, y, z, 0.0, 0.0, 0.0)
        }
    }
}
