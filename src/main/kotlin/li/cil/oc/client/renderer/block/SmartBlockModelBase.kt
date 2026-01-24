package li.cil.oc.client.renderer.block

import li.cil.oc.client.Textures
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.*
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraftforge.common.ForgeModContainer
import org.lwjgl.util.vector.Vector3f
import java.util.Collections

open class SmartBlockModelBase : IBakedModel {
    override fun getOverrides(): ItemOverrideList = ItemOverrideList.NONE

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> = Collections.emptyList()

    override fun isAmbientOcclusion(): Boolean = true

    override fun isGui3d(): Boolean = true

    override fun isBuiltInRenderer(): Boolean = false

    // Note: we don't care about the actual texture here, we just need the block
    // texture atlas. So any of our textures we know is loaded into it will do.
    override fun getParticleTexture(): TextureAtlasSprite = Textures.getSprite(Textures.Block.GenericTop)

    override fun getItemCameraTransforms(): ItemCameraTransforms = DefaultBlockCameraTransforms

    protected val DefaultBlockCameraTransforms: ItemCameraTransforms by lazy {
        val gui = ItemTransformVec3f(Vector3f(30f, 225f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.625f, 0.625f, 0.625f))
        val ground = ItemTransformVec3f(Vector3f(0f, 0f, 0f), Vector3f(0f, 3f, 0f), Vector3f(0.25f, 0.25f, 0.25f))
        val fixed = ItemTransformVec3f(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.5f, 0.5f, 0.5f))
        val thirdperson_righthand = ItemTransformVec3f(Vector3f(75f, 45f, 0f), Vector3f(0f, 2.5f, 0f), Vector3f(0.375f, 0.375f, 0.375f))
        val firstperson_righthand = ItemTransformVec3f(Vector3f(0f, 45f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.40f, 0.40f, 0.40f))
        val firstperson_lefthand = ItemTransformVec3f(Vector3f(0f, 225f, 0f), Vector3f(0f, 0f, 0f), Vector3f(0.40f, 0.40f, 0.40f))

        // scale(0.0625f): see ItemTransformVec3f.Deserializer.deserialize.
        gui.translation.scale(0.0625f)
        ground.translation.scale(0.0625f)
        fixed.translation.scale(0.0625f)
        thirdperson_righthand.translation.scale(0.0625f)
        firstperson_righthand.translation.scale(0.0625f)
        firstperson_lefthand.translation.scale(0.0625f)

