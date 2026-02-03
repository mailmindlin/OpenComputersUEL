package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.Result
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.result
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory
import kotlin.math.sqrt
import li.cil.oc.common.entity.Drone as EntityDrone

class Drone(override val agent: EntityDrone): Agent(), DeviceInfo {
  override val node = nodeFactory(Visibility.Network, "drone")
    .withConnector(Settings.get.bufferDrone)
    .create()
  override fun node(): ComponentConnector? = node

  private val deviceInfo_ by lazy {
    mapOf(
      DeviceAttribute.Class to DeviceClass.System,
      DeviceAttribute.Description to "Drone",
      DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product to "Overwatcher",
      DeviceAttribute.Capacity to agent.inventorySize.toString()
    )
  }

  override fun getDeviceInfo() = deviceInfo_

  override fun checkSideForAction(args: Arguments, n: Int) =
    args.checkSideAny(n)

  override fun suckableItems(side: EnumFacing) = entitiesInBlock(EntityItem::class.java, position) + super.suckableItems(side)

  override fun onSuckCollect(entity: EntityItem) {
    if (InventoryUtils.insertIntoInventory(entity.item, InventoryUtils.asItemHandler(inventory), slots = insertionSlots)) {
      world.playSound(agent.player(), agent.posX, agent.posY, agent.posZ, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.2f, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7f + 1) * 2)
    }
  }

  override fun onWorldInteraction(context: Context, duration: Double) {
    super.onWorldInteraction(context, duration * 2)
  }

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():string -- Get the status text currently being displayed in the GUI.")
  fun getStatusText(context: Context, args: Arguments): Result = result(agent.statusText)

  @Suppress("unused")
  @Callback(doc = "function(value:string):string -- Set the status text to display in the GUI, returns new value.")
  fun setStatusText(context: Context, args: Arguments): Result {
    agent.statusText = args.checkString(0)
    context.pause(0.1)
    return result(agent.statusText)
  }

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the current color of the flap lights as an integer encoded RGB value (0xRRGGBB).")
  fun getLightColor(context: Context, args: Arguments): Result = result(agent.lightColor)

  @Suppress("unused")
  @Callback(doc = "function(value:number):number -- Set the color of the flap lights to the specified integer encoded RGB value (0xRRGGBB).")
  fun setLightColor(context: Context, args: Arguments): Result {
    agent.lightColor = args.checkInteger(0)
    context.pause(0.1)
    return result(agent.lightColor)
  }

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function(dx:number, dy:number, dz:number) -- Change the target position by the specified offset.")
  fun move(context: Context, args: Arguments): Result? {
    val dx = args.checkDouble(0).toFloat()
    val dy = args.checkDouble(1).toFloat()
    val dz = args.checkDouble(2).toFloat()
    agent.targetX += dx
    agent.targetY += dy
    agent.targetZ += dz
    return null
  }

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the current distance to the target position.")
  fun getOffset(context: Context, args: Arguments): Result =
    result(agent.getDistance(agent.targetX.toDouble(), agent.targetY.toDouble(), agent.targetZ.toDouble()))

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the current velocity in m/s.")
  fun getVelocity(context: Context, args: Arguments): Result =
    result(sqrt(agent.motionX * agent.motionX + agent.motionY * agent.motionY + agent.motionZ * agent.motionZ) * 20) // per second

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the maximum velocity, in m/s.")
  fun getMaxVelocity(context: Context, args: Arguments): Result
    = result(agent.maxVelocity * 20) // per second

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the currently set acceleration.")
  fun getAcceleration(context: Context, args: Arguments): Result
    = result(agent.targetAcceleration * 20) // per second

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function(value:number):number -- Try to set the acceleration to the specified value and return the new acceleration.")
  fun setAcceleration(context: Context, args: Arguments): Result {
    agent.targetAcceleration = (args.checkDouble(0) / 20.0).toFloat()
    return result(agent.targetAcceleration * 20)
  }
}
