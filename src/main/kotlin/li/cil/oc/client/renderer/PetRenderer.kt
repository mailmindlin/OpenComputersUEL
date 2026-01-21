package li.cil.oc.client.renderer

import com.google.common.cache.CacheBuilder
import li.cil.oc.api.event.RobotRenderEvent
import li.cil.oc.client.renderer.tileentity.RobotRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import net.minecraftforge.client.event.RenderPlayerEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import java.util.concurrent.TimeUnit
import kotlin.math.sin

object PetRenderer {
    val hidden = mutableSetOf<String>()

    var isInitialized = false

    // http://goo.gl/frLWYR
    private val entitledPlayers = mapOf(
        "9f1f262f-0d68-4e13-9161-9eeaf4a0a1a8" to Triple(0.3, 0.9, 0.6), // Sangar
        "18f8bed4-f027-44af-8947-6a3a2317645a" to Triple(1.0, 0.0, 0.0), // Jodarion
        "36123742-2cf6-4cfc-8b65-278581b3caeb" to Triple(0.5, 0.7, 1.0), // DaKaTotal
        "2c0c214b-96f4-4565-b513-de90d5fbc977" to Triple(1.0, 0.0, 0.0), // MichiRavencroft
        "f3ba6ec8-c280-4950-bb08-1fcb2eab3a9c" to Triple(0.18, 0.95, 0.922), // Vexatos
        "9d636bdd-b9f4-4b80-b9ce-586ca04bd4f3" to Triple(0.8, 0.77, 0.75), // StoneNomad
        "23c7ed71-fb13-4abe-abe7-f355e1de6e62" to Triple(0.3, 0.3, 1.0), // LizzyTheSiren
        "076541f1-f10a-46de-a127-dfab8adfbb75" to Triple(0.2, 1.0, 0.1), // vifino
        "e7e90198-0ccf-4662-a827-192ec8f4419d" to Triple(0.0, 0.2, 0.6), // Izaya
        "f514ee69-7bbb-4e46-9e94-d8176324cec2" to Triple(0.098, 0.471, 0.784), // Wobbo
        "f812c043-78ba-4324-82ae-e8f05c52ae6e" to Triple(0.1, 0.8, 0.5) // payonel
    )

    private val petLocations = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.SECONDS)
        .build<Entity, PetLocation>()

    private var rendering: Triple<Double, Double, Double>? = null

    @SubscribeEvent
    @Suppress("unused")
    fun onPlayerRender(e: RenderPlayerEvent.Pre) {
        val uuid = e.entityPlayer.uniqueID.toString()
        if (hidden.contains(uuid) || !entitledPlayers.contains(uuid)) return
        rendering = entitledPlayers[uuid]

        val worldTime = e.entityPlayer.entityWorld.totalWorldTime
        val timeJitter = e.entityPlayer.hashCode() xor 0xFF
        val hover = sin(timeJitter + (worldTime + e.partialRenderTick) / 20.0).toFloat() * 0.03f

        val location = petLocations.get(e.entityPlayer) { PetLocation(e.entityPlayer) }

        GlStateManager.pushMatrix()
        RenderState.pushAttrib()
        val localPos = Minecraft.getMinecraft().player.getPositionEyes(e.partialRenderTick)
        val playerPos = e.entityPlayer.getPositionEyes(e.partialRenderTick)
        val correction = 1.62 - (if (e.entityPlayer.isSneaking) 0.125 else 0.0)
        GlStateManager.translate(
            playerPos.x - localPos.x,
            playerPos.y - localPos.y + correction,
            playerPos.z - localPos.z
        )

        RenderState.enableEntityLighting()
        GlStateManager.disableBlend()
        GlStateManager.enableRescaleNormal()
        GlStateManager.color(1f, 1f, 1f, 1f)

        location.applyInterpolatedTransformations(e.partialRenderTick)

        GlStateManager.scale(0.3f, 0.3f, 0.3f)
        GlStateManager.translate(0.0, hover.toDouble(), 0.0)

        val timeJitterLong = timeJitter.toLong()
        val offset = timeJitterLong + worldTime / 20.0
        RobotRenderer.renderChassis(null, offset, isRunningOverride = true)

        GlStateManager.disableRescaleNormal()

        RenderState.popAttrib()
        GlStateManager.popMatrix()

        rendering = null
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @Suppress("unused")
    fun onRobotRender(e: RobotRenderEvent) {
        rendering?.let { (r, g, b) ->
            GlStateManager.color(r.toFloat(), g.toFloat(), b.toFloat())
        }
    }

    private class PetLocation(val owner: Entity) {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var yaw = owner.rotationYaw

        var lastX = x
        var lastY = y
        var lastZ = z
        var lastYaw = yaw

        fun update() {
            val dx = owner.lastTickPosX - owner.posX
            val dy = owner.lastTickPosY - owner.posY
            val dz = owner.lastTickPosZ - owner.posZ
            val dYaw = owner.rotationYaw - yaw
            lastX = x
            lastY = y
            lastZ = z
            lastYaw = yaw
            x += dx
            y += dy
            z += dz
            x *= 0.05
            y *= 0.05
            z *= 0.05
            yaw += dYaw * 0.2f
        }

        fun applyInterpolatedTransformations(dt: Float) {
            val ix = lastX + (x - lastX) * dt
            val iy = lastY + (y - lastY) * dt
            val iz = lastZ + (z - lastZ) * dt
            val iYaw = lastYaw + (yaw - lastYaw) * dt

            GlStateManager.translate(ix, iy, iz)
            if (!isForInventory()) {
                GlStateManager.rotate(-iYaw, 0f, 1f, 0f)
            } else {
                GlStateManager.rotate(-owner.rotationYaw, 0f, 1f, 0f)
            }
            GlStateManager.translate(0.3, -0.1, -0.2)
        }

        private fun isForInventory() = Minecraft.getMinecraft().currentScreen != null && owner == Minecraft.getMinecraft().player
    }

    @SubscribeEvent
    @Suppress("unused")
    fun tickStart(e: ClientTickEvent) {
        petLocations.cleanUp()
        for (pet in petLocations.asMap().values) {
            pet.update()
        }
    }
}