        ItemCameraTransforms(
            ItemTransformVec3f.DEFAULT,
            thirdperson_righthand,
            firstperson_lefthand,
            firstperson_righthand,
            ItemTransformVec3f.DEFAULT,
            gui,
            ground,
            fixed
        )
    }

    protected val missingModel: IBakedModel
        get() = Minecraft.getMinecraft().renderItem.itemModelMesher.modelManager.missingModel

    // Standard faces for a unit cube.
    protected val UnitCube = arrayOf(
        arrayOf(Vec3d(0.0, 0.0, 1.0), Vec3d(0.0, 0.0, 0.0), Vec3d(1.0, 0.0, 0.0), Vec3d(1.0, 0.0, 1.0)),
        arrayOf(Vec3d(0.0, 1.0, 0.0), Vec3d(0.0, 1.0, 1.0), Vec3d(1.0, 1.0, 1.0), Vec3d(1.0, 1.0, 0.0)),
        arrayOf(Vec3d(1.0, 1.0, 0.0), Vec3d(1.0, 0.0, 0.0), Vec3d(0.0, 0.0, 0.0), Vec3d(0.0, 1.0, 0.0)),
        arrayOf(Vec3d(0.0, 1.0, 1.0), Vec3d(0.0, 0.0, 1.0), Vec3d(1.0, 0.0, 1.0), Vec3d(1.0, 1.0, 1.0)),
        arrayOf(Vec3d(0.0, 1.0, 0.0), Vec3d(0.0, 0.0, 0.0), Vec3d(0.0, 0.0, 1.0), Vec3d(0.0, 1.0, 1.0)),
        arrayOf(Vec3d(1.0, 1.0, 1.0), Vec3d(1.0, 0.0, 1.0), Vec3d(1.0, 0.0, 0.0), Vec3d(1.0, 1.0, 0.0))
    )

    // Planes perpendicular to facings. Negative values mean we mirror along that,
    // axis which is done to mirror back faces and the y axis (because up is
    // positive but for our texture coordinates down is positive).
    protected val Planes = arrayOf(
        Pair(Vec3d(1.0, 0.0, 0.0), Vec3d(0.0, 0.0, -1.0)),
        Pair(Vec3d(1.0, 0.0, 0.0), Vec3d(0.0, 0.0, 1.0)),
        Pair(Vec3d(-1.0, 0.0, 0.0), Vec3d(0.0, -1.0, 0.0)),
        Pair(Vec3d(1.0, 0.0, 0.0), Vec3d(0.0, -1.0, 0.0)),
        Pair(Vec3d(0.0, 0.0, 1.0), Vec3d(0.0, -1.0, 0.0)),
        Pair(Vec3d(0.0, 0.0, -1.0), Vec3d(0.0, -1.0, 0.0))
    )

    protected val White = 0xFFFFFF

    /**
     * Generates a list of arrays, each containing the four vertices making up a
     * face of the box with the specified size.
     */
    protected fun makeBox(from: Vec3d, to: Vec3d): Array<Array<Vec3d>> {
        val minX = minOf(from.x, to.x)
        val minY = minOf(from.y, to.y)
        val minZ = minOf(from.z, to.z)
        val maxX = maxOf(from.x, to.x)
        val maxY = maxOf(from.y, to.y)
        val maxZ = maxOf(from.z, to.z)
        return UnitCube.map { face ->
            face.map { vertex ->
                Vec3d(
                    maxOf(minX, minOf(maxX, vertex.x)),
                    maxOf(minY, minOf(maxY, vertex.y)),
                    maxOf(minZ, minOf(maxZ, vertex.z))
                )
            }.toTypedArray()
        }.toTypedArray()
    }

    protected fun rotateVector(v: Vec3d, angle: Double, axis: Vec3d): Vec3d {
        // vrot = v * cos(angle) + (axis x v) * sin(angle) + axis * (axis dot v)(1 - cos(angle))
        fun scale(v: Vec3d, s: Double) = Vec3d(v.x * s, v.y * s, v.z * s)
        val cosAngle = kotlin.math.cos(angle)
        val sinAngle = kotlin.math.sin(angle)
        return scale(v, cosAngle)
            .add(scale(axis.crossProduct(v), sinAngle))
            .add(scale(axis, axis.dotProduct(v) * (1 - cosAngle)))
    }

    protected fun rotateFace(face: Array<Vec3d>, angle: Double, axis: Vec3d, around: Vec3d = Vec3d(0.5, 0.5, 0.5)): Array<Vec3d> {
        return face.map { v -> rotateVector(v.subtract(around), angle, axis).add(around) }.toTypedArray()
    }

    protected fun rotateBox(box: Array<Array<Vec3d>>, angle: Double, axis: Vec3d = Vec3d(0.0, 1.0, 0.0), around: Vec3d = Vec3d(0.5, 0.5, 0.5)): Array<Array<Vec3d>> {
        return box.map { face -> rotateFace(face, angle, axis, around) }.toTypedArray()
    }

    /**
     * Create the BakedQuads for a set of quads defined by the specified vertices.
     *
     * Usually used to generate the quads for a cube previously generated using makeBox().
     */
    protected fun bakeQuads(box: Array<Array<Vec3d>>, texture: Array<out Any>, color: Int?): Array<BakedQuad> {
        val colorRGB = color ?: White
        return bakeQuads(box, texture, colorRGB)
    }

    /**
     * Create the BakedQuads for a set of quads defined by the specified vertices.
     *
     * Usually used to generate the quads for a cube previously generated using makeBox().
     */
    protected fun bakeQuads(box: Array<Array<Vec3d>>, texture: Array<out Any>, colorRGB: Int): Array<BakedQuad> {
        return EnumFacing.values().map { side ->
            val vertices = box[side.index]
            val tex = texture[side.index] as TextureAtlasSprite
            val data = quadData(vertices, side, tex, colorRGB, 0)
            BakedQuad(data, -1, side, tex, true, DefaultVertexFormats.ITEM)
        }.toTypedArray()
    }

    /**
     * Create a single BakedQuad of a unit cube's specified side.
     */
    protected fun bakeQuad(side: EnumFacing, texture: Any, color: Int?, rotation: Int): BakedQuad {
        val colorRGB = color ?: White
        val vertices = UnitCube[side.index]
        val tex = texture as TextureAtlasSprite
        val data = quadData(vertices, side, tex, colorRGB, rotation)
        return BakedQuad(data, -1, side, tex, true, DefaultVertexFormats.ITEM)
    }

    // Generate raw data used for a BakedQuad based on the specified facing, vertices, texture and rotation.
    // The UV coordinates are generated from the positions of the vertices, i.e. they are simply cube-
    // mapped. This is good enough for us.
    protected fun quadData(vertices: Array<Vec3d>, facing: EnumFacing, texture: TextureAtlasSprite, colorRGB: Int, rotation: Int): IntArray {
        val (uAxis, vAxis) = Planes[facing.index]
        val rot = (rotation + 4) % 4
        return vertices.flatMap { vertex ->
            var u = vertex.dotProduct(uAxis)
            var v = vertex.dotProduct(vAxis)
            if (uAxis.x + uAxis.y + uAxis.z < 0) u = 1 + u
            if (vAxis.x + vAxis.y + vAxis.z < 0) v = 1 + v
            for (i in 0 until rot) {
                // (u, v) = (v, -u)
                val tmp = u
                u = v
                v = (-(tmp - 0.5)) + 0.5
            }
            rawData(vertex.x, vertex.y, vertex.z, facing, texture, texture.getInterpolatedU(u * 16), texture.getInterpolatedV(v * 16), colorRGB).toList()
        }.toIntArray()
    }

    // See FaceBakery#storeVertexData.
    protected fun rawData(x: Double, y: Double, z: Double, face: EnumFacing, texture: TextureAtlasSprite, u: Float, v: Float, colorRGB: Int): IntArray {
        val vx = (face.xOffset * 127) and 0xFF
        val vy = (face.yOffset * 127) and 0xFF
        val vz = (face.zOffset * 127) and 0xFF

        return intArrayOf(
            x.toFloat().toRawBits(),
            y.toFloat().toRawBits(),
            z.toFloat().toRawBits(),
            getFaceShadeColor(face, colorRGB),
            u.toRawBits(),
            v.toRawBits(),
            vx or (vy shl 0x08) or (vz shl 0x10)
        )
    }

    protected fun getFaceShadeColor(face: EnumFacing, colorRGB: Int): Int {
        return if (ForgeModContainer.forgeLightPipelineEnabled) {
            // Forge's light pipeline uses a separate lighting stage.
            0xFF000000.toInt() or (colorRGB and 0xFF00) or ((colorRGB and 0xFF) shl 16) or ((colorRGB and 0xFF0000) shr 16)
        } else {
            // See FaceBakery.
            // TODO: This still doesn't look right on non-solid blocks (compare print3d/stairs.3dm).
            val brightness = getFaceBrightness(face)
            val b = (colorRGB shr 16) and 0xFF
            val g = (colorRGB shr 8) and 0xFF
            val r = colorRGB and 0xFF
            0xFF000000.toInt() or (shade(r, brightness) shl 16) or (shade(g, brightness) shl 8) or shade(b, brightness)
        }
    }

    private fun shade(value: Int, brightness: Float) = ((brightness * value).toInt()).coerceIn(0, 255)

    protected fun getFaceBrightness(face: EnumFacing): Float {
        return when (face) {
            EnumFacing.DOWN -> 0.5f
            EnumFacing.UP -> 1.0f
            EnumFacing.NORTH, EnumFacing.SOUTH -> 0.8f
            EnumFacing.WEST, EnumFacing.EAST -> 0.6f
        }
    }
}
