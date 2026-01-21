package li.cil.oc.client.renderer.item

import li.cil.oc.Settings
import li.cil.oc.util.RenderState
import net.minecraft.client.model.ModelBase
import net.minecraft.client.model.ModelBiped
import net.minecraft.client.model.ModelRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

object HoverBootRenderer : ModelBiped() {
    val texture = ResourceLocation(Settings.resourceDomain, "textures/model/drone.png")

    val bootLeft = ModelRenderer(this, "bootLeft")
    val bootRight = ModelRenderer(this, "bootRight")
    val body = ModelRenderer(this, "body")
    val wing0 = ModelRenderer(this, "wing0")
    val wing1 = ModelRenderer(this, "wing1")
    val wing2 = ModelRenderer(this, "wing2")
    val wing3 = ModelRenderer(this, "wing3")
    val light0 = LightModelRenderer(this, "light0")
    val light1 = LightModelRenderer(this, "light1")
    val light2 = LightModelRenderer(this, "light2")
    val light3 = LightModelRenderer(this, "light3")

    init {
        bootLeft.addChild(body)
        bootLeft.addChild(wing0)
        bootLeft.addChild(wing1)

        bootRight.addChild(body)
        bootRight.addChild(wing2)
        bootRight.addChild(wing3)

        wing0.addChild(light0)
        wing1.addChild(light1)
        wing2.addChild(light2)
        wing3.addChild(light3)

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

        bootRight.offsetY = 10.1f / 16
        bootLeft.offsetY = 10.11f / 16f

        body.addBox("top", -3f, 1f, -3f, 6, 1, 6).apply { rotateAngleY = Math.toRadians(45.0).toFloat() }
        body.addBox("middle", -1f, 0f, -1f, 2, 1, 2).apply { rotateAngleY = Math.toRadians(45.0).toFloat() }
        body.addBox("bottom", -2f, -1f, -2f, 4, 1, 4).apply { rotateAngleY = Math.toRadians(45.0).toFloat() }
        wing0.addBox("flap0", -1f, 0f, -7f, 6, 1, 6)
        wing0.addBox("pin0", 0f, -1f, -3f, 1, 3, 1)
        wing1.addBox("flap1", -1f, 0f, 1f, 6, 1, 6)
        wing1.addBox("pin1", 0f, -1f, 2f, 1, 3, 1)
        wing2.addBox("flap2", -5f, 0f, 1f, 6, 1, 6)
        wing2.addBox("pin2", -1f, -1f, 2f, 1, 3, 1)
        wing3.addBox("flap3", -5f, 0f, -7f, 6, 1, 6)
        wing3.addBox("pin3", -1f, -1f, -3f, 1, 3, 1)

        light0.addBox("flap0", -1f, 0f, -7f, 6, 1, 6)
        light1.addBox("flap1", -1f, 0f, 1f, 6, 1, 6)
        light2.addBox("flap2", -5f, 0f, 1f, 6, 1, 6)
        light3.addBox("flap3", -5f, 0f, -7f, 6, 1, 6)

        // No drone textured legs, thank you very much.
        bipedLeftLeg.cubeList.clear()
        bipedRightLeg.cubeList.clear()

        bipedLeftLeg.addChild(bootLeft)
        bipedRightLeg.addChild(bootRight)

        bipedHead.isHidden = true
        bipedHeadwear.isHidden = true
        bipedBody.isHidden = true
        bipedRightArm.isHidden = true
        bipedLeftArm.isHidden = true
    }

    var lightColor = 0x66DD55

    override fun render(entity: Entity, f0: Float, f1: Float, f2: Float, f3: Float, f4: Float, f5: Float) {
        // Because Forge is being a dummy...
        isSneak = entity.isSneaking
        // Because Forge is being an even bigger dummy...
        isChild = false
        super.render(entity, f0, f1, f2, f3, f4, f5)
    }

    class LightModelRenderer(modelBase: ModelBase, name: String) : ModelRenderer(modelBase, name) {
        override fun render(dt: Float) {
            RenderState.pushAttrib()
            GlStateManager.disableLighting()
            RenderState.disableEntityLighting()
            GlStateManager.depthFunc(GL11.GL_LEQUAL)
            RenderState.makeItBlend()
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
            val r = ((lightColor ushr 16) and 0xFF) / 255f
            val g = ((lightColor ushr 8) and 0xFF) / 255f
            val b = ((lightColor ushr 0) and 0xFF) / 255f
            GlStateManager.color(r, g, b)

            super.render(dt)

            RenderState.disableBlend()
            GlStateManager.enableLighting()
            RenderState.enableEntityLighting()
            GlStateManager.color(1f, 1f, 1f)
            RenderState.popAttrib()
        }
    }
}
