package li.cil.oc.common

import li.cil.oc.common.inventory.DatabaseInventory
import li.cil.oc.common.inventory.DiskDriveMountableInventory
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.server.component.DiskDriveMountable
import li.cil.oc.server.component.Server
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.getTileEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fml.common.network.IGuiHandler
import li.cil.oc.common.tileentity.Adapter as TEAdapter
import li.cil.oc.common.tileentity.Assembler as TEAssembler
import li.cil.oc.common.tileentity.Charger as TECharger
import li.cil.oc.common.tileentity.Case as TECase
import li.cil.oc.common.tileentity.Disassembler as TEDisassembler
import li.cil.oc.common.tileentity.DiskDrive as TEDiskDrive
import li.cil.oc.common.tileentity.Printer as TEPrinter
import li.cil.oc.common.tileentity.Raid as TERaid
import li.cil.oc.common.tileentity.Relay as TERelay
import li.cil.oc.common.tileentity.RobotProxy as TERobotProxy
import li.cil.oc.common.tileentity.Rack as TERack
import li.cil.oc.common.container.Adapter as ContainerAdapter
import li.cil.oc.common.container.Assembler as ContainerAssembler
import li.cil.oc.common.container.Charger as ContainerCharger
import li.cil.oc.common.container.Case as ContainerCase
import li.cil.oc.common.container.Database as ContainerDatabase
import li.cil.oc.common.container.Disassembler as ContainerDisassembler
import li.cil.oc.common.container.Drone as ContainerDrone
import li.cil.oc.common.container.DiskDrive as ContainerDiskDrive
import li.cil.oc.common.container.Printer as ContainerPrinter
import li.cil.oc.common.container.Raid as ContainerRaid
import li.cil.oc.common.container.Relay as ContainerRelay
import li.cil.oc.common.container.Robot as ContainerRobot
import li.cil.oc.common.container.Rack as ContainerRack
import li.cil.oc.common.container.Server as ContainerServer
import li.cil.oc.common.container.Tablet as ContainerTablet
import li.cil.oc.common.entity.Drone as EntityDrone
import li.cil.oc.common.item.UpgradeDatabase as ItemUpgradeDatabase
import li.cil.oc.common.item.Server as ItemServer
import li.cil.oc.common.item.Tablet as ItemTablet
import li.cil.oc.common.item.Terminal as ItemTerminal
import li.cil.oc.common.item.DiskDriveMountable as ItemDiskDriveMountable

abstract class GuiHandler : IGuiHandler {
    override fun getServerGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
        return when (GuiType.Categories[id]) {
            GuiType.Category.Block -> {
                val pos = BlockPosition(x, GuiType.extractY(y), z, world)
                when (val te = world.getTileEntity(pos)) {
                    is TEAdapter -> if (id == GuiType.Adapter.id) ContainerAdapter(player.inventory, te) else null
                    is TEAssembler -> if (id == GuiType.Assembler.id) ContainerAssembler(player.inventory, te) else null
                    is TECharger -> if (id == GuiType.Charger.id) ContainerCharger(player.inventory, te) else null
                    is TECase -> if (id == GuiType.Case.id) ContainerCase(player.inventory, te) else null
                    is TEDisassembler -> if (id == GuiType.Disassembler.id) ContainerDisassembler(player.inventory, te) else null
                    is TEDiskDrive -> if (id == GuiType.DiskDrive.id) ContainerDiskDrive(player.inventory, te) else null
                    is TEPrinter -> if (id == GuiType.Printer.id) ContainerPrinter(player.inventory, te) else null
                    is TERaid -> if (id == GuiType.Raid.id) ContainerRaid(player.inventory, te) else null
                    is TERelay -> if (id == GuiType.Relay.id) ContainerRelay(player.inventory, te) else null
                    is TERobotProxy -> if (id == GuiType.Robot.id) ContainerRobot(player.inventory, te.robot) else null
                    is TERack -> when (id) {
                        GuiType.Rack.id -> ContainerRack(player.inventory, te)
                        GuiType.ServerInRack.id -> {
                            val slot = GuiType.extractSlot(y)
                            val server = te.getMountable(slot) as Server
                            ContainerServer(player.inventory, server, server)
                        }
                        GuiType.DiskDriveMountableInRack.id -> {
                            val slot = GuiType.extractSlot(y)
                            val drive = te.getMountable(slot) as DiskDriveMountable
                            ContainerDiskDrive(player.inventory, drive)
                        }
                        else -> null
                    }
                    else -> null
                }
            }
            GuiType.Category.Entity -> {
                when (val entity = world.getEntityByID(x)) {
                    is EntityDrone -> if (id == GuiType.Drone.id) ContainerDrone(player.inventory, entity) else null
                    else -> null
                }
            }
            GuiType.Category.Item -> {
                val itemStackInUse = getItemStackInUse(id, player)
                when (val subItem = Delegator.subItem(itemStackInUse)) {
                    is ItemUpgradeDatabase -> if (id == GuiType.Database.id) {
                        ContainerDatabase(player.inventory, object : DatabaseInventory() {
                            override val container: ItemStack get() = itemStackInUse
                            override fun isUsableByPlayer(p: EntityPlayer): Boolean = p == player
                        })
                    } else null
                    is ItemServer -> if (id == GuiType.Server.id) {
                        ContainerServer(player.inventory, object : ServerInventory() {
                            override val container: ItemStack get() = itemStackInUse
                            override fun isUsableByPlayer(p: EntityPlayer): Boolean = p == player
                        })
                    } else null
                    is ItemTablet -> if (id == GuiType.TabletInner.id) {
                        val stack = itemStackInUse
                        if (stack.hasTagCompound()) {
                            ContainerTablet(player.inventory, ItemTablet.get(stack, player))
                        } else null
                    } else null
                    is ItemDiskDriveMountable -> if (id == GuiType.DiskDriveMountable.id) {
                        ContainerDiskDrive(player.inventory, object : DiskDriveMountableInventory() {
                            override val container: ItemStack get() = itemStackInUse
                            override fun isUsableByPlayer(p: EntityPlayer): Boolean = p == player
                        })
                    } else null
                    else -> null
                }
            }
            else -> null
        }
    }

    fun getItemStackInUse(id: Int, player: EntityPlayer): ItemStack {
        val mainItem = player.heldItemMainhand
        return when (Delegator.subItem(mainItem)) {
            is FileSystemLike -> if (id == GuiType.Drive.id) mainItem else player.inventory.offHandInventory[0]
            is ItemUpgradeDatabase -> if (id == GuiType.Database.id) mainItem else player.inventory.offHandInventory[0]
            is ItemServer -> if (id == GuiType.Server.id) mainItem else player.inventory.offHandInventory[0]
            is ItemTablet -> if (id == GuiType.Tablet.id || id == GuiType.TabletInner.id) mainItem else player.inventory.offHandInventory[0]
            is ItemTerminal -> if (id == GuiType.Terminal.id) mainItem else player.inventory.offHandInventory[0]
            is ItemDiskDriveMountable -> if (id == GuiType.DiskDriveMountable.id) mainItem else player.inventory.offHandInventory[0]
            else -> player.inventory.offHandInventory[0]
        }
    }
}
