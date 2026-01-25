package li.cil.oc.integration.util

import java.lang.reflect.Method

import li.cil.oc.common.IMC
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import java.lang.reflect.Modifier

internal object Wrench {
  /** `(EntityPlayer, BlockPos, Boolean) -> Boolean` */
  private val usages = LinkedHashSet<Method>()
  /** `(ItemStack) -> Boolean`*/
  private val checks = LinkedHashSet<Method>()

  fun addUsage(wrench: Method) {
    // Validation
    assert(wrench.parameterTypes.size == 3)
    assert(Modifier.isStatic(wrench.modifiers))
    assert(wrench.parameterTypes[0].isAssignableFrom(EntityPlayer::class.java))
    assert(wrench.parameterTypes[1].isAssignableFrom(BlockPos::class.java))
    assert(wrench.parameterTypes[2].isAssignableFrom(Boolean::class.javaPrimitiveType))
    assert(wrench.returnType == Boolean::class.javaPrimitiveType)

    usages += wrench
  }

  fun addCheck(checker: Method) {
    // Validation
    assert(checker.parameterTypes.size == 1)
    assert(Modifier.isStatic(checker.modifiers))
    assert(checker.parameterTypes[0].isAssignableFrom(ItemStack::class.java))
    assert(checker.returnType == Boolean::class.javaPrimitiveType)

    checks += checker
  }

  fun isWrench(stack: ItemStack): Boolean = !stack.isEmpty && checks.any { check -> IMC.tryInvokeStatic(check, stack, default = false) }

  fun holdsApplicableWrench(player: EntityPlayer, position: BlockPos): Boolean =
    !player.heldItemMainhand.isEmpty && usages.any { usage -> IMC.tryInvokeStatic(usage, player, position, false, default = false) }

  fun wrenchUsed(player: EntityPlayer, position: BlockPos) {
    if (player.heldItemMainhand.isEmpty) return
    usages.forEach {
      IMC.tryInvokeStaticVoid(
        it,
        player,
        position,
        java.lang.Boolean.TRUE
      )
    }
  }
}
