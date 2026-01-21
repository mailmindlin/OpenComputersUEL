package li.cil.oc.integration.minecraft

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.BundledRedstone.RedstoneProvider
import li.cil.oc.integration.vanilla.ConverterFluidContainerItem
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld.extendBlockPos
import li.cil.oc.util.ExtendedWorld.extendWorld
import net.minecraft.block.BlockRedstoneWire
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge

object ModMinecraft : ModProxy(), RedstoneProvider {
    override fun getMod(): Mods = Mods.Minecraft

    override fun initialize() {
        Driver.add(DriverBeacon)
        Driver.add(DriverBrewingStand)
        Driver.add(DriverComparator)
        Driver.add(DriverFurnace)
        Driver.add(DriverMobSpawner)
        Driver.add(DriverNoteBlock)
        Driver.add(DriverRecordPlayer)

        Driver.add(DriverBeacon.Provider)
        Driver.add(DriverBrewingStand.Provider)
        Driver.add(DriverComparator.Provider)
        Driver.add(DriverFurnace.Provider)
        Driver.add(DriverMobSpawner.Provider)
        Driver.add(DriverNoteBlock.Provider)
        Driver.add(DriverRecordPlayer.Provider)

        if (Settings.get.enableInventoryDriver) {
            Driver.add(DriverInventory())
        }
        if (Settings.get.enableTankDriver) {
            Driver.add(DriverFluidHandler())
            Driver.add(DriverFluidTank())
        }
        if (Settings.get.enableCommandBlockDriver) {
            Driver.add(DriverCommandBlock)
        }

        Driver.add(ConverterFluidContainerItem)
        Driver.add(ConverterFluidStack)
        Driver.add(ConverterFluidTankInfo)
        Driver.add(ConverterFluidTankProperties)
        Driver.add(ConverterItemStack)
        Driver.add(ConverterNBT)
        Driver.add(ConverterWorld)
        Driver.add(ConverterWorldProvider)

        RecipeHandler.init()

        BundledRedstone.addProvider(this)

        MinecraftForge.EVENT_BUS.register(EventHandlerVanilla)
    }

    override fun computeInput(pos: BlockPosition, side: EnumFacing): Int {
        val world = pos.world.get()
        return maxOf(
            world.computeRedstoneSignal(pos, side),
            if (world.getBlock(pos.offset(side)) == Blocks.REDSTONE_WIRE)
                world.getBlockMetadata(pos.offset(side)).getValue(BlockRedstoneWire.POWER)
            else 0
        )
    }

    override fun computeBundledInput(pos: BlockPosition, side: EnumFacing): IntArray? = null
}
