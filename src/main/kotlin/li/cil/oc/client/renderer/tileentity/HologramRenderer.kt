package li.cil.oc.client.renderer.tileentity

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Hologram
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.GlStateManager.CullFace
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import java.nio.IntBuffer
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object HologramRenderer : TileEntitySpecialRenderer<Hologram>(), Callable<Int>, RemovalListener<TileEntity, Int> {
    private val random = Random.Default

    /** We cache the VBOs for the projectors we render for performance. */
    private val cache = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.SECONDS)
        .removalListener(this)
        .build<Hologram, Int>()

    /**
     * Common for all holograms. Holds the vertex positions, texture
     * coordinates and normals information. Layout is: u v nx ny nz x y z
     *
     * WARNING: this optimization only works if all the holograms have the
     * same dimensions (in voxels). If we ever need holograms of different
     * sizes we could probably just fake that by making the outer layers
     * immutable (i.e. always empty).
     *
     * NOTE: It already takes up 47.25 MiB of video memory and increasing
     * hologram size to, for example, 64*64*64 will result in 168 MiB.
     */
    private var commonBuffer = 0

    /**
     * Also common for all holograms. Temporary buffer used to upload
     * hologram data to GPU. First half stores colors for each vertex
     * (0xAABBGGRR Int, alpha is used for alignment only) and second
     * half stores (Int) indices of vertices that should be drawn.
     */
    private var dataBuffer: IntBuffer? = null

    /** Used to pass the current screen along to call(). */
    private var hologram: Hologram? = null

    /**
     * Whether initialization failed (e.g. due to an out of memory error) and we
     * should render using the fallback renderer instead.
     */
    private var failed = false

    override fun render(hologram: Hologram, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        if (failed) {
            HologramRendererFallback.render(hologram, x, y, z, f, damage, alpha)
            return
        }

        this.hologram = hologram
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        if (!hologram.hasPower) return

        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS)
        RenderState.pushAttrib()
        RenderState.makeItBlend()
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)

        val playerDistSq = x * x + y * y + z * z
        val maxDistSq = hologram.getMaxRenderDistanceSquared()
        val fadeDistSq = hologram.getFadeStartDistanceSquared()
        RenderState.setBlendAlpha(0.75f * (if (playerDistSq > fadeDistSq) maxOf(0.0, 1 - ((playerDistSq - fadeDistSq) / (maxDistSq - fadeDistSq))).toFloat() else 1f))

        GlStateManager.pushMatrix()
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        when (hologram.yaw ?: EnumFacing.SOUTH) {
            EnumFacing.WEST -> GL11.glRotatef(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GL11.glRotatef(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GL11.glRotatef(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }
        when (hologram.pitch ?: EnumFacing.NORTH) {
            EnumFacing.DOWN -> GL11.glRotatef(90f, 1f, 0f, 0f)
            EnumFacing.UP -> GL11.glRotatef(-90f, 1f, 0f, 0f)
            else -> {} // No pitch.
        }

        GlStateManager.rotate(hologram.rotationAngle, hologram.rotationX, hologram.rotationY, hologram.rotationZ)
        GlStateManager.rotate(hologram.rotationSpeed * (hologram.world?.totalWorldTime ?: 0L % (360 * 20 - 1) + f).toFloat() / 20f, hologram.rotationSpeedX, hologram.rotationSpeedY, hologram.rotationSpeedZ)

        GlStateManager.scale(1.001, 1.001, 1.001) // Avoid z-fighting with other blocks.
        GlStateManager.translate(
            (hologram.translation.x * hologram.width / 16 - 1.5) * hologram.scale,
            hologram.translation.y * hologram.height / 16 * hologram.scale,
            (hologram.translation.z * hologram.width / 16 - 1.5) * hologram.scale
        )

        // Do a bit of flickering, because that's what holograms do!
        if (Settings.get.hologramFlickerFrequency > 0 && random.nextDouble() < Settings.get.hologramFlickerFrequency) {
            val rand = java.util.Random()
            GlStateManager.scale(1 + rand.nextGaussian() * 0.01, 1 + rand.nextGaussian() * 0.001, 1 + rand.nextGaussian() * 0.01)
            GlStateManager.translate(rand.nextGaussian() * 0.01, rand.nextGaussian() * 0.01, rand.nextGaussian() * 0.01)
        }

        // After the below scaling, hologram is drawn inside a [0..48]x[0..32]x[0..48] box
        GlStateManager.scale(hologram.scale / 16f, hologram.scale / 16f, hologram.scale / 16f)

        bindTexture(Textures.Model.HologramEffect)

        // Normalize normals (yes, glScale scales them too).
        GL11.glEnable(GL11.GL_NORMALIZE)

        val sx = (x + 0.5) * hologram.scale
        val sy = -(y + 0.5) * hologram.scale
        val sz = (z + 0.5) * hologram.scale
        if (sx >= -1.5 && sx <= 1.5 && sz >= -1.5 && sz <= 1.5 && sy >= 0 && sy <= 2) {
            // Camera is inside the hologram.
            GlStateManager.disableCull()
        } else {
            // Camera is outside the hologram.
            GlStateManager.enableCull()
            GlStateManager.cullFace(CullFace.BACK)
        }

        // We do two passes here to avoid weird transparency effects: in the first
        // pass we find the front-most fragment, in the second we actually draw it.
        // When we don't do this the hologram will look different from different
        // angles (because some faces will shine through sometimes and sometimes
        // they won't), so a more... consistent look is desirable.
        val glBuffer = cache.get(hologram, this)
        GlStateManager.colorMask(false, false, false, false)
        GlStateManager.depthMask(true)
        draw(glBuffer)
        GlStateManager.colorMask(true, true, true, true)
        GlStateManager.depthFunc(GL11.GL_EQUAL)
        draw(glBuffer)
        GlStateManager.depthFunc(GL11.GL_LEQUAL)

        GlStateManager.popMatrix()

        RenderState.disableBlend()
        RenderState.popAttrib()
        GL11.glPopClientAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }

    fun draw(glBuffer: Int) {
        if (initialize()) {
            validate(glBuffer)
            publish(glBuffer)
        }
    }

    private fun initialize(): Boolean = !failed && try {
        // First run only, create structure information.
        if (commonBuffer == 0) {
            val holo = hologram!!
            dataBuffer = BufferUtils.createIntBuffer(holo.width * holo.width * holo.height * 6 * 4 * 2)

            commonBuffer = GL15.glGenBuffers()

            val data = BufferUtils.createFloatBuffer(holo.width * holo.width * holo.height * 24 * (2 + 3 + 3))
            fun addVertex(x: Int, y: Int, z: Int, u: Int, v: Int, nx: Int, ny: Int, nz: Int) {
                data.put(u.toFloat())
                data.put(v.toFloat())
                data.put(nx.toFloat())
                data.put(ny.toFloat())
                data.put(nz.toFloat())
                data.put(x.toFloat())
                data.put(y.toFloat())
                data.put(z.toFloat())
            }

            for (x in 0 until holo.width) {
                for (z in 0 until holo.width) {
                    for (y in 0 until holo.height) {
                        /*
                              0---1
                              | N |
                          0---3---2---1---0
                          | W | U | E | D |
                          5---6---7---4---5
                              | S |
                              5---4
                         */

                        // South
                        addVertex(x + 1, y + 1, z + 1, 0, 0, 0, 0, 1) // 5
                        addVertex(x + 0, y + 1, z + 1, 1, 0, 0, 0, 1) // 4
                        addVertex(x + 0, y + 0, z + 1, 1, 1, 0, 0, 1) // 7
                        addVertex(x + 1, y + 0, z + 1, 0, 1, 0, 0, 1) // 6
                        // North
                        addVertex(x + 1, y + 0, z + 0, 0, 0, 0, 0, -1) // 3
                        addVertex(x + 0, y + 0, z + 0, 1, 0, 0, 0, -1) // 2
                        addVertex(x + 0, y + 1, z + 0, 1, 1, 0, 0, -1) // 1
                        addVertex(x + 1, y + 1, z + 0, 0, 1, 0, 0, -1) // 0

                        // East
                        addVertex(x + 1, y + 1, z + 1, 1, 0, 1, 0, 0) // 5
                        addVertex(x + 1, y + 0, z + 1, 1, 1, 1, 0, 0) // 6
                        addVertex(x + 1, y + 0, z + 0, 0, 1, 1, 0, 0) // 3
                        addVertex(x + 1, y + 1, z + 0, 0, 0, 1, 0, 0) // 0
                        // West
                        addVertex(x + 0, y + 0, z + 1, 1, 0, -1, 0, 0) // 7
                        addVertex(x + 0, y + 1, z + 1, 1, 1, -1, 0, 0) // 4
                        addVertex(x + 0, y + 1, z + 0, 0, 1, -1, 0, 0) // 1
                        addVertex(x + 0, y + 0, z + 0, 0, 0, -1, 0, 0) // 2

                        // Up
                        addVertex(x + 1, y + 1, z + 0, 0, 0, 0, 1, 0) // 0
                        addVertex(x + 0, y + 1, z + 0, 1, 0, 0, 1, 0) // 1
                        addVertex(x + 0, y + 1, z + 1, 1, 1, 0, 1, 0) // 4
                        addVertex(x + 1, y + 1, z + 1, 0, 1, 0, 1, 0) // 5
                        // Down
                        addVertex(x + 1, y + 0, z + 1, 0, 0, 0, -1, 0) // 6
                        addVertex(x + 0, y + 0, z + 1, 1, 0, 0, -1, 0) // 7
                        addVertex(x + 0, y + 0, z + 0, 1, 1, 0, -1, 0) // 2
                        addVertex(x + 1, y + 0, z + 0, 0, 1, 0, -1, 0) // 3
                    }
                }
            }

            // Important! OpenGL will start reading from the current buffer position.
            data.rewind()

            // This buffer never ever changes, so static is the way to go.
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, commonBuffer)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW)
        }
        true
    } catch (oom: OutOfMemoryError) {
        HologramRendererFallback.text = "Not enough memory"
        failed = true
        false
    }

    private fun validate(glBuffer: Int) {
        val holo = hologram!!
        // Refresh indexes when the hologram's data changed.
        if (holo.needsRendering) {
            fun value(hx: Int, hy: Int, hz: Int) = if (hx >= 0 && hy >= 0 && hz >= 0 && hx < holo.width && hy < holo.height && hz < holo.width) holo.getColor(hx, hy, hz) else 0

            fun isSolid(hx: Int, hy: Int, hz: Int) = value(hx, hy, hz) != 0

            fun addFace(index: Int, color: Int) {
                dataBuffer!!.put(index)
                dataBuffer!!.put(index + 1)
                dataBuffer!!.put(index + 2)
                dataBuffer!!.put(index + 3)

                dataBuffer!!.put(index, color)
                dataBuffer!!.put(index + 1, color)
                dataBuffer!!.put(index + 2, color)
                dataBuffer!!.put(index + 3, color)

                holo.visibleQuads += 1
            }

            // Copy color information, identify which quads to render and prepare data for glDrawElements
            holo.visibleQuads = 0
            var index = 0
            dataBuffer!!.position(holo.width * holo.width * holo.height * 6 * 4)
            for (hx in 0 until holo.width) {
                for (hz in 0 until holo.width) {
                    for (hy in 0 until holo.height) {
                        // Do we need to draw at least one face?
                        if (isSolid(hx, hy, hz)) {
                            // Yes, get the color of the voxel.
                            val color = holo.colors[value(hx, hy, hz) - 1]

                            // South
                            if (!isSolid(hx, hy, hz + 1)) {
                                addFace(index, color)
                            }
                            index += 4
                            // North
                            if (!isSolid(hx, hy, hz - 1)) {
                                addFace(index, color)
                            }
                            index += 4

                            // East
                            if (!isSolid(hx + 1, hy, hz)) {
                                addFace(index, color)
                            }
                            index += 4
                            // West
                            if (!isSolid(hx - 1, hy, hz)) {
                                addFace(index, color)
                            }
                            index += 4

                            // Up
                            if (!isSolid(hx, hy + 1, hz)) {
                                addFace(index, color)
                            }
                            index += 4
                            // Down
                            if (!isSolid(hx, hy - 1, hz)) {
                                addFace(index, color)
                            }
                            index += 4
                        } else {
                            // No, skip all associated indices.
                            index += 6 * 4
                        }
                    }
                }
            }

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glBuffer)
            if (holo.visibleQuads > 0) {
                // Flip the buffer to only fill in as much data as necessary.
                dataBuffer!!.flip()

                // This buffer can be updated quite frequently, so dynamic seems sensible.
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dataBuffer, GL15.GL_DYNAMIC_DRAW)
            } else {
                // Empty hologram.
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 0L, GL15.GL_DYNAMIC_DRAW)
            }

            // Reset for the next operation.
            dataBuffer!!.clear()

            holo.needsRendering = false
        }
    }

    private fun publish(glBuffer: Int) {
        val holo = hologram!!
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, commonBuffer)
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY)
        GL11.glInterleavedArrays(GL11.GL_T2F_N3F_V3F, 0, 0)

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glBuffer)
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY)
        GL11.glColorPointer(3, GL11.GL_UNSIGNED_BYTE, 4, 0)

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, glBuffer)
        GL11.glDrawElements(GL11.GL_QUADS, holo.visibleQuads * 4, GL11.GL_UNSIGNED_INT, (holo.width * holo.width * holo.height * 6 * 4 * 4).toLong())
    }

    // ----------------------------------------------------------------------- //
    // Cache
    // ----------------------------------------------------------------------- //

    override fun call(): Int {
        val glBuffer = GL15.glGenBuffers()

        // Force re-indexing.
        hologram!!.needsRendering = true

        return glBuffer
    }

    override fun onRemoval(e: RemovalNotification<TileEntity, Int>) {
        val glBuffer = e.value
        GL15.glDeleteBuffers(glBuffer)
        dataBuffer!!.clear()
    }

    @SubscribeEvent
    @Suppress("unused")
    fun onTick(e: ClientTickEvent) = cache.cleanUp()
}
