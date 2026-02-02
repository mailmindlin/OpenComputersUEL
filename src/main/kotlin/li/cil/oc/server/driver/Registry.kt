package li.cil.oc.server.driver

import li.cil.oc.OpenComputers
import li.cil.oc.api.detail.DriverAPI
import li.cil.oc.api.driver.*
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.machine.Value
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.mapArray
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import java.util.*

/**
 * This class keeps track of registered drivers and provides installation logic
 * for each registered driver.
 *
 * Each component type must register its driver with this class to be used with
 * computers, since this class is used to determine whether an object is a
 * valid component or not.
 *
 * All drivers must be installed once the game starts - in the init phase - and
 * are then injected into all computers started up past that point. A driver is
 * a set of functions made available to the computer. These functions will
 * usually require a component of the type the driver wraps to be installed in
 * the computer, but may also provide context-free functions.
 */
internal object Registry: DriverAPI {
  private val sidedBlocks = mutableListOf<DriverBlock>()

  private val items = mutableListOf<DriverItem>()

  private val converters = mutableListOf<Converter>()

  private val environmentProviders = mutableListOf<EnvironmentProvider>()

  private val inventoryProviders = mutableListOf<InventoryProvider>()

  private val blacklistInner = mutableListOf<Pair<ItemStack, MutableSet<Class<*>>>>()

  internal val blacklist: List<Pair<ItemStack, Set<Class<*>>>>
    get() = blacklistInner

  /** Used to keep track of whether we're past the init phase. */
  var locked = false

  private fun checkLocked(thing: String) {
    if (locked) throw IllegalStateException("Please register all $thing in the init phase.")
  }

  override fun add(driver: DriverBlock) {
    checkLocked("drivers")
    if (!sidedBlocks.contains(driver)) {
      OpenComputers.log.debug("Registering block driver ${driver.javaClass.name}.")
      sidedBlocks += driver
    }
  }

  override fun add(driver: DriverItem) {
    checkLocked("drivers")
    if (!items.contains(driver)) {
      OpenComputers.log.debug("Registering item driver ${driver.javaClass.name}.")
      items += driver
    }
  }

  override fun add(converter: Converter) {
    checkLocked("converters")
    if (!converters.contains(converter)) {
      OpenComputers.log.debug("Registering converter ${converter.javaClass.name}.")
      converters += converter
    }
  }

  override fun add(provider: EnvironmentProvider) {
    checkLocked("environment providers")
    if (!environmentProviders.contains(provider)) {
      OpenComputers.log.debug("Registering environment provider ${provider.javaClass.name}.")
      environmentProviders += provider
    }
  }

  override fun add(provider: InventoryProvider) {
    checkLocked("inventory providers")
    if (!inventoryProviders.contains(provider)) {
      OpenComputers.log.debug("Registering inventory provider ${provider.javaClass.name}.")
      inventoryProviders += provider
    }
  }

  override fun driverFor(world: World, pos: BlockPos, side: EnumFacing): DriverBlock? {
    val drivers = sidedBlocks.filter { it.worksWith(world, pos, side) }
    if (drivers.isEmpty()) return null
    return CompoundBlockDriver(drivers.toTypedArray())
  }

  override fun driverFor(stack: ItemStack, host: Class<out EnvironmentHost>): DriverItem? {
    if (stack.isEmpty())
      return null

    return items
      .firstOrNull {
        it is HostAware && it.worksWith(stack) && it.worksWith(stack, host)
      }
      ?: driverFor(stack)
  }

  override fun driverFor(stack: ItemStack): DriverItem? =
    if (!stack.isEmpty) items.firstOrNull { it.worksWith(stack) }
    else null

  @Deprecated("use environmentsFor", replaceWith = ReplaceWith("environmentsFor(stack)"))
  override fun environmentFor(stack: ItemStack): Class<*>?
    = environmentProviders
      .firstNotNullOfOrNull { provider -> provider.getEnvironment(stack) }

  override fun environmentsFor(stack: ItemStack): Set<Class<*>>
    = environmentProviders.mapNotNullTo(mutableSetOf()) { it.getEnvironment(stack) }

