package li.cil.oc.integration.top

import li.cil.oc.Settings
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.Screen
import li.cil.oc.common.tileentity.traits.NotAnalyzable
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.common.tileentity.traits.TileEntityTrait
import mcjty.theoneprobe.api.IProbeHitData
import mcjty.theoneprobe.api.IProbeInfo
import mcjty.theoneprobe.api.IProbeInfoProvider
import mcjty.theoneprobe.api.ProbeMode
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class TOPInfoProvider: IProbeInfoProvider {
    override fun getID(): String = "${Settings.resourceDomain}.default"

    override fun addProbeInfo(
        mode: ProbeMode,
        probeInfo: IProbeInfo,
        player: EntityPlayer,
        world: World,
        blockState: IBlockState?,
        data: IProbeHitData
    ) {
        val tile = world.getTileEntity(data.pos as BlockPos) ?: return
        if (tile !is TileEntityTrait) return // Not OC tile

        if (tile is Rotatable)
            probeInfo.text("Rotation: ${tile.pitch},${tile.yaw}")

        if (tile is Screen) {
            probeInfo.text("Screens: ${tile.screensReadonly.size} x ${tile.tier}")
            probeInfo.text("${tile.screensReadonly.joinToString { it.pos.toString() }}")
            probeInfo.text("Max: ${tile.buffer.maximumWidth}x${tile.buffer.maximumHeight}")
            probeInfo.text("Vp: ${tile.buffer.viewportWidth}x${tile.buffer.viewportHeight}")
            probeInfo.text("Res: ${tile.buffer.width}x${tile.buffer.height}")
            probeInfo.text("Aspect: ${tile.buffer.aspectRatio}")
            probeInfo.text("Keyboard: ${tile.hasKeyboard()}")
        }

        fun writeNode(node: Node?) {
            if (node == null || node.reachability() == Visibility.None) return
            if (node.address() != null && mode >= ProbeMode.EXTENDED)
                probeInfo.text("address: ${node.address()}")
//            if (node is Connector)
//                probeInfo.progress(node.localBuffer().toInt(), node.localBufferSize().toInt(), probeInfo.defaultProgressStyle().prefix("Buffer"))
            if (node is Component && mode == ProbeMode.DEBUG)
                probeInfo.text("Component Name: ${node.name()}")
        }

        val node = when (tile) {
            is NotAnalyzable -> null
            is li.cil.oc.api.network.SidedEnvironment -> tile.sidedNode(data.sideHit)
            is li.cil.oc.api.network.Environment -> tile.node()
            else -> null
        }
        writeNode(node)
    }
}