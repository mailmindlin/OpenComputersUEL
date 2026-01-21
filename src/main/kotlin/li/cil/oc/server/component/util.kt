package li.cil.oc.server.component

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.world.World
import li.cil.oc.util.ResultWrapper
import net.minecraft.item.ItemStack

/**
 * Extension function to create result arrays for component callbacks.
 * Replaces the Scala implicit conversion from package.scala.
 */
fun result(vararg args: Any?): Array<Any?> = ResultWrapper.result(*args)

internal val EnvironmentHost.world: World inline get() = this.world()