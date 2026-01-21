package li.cil.oc.common.event

import java.io.FileInputStream
import java.io.FileOutputStream

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.client.Textures
import li.cil.oc.common.EventHandler
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent
import org.lwjgl.opengl.GL11

object NanomachinesHandler {

    object Client {
        @JvmStatic
        @SubscribeEvent
        fun onRenderGameOverlay(e: RenderGameOverlayEvent.Post) {
            if (e.type == RenderGameOverlayEvent.ElementType.TEXT) {
                val mc = Minecraft.getMinecraft()
                val controller = api.Nanomachines.getController(mc.player)
                if (controller is Controller) {
                    val res = ScaledResolution(mc)
                    val sizeX = 8
                    val sizeY = 12
                    val width = res.scaledWidth
                    val height = res.scaledHeight
                    val (x, y) = Settings.get.nanomachineHudPos
                    val left = minOf(
                        width - sizeX,
                        when {
                            x < 0 -> width / 2 - 91 - 12
                            x < 1 -> (width * x).toInt()
                            else -> x.toInt()
                        }
                    )
                    val top = minOf(
                        height - sizeY,
                        when {
                            y < 0 -> height - 39
                            y < 1 -> (y * height).toInt()
                            else -> y.toInt()
                        }
                    )
                    val fill = controller.localBuffer / controller.localBufferSize
                    Minecraft.getMinecraft().textureManager.bindTexture(Textures.GUI.Nanomachines)
                    drawRect(left, top, sizeX, sizeY, sizeX, sizeY)
                    Minecraft.getMinecraft().textureManager.bindTexture(Textures.GUI.NanomachinesBar)
                    drawRect(left, top, sizeX, sizeY, sizeX, sizeY, fill)
                }
            }
        }

        private fun drawRect(x: Int, y: Int, w: Int, h: Int, tw: Int, th: Int, fill: Double = 1.0) {
            val sx = 1f / tw
            val sy = 1f / th
            val t = Tessellator.getInstance()
            val r = t.buffer
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
            r.pos(x.toDouble(), (y + h).toDouble(), 0.0).tex(0.0, (h * sy).toDouble()).endVertex()
            r.pos((x + w).toDouble(), (y + h).toDouble(), 0.0).tex((w * sx).toDouble(), (h * sy).toDouble()).endVertex()
            r.pos((x + w).toDouble(), y + h * (1 - fill), 0.0).tex((w * sx).toDouble(), 1 - fill).endVertex()
            r.pos(x.toDouble(), y + h * (1 - fill), 0.0).tex(0.0, 1 - fill).endVertex()
            t.draw()
        }
    }

    object Common {
        @JvmStatic
        @SubscribeEvent
        fun onPlayerRespawn(e: PlayerRespawnEvent) {
            val controller = api.Nanomachines.getController(e.player)
            if (controller is Controller) {
                controller.changeBuffer(-controller.localBuffer)
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onLivingUpdate(e: LivingEvent.LivingUpdateEvent) {
            val entity = e.entity
            if (entity is EntityPlayer) {
                val controller = api.Nanomachines.getController(entity)
                if (controller is ControllerImpl) {
                    if (controller.player === entity) {
                        controller.update()
                    } else {
                        // Player entity instance changed (e.g. respawn), recreate the controller.
                        val nbt = NBTTagCompound()
                        controller.save(nbt)
                        api.Nanomachines.uninstallController(controller.player)
                        val newController = api.Nanomachines.installController(entity)
                        if (newController is ControllerImpl) {
                            newController.load(nbt)
                            newController.reset()
                        }
                    }
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onPlayerSave(e: PlayerEvent.SaveToFile) {
            val file = e.getPlayerFile("ocnm")
            val controller = api.Nanomachines.getController(e.entityPlayer)
            if (controller is ControllerImpl) {
                try {
                    val nbt = NBTTagCompound()
                    controller.save(nbt)
                    val fos = FileOutputStream(file)
                    try {
                        CompressedStreamTools.writeCompressed(nbt, fos)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Error saving nanomachine state.", t)
                    }
                    fos.close()
                } catch (t: Throwable) {
                    OpenComputers.log.warn("Error saving nanomachine state.", t)
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onPlayerLoad(e: PlayerEvent.LoadFromFile) {
            val file = e.getPlayerFile("ocnm")
            if (file.exists()) {
                val controller = api.Nanomachines.getController(e.entityPlayer)
                if (controller is ControllerImpl) {
                    try {
                        val fis = FileInputStream(file)
                        try {
                            controller.load(CompressedStreamTools.readCompressed(fis))
                        } catch (t: Throwable) {
                            OpenComputers.log.warn("Error loading nanomachine state.", t)
                        }
                        fis.close()
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Error loading nanomachine state.", t)
                    }
                }
            }
        }

        @JvmStatic
        @SubscribeEvent
        fun onPlayerDisconnect(e: PlayerLoggedOutEvent) {
            val controller = api.Nanomachines.getController(e.player)
            if (controller is ControllerImpl) {
                // Wait a tick because saving is done after this event.
                EventHandler.scheduleServer { api.Nanomachines.uninstallController(e.player) }
            }
        }
    }
}
