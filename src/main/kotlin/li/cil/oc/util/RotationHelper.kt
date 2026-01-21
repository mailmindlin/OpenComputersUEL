package li.cil.oc.util

import net.minecraft.util.EnumFacing
import kotlin.math.roundToInt

object RotationHelper {
    @JvmStatic
    fun fromYaw(yaw: Float): EnumFacing {
        return when ((yaw / 360 * 4).roundToInt() and 3) {
            0 -> EnumFacing.SOUTH
            1 -> EnumFacing.WEST
            2 -> EnumFacing.NORTH
            3 -> EnumFacing.EAST
            else -> EnumFacing.SOUTH
        }
    }

    @JvmStatic
    fun toLocal(pitch: EnumFacing, yaw: EnumFacing, value: EnumFacing): EnumFacing =
        translationFor(pitch, yaw)[value.ordinal]

    @JvmStatic
    fun toGlobal(pitch: EnumFacing, yaw: EnumFacing, value: EnumFacing): EnumFacing =
        inverseTranslationFor(pitch, yaw)[value.ordinal]

    @JvmStatic
    fun translationFor(pitch: EnumFacing, yaw: EnumFacing): Array<EnumFacing> {
        synchronized(translationCache) {
            return translationCache
                .getOrPut(pitch) { mutableMapOf() }
                .getOrPut(yaw) { translations[pitch.ordinal][yaw.ordinal - 2] }
        }
    }

    @JvmStatic
    fun inverseTranslationFor(pitch: EnumFacing, yaw: EnumFacing): Array<EnumFacing> {
        synchronized(inverseTranslationCache) {
            return inverseTranslationCache
                .getOrPut(pitch) { mutableMapOf() }
                .getOrPut(yaw) {
                    val t = translationFor(pitch, yaw)
                    t.indices
                        .map { EnumFacing.byIndex(it) }
                        .map { t.indexOf(it) }
                        .map { EnumFacing.byIndex(it) }
                        .toTypedArray()
                }
        }
    }

    // ----------------------------------------------------------------------- //

    private val translationCache = mutableMapOf<EnumFacing, MutableMap<EnumFacing, Array<EnumFacing>>>()
    private val inverseTranslationCache = mutableMapOf<EnumFacing, MutableMap<EnumFacing, Array<EnumFacing>>>()

    /**
     * Translates forge directions based on the block's pitch and yaw. The base
     * forward direction is facing south with no pitch. The outer array is for
     * the three different pitch states, the inner for the four different yaw
     * states.
     */
    private val translations = arrayOf(
        // Pitch = Down
        arrayOf(
            // Yaw = North
            arrayOf(D.south, D.north, D.up, D.down, D.east, D.west),
            // Yaw = South
            arrayOf(D.south, D.north, D.down, D.up, D.west, D.east),
            // Yaw = West
            arrayOf(D.south, D.north, D.west, D.east, D.up, D.down),
            // Yaw = East
            arrayOf(D.south, D.north, D.east, D.west, D.down, D.up)
        ),
        // Pitch = Up
        arrayOf(
            // Yaw = North
            arrayOf(D.north, D.south, D.down, D.up, D.east, D.west),
            // Yaw = South
            arrayOf(D.north, D.south, D.up, D.down, D.west, D.east),
            // Yaw = West
            arrayOf(D.north, D.south, D.west, D.east, D.down, D.up),
            // Yaw = East
            arrayOf(D.north, D.south, D.east, D.west, D.up, D.down)
        ),
        // Pitch = Forward (North|East|South|West)
        arrayOf(
            // Yaw = North
            arrayOf(D.down, D.up, D.south, D.north, D.east, D.west),
            // Yaw = South
            arrayOf(D.down, D.up, D.north, D.south, D.west, D.east),
            // Yaw = West
            arrayOf(D.down, D.up, D.west, D.east, D.south, D.north),
            // Yaw = East
            arrayOf(D.down, D.up, D.east, D.west, D.north, D.south)
        )
    )

    /** Shortcuts for forge directions to make the above more readable. */
    private object D {
        val down: EnumFacing = EnumFacing.DOWN
        val up: EnumFacing = EnumFacing.UP
        val north: EnumFacing = EnumFacing.NORTH
        val south: EnumFacing = EnumFacing.SOUTH
        val west: EnumFacing = EnumFacing.WEST
        val east: EnumFacing = EnumFacing.EAST
    }
}
