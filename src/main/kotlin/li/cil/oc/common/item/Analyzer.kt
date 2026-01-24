package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.Items
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.tileentity.Screen as TEScreen
import li.cil.oc.server.PacketSender
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.getTileEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class Analyzer(override val parent: Delegator) : Delegate {
    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (player.isSneaking && stack.hasTagCompound()) {
            stack.tagCompound!!.removeTag(Settings.namespace + "clipboard")
            if (stack.tagCompound!!.isEmpty) {
                stack.tagCompound = null
            }
        }
        return super.onItemRightClick(stack, world, player)
    }

    override var showInItemList: Boolean = false
    override val itemId: Int = 0

    override fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val world = player.entityWorld
        val tileEntity = world.getTileEntity(position)
        if (tileEntity is TEScreen && side == tileEntity.facing()) {
            return if (player.isSneaking) {
                tileEntity.copyToAnalyzer(player, hitX, hitY, hitZ)
            } else if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "clipboard")) {
                if (!world.isRemote) {
                    tileEntity.origin.buffer.clipboard(stack.tagCompound!!.getString(Settings.namespace + "clipboard"), player)
                }
                true
            } else {
                false
            }
        }
        return Analyzer.analyze(position.world?.getTileEntity(position), player, side, hitX, hitY, hitZ)
    }

    companion object {
        private val analyzer by lazy { Items.get(Constants.ItemName.Analyzer) }

        @JvmStatic
        @SubscribeEvent
        fun onInteract(e: PlayerInteractEvent.EntityInteract) {
            val player = e.entityPlayer
            val held = player.getHeldItem(e.hand)
            if (Items.get(held) == analyzer) {
                if (analyze(e.target, player, EnumFacing.DOWN, 0f, 0f, 0f)) {
                    player.swingArm(e.hand)
                    e.isCanceled = true
                }
            }
        }

        @JvmStatic
        fun analyze(thing: Any?, player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
            val world = player.world
            return when (thing) {
                is Analyzable -> {
                    if (!world.isRemote) {
                        analyzeNodes(thing.onAnalyze(player, side, hitX, hitY, hitZ), player)
                    }
                    true
                }
                is SidedEnvironment -> {
                    if (!world.isRemote) {
                        analyzeNodes(arrayOf(thing.sidedNode(side)), player)
                    }
                    true
                }
                is Environment -> {
                    if (!world.isRemote) {
                        analyzeNodes(arrayOf(thing.node()), player)
                    }
                    true
                }
                else -> false
            }
        }

        private fun analyzeNodes(nodes: Array<Node>?, player: EntityPlayer) {
            if (nodes == null) return
            for (node in nodes) {
                if (node == null) continue
                if (player is FakePlayer) continue
                if (player is EntityPlayerMP) {
                    val host = node.host()
                    if (host is Machine) {
                        if (host.lastError() != null) {
                            player.sendMessage(Localization.Analyzer.LastError(host.lastError()))
                        }
                        player.sendMessage(Localization.Analyzer.Components(host.componentCount(), host.maxComponents()))
                        val list = host.users()
                        if (list.isNotEmpty()) {
                            player.sendMessage(Localization.Analyzer.Users(list.asIterable()))
                        }
                    }
                    if (node is Connector) {
                        if (node.localBufferSize() > 0) {
                            player.sendMessage(Localization.Analyzer.StoredEnergy("%.2f/%.2f".format(node.localBuffer(), node.localBufferSize())))
                        }
                        player.sendMessage(Localization.Analyzer.TotalEnergy("%.2f/%.2f".format(node.globalBuffer(), node.globalBufferSize())))
                    }
                    if (node is Component) {
                        player.sendMessage(Localization.Analyzer.ComponentName(node.name()))
                    }
                    val address = node.address()
                    if (address != null && address.isNotEmpty()) {
                        player.sendMessage(Localization.Analyzer.Address(address))
                        PacketSender.sendAnalyze(address, player)
                    }
                }
            }
        }
    }

}
