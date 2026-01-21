package li.cil.oc.common.init

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.common.Tier
import li.cil.oc.common.block.*
import li.cil.oc.common.recipe.Recipes
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.fml.common.registry.GameRegistry

object Blocks {
    @JvmStatic
    fun init() {
        registerTileEntity(li.cil.oc.common.tileentity.Adapter::class.java, Settings.namespace + "adapter")
        registerTileEntity(li.cil.oc.common.tileentity.Assembler::class.java, Settings.namespace + "assembler")
        registerTileEntity(li.cil.oc.common.tileentity.Cable::class.java, Settings.namespace + "cable")
        registerTileEntity(li.cil.oc.common.tileentity.Capacitor::class.java, Settings.namespace + "capacitor")
        registerTileEntity(li.cil.oc.common.tileentity.CarpetedCapacitor::class.java, Settings.namespace + "carpetedCapacitor")
        registerTileEntity(li.cil.oc.common.tileentity.Case::class.java, Settings.namespace + "case")
        registerTileEntity(li.cil.oc.common.tileentity.Charger::class.java, Settings.namespace + "charger")
        registerTileEntity(li.cil.oc.common.tileentity.DiskDrive::class.java, Settings.namespace + "diskDrive")
        registerTileEntity(li.cil.oc.common.tileentity.Disassembler::class.java, Settings.namespace + "disassembler")
        registerTileEntity(li.cil.oc.common.tileentity.Keyboard::class.java, Settings.namespace + "keyboard")
        registerTileEntity(li.cil.oc.common.tileentity.Hologram::class.java, Settings.namespace + "hologram")
        registerTileEntity(li.cil.oc.common.tileentity.Geolyzer::class.java, Settings.namespace + "geolyzer")
        registerTileEntity(li.cil.oc.common.tileentity.Microcontroller::class.java, Settings.namespace + "microcontroller")
        registerTileEntity(li.cil.oc.common.tileentity.MotionSensor::class.java, Settings.namespace + "motionSensor")
        registerTileEntity(li.cil.oc.common.tileentity.NetSplitter::class.java, Settings.namespace + "netSplitter")
        registerTileEntity(li.cil.oc.common.tileentity.PowerConverter::class.java, Settings.namespace + "powerConverter")
        registerTileEntity(li.cil.oc.common.tileentity.PowerDistributor::class.java, Settings.namespace + "powerDistributor")
        registerTileEntity(li.cil.oc.common.tileentity.Print::class.java, Settings.namespace + "print")
        registerTileEntity(li.cil.oc.common.tileentity.Printer::class.java, Settings.namespace + "printer")
        registerTileEntity(li.cil.oc.common.tileentity.Raid::class.java, Settings.namespace + "raid")
        registerTileEntity(li.cil.oc.common.tileentity.Redstone::class.java, Settings.namespace + "redstone")
        registerTileEntity(li.cil.oc.common.tileentity.Relay::class.java, Settings.namespace + "relay")
        registerTileEntity(li.cil.oc.common.tileentity.RobotProxy::class.java, Settings.namespace + "robot")
        registerTileEntity(li.cil.oc.common.tileentity.Screen::class.java, Settings.namespace + "screen")
        registerTileEntity(li.cil.oc.common.tileentity.Rack::class.java, Settings.namespace + "rack")
        registerTileEntity(li.cil.oc.common.tileentity.Transposer::class.java, Settings.namespace + "transposer")
        registerTileEntity(li.cil.oc.common.tileentity.Waypoint::class.java, Settings.namespace + "waypoint")

        Recipes.addBlock(Adapter(), Constants.BlockName.Adapter, "oc:adapter")
        Recipes.addBlock(Assembler(), Constants.BlockName.Assembler, "oc:assembler")
        Recipes.addBlock(Cable(), Constants.BlockName.Cable, "oc:cable")
        Recipes.addBlock(Capacitor(), Constants.BlockName.Capacitor, "oc:capacitor")
        Recipes.addBlock(Case(Tier.One), Constants.BlockName.CaseTier1, "oc:case1")
        Recipes.addBlock(Case(Tier.Three), Constants.BlockName.CaseTier3, "oc:case3")
        Recipes.addBlock(Case(Tier.Two), Constants.BlockName.CaseTier2, "oc:case2")
        Recipes.addBlock(ChameliumBlock(), Constants.BlockName.ChameliumBlock, "oc:chameliumBlock")
        Recipes.addBlock(Charger(), Constants.BlockName.Charger, "oc:charger")
        Recipes.addBlock(Disassembler(), Constants.BlockName.Disassembler, "oc:disassembler")
        Recipes.addBlock(DiskDrive(), Constants.BlockName.DiskDrive, "oc:diskDrive")
        Recipes.addBlock(Geolyzer(), Constants.BlockName.Geolyzer, "oc:geolyzer")
        Recipes.addBlock(Hologram(Tier.One), Constants.BlockName.HologramTier1, "oc:hologram1")
        Recipes.addBlock(Hologram(Tier.Two), Constants.BlockName.HologramTier2, "oc:hologram2")
        Recipes.addBlock(Keyboard(), Constants.BlockName.Keyboard, "oc:keyboard")
        Recipes.addBlock(MotionSensor(), Constants.BlockName.MotionSensor, "oc:motionSensor")
        Recipes.addBlock(PowerConverter(), Constants.BlockName.PowerConverter, "oc:powerConverter")
        Recipes.addBlock(PowerDistributor(), Constants.BlockName.PowerDistributor, "oc:powerDistributor")
        Recipes.addBlock(Printer(), Constants.BlockName.Printer, "oc:printer")
        Recipes.addBlock(Raid(), Constants.BlockName.Raid, "oc:raid")
        Recipes.addBlock(Redstone(), Constants.BlockName.Redstone, "oc:redstone")
        Recipes.addBlock(Relay(), Constants.BlockName.Relay, "oc:relay")
        Recipes.addBlock(Screen(Tier.One), Constants.BlockName.ScreenTier1, "oc:screen1")
        Recipes.addBlock(Screen(Tier.Three), Constants.BlockName.ScreenTier3, "oc:screen3")
        Recipes.addBlock(Screen(Tier.Two), Constants.BlockName.ScreenTier2, "oc:screen2")
        Recipes.addBlock(Rack(), Constants.BlockName.Rack, "oc:rack", "oc:rack")
        Recipes.addBlock(Waypoint(), Constants.BlockName.Waypoint, "oc:waypoint")

        Items.registerBlock(Case(Tier.Four), Constants.BlockName.CaseCreative)
        Items.registerBlock(Microcontroller(), Constants.BlockName.Microcontroller)
        Items.registerBlock(Print(), Constants.BlockName.Print)
        Items.registerBlock(RobotAfterimage(), Constants.BlockName.RobotAfterimage)
        Items.registerBlock(RobotProxy(), Constants.BlockName.Robot)

        // v1.5.10
        Recipes.addBlock(FakeEndstone(), Constants.BlockName.Endstone, "oc:stoneEndstone")

        // v1.5.14
        Recipes.addBlock(NetSplitter(), Constants.BlockName.NetSplitter, "oc:netSplitter")

        // v1.5.16
        Recipes.addBlock(Transposer(), Constants.BlockName.Transposer, "oc:transposer")

        // v1.7.2
        Recipes.addBlock(CarpetedCapacitor(), Constants.BlockName.CarpetedCapacitor, "oc:carpetedCapacitor")
    }

    private fun registerTileEntity(tileEntityClass: Class<out TileEntity>, key: String) {
        GameRegistry.registerTileEntity(tileEntityClass, key)
    }
}
