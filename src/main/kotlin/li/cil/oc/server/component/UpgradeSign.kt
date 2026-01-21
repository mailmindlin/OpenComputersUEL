package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.event.SignChangeEvent
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.BlockPosition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntitySign
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.WorldServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.eventhandler.Event

abstract class UpgradeSign : AbstractManagedEnvironment(), DeviceInfoKt {
    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Sign upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Labelizer Deluxe"
    )

    abstract val host: EnvironmentHost

    protected fun getValue(tileEntity: TileEntitySign?): Array<Any?> {
        return if (tileEntity != null) {
            result(tileEntity.signText.joinToString("\n") { it.unformattedText })
        } else {
            result(Unit, "no sign")
        }
    }

    protected fun setValue(tileEntity: TileEntitySign?, text: String): Array<Any?> {
        return if (tileEntity != null) {
            val player = when (host) {
                is internal.Robot -> (host as internal.Robot).player()
                else -> FakePlayerFactory.get(host.world() as WorldServer, Settings.get.fakePlayerProfile)
            }

            val lines = text.lines()
                .let { if (it.size < 4) it + List(4 - it.size) { "" } else it }
                .take(4)
                .map { line -> if (line.length > 15) line.substring(0, 15) else line }
                .toTypedArray()

            if (!canChangeSign(player, tileEntity, lines)) {
                return result(Unit, "not allowed")
            }

            lines.map { line -> TextComponentString(line) }.forEachIndexed { index, component ->
                tileEntity.signText[index] = component
            }
            host.world().notifyBlockUpdate(tileEntity.pos, tileEntity.world.getBlockState(tileEntity.pos), tileEntity.world.getBlockState(tileEntity.pos), 3)

            MinecraftForge.EVENT_BUS.post(SignChangeEvent.Post(tileEntity, lines))

            result(tileEntity.signText.joinToString("\n") { it.unformattedText })
        } else {
            result(Unit, "no sign")
        }
    }

    protected fun findSign(side: EnumFacing): TileEntitySign? {
        val hostPos = BlockPosition(host)
        return when (val te = host.world.getTileEntity(hostPos)) {
            is TileEntitySign -> te
            else -> when (val te2 = host.world.getTileEntity(hostPos.offset(side))) {
                is TileEntitySign -> te2
                else -> null
            }
        }
    }

    private fun canChangeSign(player: EntityPlayer, tileEntity: TileEntitySign, lines: Array<String>): Boolean {
        if (!host.world().isBlockModifiable(player, tileEntity.pos)) {
            return false
        }
        val event = BlockEvent.BreakEvent(host.world(), tileEntity.pos, tileEntity.world.getBlockState(tileEntity.pos), player)
        MinecraftForge.EVENT_BUS.post(event)
        if (event.isCanceled || event.result == Event.Result.DENY) {
            return false
        }

        val signEvent = SignChangeEvent.Pre(tileEntity, lines)
        MinecraftForge.EVENT_BUS.post(signEvent)
        return !(signEvent.isCanceled || signEvent.result == Event.Result.DENY)
    }

    override fun onMessage(message: Message) {
        super.onMessage(message)
        if (message.name() == "tablet.use") {
            val sourceHost = message.source().host()
            if (sourceHost is Machine) {
                val machineHost = sourceHost.host()
                val data = message.data
                if (machineHost is internal.Tablet && data.size >= 8 &&
                    data[0] is NBTTagCompound && data[1] is ItemStack && data[2] is EntityPlayer &&
                    data[3] is BlockPosition && data[4] is EnumFacing &&
                    data[5] is Float && data[6] is Float && data[7] is Float
                ) {
                    val nbt = data[0] as NBTTagCompound
                    val blockPos = data[3] as BlockPosition
                    when (val te = host.world().getTileEntity(blockPos)) {
                        is TileEntitySign -> {
                            nbt.setString("signText", te.signText.joinToString("\n") { it.unformattedText })
                        }
                    }
                }
            }
        }
    }
}
