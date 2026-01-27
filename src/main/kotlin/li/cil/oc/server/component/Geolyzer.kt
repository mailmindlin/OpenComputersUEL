package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.event.GeolyzerEvent
import li.cil.oc.api.event.GeolyzerEvent.Analyze
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.item.TabletWrapper
import li.cil.oc.common.tileentity.Microcontroller
import li.cil.oc.common.tileentity.position
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.util.*
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import li.cil.oc.common.entity.Drone as EntityDrone
import li.cil.oc.common.tileentity.Robot as EntityRobot
import li.cil.oc.server.component.traits.WorldControl as TraitWorldControl

class Geolyzer(val host: EnvironmentHost): ManagedEnvironmentKt(), DeviceInfo, TraitWorldControl {
  override val node = newComponentConnector(Visibility.Network, "geolyzer")

  private val deviceInfo = mapOf(
    DeviceAttribute.Class to DeviceClass.Generic,
    DeviceAttribute.Description to "Geolyzer",
    DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product to "Terrain Analyzer MkII",
    DeviceAttribute.Capacity to Settings.get.geolyzerRange.toString()
  )

  override fun getDeviceInfo() = deviceInfo

  // ----------------------------------------------------------------------- //

  override fun checkSideForAction(args: Arguments, n: Int): EnumFacing {
    val side = args.checkSideAny(n)
    return when (host) {
      is EntityRobot -> host.proxy.toGlobal(side)
      is EntityDrone -> host.toGlobal(side)
      is Microcontroller -> host.toLocal(side)!! // not really sure what it is reversed for microcontrollers
      is TabletWrapper -> host.toGlobal(side)
      else -> side
    }
  }

  override val position: BlockPosition = when (host) {
    is EntityRobot -> host.proxy.position
    is EntityDrone -> BlockPosition(host.position, host.world)
    is Microcontroller -> host.position
    is TabletWrapper -> BlockPosition(host.xPosition(), host.yPosition(), host.zPosition(), host.world)
    else -> BlockPosition(host)
  }

  private val canSeeSky: Boolean get() {
    val blockPos = position.offset(EnumFacing.UP)
    return !host.world.provider.isNether && host.world.canBlockSeeSky(blockPos.toBlockPos())
  }

  @Callback(doc = """function():boolean -- Returns whether there is a clear line of sight to the sky directly above.""")
  fun canSeeSky(computer: Context, args: Arguments): Result = result(canSeeSky)

  @Callback(doc = """function():boolean -- Return whether the sun is currently visible directly above.""")
  fun isSunVisible(computer: Context, args: Arguments): Result {
    val blockPos = BlockPosition(host).offset(EnumFacing.UP)
    return result(
      host.world.isDaytime &&
      canSeeSky &&
        (!host.world.getBiome(blockPos.toBlockPos()).canRain() || (!host.world.isRaining && !host.world.isThundering)))
  }

  data class ScanArgs(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
    val optIndex: Int
  )

  @Callback(doc = """function(x:number, z:number[, y:number, w:number, d:number, h:number][, ignoreReplaceable:boolean|options:table]):table -- Analyzes the density of the column at the specified relative coordinates.""")
  fun scan(computer: Context, args: Arguments): Result {
    val (minX, minY, minZ, maxX, maxY, maxZ, optIndex) = getScanArgs(args)
    val volume = (maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - minY + 1)
    if (volume > 64) throw IllegalArgumentException("volume too large (maximum is 64)")
    val options = if (args.isBoolean(optIndex))
      mapOf("includeReplaceable" to !args.checkBoolean(optIndex))
    else
      args.optTable(optIndex, emptyMap<Any?, Any?>())

    if (minX.absoluteValue > Settings.get.geolyzerRange || maxX.absoluteValue > Settings.get.geolyzerRange ||
      minY.absoluteValue > Settings.get.geolyzerRange || maxY.absoluteValue > Settings.get.geolyzerRange ||
      minZ.absoluteValue > Settings.get.geolyzerRange || maxZ.absoluteValue > Settings.get.geolyzerRange) {
      throw IllegalArgumentException("location out of bounds")
    }

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result(Unit, "not enough energy")

    val event = GeolyzerEvent.Scan(host, options, minX, minY, minZ, maxX, maxY, maxZ)
    MinecraftForge.EVENT_BUS.post(event)
    return if (event.isCanceled) result(Unit, "scan was canceled")
    else result(event.data)
  }

