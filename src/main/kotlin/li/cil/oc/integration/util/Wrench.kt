package li.cil.oc.integration.util

import java.lang.reflect.Method

import li.cil.oc.common.IMC
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

import scala.collection.mutable

internal object Wrench {
  private val usages = LinkedHashSet<Method>()
  private val checks = LinkedHashSet<Method>()

  fun addUsage(wrench: Method) { usages += wrench }

  fun addCheck(checker: Method) { checks += checker }

  fun isWrench(stack: ItemStack): Boolean = !stack.isEmpty && checks.exists(IMC.tryInvokeStatic(_, stack)(false))

  fun holdsApplicableWrench(player: EntityPlayer, position: BlockPos): Boolean =
    !player.heldItemMainhand.isEmpty && usages.exists(IMC.tryInvokeStatic(_, player, position, java.lang.Boolean.FALSE)(false))

  fun wrenchUsed(player: EntityPlayer, position: BlockPos) {
    if (!player.heldItemMainhand.isEmpty) usages.foreach(
      IMC.tryInvokeStaticVoid(
        _,
        player,
        position,
        java.lang.Boolean.TRUE
      )
    )
  }
}
