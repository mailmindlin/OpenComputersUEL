package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.optSideAny
import li.cil.oc.util.optSideForAction
import net.minecraft.block.BlockPistonBase
import net.minecraft.block.material.EnumPushReaction
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory
import li.cil.oc.api.internal.Drone as InternalDrone
import li.cil.oc.api.internal.Rotatable as InternalRotatable
import li.cil.oc.api.internal.Tablet as InternalTablet
import li.cil.oc.server.PacketSender as ServerPacketSender

object PistonTraits {
    interface ExtendAware {
        val host: EnvironmentHost
        fun pushOrigin(side: EnumFacing): BlockPosition = BlockPosition(host)
        fun pushDirection(args: Arguments, index: Int): EnumFacing
    }

    interface DroneLike : ExtendAware {
        override fun pushDirection(args: Arguments, index: Int): EnumFacing = args.optSideAny(index, EnumFacing.SOUTH)
    }

    interface RotatableLike : ExtendAware {
        val rotatable: InternalRotatable
        override fun pushDirection(args: Arguments, index: Int): EnumFacing =
            (rotatable as InternalRotatable).toGlobal(args.optSideForAction(index, EnumFacing.SOUTH))
    }

    interface TabletLike : ExtendAware {
        val tablet: InternalTablet
        override fun pushOrigin(side: EnumFacing): BlockPosition =
            if (side == EnumFacing.DOWN && tablet.player().eyeHeight > 1)
                BlockPosition(host).offset(EnumFacing.DOWN)
            else
                BlockPosition(host)
    }
}

abstract class UpgradePiston(override val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfoKt, PistonTraits.ExtendAware {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("piston")
        .withConnector()
        .create()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Piston upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Displacer II+"
    )

    open val isSticky: Boolean = false

    @Callback(doc = "function():boolean -- Returns true if the piston is sticky, i.e. it can also pull.")
    fun isSticky(context: Context, args: Arguments): Array<Any?> = result(isSticky)

    protected fun doPistonAction(context: Context, side: EnumFacing, extending: Boolean): Array<Any?> {
        val sound = if (extending) SoundEvents.BLOCK_PISTON_EXTEND.registryName else SoundEvents.BLOCK_PISTON_CONTRACT.registryName
        val hostPos = pushOrigin(side).toBlockPos()
        val piston = BlockPistonBase(isSticky)

        if (!extending) {
            if (!isSticky) {
                // this is a bug in oc code
                throw NoSuchMethodError("piston is not sticky. does not have pull")
            }
            // make sure that any obstruction block has breaking mobility
            val innerBlockPos = hostPos.offset(side)
            val innerBlockState = host.world().getBlockState(innerBlockPos)
            if (innerBlockState != null) {
                if (!innerBlockState.block.isAir(innerBlockState, host.world(), innerBlockPos)) {
                    if (innerBlockState.pushReaction != EnumPushReaction.DESTROY) {
                        return result(false, "path is obstructed")
                    }
                }
            }
        }

        return if (piston.doMove(host.world(), hostPos, side, extending)) {
            // send piston extend sound to clients
            synchronized(host) {
                ServerPacketSender.sendSound(
                    host.world, hostPos.x.toDouble(), hostPos.y.toDouble(), hostPos.z.toDouble(),
                    sound, SoundCategory.BLOCKS, range = 15.0
                )
            }
            context.pause(1.0 / 20.0)
            result(true)
        } else {
            result(false, "move failed")
        }
    }

    @Callback(doc = "function([side:number]):boolean -- Tries to push the block on the specified side of the container of the upgrade. Defaults to front.")
    fun push(context: Context, args: Arguments): Array<Any?> {
        val side = pushDirection(args, index = 0)
        return doPistonAction(context, side, true)
    }


    class Drone(drone: InternalDrone) : UpgradePiston(drone), PistonTraits.DroneLike
    open class Rotatable(override val rotatable: InternalRotatable) : UpgradePiston(rotatable), PistonTraits.RotatableLike
    class Tablet(override val tablet: InternalTablet) : Rotatable(tablet), PistonTraits.TabletLike
}

abstract class UpgradeStickyPiston(host: EnvironmentHost) : UpgradePiston(host) {
    override val isSticky: Boolean = true

    @Callback(doc = "function([side:number]):boolean -- Tries to reach out to the side given (default front) and pull a block similar to a vanilla sticky piston.")
    fun pull(context: Context, args: Arguments): Array<Any?> {
        val side = pushDirection(args, index = 0)
        return doPistonAction(context, side, false)
    }
}

object UpgradePiston {
    class Drone(drone: InternalDrone) : UpgradePiston(drone), PistonTraits.DroneLike

    open class Rotatable(override val rotatable: InternalRotatable) : UpgradePiston(rotatable), PistonTraits.RotatableLike

    class Tablet(override val tablet: InternalTablet) : Rotatable(tablet), PistonTraits.TabletLike
}

object UpgradeStickyPiston {
    class Drone(drone: InternalDrone) : UpgradeStickyPiston(drone), PistonTraits.DroneLike

    open class Rotatable(override val rotatable: InternalRotatable) : UpgradeStickyPiston(rotatable), PistonTraits.RotatableLike

    class Tablet(override val tablet: InternalTablet) : Rotatable(tablet), PistonTraits.TabletLike
}
