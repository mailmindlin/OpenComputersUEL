package li.cil.oc.common.tileentity.traits.delegates

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.traits.Colored
import li.cil.oc.common.tileentity.traits.TileEntityTrait
import li.cil.oc.server.PacketSender
import li.cil.oc.util.Color
import net.minecraft.item.EnumDyeColor
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Delegate class that holds color state for tile entities.
 * Used by the Colored interface to avoid state in interfaces.
 */
class ColoredDelegate(
    private val tileEntity: TileEntityTrait,
    defaultColor: UInt = Color.rgbValues(EnumDyeColor.SILVER),
    private val onChanged: () -> Unit = {}
) {
    var color: Int = defaultColor
        private set

    /**
     * Sets the color value. Use this method from interface default implementations
     * since Kotlin interfaces can't use property setters directly.
     */
    fun setColor(value: Int) {
        if (color != value) {
            color = value
            onColorChanged()
        }
    }

    private fun onColorChanged() {
        val world = tileEntity.world
        if (world != null && tileEntity.isServer) {
            PacketSender.sendColorChange(tileEntity.asTileEntity())
        }
        onChanged()
    }

    fun readFromNBTForServer(nbt: NBTTagCompound) {
        if (nbt.hasKey(RenderColorTagCompat)) {
            color = Color.rgbValues(EnumDyeColor.byMetadata(nbt.getInteger(RenderColorTagCompat)))
        }
        if (nbt.hasKey(RenderColorTag)) {
            color = nbt.getInteger(RenderColorTag)
        }
    }

    fun writeToNBTForServer(nbt: NBTTagCompound) {
        nbt.setInteger(RenderColorTag, color)
    }

    @SideOnly(Side.CLIENT)
    fun readFromNBTForClient(nbt: NBTTagCompound) {
        color = nbt.getInteger(RenderColorTag)
    }

    fun writeToNBTForClient(nbt: NBTTagCompound) {
        nbt.setInteger(RenderColorTag, color)
    }

    companion object {
        private val RenderColorTag = Settings.namespace + "renderColorRGB"
        private val RenderColorTagCompat = Settings.namespace + "renderColor"
    }
}
