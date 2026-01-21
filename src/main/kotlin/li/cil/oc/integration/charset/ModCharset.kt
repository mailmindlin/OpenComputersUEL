package li.cil.oc.integration.charset

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.BundledRedstone.RedstoneProvider
import li.cil.oc.util.BlockPosition
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import pl.asie.charset.api.wires.IBundledEmitter
import pl.asie.charset.api.wires.IBundledReceiver
import pl.asie.charset.api.wires.IRedstoneEmitter

object ModCharset : ModProxy(), RedstoneProvider {
    class BundledRedstoneView(
        val data: Array<Int>,
        val onChange: () -> Unit
    ) : IBundledEmitter, IBundledReceiver {
        override fun getBundledSignal(): ByteArray = data.map { it.toByte() }.toByteArray()

        override fun onBundledInputChange() {
            onChange()
        }
    }

    override fun getMod() = Mods.Charset

    override fun initialize() {
        BundledRedstone.addProvider(this)
    }

    override fun computeInput(pos: BlockPosition, side: EnumFacing): Int {
        val world = pos.world.get() ?: return 0
        val npos = pos.toBlockPos().offset(side)
        val tile = world.getTileEntity(npos) as? TileEntity ?: return 0

        if (tile.hasCapability(CapabilitiesCharset.REDSTONE_EMITTER, side.opposite)) {
            val emitter = tile.getCapability(CapabilitiesCharset.REDSTONE_EMITTER, side.opposite)
            if (emitter is IRedstoneEmitter) {
                return kotlin.math.min(emitter.redstoneSignal, 15)
            }
        }
        return 0
    }

    fun computeBundledInput(pos: BlockPosition, side: EnumFacing): Array<Int>? {
        val world = pos.world.get() ?: return null
        val npos = pos.toBlockPos().offset(side)
        val tile = world.getTileEntity(npos) as? TileEntity ?: return null

        if (tile.hasCapability(CapabilitiesCharset.BUNDLED_EMITTER, side.opposite)) {
            val emitter = tile.getCapability(CapabilitiesCharset.BUNDLED_EMITTER, side.opposite)
            if (emitter is IBundledEmitter) {
                return emitter.bundledSignal.map { it.toInt() and 0xFF }.toTypedArray()
            }
        }
        return null
    }
}
