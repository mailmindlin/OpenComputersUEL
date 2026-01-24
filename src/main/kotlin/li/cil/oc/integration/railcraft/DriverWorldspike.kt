package li.cil.oc.integration.railcraft

import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.server.component.Result
import li.cil.oc.util.ResultWrapper.result
import mods.railcraft.common.blocks.machine.worldspike.TileWorldspike
import mods.railcraft.common.blocks.machine.worldspike.WorldspikeVariant
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.Objects

object DriverWorldspike : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileWorldspike::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileWorldspike)

    class Environment(val tile: TileWorldspike) :
        ManagedTileEntityEnvironment<TileWorldspike>(tile, "worldspike"), NamedBlock {

        override fun preferredName() = "worldspike"

        override fun priority() = 5

        @Callback(doc = "function():int -- Get the amount of fuel.")
        fun getFuel(context: Context, args: Arguments): Result =
            result(tile.fuelAmount)

        @Callback(doc = "function():string -- Get the anchor owner name.")
        fun getOwner(context: Context, args: Arguments): Result {
            val owner = tile.owner
            return if (owner == null || owner.name == null || Objects.equals(owner.name, "[unknown]")) {
                result()
            } else {
                result(owner.name)
            }
        }

        @Callback(doc = "function():string -- Get the anchor type.")
        fun getType(context: Context, args: Arguments): Result =
            when (tile.machineType) {
                WorldspikeVariant.STANDARD -> result("world")
                WorldspikeVariant.ADMIN -> result("admin")
                WorldspikeVariant.PERSONAL -> result("personal")
                WorldspikeVariant.PASSIVE -> result("passive")
                else -> result("missing")
            }

        @Callback(doc = "function():table -- Get the anchor fuel slot's contents.")
        fun getFuelSlotContents(context: Context, args: Arguments): Result =
            if (tile.needsFuel()) {
                result(tile.getStackInSlot(0))
            } else {
                result()
            }

        @Callback(doc = "function():boolean -- If the anchor is disabled (powered by redstone).")
        fun isDisabled(context: Context, args: Arguments): Result =
            result(tile.isPowered)
    }
}
