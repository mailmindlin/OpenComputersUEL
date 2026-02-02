package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.FileSystem
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.ToolDurabilityProviders
import li.cil.oc.server.PacketSender
import li.cil.oc.util.*
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumParticleTypes
import li.cil.oc.common.tileentity.Robot as TERobot

class Robot(override val agent: TERobot): Agent(), DeviceInfo {
  override val node = nodeFactory(Visibility.Network).
    withComponent("robot").
    withConnector(Settings.get.bufferRobot).
    create()

  private val romRobot: ManagedEnvironment? = FileSystem.asManagedEnvironment(FileSystem.fromClass(OpenComputers::class.java, Settings.resourceDomain, "lua/component/robot"), "robot")

  private val deviceInfo_ by lazy {
    mapOf(
      DeviceAttribute.Class to DeviceInfo.DeviceClass.System,
      DeviceAttribute.Description to "Robot",
      DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product to "Caterpillar",
      DeviceAttribute.Capacity to agent.sizeInventory.toString()
    )
  }

  override fun getDeviceInfo() = deviceInfo_

  // ----------------------------------------------------------------------- //

  override fun checkSideForAction(args: Arguments, n: Int) = agent.toGlobal(args.checkSideForAction(n))!!

  override fun onWorldInteraction(context: Context, duration: Double) {
    super.onWorldInteraction(context, duration)
    agent.animateSwing(duration)
  }

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the current color of the activity light as an integer encoded RGB value (0xRRGGBB).")
  fun getLightColor(context: Context, args: Arguments): Result = result(agent.info.lightColor)

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function(value:number):number -- Set the color of the activity light to the specified integer encoded RGB value (0xRRGGBB).")
  fun setLightColor(context: Context, args: Arguments): Result {
    agent.setLightColor(args.checkInteger(0))
    context.pause(0.1)
    return result(agent.info.lightColor)
  }

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():number -- Get the durability of the currently equipped tool.")
  fun durability(context: Context, args: Arguments): Result {

    val item = agent.equipmentInventory.getStackInSlot(0).notEmpty() ?: return result(Unit, "no tool equipped")

    val durability = ToolDurabilityProviders.getDurability(item) ?: return result(Unit, "tool cannot be damaged")
    return result(durability)
  }

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function(direction:number):boolean -- Move in the specified direction.")
  fun move(context: Context, args: Arguments): Result {
    val direction = agent.toGlobal(args.checkSideForMovement(0))!!
    if (agent.isAnimatingMove) {
      // This shouldn't really happen due to delays being enforced, but just to
      // be on the safe side...
      return result(Unit, "already moving")
    }

    val (something, what) = blockContent(direction)
    if (something) {
      context.pause(0.4)
      PacketSender.sendParticleEffect(BlockPosition(agent), EnumParticleTypes.CRIT, 8, 0.25, direction)
      return result(Unit, what)
    }

    if (!node!!.tryChangeBuffer(-Settings.get.robotMoveCost))
      return result(Unit, "not enough energy")

    if (!agent.move(direction)) {
      node.changeBuffer(Settings.get.robotMoveCost)
      context.pause(0.4)
      PacketSender.sendParticleEffect(BlockPosition(agent), EnumParticleTypes.CRIT, 8, 0.25, direction)
      result(Unit, "impossible move")
    }
    context.pause(Settings.get.moveDelay)
    return result(true)
  }

  @Suppress("unused")
  @Callback(doc = "function(clockwise:boolean):boolean -- Rotate in the specified direction.")
  fun turn(context: Context, args: Arguments): Result {
    val clockwise = args.checkBoolean(0)
    if (!node!!.tryChangeBuffer(-Settings.get.robotTurnCost))
      return result(Unit, "not enough energy")

    agent.rotate(if (clockwise) EnumFacing.UP else EnumFacing.DOWN)
    agent.animateTurn(clockwise, Settings.get.turnDelay)
    context.pause(Settings.get.turnDelay)
    return result(true)
  }

  // ----------------------------------------------------------------------- //

  override fun onConnect(node: Node) {
    super.onConnect(node)
    if (node == this.node) {
      romRobot?.let { fs ->
        (fs.node() as Component).setVisibility(Visibility.Network)
        node.connect(fs.node())
      }
    }
  }

  override fun onMessage(message: Message) {
    super.onMessage(message)
    if (message.name() == "network.message" && message.source() != agent.node()) {
      val data = message.data()
      if (data.size == 1) {
        val packet = data[0]
        if (packet is Packet) {
          agent.proxy.node()!!.sendToReachable(message.name(), packet)
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //

  private final val RomRobotTag = "romRobot"

  override fun load(nbt: NBTTagCompound) {
    super.load(nbt)
    romRobot?.load(nbt.getCompoundTag(RomRobotTag))
  }

  override fun save(nbt: NBTTagCompound) {
    super.save(nbt)
    romRobot?.let { fs ->
      nbt.setNewCompoundTag(RomRobotTag, fs::save)
    }
  }
}
