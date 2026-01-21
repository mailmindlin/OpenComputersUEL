package li.cil.oc.common

import li.cil.oc.common.inventory.DatabaseInventory
import li.cil.oc.common.inventory.DiskDriveMountableInventory
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.DiskDriveMountable
import li.cil.oc.server.component.Server
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld.getTileEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fml.common.network.IGuiHandler

abstract class GuiHandler : IGuiHandler {
    override fun getServerGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
        return when (GuiType.Categories[id]) {
            GuiType.Category.Block -> {
                val pos = BlockPosition(x, GuiType.extractY(y), z, world)
                when (val te = world.getTileEntity(pos)) {
                    is tileentity.Adapter -> if (id == GuiType.Adapter.id) container.Adapter(player.inventory, te) else null
                    is tileentity.Assembler -> if (id == GuiType.Assembler.id) container.Assembler(player.inventory, te) else null
                    is tileentity.Charger -> if (id == GuiType.Charger.id) container.Charger(player.inventory, te) else null
                    is tileentity.Case -> if (id == GuiType.Case.id) container.Case(player.inventory, te) else null
                    is tileentity.Disassembler -> if (id == GuiType.Disassembler.id) container.Disassembler(player.inventory, te) else null
                    is tileentity.DiskDrive -> if (id == GuiType.DiskDrive.id) container.DiskDrive(player.inventory, te) else null
                    is tileentity.Printer -> if (id == GuiType.Printer.id) container.Printer(player.inventory, te) else null
                    is tileentity.Raid -> if (id == GuiType.Raid.id) container.Raid(player.inventory, te) else null
                    is tileentity.Relay -> if (id == GuiType.Relay.id) container.Relay(player.inventory, te) else null
                    is tileentity.RobotProxy -> if (id == GuiType.Robot.id) container.Robot(player.inventory, te.robot) else null
                    is tileentity.Rack -> when (id) {
                        GuiType.Rack.id -> container.Rack(player.inventory, te)
                        GuiType.ServerInRack.id -> {
                            val slot = GuiType.extractSlot(y)
                            val server = te.getMountable(slot) as Server
                            container.Server(player.inventory, server, server)
                        }
                        GuiType.DiskDriveMountableInRack.id -> {
                            val slot = GuiType.extractSlot(y)
                            val drive = te.getMountable(slot) as DiskDriveMountable
                            container.DiskDrive(player.inventory, drive)
                        }
                        else -> null
                    }
                    else -> null
                }
            }
            GuiType.Category.Entity -> {
                when (val entity = world.getEntityByID(x)) {
                    is entity.Drone -> if (id == GuiType.Drone.id) container.Drone(player.inventory, entity) else null
                    else -> null
                }
            }
            GuiType.Category.Item -> {
                val itemStackInUse = getItemStackInUse(id, player)
                when (val subItem = Delegator.subItem(itemStackInUse)) {
                    is item.UpgradeDatabase -> if (id == GuiType.Database.id) {
                        container.Database(player.inventory, object : DatabaseInventory() {
                            override val container: ItemStack get() = itemStackInUse
                            override fun isUsableByPlayer(p: EntityPlayer): Boolean = p == player
                        })
                    } else null
                    is item.Server -> if (id == GuiType.Server.id) {
                        container.Server(player.inventory, object : ServerInventory() {
                            override val container: ItemStack get() = itemStackInUse
                            override fun isUsableByPlayer(p: EntityPlayer): Boolean = p == player
                        })
                    } else null
                    is item.Tablet -> if (id == GuiType.TabletInner.id) {
                        val stack = itemStackInUse
                        if (stack.hasTagCompound()) {
                            container.Tablet(player.inventory, item.Tablet.get(stack, player))
                        } else null
                    } else null
                    is item.DiskDriveMountable -> if (id == GuiType.DiskDriveMountable.id) {
                        container.DiskDrive(player.inventory, object : DiskDriveMountableInventory() {
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
            is item.traits.FileSystemLike -> if (id == GuiType.Drive.id) mainItem else player.inventory.offHandInventory[0]
            is item.UpgradeDatabase -> if (id == GuiType.Database.id) mainItem else player.inventory.offHandInventory[0]
            is item.Server -> if (id == GuiType.Server.id) mainItem else player.inventory.offHandInventory[0]
            is item.Tablet -> if (id == GuiType.Tablet.id || id == GuiType.TabletInner.id) mainItem else player.inventory.offHandInventory[0]
            is item.Terminal -> if (id == GuiType.Terminal.id) mainItem else player.inventory.offHandInventory[0]
            is item.DiskDriveMountable -> if (id == GuiType.DiskDriveMountable.id) mainItem else player.inventory.offHandInventory[0]
            else -> player.inventory.offHandInventory[0]
        }
    }
}
