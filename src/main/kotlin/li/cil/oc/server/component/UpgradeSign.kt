package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.event.SignChangeEvent
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.internal.Robot as InternalRobot
import li.cil.oc.api.internal.Tablet as InternalTablet
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.getTileEntity
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

abstract class UpgradeSign : ManagedEnvironmentKt(), DeviceInfo {
    private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Sign upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Labelizer Deluxe"
    )

    override fun getDeviceInfo() = deviceInfo

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
                is InternalRobot -> (host as InternalRobot).player()
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
        val message = TabletUseMessage.tryParse(message) ?: return


        val te = host.world().getTileEntity(message.blockPos) as? TileEntitySign ?: return
        message.nbt
            .setString("signText", te.signText.joinToString("\n") { it.unformattedText })
    }

    class UpgradeSignInAdapter(override val host: EnvironmentHost) : UpgradeSign() {
        override val node = nodeFactory(Visibility.Network)
            .withComponent("sign", Visibility.Network)
            .withConnector()
            .create()

        // ----------------------------------------------------------------------- //

        @Suppress("unused", "unused_parameter")
        @Callback(doc = "function(side:number):string -- Get the text on the sign on the specified side of the adapter.")
        fun getValue(context: Context, args: Arguments): Array<Any?> =
            super.getValue(findSign(args.checkSideAny(0)))

        @Suppress("unused", "unused_parameter")
        @Callback(doc = "function(side:number, value:string):string -- Set the text on the sign on the specified side of the adapter.")
        fun setValue(context: Context, args: Arguments): Array<Any?> =
            super.setValue(findSign(args.checkSideAny(0)), args.checkString(1))
    }

    class UpgradeSignInRotatable(private val rotatableHost: EnvironmentHost) : UpgradeSign() {
        override val host: EnvironmentHost
            get() = rotatableHost

        private val rotatable: Rotatable
            get() = rotatableHost as Rotatable

        override val node = nodeFactory(Visibility.Network)
            .withComponent("sign", Visibility.Neighbors)
            .withConnector()
            .create()

        // ----------------------------------------------------------------------- //

        @Suppress("unused", "unused_parameter")
        @Callback(doc = "function():string -- Get the text on the sign in front of the host.")
        fun getValue(context: Context, args: Arguments): Array<Any?> =
            super.getValue(findSign(rotatable.facing()))

        @Suppress("unused", "unused_parameter")
        @Callback(doc = "function(value:string):string -- Set the text on the sign in front of the host.")
        fun setValue(context: Context, args: Arguments): Array<Any?> =
            super.setValue(findSign(rotatable.facing()), args.checkString(0))
    }
}
