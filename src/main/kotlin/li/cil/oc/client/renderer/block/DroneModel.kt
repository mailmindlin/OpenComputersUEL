package li.cil.oc.client.renderer.block

import li.cil.oc.client.Textures
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.Collections

object DroneModel : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        val faces = mutableListOf<BakedQuad>()

        for (box in Boxes) {
            faces.addAll(bakeQuads(box, Array(6) { droneTexture }, null))
        }

        return faces
    }

    protected val droneTexture: Any
        get() = Textures.getSprite(Textures.Item.DroneItem)

    protected val Boxes = arrayOf(
        makeBox(Vec3d(1.0 / 16, 7.0 / 16, 1.0 / 16), Vec3d(7.0 / 16, 8.0 / 16, 7.0 / 16)),
        makeBox(Vec3d(1.0 / 16, 7.0 / 16, 9.0 / 16), Vec3d(7.0 / 16, 8.0 / 16, 15.0 / 16)),
        makeBox(Vec3d(9.0 / 16, 7.0 / 16, 1.0 / 16), Vec3d(15.0 / 16, 8.0 / 16, 7.0 / 16)),
        makeBox(Vec3d(9.0 / 16, 7.0 / 16, 9.0 / 16), Vec3d(15.0 / 16, 8.0 / 16, 15.0 / 16)),
        rotateBox(makeBox(Vec3d(6.0 / 16, 6.0 / 16, 6.0 / 16), Vec3d(10.0 / 16, 9.0 / 16, 10.0 / 16)), 45.0)
    )

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return DroneModel
        }
    }
}
