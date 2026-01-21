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

object Callbacks {
  private val cache = mutableMapOf<Class<*>, Map<String, Callback>>()

  fun apply(host: Any) = when (host) {
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

  private fun staticAnalyze(seed: Class<*>, shouldAdd: ((String) -> Boolean)? = null, optCallbacks: MutableMap<String, Callback>? = null) {
    val callbacks = optCallbacks ?: mutableMapOf()
    var c: Class<*>? = seed
    while (c != null && c != Any::class.java) {
      for (m in c.declaredMethods) {
        if (!m.isAnnotationPresent(MachineCallback::class.java))
          continue

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

        val a = m.getAnnotation(MachineCallback::class.java)
        val name = if (a.value != null && a.value.trim() != "") a.value else m.name
        if (shouldAdd?.invoke(name) ?: true) {
          callbacks[name] = ComponentCallback(m, a)
        }
      }
      c = c.superclass
    }
    callbacks
  }

  // ----------------------------------------------------------------------- //

  abstract class Callback(val annotation: MachineCallback) {
    abstract fun apply(instance: Any, context: Context, args: Arguments): Array<*>
  }

  class ComponentCallback(val method: Method, annotation: MachineCallback): Callback(annotation) {
    val callWrapper = CallbackWrapper.createCallbackWrapper(method)

    override fun apply(instance: Any, context: Context, args: Arguments) = callWrapper.call(instance, context, args)
  }

  class PeripheralCallback(private val name: String): Callback(PeripheralAnnotation(name)) {
    override fun apply(instance: Any, context: Context, args: Arguments): Array<*> {
      return when (instance) {
        is ManagedPeripheral -> instance.invoke(name, context, args)
        else -> throw NoSuchMethodException()
      }
    }
  }

}
