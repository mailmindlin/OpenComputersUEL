package li.cil.oc.client.renderer.entity

import li.cil.oc.common.entity.Drone
import li.cil.oc.util.RenderState
import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d
import org.lwjgl.opengl.GL11
import kotlin.math.sin

class ModelQuadcopter : ModelBase() {
    val body = ModelRenderer(this, "body")
    val wing0 = ModelRenderer(this, "wing0")
    val wing1 = ModelRenderer(this, "wing1")
    val wing2 = ModelRenderer(this, "wing2")
    val wing3 = ModelRenderer(this, "wing3")
    val light0 = ModelRenderer(this, "light0")
    val light1 = ModelRenderer(this, "light1")
    val light2 = ModelRenderer(this, "light2")
    val light3 = ModelRenderer(this, "light3")

    init {
        textureWidth = 64
        textureHeight = 32

        setTextureOffset("body.middle", 0, 23)
        setTextureOffset("body.top", 0, 1)
        setTextureOffset("body.bottom", 0, 17)
        setTextureOffset("wing0.flap0", 0, 9)
        setTextureOffset("wing0.pin0", 0, 27)
        setTextureOffset("wing1.flap1", 0, 9)
        setTextureOffset("wing1.pin1", 0, 27)
        setTextureOffset("wing2.flap2", 0, 9)
        setTextureOffset("wing2.pin2", 0, 27)
        setTextureOffset("wing3.flap3", 0, 9)
        setTextureOffset("wing3.pin3", 0, 27)

        setTextureOffset("light0.flap0", 24, 0)
        setTextureOffset("light1.flap1", 24, 0)
        setTextureOffset("light2.flap2", 24, 0)
        setTextureOffset("light3.flap3", 24, 0)

        body.addBox("top", -3f, 1f, -3f, 6, 1, 6).apply { rotateAngleY = Math.toRadians(45.0).toFloat() }
        body.addBox("middle", -1f, 0f, -1f, 2, 1, 2).apply { rotateAngleY = Math.toRadians(45.0).toFloat() }
        body.addBox("bottom", -2f, -1f, -2f, 4, 1, 4).apply { rotateAngleY = Math.toRadians(45.0).toFloat() }
        wing0.addBox("flap0", 1f, 0f, -7f, 6, 1, 6)
        wing0.addBox("pin0", 2f, -1f, -3f, 1, 3, 1)
        wing1.addBox("flap1", 1f, 0f, 1f, 6, 1, 6)
        wing1.addBox("pin1", 2f, -1f, 2f, 1, 3, 1)
        wing2.addBox("flap2", -7f, 0f, 1f, 6, 1, 6)
        wing2.addBox("pin2", -3f, -1f, 2f, 1, 3, 1)
        wing3.addBox("flap3", -7f, 0f, -7f, 6, 1, 6)
        wing3.addBox("pin3", -3f, -1f, -3f, 1, 3, 1)

        light0.addBox("flap0", 1f, 0f, -7f, 6, 1, 6)
        light1.addBox("flap1", 1f, 0f, 1f, 6, 1, 6)
        light2.addBox("flap2", -7f, 0f, 1f, 6, 1, 6)
        light3.addBox("flap3", -7f, 0f, -7f, 6, 1, 6)
    }

    private val scale = 1 / 16f
    private val up = Vec3d(0.0, 1.0, 0.0)

