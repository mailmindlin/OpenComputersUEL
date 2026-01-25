package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.api.internal.Colored as InternalColored
import li.cil.oc.server.PacketSender
import li.cil.oc.util.Color
import net.minecraft.item.EnumDyeColor
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Interface for tile entities that have a color.
 * Implementations must provide a {@link Colored.Delegate} instance.
 */
interface Colored : TileEntityTrait, InternalColored {
    val colorDelegate: Delegate

    override fun getColor(): Int = this.color.toInt()
    override fun setColor(value: Int) {
        this.colorDelegate.color = value.toUInt()
    }
    var color: UInt
        get() = colorDelegate.color
        set(value) { colorDelegate.color = value }

    override fun controlsConnectivity(): Boolean = false

    val consumesDye: Boolean get() = false


    fun onColorChanged() {}

    /**
     * Delegate class that holds color state for tile entities.
     * Used by the Colored interface to avoid state in interfaces.
     */
    class Delegate(
        private val tileEntity: Colored,
        defaultColor: UInt = Color.rgbValues(EnumDyeColor.SILVER),
    ): NbtSeriailzable {
        var color: UInt = defaultColor
            set(value) {
                if (field == value) return
                field = value
                onColorChanged()
            }

        private fun onColorChanged() {
            val world = tileEntity.world
            if (world != null && tileEntity.isServer) {
                PacketSender.sendColorChange(tileEntity)
            }
            tileEntity.onColorChanged()
        }

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            if (nbt.hasKey(RenderColorTagCompat)) {
                color = Color.rgbValues(EnumDyeColor.byMetadata(nbt.getInteger(RenderColorTagCompat)))
            }
            if (nbt.hasKey(RenderColorTag)) {
                color = nbt.getInteger(RenderColorTag).toUInt()
            }
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            nbt.setInteger(RenderColorTag, color.toInt())
        }

        @SideOnly(Side.CLIENT)
        override fun readFromNBTForClient(nbt: NBTTagCompound) {
            color = nbt.getInteger(RenderColorTag).toUInt()
        }

        override fun writeToNBTForClient(nbt: NBTTagCompound) {
            nbt.setInteger(RenderColorTag, color.toInt())
        }

        companion object {
            private const val RenderColorTag = Settings.namespace + "renderColorRGB"
            private const val RenderColorTagCompat = Settings.namespace + "renderColor"
        }
    }

}
