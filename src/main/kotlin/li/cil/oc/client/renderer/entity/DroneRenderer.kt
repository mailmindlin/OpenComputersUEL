package li.cil.oc.client.renderer.entity

import li.cil.oc.client.Textures
import li.cil.oc.common.entity.Drone
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.entity.Render
import net.minecraft.client.renderer.entity.RenderManager

class DroneRenderer(manager: RenderManager) : Render<Drone>(manager) {
    val model = ModelQuadcopter()

    override fun doRender(entity: Drone, x: Double, y: Double, z: Double, yaw: Float, dt: Float) {
        bindEntityTexture(entity)
        GlStateManager.pushMatrix()
        RenderState.pushAttrib()

        GlStateManager.translate(x, y + 2 / 16f, z)

        model.render(entity, 0f, 0f, 0f, 0f, 0f, dt)

        RenderState.popAttrib()
        GlStateManager.popMatrix()
    }

    override fun getEntityTexture(entity: Drone) = Textures.Model.Drone
}
