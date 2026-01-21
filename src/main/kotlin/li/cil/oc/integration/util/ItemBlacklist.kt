package li.cil.oc.integration.util

import li.cil.oc.common.item.traits.Delegate
import net.minecraft.block.Block
import net.minecraft.item.ItemStack

internal object ItemBlacklist {
  /** Lazily evaluated stacks to avoid creating stacks with unregistered items/blocks. */
  private val hiddenItems = mutableSetOf<() -> ItemStack>()

  /** List of consumers for item stacks (blacklisting for NEI and JEI). */
  private val consumers =  mutableSetOf<(ItemStack) -> Unit>()

  fun hide(block: Block) {
    hiddenItems.add { ItemStack(block) }
  }

  fun hide(item: Delegate) {
    hiddenItems.add { item.createItemStack() }
  }

  fun apply() {
    for (consumer in consumers) {
      for (stack in hiddenItems) {
        consumer(stack())
      }
    }
  }
}