    private fun doRender(drone: Drone, dt: Float) {
        if (drone.isRunning) {
            val timeJitter = drone.hashCode() xor 0xFF
            GlStateManager.translate(0f, (sin(timeJitter + (drone.entityWorld.totalWorldTime + dt) / 20.0) * (1 / 16f)).toFloat(), 0f)
        }

        val velocity = Vec3d(drone.motionX, drone.motionY, drone.motionZ)
        val direction = velocity.normalize()
        if (direction.dotProduct(up) < 0.99) {
            // Flying sideways.
            val rotationAxis = direction.crossProduct(up)
            val relativeSpeed = velocity.length().toFloat() / drone.maxVelocity
            GlStateManager.rotate(relativeSpeed * -20, rotationAxis.x.toFloat(), rotationAxis.y.toFloat(), rotationAxis.z.toFloat())
        }

        GlStateManager.rotate(drone.bodyAngle, 0f, 1f, 0f)

        body.render(scale)

        wing0.rotateAngleX = drone.flapAngles[0][0]
        wing0.rotateAngleZ = drone.flapAngles[0][1]
        wing1.rotateAngleX = drone.flapAngles[1][0]
        wing1.rotateAngleZ = drone.flapAngles[1][1]
        wing2.rotateAngleX = drone.flapAngles[2][0]
        wing2.rotateAngleZ = drone.flapAngles[2][1]
        wing3.rotateAngleX = drone.flapAngles[3][0]
        wing3.rotateAngleZ = drone.flapAngles[3][1]

        wing0.render(scale)
        wing1.render(scale)
        wing2.render(scale)
        wing3.render(scale)

        if (drone.isRunning) {
            RenderState.disableEntityLighting()
            GlStateManager.depthFunc(GL11.GL_LEQUAL)

            light0.rotateAngleX = drone.flapAngles[0][0]
            light0.rotateAngleZ = drone.flapAngles[0][1]
            light1.rotateAngleX = drone.flapAngles[1][0]
            light1.rotateAngleZ = drone.flapAngles[1][1]
            light2.rotateAngleX = drone.flapAngles[2][0]
            light2.rotateAngleZ = drone.flapAngles[2][1]
            light3.rotateAngleX = drone.flapAngles[3][0]
            light3.rotateAngleZ = drone.flapAngles[3][1]

            // Additive blending for the lights.
            RenderState.makeItBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)

            val lightColor = drone.lightColor
            val r = (lightColor ushr 16) and 0xFF
            val g = (lightColor ushr 8) and 0xFF
            val b = (lightColor ushr 0) and 0xFF
            GlStateManager.color(r / 255f, g / 255f, b / 255f)

            light0.render(scale)
            light1.render(scale)
            light2.render(scale)
            light3.render(scale)

            RenderState.disableBlend()
            RenderState.enableEntityLighting()
            GlStateManager.color(1f, 1f, 1f, 1f)
        }
    }

    // For inventory rendering.
    fun render() {
        body.render(scale)

        val tilt = Math.toRadians(2.0).toFloat()
        wing0.rotateAngleX = tilt
        wing0.rotateAngleZ = tilt
        wing1.rotateAngleX = -tilt
        wing1.rotateAngleZ = tilt
        wing2.rotateAngleX = -tilt
        wing2.rotateAngleZ = -tilt
        wing3.rotateAngleX = tilt
        wing3.rotateAngleZ = -tilt

        wing0.render(scale)
        wing1.render(scale)
        wing2.render(scale)
        wing3.render(scale)

        RenderState.disableEntityLighting()
        GlStateManager.depthFunc(GL11.GL_LEQUAL)

        light0.rotateAngleX = tilt
        light0.rotateAngleZ = tilt
        light1.rotateAngleX = -tilt
        light1.rotateAngleZ = tilt
        light2.rotateAngleX = -tilt
        light2.rotateAngleZ = -tilt
        light3.rotateAngleX = tilt
        light3.rotateAngleZ = -tilt


        RenderState.makeItBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
        GlStateManager.color(0x66 / 255f, 0xDD / 255f, 0x55 / 255f)

        light0.render(scale)
        light1.render(scale)
        light2.render(scale)
        light3.render(scale)

        RenderState.disableBlend()
        RenderState.enableEntityLighting()
        GlStateManager.color(1f, 1f, 1f, 1f)
    }

    override fun render(entity: Entity, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float, f6: Float) {
        doRender(entity as Drone, f6)
    }
}
