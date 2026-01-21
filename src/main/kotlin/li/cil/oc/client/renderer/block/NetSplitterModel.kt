package li.cil.oc.client.renderer.block

import li.cil.oc.client.Textures
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity.NetSplitter
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.Collections

object NetSplitterModel : SmartBlockModelBase() {
    override fun getOverrides(): ItemOverrideList = ItemOverride

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        if (state is IExtendedBlockState) {
            val tile = state.getValue(PropertyTile)
            if (tile is NetSplitter) {
                val faces = mutableListOf<BakedQuad>()

                faces.addAll(BaseModel)
                addSideQuads(faces, EnumFacing.values().map { tile.isSideOpen(it) }.toBooleanArray())

                return faces
            }
        }
        return super.getQuads(state, side, rand)
    }

    private val splitterTexture: Array<out Any>
        get() = arrayOf(
            Textures.getSprite(Textures.Block.NetSplitterTop),
            Textures.getSprite(Textures.Block.NetSplitterTop),
            Textures.getSprite(Textures.Block.NetSplitterSide),
            Textures.getSprite(Textures.Block.NetSplitterSide),
            Textures.getSprite(Textures.Block.NetSplitterSide),
            Textures.getSprite(Textures.Block.NetSplitterSide)
        )

    private fun generateBaseModel(): Array<BakedQuad> {
        val faces = mutableListOf<BakedQuad>()

        // Bottom.
        faces.addAll(bakeQuads(makeBox(Vec3d(0 / 16.0, 0 / 16.0, 5 / 16.0), Vec3d(5 / 16.0, 5 / 16.0, 11 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(11 / 16.0, 0 / 16.0, 5 / 16.0), Vec3d(16 / 16.0, 5 / 16.0, 11 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 0 / 16.0, 0 / 16.0), Vec3d(11 / 16.0, 5 / 16.0, 5 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 0 / 16.0, 11 / 16.0), Vec3d(11 / 16.0, 5 / 16.0, 16 / 16.0)), splitterTexture, null))
        // Corners.
        faces.addAll(bakeQuads(makeBox(Vec3d(0 / 16.0, 0 / 16.0, 0 / 16.0), Vec3d(5 / 16.0, 16 / 16.0, 5 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(11 / 16.0, 0 / 16.0, 0 / 16.0), Vec3d(16 / 16.0, 16 / 16.0, 5 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(0 / 16.0, 0 / 16.0, 11 / 16.0), Vec3d(5 / 16.0, 16 / 16.0, 16 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(11 / 16.0, 0 / 16.0, 11 / 16.0), Vec3d(16 / 16.0, 16 / 16.0, 16 / 16.0)), splitterTexture, null))
        // Top.
        faces.addAll(bakeQuads(makeBox(Vec3d(0 / 16.0, 11 / 16.0, 5 / 16.0), Vec3d(5 / 16.0, 16 / 16.0, 11 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(11 / 16.0, 11 / 16.0, 5 / 16.0), Vec3d(16 / 16.0, 16 / 16.0, 11 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 11 / 16.0, 0 / 16.0), Vec3d(11 / 16.0, 16 / 16.0, 5 / 16.0)), splitterTexture, null))
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 11 / 16.0, 11 / 16.0), Vec3d(11 / 16.0, 16 / 16.0, 16 / 16.0)), splitterTexture, null))

        return faces.toTypedArray()
    }

    private var BaseModel = emptyArray<BakedQuad>()

    @SubscribeEvent
    @Suppress("unused")
    fun onTextureStitch(e: TextureStitchEvent.Post) {
        BaseModel = generateBaseModel()
    }

    private fun addSideQuads(faces: MutableList<BakedQuad>, openSides: BooleanArray) {
        val down = openSides[EnumFacing.DOWN.ordinal]
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, if (down) 0 / 16.0 else 2 / 16.0, 5 / 16.0), Vec3d(11 / 16.0, 5 / 16.0, 11 / 16.0)), splitterTexture, null))

        val up = openSides[EnumFacing.UP.ordinal]
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 11 / 16.0, 5 / 16.0), Vec3d(11 / 16.0, if (up) 16 / 16.0 else 14 / 16.0, 11 / 16.0)), splitterTexture, null))

        val north = openSides[EnumFacing.NORTH.ordinal]
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 5 / 16.0, if (north) 0 / 16.0 else 2 / 16.0), Vec3d(11 / 16.0, 11 / 16.0, 5 / 16.0)), splitterTexture, null))

        val south = openSides[EnumFacing.SOUTH.ordinal]
        faces.addAll(bakeQuads(makeBox(Vec3d(5 / 16.0, 5 / 16.0, 11 / 16.0), Vec3d(11 / 16.0, 11 / 16.0, if (south) 16 / 16.0 else 14 / 16.0)), splitterTexture, null))

        val west = openSides[EnumFacing.WEST.ordinal]
        faces.addAll(bakeQuads(makeBox(Vec3d(if (west) 0 / 16.0 else 2 / 16.0, 5 / 16.0, 5 / 16.0), Vec3d(5 / 16.0, 11 / 16.0, 11 / 16.0)), splitterTexture, null))

        val east = openSides[EnumFacing.EAST.ordinal]
        faces.addAll(bakeQuads(makeBox(Vec3d(11 / 16.0, 5 / 16.0, 5 / 16.0), Vec3d(if (east) 16 / 16.0 else 14 / 16.0, 11 / 16.0, 11 / 16.0)), splitterTexture, null))
    }

    class ItemModel(val stack: ItemStack) : SmartBlockModelBase() {
        val data = PrintData(stack)

        override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
            val faces = mutableListOf<BakedQuad>()

            Textures.Block.bind()

            faces.addAll(BaseModel)
            addSideQuads(faces, BooleanArray(EnumFacing.values().size) { false })

            return faces
        }
    }

    object ItemOverride : ItemOverrideList(Collections.emptyList()) {
        override fun handleItemState(originalModel: IBakedModel, stack: ItemStack, world: World?, entity: EntityLivingBase?): IBakedModel {
            return ItemModel(stack)
        }
    }
}
