package li.cil.oc.client.renderer.block

import li.cil.oc.client.Textures
import li.cil.oc.common.block.Cable as BlockCable
import li.cil.oc.common.tileentity.Cable as TECable
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Color
import li.cil.oc.util.ExtendedWorld
import li.cil.oc.util.ItemColorizer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.common.property.IExtendedBlockState
import java.util.Collections

object CableModel : CableModelBase()

open class CableModelBase : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        if (state is IExtendedBlockState) {
            val neighbors = state.getValue(BlockCable.NeighborsProp)
            val color = state.getValue(BlockCable.ColorProp)
            val isCableSide = state.getValue(BlockCable.IsSideCableProp)

            if (neighbors is Int && color is Int && isCableSide is Int) {
                val faces = mutableListOf<BakedQuad>()

                faces.addAll(bakeQuads(Middle, cableTexture, color))
                for (facing in EnumFacing.values()) {
                    val connected = (neighbors and (1 shl facing.index)) != 0
                    val isCableOnSide = (isCableSide and (1 shl facing.index)) != 0
                    val (plug, shortBody, longBody) = Connected[facing.index]
                    if (connected) {
                        if (isCableOnSide) {
                            faces.addAll(bakeQuads(longBody, cableTexture, color))
                        } else {
                            faces.addAll(bakeQuads(shortBody, cableTexture, color))
                            faces.addAll(bakeQuads(plug, cableCapTexture, null))
                        }
                    } else if (((1 shl facing.opposite.index) and neighbors) == neighbors || neighbors == 0) {
                        faces.addAll(bakeQuads(Disconnected[facing.index], cableCapTexture, null))
                    }
                }

                return faces
            }
        }
        return super.getQuads(state, side, rand)
    }

    protected fun isCable(pos: BlockPosition): Boolean {
        val world = pos.world ?: return false
        return world.getTileEntity(pos.toBlockPos()) is TECable
    }

    protected val Middle = makeBox(Vec3d(6 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 10 / 16.0))

    // Per side, always plug + short cable + long cable (no plug).
    protected val Connected = arrayOf(
        Triple(
            makeBox(Vec3d(5 / 16.0, 0 / 16.0, 5 / 16.0), Vec3d(11 / 16.0, 1 / 16.0, 11 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 1 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 6 / 16.0, 10 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 0 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 6 / 16.0, 10 / 16.0))
        ),
        Triple(
            makeBox(Vec3d(5 / 16.0, 15 / 16.0, 5 / 16.0), Vec3d(11 / 16.0, 16 / 16.0, 11 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 10 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 15 / 16.0, 10 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 10 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 16 / 16.0, 10 / 16.0))
        ),
        Triple(
            makeBox(Vec3d(5 / 16.0, 5 / 16.0, 0 / 16.0), Vec3d(11 / 16.0, 11 / 16.0, 1 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 6 / 16.0, 1 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 6 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 6 / 16.0, 0 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 6 / 16.0))
        ),
        Triple(
            makeBox(Vec3d(5 / 16.0, 5 / 16.0, 15 / 16.0), Vec3d(11 / 16.0, 11 / 16.0, 16 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 6 / 16.0, 10 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 15 / 16.0)),
            makeBox(Vec3d(6 / 16.0, 6 / 16.0, 10 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 16 / 16.0))
        ),
        Triple(
            makeBox(Vec3d(0 / 16.0, 5 / 16.0, 5 / 16.0), Vec3d(1 / 16.0, 11 / 16.0, 11 / 16.0)),
            makeBox(Vec3d(1 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(6 / 16.0, 10 / 16.0, 10 / 16.0)),
            makeBox(Vec3d(0 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(6 / 16.0, 10 / 16.0, 10 / 16.0))
        ),
        Triple(
            makeBox(Vec3d(15 / 16.0, 5 / 16.0, 5 / 16.0), Vec3d(16 / 16.0, 11 / 16.0, 11 / 16.0)),
            makeBox(Vec3d(10 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(15 / 16.0, 10 / 16.0, 10 / 16.0)),
            makeBox(Vec3d(10 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(16 / 16.0, 10 / 16.0, 10 / 16.0))
        )
    )

    // Per side, cap only.
    protected val Disconnected = arrayOf(
        makeBox(Vec3d(6 / 16.0, 5 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 6 / 16.0, 10 / 16.0)),
        makeBox(Vec3d(6 / 16.0, 10 / 16.0, 6 / 16.0), Vec3d(10 / 16.0, 11 / 16.0, 10 / 16.0)),
        makeBox(Vec3d(6 / 16.0, 6 / 16.0, 5 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 6 / 16.0)),
        makeBox(Vec3d(6 / 16.0, 6 / 16.0, 10 / 16.0), Vec3d(10 / 16.0, 10 / 16.0, 11 / 16.0)),
        makeBox(Vec3d(5 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(6 / 16.0, 10 / 16.0, 10 / 16.0)),
        makeBox(Vec3d(10 / 16.0, 6 / 16.0, 6 / 16.0), Vec3d(11 / 16.0, 10 / 16.0, 10 / 16.0))
    )

    protected val cableTexture: Array<out Any>
        get() = Array(6) { Textures.getSprite(Textures.Block.Cable) }

    protected val cableCapTexture: Array<out Any>
        get() = Array(6) { Textures.getSprite(Textures.Block.CableCap) }

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        class ItemModel(val stack: ItemStack) : SmartBlockModelBase() {
            override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
                val faces = mutableListOf<BakedQuad>()

                val color = if (ItemColorizer.hasColor(stack)) ItemColorizer.getColor(stack) else Color.rgbValues(EnumDyeColor.SILVER).toInt()

                faces.addAll(bakeQuads(CableModel.Middle, CableModel.cableTexture, color))
                faces.addAll(bakeQuads(CableModel.Connected[0].second, CableModel.cableTexture, color))
                faces.addAll(bakeQuads(CableModel.Connected[1].second, CableModel.cableTexture, color))
                faces.addAll(bakeQuads(CableModel.Connected[0].first, CableModel.cableCapTexture, null))
                faces.addAll(bakeQuads(CableModel.Connected[1].first, CableModel.cableCapTexture, null))

                return faces
            }
        }

        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return ItemModel(stack)
        }
    }
}
