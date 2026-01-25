package li.cil.oc.server.component

import li.cil.oc.Settings
import li.cil.oc.api.event.RobotPlaceInAirEvent
import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.agent.ActivationType
import li.cil.oc.server.agent.Player
import li.cil.oc.util.*
import net.minecraft.block.Block
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityMinecart
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.common.MinecraftForge
import li.cil.oc.api.internal.Agent as InternalAgent
import li.cil.oc.common.entity.Drone as EntityDrone
import li.cil.oc.server.component.traits.InventoryControl as TraitInventoryControl
import li.cil.oc.server.component.traits.InventoryWorldControl as TraitInventoryWorldControl
import li.cil.oc.server.component.traits.TankAware as TraitTankAware
import li.cil.oc.server.component.traits.TankControl as TraitTankControl
import li.cil.oc.server.component.traits.TankWorldControl as TraitTankWorldControl
import li.cil.oc.server.component.traits.WorldControl as TraitWorldControl

abstract class Agent: ManagedEnvironmentKt(), TraitWorldControl, TraitInventoryControl, TraitInventoryWorldControl, TraitTankAware, TraitTankControl, TraitTankWorldControl {
  protected abstract val agent: InternalAgent

  override val position get() = BlockPosition(agent)

  override val fakePlayer: EntityPlayer = agent.player()

  protected fun rotatedPlayer(facing: EnumFacing = agent.facing(), side: EnumFacing = agent.facing()): Player {
    val player = agent.player() as Player
    Player.updatePositionAndRotation(player, facing, side)
    // no need to set inventory, calling agent.Player already did that
    //Player.setInventoryPlayerItems(player)
    return player
  }

  // ----------------------------------------------------------------------- //

  override val inventory: IInventory get() = agent.mainInventory()
  override var selectedSlot: Int
    get() = agent.selectedSlot()
    set(slot) { agent.setSelectedSlot(slot) }

  // ----------------------------------------------------------------------- //

  override val tank: MultiTank
    get() = agent.tank()

  override var selectedTank: Int
    get() = agent.selectedTank()
    set(value) { agent.setSelectedTank(value) }

  // ----------------------------------------------------------------------- //

  private fun canPlaceInAir(): Boolean {
    val event = RobotPlaceInAirEvent(agent)
    MinecraftForge.EVENT_BUS.post(event)
    return event.isAllowed
  }

