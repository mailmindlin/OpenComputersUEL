package li.cil.oc.integration.enderstorage

import codechicken.enderstorage.api.Frequency
import codechicken.enderstorage.tile.TileEnderTank
import codechicken.enderstorage.tile.TileFrequencyOwner
import codechicken.lib.colour.EnumColour
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World


internal class EnvironmentFrequencyOwner(tileEntity: TileFrequencyOwner) :
    ManagedTileEntityEnvironment<TileFrequencyOwner>(
        tileEntity,
        if (tileEntity is TileEnderTank) "ender_tank" else "ender_chest"
    ), NamedBlock {

    override fun preferredName(): String {
        return if (tileEntity is TileEnderTank) "ender_tank" else "ender_chest"
    }

    override fun priority(): Int = 0

    @Callback(doc = "function():table -- Get the currently set frequency. {left, middle, right}")
    fun getFrequency(context: Context?, args: Arguments?): Array<out Any> {
        val frequencies = arrayOfNulls<Any>(3)
        val frequency = tileEntity.frequency
        frequencies[0] = frequency.getLeft().ordinal
        frequencies[1] = frequency.getMiddle().ordinal
        frequencies[2] = frequency.getRight().ordinal
        return arrayOf(frequencies)
    }

    @Callback(doc = "function(left:number, middle:number, right:number) -- Set the frequency. Range 0-15 (inclusive).")
    fun setFrequency(context: Context?, args: Arguments): Array<Any>? {
        val left: Int
        val middle: Int
        val right: Int
        if (args.count() == 1) {
            val freq = args.checkInteger(0)
            require((freq and 0xFFF) == freq) { "invalid frequency" }
            left = (freq shr 8) and 0xF
            middle = (freq shr 4) and 0xF
            right = freq and 0xF
        } else {
            left = args.checkInteger(0)
            middle = args.checkInteger(1)
            right = args.checkInteger(2)
            require(!((left and 0xF) != left || (middle and 0xF) != middle || (right and 0xF) != right)) { "invalid frequency" }
        }
        tileEntity.setFreq(
            Frequency(
                EnumColour.fromWoolMeta(left),
                EnumColour.fromWoolMeta(middle),
                EnumColour.fromWoolMeta(right),
                tileEntity.frequency.owner
            )
        )
        return null
    }

    @Callback(doc = "function():string -- Get the name of the owner, which is usually a player's name or 'global'.")
    fun getOwner(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.frequency.owner)
    }

    @Callback(doc = "function():table -- Get the currently set frequency as a table of color names.")
    fun getFrequencyColors(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.frequency.toArray())
    }

    @Callback(doc = "function():table -- Get a table with the mapping of colors (as Minecraft names) to Frequency numbers. NB: Frequencies are zero based!")
    fun getColors(context: Context?, args: Arguments?): Array<Any> {
        val length = EnumColour.values().size
        val colors: MutableMap<Int, EnumColour> = HashMap()
        for (i in 0 until length) {
            colors[i] = EnumColour.values()[i]
        }
        return arrayOf(colors)
    }
}