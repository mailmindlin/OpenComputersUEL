package li.cil.oc.integration.util

import net.minecraft.client.resources.I18n
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.DamageSource
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentTranslation

class DamageSourceWithRandomCause(name: String, private val numCauses: Int): DamageSource(name) {
  override fun getDeathMessage(damagee: EntityLivingBase): ITextComponent {
    val damager = damagee.attackingEntity
    val format = "death.attack." + damageType + "." + (damagee.world.rand.nextInt(numCauses) + 1)
    val withCauseFormat = format + ".player"
    return if (damager != null && I18n.hasKey(withCauseFormat))
      TextComponentTranslation(withCauseFormat, damagee.displayName, damager.displayName)
    else
      TextComponentTranslation(format, damagee.displayName)
  }
}