  open fun onWorldInteraction(context: Context, duration: Double) {
    context.pause(duration)
  }

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = "function():string -- Get the name of the agent.")
  fun name(context: Context, args: Arguments): Result = result(agent.name())

  @Suppress("unused")
  @Callback(doc = "function(side:number[, face:number=side[, sneaky:boolean=false]]):boolean, string -- Perform a 'left click' towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
  fun swing(context: Context, args: Arguments): Result {
    // Swing the equipped tool (left click).
    val facing = checkSideForAction(args, 0)
    val sides =
      if (args.isInteger(1)) {
        sequenceOf(checkSideForFace(args, 1, facing))
      } else {
        // Always try the direction we're looking first.
        sequenceOf(facing) + EnumFacing.values().filter { side -> side != facing && side != facing.opposite }
      }
    val sneaky = args.isBoolean(2) && args.checkBoolean(2)

    fun triggerDelay(delay: Double = Settings.get.swingDelay) {
      onWorldInteraction(context, delay)
    }

    fun attack(player: Player, entity: Entity): Pair<Boolean, String> {
      beginConsumeDrops(entity)
      player.attackTargetEntityWithCurrentItem(entity)
      // Mine carts have to be hit quickly in succession to break, so we click
      // until it breaks. But avoid an infinite loop... you never know.
      if (entity is EntityMinecart) {
        for (i in 0 until 10) {
          if (entity.isDead) break
          player.attackTargetEntityWithCurrentItem(entity)
        }
      }
      endConsumeDrops(player, entity)
      triggerDelay()
      return Pair(true, "entity")
    }
    fun click(player: Player, pos: BlockPos, side: EnumFacing): Pair<Boolean, String> {
      val breakTime = player.clickBlock(pos, side)
      val broke = breakTime > 0
      if (broke) {
        triggerDelay(breakTime)
      }
      return Pair(broke, "block")
    }

    var reason: String? = null
    for (side in sides) {
      val player = rotatedPlayer(facing, side)
      player.isSneaking = sneaky

      val (success, what) = run {
        val hit = pick(player, Settings.get.swingRange)
        when (hit?.typeOfHit) {
          RayTraceResult.Type.ENTITY ->
            attack(player, hit.entityHit)
          RayTraceResult.Type.BLOCK ->
            click(player, hit.blockPos, hit.sideHit)
          else -> {
            // Retry with full block bounds, disregarding swing range.
            val entity = player.closestEntity(EntityLivingBase::class.java)
            if (entity != null) {
              attack(player, entity)
            } else if (world.extinguishFire(player, position, facing)) {
                triggerDelay()
              Pair(true, "fire")
            } else {
              Pair(false, "air")
            }
          }
        }
      }

      player.isSneaking = false
      if (success) {
        return result(true, what)
      }
      reason = reason ?: what
    }

    // all side attempts failed - but there could be a partial block that is hard to "see"
    val (hasBlock, _) = blockContent(facing)
    if (hasBlock) {
      val blockPos = position.offset(facing)
      val player = rotatedPlayer(facing, facing)
      player.isSneaking = sneaky
      val (ok, why) = click(player, blockPos.toBlockPos(), facing)
      player.isSneaking = false
      return result(ok, why)
    }

    return result(false, reason)
  }

  private fun Arguments.faces(index: Int, facing: EnumFacing): Sequence<EnumFacing> {
    if (isInteger(index)) {
      return sequenceOf(checkSideForFace(this, index, facing))
    } else {
      // Always try the direction we're looking first.
      return sequenceOf(facing) + EnumFacing.values().filter { side -> side != facing && side != facing.opposite }
    }
  }

  @Callback(doc = "function(side:number[, face:number=side[, sneaky:boolean=false[, duration:number=0]]]):boolean, string -- Perform a 'right click' towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
  fun use(context: Context, args: Arguments): Result {
    val facing = checkSideForAction(args, 0)
    val sides = args.faces(1, facing)
    val sneaky = args.isBoolean(2) && args.checkBoolean(2)
    val duration =
      if (args.isDouble(3)) args.checkDouble(3)
      else 0.0

    fun triggerDelay() {
      onWorldInteraction(context, Settings.get.useDelay)
    }
    fun activationResult(activationType: ActivationType): Pair<Boolean, String> =
      when (activationType) {
        ActivationType.BlockActivated -> {
          triggerDelay()
          Pair(true, "block_activated")
        }
        ActivationType.ItemPlaced -> {
          triggerDelay()
          Pair(true, "item_placed")
        }
        ActivationType.ItemUsed -> {
          triggerDelay()
          Pair(true, "item_used")
        }
        else -> Pair(false, "")
      }

    fun interact(player: Player, entity: Entity): EnumActionResult {
      beginConsumeDrops(entity)
      val result = player.interactOn(entity, EnumHand.MAIN_HAND)
      endConsumeDrops(player, entity)
      return result
    }

    for (side in sides) {
      val player = rotatedPlayer(facing, side)
      player.isSneaking = sneaky

      /*val hit = pick(player, Settings.get.useAndPlaceRange)
      val (success, what) = Option(pick(player, Settings.get.useAndPlaceRange)) match {
        case Some(hit) if hit.typeOfHit == RayTraceResult.Type.ENTITY && interact(player, hit.entityHit) == EnumActionResult.SUCCESS =>
          triggerDelay()
          (true, "item_interacted")
        case Some(hit) if hit.typeOfHit == RayTraceResult.Type.BLOCK =>
          val (blockPos, hx, hy, hz) = clickParamsFromHit(hit)
          activationResult(player.activateBlockOrUseItem(blockPos, hit.sideHit, hx, hy, hz, duration))
        case _ =>
          (if (canPlaceInAir) {
            val (blockPos, hx, hy, hz) = clickParamsForPlace(facing)
            if (player.placeBlock(0, blockPos, facing, hx, hy, hz))
              ActivationType.ItemPlaced
            else {
              val (blockPos, hx, hy, hz) = clickParamsForItemUse(facing, side)
              player.activateBlockOrUseItem(blockPos, side.getOpposite, hx, hy, hz, duration)
            }
          } else ActivationType.None) match {
            case ActivationType.None =>
              if (player.useEquippedItem(duration)) {
                triggerDelay()
                (true, "item_used")
              }
              else (false, "air")
            case activationType => activationResult(activationType)
          }
      }*/

      player.isSneaking = false
      /*if (success) {
        return result(true, what)
      }*/
      TODO()
    }

    return result(false)
  }

  @Callback(doc = "function(side:number[, face:number=side[, sneaky:boolean=false]]):boolean -- Place a block towards the specified side. The `face' allows a more precise click calibration, and is relative to the targeted blockspace.")
  fun place(context: Context, args: Arguments): Result {
    val facing = checkSideForAction(args, 0)
    val sides = args.faces(1, facing)
    val sneaky = args.isBoolean(2) && args.checkBoolean(2)
    val stack = agent.mainInventory().getStackInSlot(agent.selectedSlot())
    if (stack.isEmpty) {
      return result(Unit, "nothing selected")
    }

    for (side in sides) {
      val player = rotatedPlayer(facing, side)
      player.isSneaking = sneaky
      val hit = pick(player, Settings.get.useAndPlaceRange)
      val success = when {
        hit?.typeOfHit == RayTraceResult.Type.BLOCK -> {
          val (blockPos, h) = hit.clickParamsFromHit()
          player.placeBlock(agent.selectedSlot(), blockPos, hit.sideHit, h.x, h.y, h.z)
        }
        hit == null && canPlaceInAir() && player.closestEntity(Entity::class.java) == null -> {
          val (blockPos, h) = facing.clickParamsForPlace()
          // blockPos here is the position of the agent
          // When a robot uses angel placement, the ItemBlock code offsets the pos to EnumFacing
          // but for a drone, the block at its position is air, which is replaceable, and thus
          // ItemBlock does not offset the position. We can do it here to correct that, and the code
          // here is still correct for the robot's use case
          val adjustedPos: BlockPos = blockPos.offset(facing)
          // adjustedPos is the position we want to place the block
          // but onItemUse will try to adjust the placement if the target position is not replaceable
          // we don't want that
          val block: Block = world.getBlockState(adjustedPos).block
          if (block.isReplaceable(world, adjustedPos)) {
            player.placeBlock(agent.selectedSlot(), adjustedPos, facing, h.x, h.y, h.z)
          } else {
            false
          }
        }
        else -> false
      }
      player.isSneaking = false
      if (success) {
        onWorldInteraction(context, Settings.get.placeDelay)
        return result(true)
      }
    }

    return result(false)
  }

  // ----------------------------------------------------------------------- //

  protected fun beginConsumeDrops(entity: Entity) {
    entity.captureDrops = true
  }


  protected fun endConsumeDrops(player: Player, entity: Entity) {
    entity.captureDrops = false
    // this inventory size check is a HACK to preserve old behavior that a agent can suck items out
    // of the capturedDrops. Ideally, we'd only pick up items off the ground. We could clear the
    // capturedDrops when Player.attackTargetEntityWithCurrentItem() is called
    // But this felt slightly less hacky, slightly
    if (player.inventory.sizeInventory > 0) {
      for (drop in entity.capturedDrops) {
        if (!drop.isDead) {
          val stack = drop.item
          InventoryUtils.addToPlayerInventory(stack, player, spawnInWorld = false)
        }
      }
    }
    entity.capturedDrops.clear()
  }

  // ----------------------------------------------------------------------- //

  private fun checkSideForFace(args: Arguments, n: Int, facing: EnumFacing): EnumFacing = agent.toGlobal(args.checkSideForFace(n, agent.toLocal(facing)))

  protected fun pick(player: Player, range: Double): RayTraceResult? {
    val origin = Vec3d(
      player.posX + player.facing.xOffset * 0.5,
      player.posY + player.facing.yOffset * 0.5,
      player.posZ + player.facing.zOffset * 0.5)
    val blockCenter = origin.add(
      player.facing.xOffset * 0.51,
      player.facing.yOffset * 0.51,
      player.facing.zOffset * 0.51)
    val target = blockCenter.add(
      player.side.xOffset * range,
      player.side.yOffset * range,
      player.side.zOffset * range)

    val hit = world.rayTraceBlocks(origin, target)
    val entity = player.closestEntity(Entity::class.java)
    if (entity is EntityLivingBase || entity is EntityMinecart || entity is EntityDrone)
      if (hit == null || Vec3d(player.posX, player.posY, player.posZ).distanceTo(hit.hitVec) > player.getDistance(entity))
        return RayTraceResult(entity)
    return hit
  }

  private fun RayTraceResult.clickParamsFromHit(): Pair<BlockPos, Vec3f>
    = Pair (blockPos, hitVec.toFloat() - blockPos)

  private fun clickParamsForItemUse(facing: EnumFacing, side: EnumFacing): Pair<BlockPos, Vec3f> {
    val blockPos = position.offset(facing).offset(side)
    return Pair(blockPos.toBlockPos(), side.offset.axpy(-0.5f, 0.5f))
  }

  private fun EnumFacing.clickParamsForPlace(): Pair<BlockPos, Vec3f>
    = Pair(position.toBlockPos(), this.offset.axpy(0.5f, 0.5f))
}
