@file:JvmName("ComponentUtils")
package li.cil.oc.common.component

import li.cil.oc.util.ResultWrapper

/**
 * Utility function for creating result arrays from callbacks.
 * This replaces the Scala package object's implicit conversion.
 */
internal fun result(vararg args: Any?): Array<Any?> = ResultWrapper.result(*args)
