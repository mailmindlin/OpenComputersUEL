package li.cil.oc.integration.util

import li.cil.oc.util.BlockPosition
import li.cil.oc.util.blockExists
import net.minecraft.util.EnumFacing

object BundledRedstone {
    val providers = mutableListOf<RedstoneProvider>()

    fun addProvider(provider: RedstoneProvider) {
        providers.add(provider)
    }

    val isAvailable: Boolean
        get() = providers.isNotEmpty()

    fun computeInput(pos: BlockPosition, side: EnumFacing): Int {
        return if (pos.world!!.blockExists(pos.offset(side))) {
            providers.map { it.computeInput(pos, side) }.maxOrNull() ?: 0
        } else {
            0
        }
    }

    fun computeBundledInput(pos: BlockPosition, side: EnumFacing): IntArray? {
        return if (pos.world!!.blockExists(pos.offset(side))) {
            val inputs = providers.mapNotNull { it.computeBundledInput(pos, side) }
            if (inputs.isEmpty()) {
                null
            } else {
                inputs.reduce { a, b ->
                    a.zip(b).map { (l, r) -> maxOf(l, r) }.toIntArray()
                }
            }
        } else {
            null
        }
    }

    interface RedstoneProvider {
        fun computeInput(pos: BlockPosition, side: EnumFacing): Int

        fun computeBundledInput(pos: BlockPosition, side: EnumFacing): IntArray?
    }
}
