package li.cil.oc.integration.appeng

import java.util.LinkedList
import java.util.Optional

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.networking.IGrid
import appeng.api.networking.IGridHost
import appeng.api.networking.IGridNode
import appeng.api.networking.crafting.ICraftingJob
import appeng.api.networking.crafting.ICraftingLink
import appeng.api.networking.crafting.ICraftingRequester
import appeng.api.networking.security.IActionHost
import appeng.api.storage.data.IAEItemStack
import appeng.api.util.AECableType
import appeng.api.util.AEPartLocation
import com.google.common.collect.ImmutableSet
import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.common.EventHandler
import li.cil.oc.server.component.Result
import li.cil.oc.server.driver.Registry
import li.cil.oc.util.*
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.Constants.NBT
import scala.concurrent.Future
import kotlin.collections.map

// Note to self: this class is used by ExtraCells (and potentially others), do not rename / drastically change it.
// AETile must be a TileEntity that also implements IActionHost and IGridHost
interface NetworkControl<AETile> where AETile : TileEntity, AETile : IActionHost, AETile : IGridHost {
  val tile: AETile
  val pos: AEPartLocation

  fun node(): Node?

  private fun aeCraftItem(aeItem: IAEItemStack): IAEItemStack {
    val patterns = AEUtil.getGridCrafting(tile.getGridNode(pos)!!.grid).getCraftingFor(aeItem, null, 0, tile.world)
    return patterns.firstOrNull { pattern ->
      pattern.outputs.any { it.isSameType(aeItem) }
    }?.outputs?.firstOrNull { it.isSameType(aeItem) }
      ?: aeItem.copy().apply { stackSize = 0 } // Should not be possible, but hey...
  }

  private fun aePotentialItem(aeItem: IAEItemStack): IAEItemStack {
    return if (aeItem.stackSize > 0 || !aeItem.isCraftable)
      aeItem
    else
      aeCraftItem(aeItem)
  }

  private fun isSequentialTable(map: HashMap<*, *>): Boolean {
    // if this element has n=number, it is the table wrapping showing how large the array is
    return map.all { (key, value) ->
      when {
        key is String && value is Number -> key == "n"
        key is Number && value is Any -> key.toInt() >= 1
        else -> false
      }
    }
  }

  private fun reduceSequentialTable(map: HashMap<*, *>): Array<Any?> {
    // in place of a table pack, we want a hash map of tuples
    val tuples = LinkedList<Any?>()
    map.forEach { (key, value) ->
      if (key !is String || key != "n") {
        tuples.add(reduceLuaValue(value))
      }
    }
    return tuples.toTypedArray()
  }

  private fun reduceHashTable(map: HashMap<*, *>): java.util.HashMap<Any?, Any?> {
    val hash = java.util.HashMap<Any?, Any?>()
    map.forEach { (key, value) ->
      hash[key] = value
    }
    return hash
  }

  private fun reduceLuaValue(any: Any?): Any? {
    return when (any) {
      is HashMap<*, *> ->
        if (isSequentialTable(any))
          reduceSequentialTable(any)
        else
          reduceHashTable(any)
      else -> any
    }
  }

  private fun getFilter(args: Arguments, index: Int): Map<Any?, Any?> {
    val hash = java.util.HashMap<Any?, Any?>()
    Registry.convert(arrayOf(args.optTable(index, emptyMap<Any?, Any?>())))
      ?.firstOrNull()?.let { converted ->
        when (converted) {
          is Map<*, *> -> converted.forEach { (key, value) ->
            hash[reduceLuaValue(key)] = reduceLuaValue(value)
          }
        }
      }
    return hash
  }

  private fun allItems(): Iterable<IAEItemStack> {
    val storage = AEUtil.getGridStorage(tile.getGridNode(pos)!!.grid)
    val inventory = storage.getInventory(AEUtil.itemStorageChannel)
    return inventory.storageList
  }

  private fun allCraftables(): Iterable<IAEItemStack> =
    allItems().filter { it.isCraftable }.map { aeCraftItem(it) }

  private fun convert(aeItem: IAEItemStack): Map<Any?, Any?> {
    // I would prefer to move the convert code to the registry for IAEItemStack
    // but craftables need the device that crafts them
    val hash = java.util.HashMap<Any?, Any?>()
    Registry.convert(arrayOf(aePotentialItem(aeItem).createItemStack()))
      ?.firstOrNull()
      ?.let { it as? Map<*, *> }
      ?.forEach { (key, value) ->
        hash[key] = value
      }
    hash["isCraftable"] = aeItem.isCraftable
    hash["size"] = aeItem.stackSize
    return hash
  }