  override fun itemHandlerFor(stack: ItemStack, player: EntityPlayer?): IItemHandler? {
    return inventoryProviders
      .find { provider -> provider.worksWith(stack, player) }
      ?.let { provider -> InventoryUtils.asItemHandler(provider.getInventory(stack, player)) }
      ?: run {
        if(stack.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null))
          stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
        else null
      }
  }

  override fun itemDrivers(): List<DriverItem> = items.toList()

  fun blacklistHost(stack: ItemStack, host: Class<*>) {
    val list = blacklistInner.find { it.first.isItemEqual(stack) }
    if (list == null) {
      blacklistInner.add(Pair(stack, mutableSetOf(host)))
    } else {
      list.second.add(host)
    }
  }

  fun Array<out Any?>.convert(): Array<out Any?>
    = this.mapArray { convertRecursively(it, IdentityHashMap()) }

  @Deprecated("use value.convert()", replaceWith = ReplaceWith("this.run { value.convert() }"))
  @JvmName("convertOld")
  fun convert(value: Array<*>?): Array<Any?>?
    = value?.mapArray { convertRecursively(it, IdentityHashMap()) }

  fun convertRecursively(value: Any?, memo: IdentityHashMap<Any, Any>, force: Boolean = false): Any? {
    val valueRef = when (value) {
      is Number -> value
      is Any -> value
      null -> null
      //TODO: does this match primitives?
      else -> value as Any
    }

    return if (!force && memo.containsKey(valueRef)) {
      memo[valueRef]
    } else when (valueRef) {
      null, Unit -> null
      is Boolean -> valueRef
      is Char, is String -> valueRef
      is Byte, is Short, is Int, is Long -> valueRef
      is Float, is Double -> valueRef
      is Number -> valueRef.toDouble()

      is BooleanArray, is ByteArray, is CharArray, is ShortArray, is IntArray, is LongArray, is FloatArray, is DoubleArray -> valueRef
//      is Array<String> -> arg
      is Value -> valueRef

      is Array<*> -> convertList(valueRef, valueRef.withIndex().iterator(), memo)
//      case arg: Product => convertList(arg, arg.productIterator.zipWithIndex, memo)
//      case arg: Seq[_] => convertList(arg, arg.zipWithIndex.iterator, memo)

      is Map<*, *> -> convertMap(valueRef, valueRef, memo)
      is Iterable<*> -> convertList(valueRef, valueRef.withIndex().iterator(), memo)
      else -> {
        val converted = hashMapOf<Any, Any>()
        memo[valueRef] = converted
        for (converter in converters) {
          try {
            converter.convert(valueRef, converted)
          } catch (e: Exception) {
            OpenComputers.log.warn ("Type converter threw an exception.", e)
          }
        }
        if (converted.isEmpty()) {
          val s = valueRef.toString()
          memo[valueRef] = s
          return s
        }
        // This is a little nasty but necessary because we need to keep the
        // 'converted' value up-to-date for any reference created to it in
        // the following convertRecursively call. For example:
        // - Converter C is called for A with map M.
        // - C puts A into M again.
        // - convertRecursively(M) encounters A in the memoization map, uses M.
        //   That M is then 'wrong', as in not fully converted. Hence the clear
        //   plus copy action afterwards.
        memo[converted] = converted // Makes convertMap re-use the map.
        convertRecursively(converted, memo, force = true)
        memo.remove(converted)
        if (converted.size == 1 && converted.containsKey("oc:flatten")) {
          val value = converted.get("oc:flatten")
          memo[valueRef] = value // Update memoization map.
          value
        } else {
          converted
        }
      }
    }
  }

  private fun convertList(obj: Any, list: Iterator<IndexedValue<Any?>>, memo: IdentityHashMap<Any, Any>): Array<Any?> {
    val converted = mutableListOf<Any?>()
    memo[obj] = converted
    for ((value, index) in list) {
      converted += convertRecursively(value, memo)
    }
    return converted.toTypedArray()
  }

  private fun convertMap(obj: Any, map: Map<*, *>, memo: IdentityHashMap<Any, Any>): Any {
    val converted = (memo.getOrPut(obj) { mutableMapOf<Any?, Any?>() } as? MutableMap<Any?, Any?>)!!
    for ((key, value) in map) {
      converted[convertRecursively(key, memo)] = convertRecursively(value, memo)
    }
    return converted
  }
}