  private fun getScanArgs(args: Arguments): ScanArgs {
    val minX = args.checkInteger(0)
    val minZ = args.checkInteger(1)
    if (args.isInteger(2) && args.isInteger(3) && args.isInteger(4) && args.isInteger(5)) {
      val minY = args.checkInteger(2)
      val w = args.checkInteger(3)
      val d = args.checkInteger(4)
      val h = args.checkInteger(5)
      val maxX = minX + w - 1
      val maxY = minY + h - 1
      val maxZ = minZ + d - 1

      return ScanArgs(
        min(minX, maxX), min(minY, maxY), min(minZ, maxZ),
        max(minX, maxX), max(minY, maxY), max(minZ, maxZ),
        6
      )
    } else {
      return ScanArgs(minX, -32, minZ, minX, 31, minZ, 2)
    }
  }

  @Callback(doc = """function(side:number[,options:table]):table -- Get some information on a directly adjacent block.""")
  fun analyze(computer: Context, args: Arguments): Result {
    if (!Settings.get.allowItemStackInspection)
      return result(Unit, "not enabled in config")

    val side = args.checkSideAny(0)
    val globalSide = when (host) {
      is Rotatable -> host.toGlobal(side)!!
      else -> side
    }

    val options = args.optTable(1, emptyMap<Any?, Any?>())

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result(Unit, "not enough energy")

    val globalPos = BlockPosition(host).offset(globalSide)
    val event = Analyze(host, options, globalPos.toBlockPos())
    MinecraftForge.EVENT_BUS.post(event)
    return if (event.isCanceled) result(Unit, "scan was canceled")
    else result(event.data)
  }

  @Callback(doc = """function(side:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack representation of the block on the specified side in a database component.""")
  fun store(computer: Context, args: Arguments): Result {
    val side = args.checkSideAny(0)
    val globalSide = when (host) {
      is Rotatable -> host.toGlobal(side)!!
      else -> side
    }

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result(Unit, "not enough energy")

    val blockPos = BlockPosition(host).offset(globalSide)
    val block = host.world.getBlock(blockPos)
    val item = Item.getItemFromBlock(block) ?: return result(Unit, "block has no registered item representation")
    val metadata = host.world.getBlockMetadata(blockPos)
    val damage = block.damageDropped(metadata)
    val stack = ItemStack(item, 1, damage)
    return DatabaseAccess.withDatabase(node, args.checkString(1)) { database ->
      val toSlot = args.checkSlot(database.data, 2)
      val nonEmpty = database.getStackInSlot(toSlot) != ItemStack.EMPTY // not the same as isEmpty! zero size stacks!
      database.setStackInSlot(toSlot, stack)
      result(nonEmpty)
    }
  }

  override fun onMessage(message: Message) {
    super.onMessage(message)
    val message = TabletUseMessage.tryParse(message) ?: return

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost)) return

    val event = Analyze(host, emptyMap<Any?, Any?>(), message.blockPos.toBlockPos())
    MinecraftForge.EVENT_BUS.post(event)
    if (event.isCanceled) return
    val nbt = message.nbt
    for ((key, value) in event.data) {
      when (value) {
        is Number -> { nbt.setDouble(key, value.toDouble()) }
        is String -> { if (value.isNotEmpty()) nbt.setString(key, value) }
        else -> {}// Unsupported, ignore.
      }
    }
  }
}