  @Callback(doc = """function():table -- Get a list of tables representing the available CPUs in the network.""")
  fun getCpus(context: Context, args: Arguments): Array<Any?> {
    val buffer = mutableListOf<Map<String, Any>>()
    AEUtil.getGridCrafting(tile.getGridNode(pos)!!.grid).cpus.forEach { cpu ->
      buffer.add(
        mapOf(
          "name" to cpu.name,
          "storage" to cpu.availableStorage,
          "coprocessors" to cpu.coProcessors,
          "busy" to cpu.isBusy
        )
      )
    }
    return result(buffer.toTypedArray())
  }

  @Callback(doc = """function([filter:table]):table -- Get a list of known item recipes. These can be used to issue crafting requests.""")
  fun getCraftables(context: Context, args: Arguments): Array<Any?> {
    val filter = getFilter(args, 0)
    return result(
      allCraftables()
        .filter { aeCraftItem -> filter.isEmpty() || matches(convert(aeCraftItem), filter) }
        .map { Craftable(tile, pos, it) }
        .toTypedArray()
    )
  }

  @Callback(doc = """function([filter:table]):table -- Get a list of the stored items in the network.""")
  fun getItemsInNetwork(context: Context, args: Arguments): Array<Any?> {
    val filter = getFilter(args, 0)
    return result(
      allItems()
        .map { convert(it) }
        .filter { matches(it, filter) }
        .toTypedArray()
    )
  }

  @Callback(doc = """function([filter:table, dbAddress:string, startSlot:number, count:number]): bool -- Store items in the network matching the specified filter in the database with the specified address.""")
  fun store(context: Context, args: Arguments): Array<Any?> {
    val filter = getFilter(args, 0)
    val database = when (val address = args.optString(1, null)) {
      is String -> DatabaseAccess.database(node()!!, address)
      else -> DatabaseAccess.databases(node()!!).firstOrNull()
        ?: throw IllegalArgumentException("no database upgrade found")
    }
    val items = allItems()
      .filter { aeItem -> matches(convert(aeItem), filter) }
      .map { aePotentialItem(it) }
      .toList()
    val offset = args.optSlot(database.data, 2, 0)
    val count = args.optInteger(3, Int.MAX_VALUE).coerceAtMost(database.size() - offset).coerceAtMost(items.size)
    var slot = offset
    for (i in 0 until count) {
      val stack = items[i]?.createItemStack()?.copy()
      while (!database.getStackInSlot(slot).isNullOrEmpty() && slot < database.size()) slot += 1
      if (database.getStackInSlot(slot).isNullOrEmpty()) {
        database.setStackInSlot(slot, stack)
      }
    }
    return result(true)
  }

  @Callback(doc = """function():table -- Get a list of the stored fluids in the network.""")
  fun getFluidsInNetwork(context: Context, args: Arguments): Array<Any?> =
    result(
      AEUtil.getGridStorage(tile.getGridNode(pos)!!.grid)
        .getInventory(AEUtil.fluidStorageChannel)
        .storageList
        .filterNotNull()
        .map { it.fluidStack }
        .toTypedArray()
    )

