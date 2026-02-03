package li.cil.oc.client

import com.google.common.base.Strings
import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.common.GuiType
import li.cil.oc.client.gui.*
import li.cil.oc.common.inventory.DatabaseInventory
import li.cil.oc.common.inventory.DiskDriveMountableInventory
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.Tablet
import li.cil.oc.common.GuiHandler as CommonGuiHandler
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.getTileEntity
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.world.World
import net.minecraft.item.ItemStack

object GuiHandler : CommonGuiHandler() {
  override fun getClientGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
    return when (GuiType.Categories[id]) {
      GuiType.Category.Block -> {
        val t = world.getTileEntity(BlockPosition(x, GuiType.extractY(y), z)) ?: return null
        OpenComputers.log.info("Client GUI $id for tile $t")
        when (t) {
          is li.cil.oc.common.tileentity.Adapter -> if (id == GuiType.Adapter.id) Adapter(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Assembler -> if (id == GuiType.Assembler.id) Assembler(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Case -> if (id == GuiType.Case.id) Case(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Charger -> if (id == GuiType.Charger.id) Charger(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Disassembler -> if (id == GuiType.Disassembler.id) Disassembler(player.inventory, t) else null
          is li.cil.oc.common.tileentity.DiskDrive -> if (id == GuiType.DiskDrive.id) DiskDrive(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Printer -> if (id == GuiType.Printer.id) Printer(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Rack -> when (id) {
            GuiType.Rack.id -> Rack(player.inventory, t)
            GuiType.ServerInRack.id -> {
              val slot = GuiType.extractSlot(y)
              Server(player.inventory, object : ServerInventory() {
                override val container: ItemStack get() = t.getStackInSlot(slot)
                override fun isUsableByPlayer(player: EntityPlayer) = t.isUsableByPlayer(player)
              }, t, slot)
            }
            GuiType.DiskDriveMountableInRack.id -> {
              val slot = GuiType.extractSlot(y)
              DiskDrive(player.inventory, object : DiskDriveMountableInventory() {
                override val container: ItemStack get() = t.getStackInSlot(slot)
                override fun isUsableByPlayer(player: EntityPlayer): Boolean = t.isUsableByPlayer(player)
              })
            }
            else -> null
          }
          is li.cil.oc.common.tileentity.Raid -> if (id == GuiType.Raid.id) Raid(player.inventory, t) else null
          is li.cil.oc.common.tileentity.Relay -> if (id == GuiType.Relay.id) Relay(player.inventory, t) else null
          is li.cil.oc.common.tileentity.RobotProxy -> if (id == GuiType.Robot.id) Robot(player.inventory, t.robot) else null
          is li.cil.oc.common.tileentity.Screen -> if (id == GuiType.Screen.id) {
            Screen(t.origin.buffer, t.tier.canTouch, { t.origin.hasKeyboard() }, { t.origin.buffer.isRenderingEnabled })
          } else null
          is li.cil.oc.common.tileentity.Waypoint -> if (id == GuiType.Waypoint.id) Waypoint(t) else null
          else -> null
        }
      }
      GuiType.Category.Entity -> {
        when (val entity = world.getEntityByID(x)) {
          is li.cil.oc.common.entity.Drone -> if (id == GuiType.Drone.id) Drone(player.inventory, entity) else null
          else -> null
        }
      }
      GuiType.Category.Item -> {
        val itemStackInUse = getItemStackInUse(id, player)
        when (val subItem = Delegator.subItem(itemStackInUse)) {
          is li.cil.oc.common.item.traits.FileSystemLike -> if (id == GuiType.Drive.id) Drive(player.inventory) { itemStackInUse } else null
          is li.cil.oc.common.item.UpgradeDatabase -> if (id == GuiType.Database.id) {
            Database(player.inventory, object : DatabaseInventory() {
              override val container get() = itemStackInUse
              override fun isUsableByPlayer(player: EntityPlayer) = true
            })
          } else null
          is li.cil.oc.common.item.Server -> if (id == GuiType.Server.id) {
            Server(player.inventory, object : ServerInventory() {
              override val container get() = itemStackInUse
              override fun isUsableByPlayer(player: EntityPlayer) = true
            }, null, 0)
          } else null
          is Tablet -> when (id) {
            GuiType.Tablet.id -> {
              val stack = itemStackInUse
              if (stack.hasTagCompound()) {
                val bufferOption =
                  Tablet.get(stack, player).components.firstNotNullOfOrNull { component -> component as? TextBuffer }
                if (bufferOption != null) {
                  Screen(bufferOption, true, { true }, { bufferOption.isRenderingEnabled })
                } else null
              } else null
            }
            GuiType.TabletInner.id -> {
              val stack = itemStackInUse
              if (stack.hasTagCompound()) {
                Tablet(player.inventory, Tablet.get(stack, player))
              } else null
            }
            else -> null
          }
          is li.cil.oc.common.item.DiskDriveMountable -> if (id == GuiType.DiskDriveMountable.id) {
            DiskDrive(player.inventory, object : DiskDriveMountableInventory() {
              override val container get() = itemStackInUse
              override fun isUsableByPlayer(activePlayer: EntityPlayer): Boolean = activePlayer == player
            })
          } else null
          is li.cil.oc.common.item.Terminal -> if (id == GuiType.Terminal.id) {
            val stack = itemStackInUse
            if (stack.hasTagCompound()) {
              val address = stack.tagCompound!!.getString(Settings.namespace + "server")
              val key = stack.tagCompound!!.getString(Settings.namespace + "key")
              if (!Strings.isNullOrEmpty(key) && !Strings.isNullOrEmpty(address)) {
                val term = li.cil.oc.common.component.TerminalServer.loaded.find(address)
                if (term?.rack != null) {
                  val rack = term.rack
                  if (rack is TileEntity && rack is li.cil.oc.api.internal.Rack) {
                    fun inRange() = player.isEntityAlive && !rack.isInvalid && rack.getDistanceSq(player.posX, player.posY, player.posZ) < term.range * term.range
                    if (inRange()) {
                      if (term.sidedKeys.contains(key)) {
                        return Screen(term.buffer, true, { true }) {
                          // Check if someone else bound a term to our server.
                          if (stack.tagCompound!!.getString(Settings.namespace + "key") != key) {
                            Minecraft.getMinecraft().displayGuiScreen(null)
                          }
                          // Check whether we're still in range.
                          if (!inRange()) {
                            Minecraft.getMinecraft().displayGuiScreen(null)
                          }
                          true
                        }
                      } else {
                        player.sendMessage(Localization.Terminal.InvalidKey())
                      }
                    } else {
                      player.sendMessage(Localization.Terminal.OutOfRange())
                    }
                  }
                } else {
                  player.sendMessage(Localization.Terminal.OutOfRange())
                }
              }
            }
            null
          } else null
          else -> null
        }
      }
      GuiType.Category.None -> if (id == GuiType.Manual.id) Manual() else null
      else -> null
    }
  }
}
