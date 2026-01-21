package li.cil.oc.integration.appeng

import appeng.api.networking.IGridHost
import appeng.api.networking.security.IActionHost
import appeng.api.util.AEPartLocation
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverController : DriverSidedTileEntity() {
  override fun getTileEntityClass(): Class<*>? = AEUtil.controllerClass()

  override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
    Environment(world.getTileEntity(pos) as TileController)

  class Environment(override val tile: TileController) :
    ManagedTileEntityEnvironment<TileController>(tile, "me_controller"),
    NamedBlock,
    NetworkControl<TileController> {

    override fun preferredName() = "me_controller"

    override val pos: AEPartLocation = AEPartLocation.INTERNAL

    override fun priority() = 5
  }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (AEUtil.isController(stack))
        Environment::class.java
      else null
  }
}

// Kotlin doesn't support intersection types, so we use TileEntity as base
// and cast to required interfaces when needed
private typealias TileController = TileEntity
