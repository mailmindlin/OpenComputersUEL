package li.cil.oc.common.event

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.tileentity.RenderUtil
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld.extendedWorld
import li.cil.oc.util.RenderState
import li.cil.oc.util.getLightBrightnessForSkyBlocks
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object RackMountableRenderHandler {
    private val Servers by lazy {
        arrayOf(
            Constants.ItemInfo.ServerTier1,
            Constants.ItemInfo.ServerTier2,
            Constants.ItemInfo.ServerTier3,
            Constants.ItemInfo.ServerCreative
        )
    }

    @JvmStatic
    @SubscribeEvent
    fun onRackMountableRendering(e: RackMountableRenderEvent.TileEntity) {
        if (e.data == null) return
        when (ApiItems.get(e.rack.getStackInSlot(e.mountable))) {
            Constants.ItemInfo.DiskDriveMountable -> {
                // Disk drive.

                if (e.data.hasKey("disk")) {
                    val stack = ItemStack(e.data.getCompoundTag("disk"))
                    if (!stack.isEmpty) {
                        GlStateManager.pushMatrix()
                        GlStateManager.scale(1f, -1f, 1f)
                        GlStateManager.translate(10 / 16f, -(3.5f + e.mountable * 3f) / 16f, -2 / 16f)
                        GlStateManager.rotate(90f, -1f, 0f, 0f)
                        GlStateManager.scale(0.5f, 0.5f, 0.5f)

                        val brightness = e.rack.world().getLightBrightnessForSkyBlocks(BlockPosition(e.rack).offset(e.rack.facing()), 0)
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (brightness % 65536).toFloat(), (brightness / 65536).toFloat())

                        // This is very 'meh', but item frames do it like this, too!
                        val entity = EntityItem(e.rack.world(), 0.0, 0.0, 0.0, stack)
                        entity.hoverStart = 0f
                        Minecraft.getMinecraft().renderItem.renderItem(entity.item, ItemCameraTransforms.TransformType.FIXED)
                        GlStateManager.popMatrix()
                    }
                }

                if (System.currentTimeMillis() - e.data.getLong("lastAccess") < 400 && e.rack.world().rand.nextDouble() > 0.1) {
                    RenderState.disableEntityLighting()
                    RenderState.makeItBlend()

                    e.renderOverlayFromAtlas(Textures.Block.RackDiskDriveActivity)

                    RenderState.disableBlend()
                    RenderState.enableEntityLighting()
                }
            }
            in Servers -> {
                // Server.
                RenderState.disableEntityLighting()
                RenderState.makeItBlend()

                if (e.data.getBoolean("isRunning")) {
                    e.renderOverlayFromAtlas(Textures.Block.RackServerOn)
                }
                if (e.data.getBoolean("hasErrored") && RenderUtil.shouldShowErrorLight(e.rack.hashCode() * (e.mountable + 1))) {
                    e.renderOverlayFromAtlas(Textures.Block.RackServerError)
                }
                if (System.currentTimeMillis() - e.data.getLong("lastFileSystemAccess") < 400 && e.rack.world().rand.nextDouble() > 0.1) {
                    e.renderOverlayFromAtlas(Textures.Block.RackServerActivity)
                }
                if ((System.currentTimeMillis() - e.data.getLong("lastNetworkActivity") < 300 && System.currentTimeMillis() % 200 > 100) && e.data.getBoolean("isRunning")) {
                    e.renderOverlayFromAtlas(Textures.Block.RackServerNetworkActivity)
                }

                RenderState.disableBlend()
                RenderState.enableEntityLighting()
            }
            Constants.ItemInfo.TerminalServer -> {
                // Terminal server.
                RenderState.disableEntityLighting()
                RenderState.makeItBlend()

                e.renderOverlayFromAtlas(Textures.Block.RackTerminalServerOn)
                val countConnected = e.data.getTagList("keys", NBT.TAG_STRING).tagCount()

                if (countConnected > 0) {
                    val u0 = 7 / 16f
                    val u1 = u0 + (2 * countConnected - 1) / 16f
                    e.renderOverlayFromAtlas(Textures.Block.RackTerminalServerPresence, u0, u1)
                }

                RenderState.disableBlend()
                RenderState.enableEntityLighting()
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRackMountableRendering(e: RackMountableRenderEvent.Block) {
        when (ApiItems.get(e.rack.getStackInSlot(e.mountable))) {
            Constants.ItemInfo.DiskDriveMountable -> {
                // Disk drive.
                e.frontTextureOverride = Textures.getSprite(Textures.Block.RackDiskDrive)
            }
            in Servers -> {
                // Server.
                e.frontTextureOverride = Textures.getSprite(Textures.Block.RackServer)
            }
            Constants.ItemInfo.TerminalServer -> {
                // Terminal server.
                e.frontTextureOverride = Textures.getSprite(Textures.Block.RackTerminalServer)
            }
        }
    }
}
