package li.cil.oc.server.component

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.server.machine.Machine
import li.cil.oc.util.BlockPosition
import net.minecraft.world.World
import li.cil.oc.util.ResultWrapper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing

/**
 * Extension function to create result arrays for component callbacks.
 * Replaces the Scala implicit conversion from package.scala.
 */
fun result(vararg args: Any?): Array<Any?> = ResultWrapper.result(*args)

internal val EnvironmentHost.world: World inline get() = this.world()

internal data class TabletUseMessage(
    val nbt: NBTTagCompound,
    val stack: ItemStack,
    val player: EntityPlayer,
    val blockPos: BlockPosition,
    val side: EnumFacing,
    val hitX: Float,
    val hitY: Float,
    val hitZ: Float,
) {
    companion object {
        internal fun tryParse(message: Message): TabletUseMessage? {
            if (message.name() != "tablet.use") return null;
            val machine = message.source().host() as? Machine ?: return null
//            val tablet = machine.host as? internal.Tablet ?: return null
            val data = message.data()
            if (data.size != 8) return null

            // Validate message data
            val nbt = data[0] as? NBTTagCompound ?: return null
            val stack = data[1] as? ItemStack ?: return null
            val player = data[2] as? EntityPlayer ?: return null
            val blockPos = data[3] as? BlockPosition ?: return null
            val side = data[4] as? EnumFacing ?: return null
            val hitX = data[5] as? Float ?: return null
            val hitY = data[6] as? Float ?: return null
            val hitZ = data[7] as? Float ?: return null
            return TabletUseMessage(nbt, stack, player, blockPos, side, hitX, hitY, hitZ)
        }
    }
}

internal fun Arguments.drop(n: Int): Array<Any?> = (this as Iterable<*>).drop(2).toTypedArray()