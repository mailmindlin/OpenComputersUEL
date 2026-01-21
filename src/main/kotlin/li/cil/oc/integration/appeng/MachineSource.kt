package li.cil.oc.integration.appeng

import java.util.Optional

import appeng.api.networking.security.IActionHost
import appeng.api.networking.security.IActionSource
import net.minecraft.entity.player.EntityPlayer

class MachineSource(val via: IActionHost) : IActionSource {
  override fun player(): Optional<EntityPlayer> = Optional.empty()

  override fun machine(): Optional<IActionHost> = Optional.of(this.via)

  override fun <T> context(key: Class<T>): Optional<T> = Optional.empty()
}