  @Callback(doc = """function():number -- Get the average power injection into the network.""")
  fun getAvgPowerInjection(context: Context, args: Arguments): Array<Any?> =
    result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).avgPowerInjection)

  @Callback(doc = """function():number -- Get the average power usage of the network.""")
  fun getAvgPowerUsage(context: Context, args: Arguments): Array<Any?> =
    result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).avgPowerUsage)

  @Callback(doc = """function():number -- Get the idle power usage of the network.""")
  fun getIdlePowerUsage(context: Context, args: Arguments): Array<Any?> =
    result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).idlePowerUsage)

  @Callback(doc = """function():number -- Get the maximum stored power in the network.""")
  fun getMaxStoredPower(context: Context, args: Arguments): Array<Any?> =
    result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).maxStoredPower)

  @Callback(doc = """function():number -- Get the stored power in the network. """)
  fun getStoredPower(context: Context, args: Arguments): Array<Any?> =
    result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).storedPower)

  @Callback(doc = """function():boolean -- True if the AE network is considered online""")
  fun isNetworkPowered(context: Context, args: Arguments): Array<Any?> =
    result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).isNetworkPowered)

  @Callback(direct = false, doc = """function():number -- Returns the energy demand on the AE network""")
  fun getEnergyDemand(context: Context, args: Arguments): Array<Any?> {
    context.consumeCallBudget(1.5)
    return result(AEUtil.getGridEnergy(tile.getGridNode(pos)!!.grid).getEnergyDemand(Double.MAX_VALUE))
  }

  private fun matches(stack: Map<Any?, Any?>?, filter: Map<Any?, Any?>): Boolean {
    if (stack == null) return false
    return filter.all { (key, value) ->
      contains(stack, key, value)
    }
  }

  private fun contains(stack: Map<Any?, Any?>, key: Any?, value: Any?): Boolean {
    return stack.containsKey(key) && valueMatch(value, stack[key])
  }

  private fun valueMatch(a: Any?, b: Any?): Boolean {
    if ((a == null) != (b == null)) return false
    if (a == b) return true
    return when {
      a is Number && b is Number -> a.toInt() == b.toInt()
      a is Array<*> && b is Array<*> -> a.all { aItem ->
        when (aItem) {
          is java.util.HashMap<*, *> -> aItem.all { (aKey, aValue) ->
            b.any { bItem ->
              when (bItem) {
                is HashMap<*, *> -> bItem.any { (bKey, bValue) ->
                  valueMatch(aKey, bKey) && valueMatch(aValue, bValue)
                }
                else -> false
              }
            }
          }
          else -> false
        }
      }
      else -> false
    }
  }

  companion object {
    private val dispatchPool: SafeThreadPool = ThreadPoolFactory.createSafePool("AE2", 1)

    object LinkCache {
      val linkCache = mutableMapOf<String, ICraftingLink>()
      val statusCache = mutableMapOf<String, CraftingStatus>()

      fun store(link: ICraftingLink): ICraftingLink {
        statusCache.remove(link.craftingID)?.let { status ->
          status.setLink(link)
        } ?: run {
          linkCache[link.craftingID] = link
        }
        return link
      }

      fun store(status: CraftingStatus, id: String) {
        linkCache.remove(id)?.let { link ->
          status.setLink(link)
        } ?: run {
          statusCache[id] = status
        }
      }
    }

    class Craftable : AbstractValue, ICraftingRequester, IGridHost {
      // Controller must be a TileEntity that implements both IActionHost and IGridHost
      // We store it as TileEntity and cast when needed
      var controller: TileEntity? = null
      var pos: AEPartLocation? = null
      var stack: IAEItemStack? = null

      constructor(controller: TileEntity, pos: AEPartLocation, stack: IAEItemStack) {
        this.controller = controller
        this.pos = pos
        this.stack = stack
      }

      constructor() {
        // Default constructor with nulls
      }

      private val links = mutableSetOf<ICraftingLink>()

      // ----------------------------------------------------------------------- //

      override fun getRequestedJobs(): ImmutableSet<ICraftingLink> = ImmutableSet.copyOf(links)

      override fun jobStateChange(link: ICraftingLink) {
        links -= link
      }

      // rv1
      fun injectCratedItems(link: ICraftingLink, stack: IAEItemStack, p3: Actionable): IAEItemStack = stack

      // rv2
      override fun injectCraftedItems(link: ICraftingLink, stack: IAEItemStack, p3: Actionable): IAEItemStack = stack

      override fun getActionableNode(): IGridNode = (controller as IActionHost).actionableNode

      override fun getGridNode(dir: AEPartLocation): IGridNode? = (controller as? IGridHost)?.getGridNode(pos!!)
      override fun getCableConnectionType(dir: AEPartLocation): AECableType =
        (controller as IGridHost).getCableConnectionType(dir)

      override fun securityBreak() = (controller as IGridHost).securityBreak()

      // ----------------------------------------------------------------------- //

      private fun withController(f: (TileEntity) -> Array<Any?>): Array<Any?> {
        return if (delayData != null) {
          result(Unit, "waiting for ae network to load")
        } else {
          val ctrl = controller
          if (ctrl == null || ctrl.isInvalid) {
            result(Unit, "no controller")
          } else {
            f(ctrl)
          }
        }
      }

      private fun withGridNode(f: (IGridNode) -> Array<Any?>): Array<Any?> {
        return withController { c ->
          (c as? IGridHost)?.getGridNode(pos!!)?.let { grid -> f(grid) }
            ?: result(Unit, "no ae grid")
        }
      }

      @Callback(doc = """function():table -- Returns the item stack representation of the crafting result.""")
      fun getItemStack(context: Context, args: Arguments): Array<Any?> = arrayOf(stack?.createItemStack())

      @Callback(doc = """function():number -- Returns the number of requests in progress.""")
      fun requesting(context: Context, args: Arguments): Array<Any?> {
        return withGridNode { gridNode ->
          val craftingGrid = AEUtil.getGridCrafting(gridNode.grid)
          result(craftingGrid.requesting(stack!!))
        }
      }

      @Callback(doc = """function([amount:int=1, prioritizePower:boolean=true, cpuName:string]):userdata -- Requests item to be crafted, returning an object that allows tracking the crafting status.""")
      fun request(context: Context, args: Arguments): Array<Any?> {
        return withGridNode { gridNode ->
          val prioritizePower = args.optBoolean(1, true)
          val count = args.optInteger(0, 1)
          val cpuName = args.optString(2, "")

          val request = stack!!.copy()
          request.stackSize = count.toLong()

          val craftingGrid = AEUtil.getGridCrafting(gridNode.grid)

          val ctrl = controller!!
          val source = MachineSource(ctrl as IActionHost)
          val future = craftingGrid.beginCraftingJob(ctrl.world, gridNode.grid, source, request, null)
          val cpu = if (cpuName.isNotEmpty()) {
            craftingGrid.cpus.firstOrNull { c -> cpuName == c.name }
          } else null

          val status = CraftingStatus()
          dispatchPool.withPool { pool -> pool.submit {
            try {
              val job = future.get() // Make 100% sure we wait for this outside the scheduled closure.
              EventHandler.scheduleServer {
                val link = craftingGrid.submitJob(job, this@Craftable, cpu, prioritizePower, source)
                if (link != null) {
                  status.setLink(link)
                  links += link
                } else {
                  status.fail("missing resources?")
                }
              }
            } catch (e: Exception) {
              OpenComputers.log.debug("Error submitting job to AE2.", e)
              status.fail(e.toString())
            }
          } }

          result(status)
        }
      }

      // ----------------------------------------------------------------------- //

      private val DIMENSION_KEY = "dimension"
      private val X_KEY = "x"
      private val Y_KEY = "y"
      private val Z_KEY = "z"
      private val LINKS_KEY = "links"
      private val POS_KEY = "pos"

      private val MAX_BACKOFF_TICKS = 20 * 5 // 5 seconds
      private val BACKOFF_SCALE = 2 // multiply by this factor on each failure

      private class EphemeralDelayData(val dimension: Int, val x: Int, val y: Int, val z: Int) {
        var delay: Int = 1
      }

      private var delayData: EphemeralDelayData? = null // null unless delay loading is active

      // return true when we do not want to try again, either because we completely failed or we succeeded
      // return false when things appears just not ready yet
      private fun tryLoadGrid(dimension: Int, x: Int, y: Int, z: Int): Boolean {
        val world = DimensionManager.getWorld(dimension) ?: return false // maybe the dimension isn't loaded yet
        val tileEntity = world.getTileEntity(BlockPos(x, y, z)) ?: return false // maybe the chunk isn't loaded yet
        if (tileEntity !is IActionHost || tileEntity !is IGridHost) {
          return true // failure: looks like the tile was swapped before we could see it
        }
        val gridHost = tileEntity as IGridHost
        val gridNode = gridHost.getGridNode(pos!!) ?: return false // this is typical as the ae network is still loading
        val craftingGrid = AEUtil.getGridCrafting(gridNode.grid) ?: return true // failure: not known right now what would cause this, bail out
        craftingGrid.addNode(gridNode, this)
        controller = tileEntity as TileEntity
        return true // finally! no more retries needed
      }

      private fun delayLoadGrid() {
        delayData?.let { data -> // weird if it was null
          if (tryLoadGrid(data.dimension, data.x, data.y, data.z)) {
            delayData = null // no longer needed
          } else {
            pushDelayLoadBackoff(data.delay * BACKOFF_SCALE)
          }
        }
      }

      private fun pushDelayLoadBackoff(delay: Int) {
        delayData?.let { data -> // should not be called if null
          data.delay = delay.coerceAtMost(MAX_BACKOFF_TICKS)
          EventHandler.scheduleServer(::delayLoadGrid, data.delay)
        }
      }

      override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        stack = AEUtil.itemStorageChannel.createStack(ItemStack(nbt))
        links.addAll(
          nbt.getTagList(LINKS_KEY, NBT.TAG_COMPOUND).map { tag ->
            LinkCache.store(AEApi.instance().storage().loadCraftingLink(tag as NBTTagCompound, this))
          }
        )
        pos = AEPartLocation.fromOrdinal(NbtDataStream.getOptInt(nbt, POS_KEY, AEPartLocation.INTERNAL.ordinal))
        if (nbt.hasKey(DIMENSION_KEY)) {
          val dimension = nbt.getInteger(DIMENSION_KEY)
          val x = nbt.getInteger(X_KEY)
          val y = nbt.getInteger(Y_KEY)
          val z = nbt.getInteger(Z_KEY)
          delayData = EphemeralDelayData(dimension, x, y, z)
          // all of this delay load could have been done nested
          // but i don't want infinite lambda nesting in cases where the load is never ready
          pushDelayLoadBackoff(1)
        }
      }

      override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        stack?.createItemStack()?.writeToNBT(nbt)
        nbt.setNewTagList(
          LINKS_KEY,
          links.map { link ->
            val comp = NBTTagCompound()
            link.writeToNBT(comp)
            comp
          }
        )
        pos?.let { nbt.setInteger(POS_KEY, it.ordinal) }
        controller?.let { ctrl ->
          if (!ctrl.isInvalid) {
            nbt.setInteger(DIMENSION_KEY, ctrl.world.provider.dimension)
            nbt.setInteger(X_KEY, ctrl.pos.x)
            nbt.setInteger(Y_KEY, ctrl.pos.y)
            nbt.setInteger(Z_KEY, ctrl.pos.z)
          }
        }
      }
    }

    class CraftingStatus : AbstractValue() {
      private var isComputing: Boolean = true
      private var link: ICraftingLink? = null
      private var failed = false
      private var reason = "no link"

      fun setLink(value: ICraftingLink) {
        isComputing = false
        link = value
      }

      fun fail(reason: String) {
        isComputing = false
        failed = true
        this.reason = "request failed ($reason)"
      }

      private fun tryCraft(): Either<ICraftingLink, Result> {
        if (isComputing) return Either.Right(result(Unit, "computing"))
        val craft = link
        if (craft == null || failed) return Either.Right(result(false, reason))
        return Either.Left(craft)
      }

      private inline fun asCraft(f: (ICraftingLink) -> Result): Result {
        if (isComputing) return result(Unit, "computing")
        val craft = link
        if (craft == null || failed) return result(false, reason)
        return f(craft)
      }

      @Callback(doc = """function():boolean -- Get whether the crafting request has been canceled.""")
      fun isCanceled(context: Context, args: Arguments): Result
        = asCraft { result(it.isCanceled) }

      @Callback(doc = """function():boolean -- Get whether the crafting request is done.""")
      fun isDone(context: Context, args: Arguments): Result
        = asCraft { result(it.isDone) }

      @Callback(doc = """function():boolean -- Cancels the request. Returns false if the craft cannot be canceled or nil if the link is computing""")
      fun cancel(context: Context, args: Arguments): Result {
        return asCraft { craft ->
          if (craft.isDone) {
            return result(false, "job already completed")
          }
          craft.cancel()
          result(true)
        }
      }

      private val COMPUTING_KEY: String = "computing"
      private val LINK_ID_KEY: String = "link"
      private val FAILED_KEY: String = "failed"
      private val REASON_KEY: String = "reason"

      override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setBoolean(COMPUTING_KEY, isComputing)
        link?.let {
          nbt.setString(LINK_ID_KEY, it.craftingID)
        }
        nbt.setBoolean(FAILED_KEY, failed)
        nbt.setString(REASON_KEY, reason)
      }

      override fun load(nbt: NBTTagCompound) {
        super.load(nbt)

        isComputing = NbtDataStream.getOptBoolean(nbt, COMPUTING_KEY, isComputing)
        val id = NbtDataStream.getOptString(nbt, LINK_ID_KEY, "")
        if (id.isNotEmpty()) {
          LinkCache.store(this, id)
        }
        failed = NbtDataStream.getOptBoolean(nbt, FAILED_KEY, failed)
        reason = NbtDataStream.getOptString(nbt, REASON_KEY, reason)
      }
    }
  }
}
