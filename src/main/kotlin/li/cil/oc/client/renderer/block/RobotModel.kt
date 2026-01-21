package li.cil.oc.client.renderer.block

import li.cil.oc.client.Textures
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import java.util.Collections

object RobotModel : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    object ItemModel : SmartBlockModelBase() {
        private const val size = 0.4f
        private const val l = 0.5f - size
        private const val h = 0.5f + size

        private val top = floatArrayOf(0.5f, 1f, 0.5f, 0.25f, 0.25f)
        private val top1 = floatArrayOf(l, 0.5f, h, 0f, 0f)
        private val top2 = floatArrayOf(h, 0.5f, h, 0f, 0.5f)
        private val top3 = floatArrayOf(h, 0.5f, l, 0.5f, 0.5f)
        private val top4 = floatArrayOf(l, 0.5f, l, 0.5f, 0f)

        private val bottom = floatArrayOf(0.5f, 0f, 0.5f, 0.75f, 0.25f)
        private val bottom1 = floatArrayOf(l, 0.5f, l, 0.5f, 0.5f)
        private val bottom2 = floatArrayOf(h, 0.5f, l, 0.5f, 0f)
        private val bottom3 = floatArrayOf(h, 0.5f, h, 1f, 0f)
        private val bottom4 = floatArrayOf(l, 0.5f, h, 1f, 0.5f)

        // I don't know why this is super-bright when using 0xFF888888 :/
        private const val tint = 0xFF555555.toInt()

        protected val robotTexture: Any
            get() = Textures.getSprite(Textures.Item.Robot)

        private fun interpolate(v0: FloatArray, v1: FloatArray) =
            floatArrayOf(
                v0[0] * 0.5f + v1[0] * 0.5f,
                v0[1] * 0.5f + v1[1] * 0.5f,
                v0[2] * 0.5f + v1[2] * 0.5f,
                v0[3] * 0.5f + v1[3] * 0.5f,
                v0[4] * 0.5f + v1[4] * 0.5f
            )

        private fun quad(vararg verts: FloatArray): IntArray {
            val texture = Textures.getSprite(Textures.Item.Robot)
            val added = interpolate(verts.last(), verts.first())
            return (verts.toList() + added).flatMap { v ->
                rawData(
                    ((v[0] - 0.5f) * 1.4f + 0.5f).toDouble(),
                    ((v[1] - 0.5f) * 1.4f + 0.5f).toDouble(),
                    ((v[2] - 0.5f) * 1.4f + 0.5f).toDouble(),
                    EnumFacing.UP, texture, texture.getInterpolatedU((v[3] * 16).toDouble()), texture.getInterpolatedV((v[4] * 16).toDouble()),
                    White
                ).toList()
            }.toIntArray()
        }

        override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
            val faces = mutableListOf<BakedQuad>()
            val texture = Textures.getSprite(Textures.Item.Robot)

            faces.add(BakedQuad(quad(top, top1, top2), tint, EnumFacing.NORTH, texture, true, DefaultVertexFormats.ITEM))
            faces.add(BakedQuad(quad(top, top2, top3), tint, EnumFacing.EAST, texture, true, DefaultVertexFormats.ITEM))
            faces.add(BakedQuad(quad(top, top3, top4), tint, EnumFacing.SOUTH, texture, true, DefaultVertexFormats.ITEM))
            faces.add(BakedQuad(quad(top, top4, top1), tint, EnumFacing.WEST, texture, true, DefaultVertexFormats.ITEM))

            faces.add(BakedQuad(quad(bottom, bottom1, bottom2), tint, EnumFacing.NORTH, texture, true, DefaultVertexFormats.ITEM))
            faces.add(BakedQuad(quad(bottom, bottom2, bottom3), tint, EnumFacing.EAST, texture, true, DefaultVertexFormats.ITEM))
            faces.add(BakedQuad(quad(bottom, bottom3, bottom4), tint, EnumFacing.SOUTH, texture, true, DefaultVertexFormats.ITEM))
            faces.add(BakedQuad(quad(bottom, bottom4, bottom1), tint, EnumFacing.WEST, texture, true, DefaultVertexFormats.ITEM))

            return faces
        }
    }

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return ItemModel
        }
    }
}
