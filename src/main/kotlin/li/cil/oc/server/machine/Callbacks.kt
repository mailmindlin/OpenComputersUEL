package li.cil.oc.server.machine

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import li.cil.oc.OpenComputers
import li.cil.oc.api.driver.MethodWhitelist
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Callback as MachineCallback
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.FilteredEnvironment
import li.cil.oc.api.network.ManagedPeripheral
import li.cil.oc.server.driver.CompoundBlockEnvironment
import li.cil.oc.util.Stack

private inline fun walkAncestors(start: Class<*>, f: (Class<*>) -> Unit) {
  val encountered = mutableSetOf<Class<*>>(Any::class.java)
  val queue = Stack<Class<*>>()
  queue.push(start)

  while (queue.isNotEmpty()) {
    val current = queue.pop()
    if (!encountered.add(current)) continue
    f(current)

    // Push super types
    val superclass = current.superclass
    if (superclass !in encountered)
      queue.push(superclass)

    for (iface in current.interfaces) {
      if (iface !in encountered)
        queue.push(superclass)
    }
  }
}

object Callbacks {
  private val cache = mutableMapOf<Class<*>, Map<String, Callback>>()

  operator fun invoke(host: Any) = when (host) {
    is CompoundBlockEnvironment -> dynamicAnalyze(host)
    is ManagedPeripheral -> dynamicAnalyze(host)
    is FilteredEnvironment -> dynamicAnalyze(host)
    else -> cache.getOrPut(host.javaClass) { dynamicAnalyze(host) }
  }

  // Clear the cache; used when world is unloaded, mostly to allow reacting to
  // stuff (aka configs) that may influence which @Callbacks are enabled.
  fun clear() {
    cache.clear()
  }

  fun fromClass(environment: Class<*>) = staticAnalyze(environment)

  private fun dynamicAnalyze(host: Any): Map<String, Callback> {
    val whitelists = mutableListOf<Set<String>>()
    val callbacks = mutableMapOf<String, Callback>()

    // Lazy val to allow referencing it in closures before it's actually
    // initialized after the base whitelist has been compiled.
    val whitelist: Set<String> by lazy { whitelists.reduceOrNull { u, v -> u.intersect(v) } ?: emptySet() }
    fun shouldAdd(name: String): Boolean = !callbacks.contains(name) && (whitelist.isEmpty() || whitelist.contains(name))

    fun process(environment: Any): Pair<Int, () -> Unit> {
      if (environment is MethodWhitelist) {
        whitelists += environment.whitelistedMethods().toSet()
      }
      val priority: Int = if (environment is NamedBlock) {
        environment.priority()
      } else {
        0
      }
      val filter: (String) -> Boolean = if (environment is FilteredEnvironment) {
        { s: String -> shouldAdd(s) && environment.isCallbackEnabled(s) }
      } else {
        ::shouldAdd
      }

      val handler: () -> Unit = if (environment is ManagedPeripheral) {
        {
          for (name in environment.methods()) {
            if (filter(name)) {
              callbacks[name] = PeripheralCallback(name)
            }
          }
          staticAnalyze(environment.javaClass, filter, callbacks)
        }
      } else {
        {
          staticAnalyze(environment.javaClass, filter, callbacks)
        }
      }
      return Pair(priority, handler)
    }

    // First collect whitelist and priority information, then sort and
    // fetch callbacks.
    if (host is CompoundBlockEnvironment) {
      host.environments.map { env -> process(env.second) }
        .sortedByDescending { it.first }
        .forEach { it.second() }
    } else {
      process(host).second()
    }

    return callbacks.toMap()
  }

  private fun staticAnalyze(seed: Class<*>, shouldAdd: ((String) -> Boolean)? = null, optCallbacks: MutableMap<String, Callback>? = null): Map<String, Callback> {
    walkAncestors(seed) { c ->
      for (m in c.declaredMethods) {
        val a = m.getAnnotation(CallbackAnnotation::class.java) ?: continue

        if (m.parameterTypes.size != 2 ||
          m.parameterTypes[0] != Context::class.java ||
          m.parameterTypes[1] != Arguments::class.java) {
          OpenComputers.log.error("Invalid use of Callback annotation on ${m.declaringClass.name}.${m.name}: invalid argument types or count.")
          continue
        }
        if (m.returnType != Array<Any>::class.java) {
          OpenComputers.log.error("Invalid use of Callback annotation on ${m.declaringClass.name}.${m.name}: invalid return type.")
          continue
        }
        if (!Modifier.isPublic(m.modifiers)) {
          OpenComputers.log.error("Invalid use of Callback annotation on ${m.declaringClass.name}.${m.name}: method must be public.")
          continue
        }

        val name = if (a.value != null && a.value.trim() != "") a.value else m.name
        if (shouldAdd?.invoke(name) != false) {
          callbacks[name] = ComponentCallback(m, a)
        }
      }
    }
    return callbacks
  }

  // ----------------------------------------------------------------------- //

  sealed class Callback(internal val annotation: CallbackAnnotation) {
    abstract operator fun invoke(instance: Any, context: Context, args: Arguments): Array<out Any?>?
  }

  internal class ComponentCallback(method: Method, annotation: CallbackAnnotation): Callback(annotation) {
    private val callWrapper = CallbackWrapper.createCallbackWrapper(method)
    /** The class that declared this callback */
    val declaringClass: Class<*> = method.declaringClass

    override fun invoke(instance: Any, context: Context, args: Arguments) = callWrapper.call(instance, context, args)
  }

  internal class PeripheralCallback(val name: String): Callback(PeripheralAnnotation(name)) {
    override fun invoke(instance: Any, context: Context, args: Arguments): Array<out Any?>? {
      val peripheral = (instance as? ManagedPeripheral) ?: throw NoSuchMethodException("instance must be ManagedPeripheral")
      return peripheral.invoke(name, context, args)
    }
  }

}
