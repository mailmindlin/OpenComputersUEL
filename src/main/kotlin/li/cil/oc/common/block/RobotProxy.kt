package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Items
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.traits.StateAware
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity.Robot as TERobot
import li.cil.oc.common.tileentity.RobotProxy as TERobotProxy
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.server.PacketSender
import li.cil.oc.server.agent.Player
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import java.util.ArrayList

class RobotProxy : RedstoneAware(), StateAware {
    init {
        setLightOpacity(0)
        setCreativeTab(null)
        ItemBlacklist.hide(this)
    }

    override fun getTranslationKey(): String = "robot"

    var moving: ThreadLocal<TERobot?> = object : ThreadLocal<TERobot?>() {
        override fun initialValue(): TERobot? = null
    }

    // ----------------------------------------------------------------------- //

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TERobotProxy) tileEntity.robot.info.copyItemStack() else ItemStack.EMPTY
    }

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TERobotProxy) {
            val robot = tileEntity.robot
            val bounds = AxisAlignedBB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)
            if (robot.isAnimatingMove) {
                val remaining = robot.animationTicksLeft.toDouble() / robot.animationTicksTotal.toDouble()
                val blockPos = robot.moveFrom!!
                val vec = robot.pos
                val delta = BlockPos(blockPos.x - vec.x, blockPos.y - vec.y, blockPos.z - vec.z)
                bounds.offset(delta.x * remaining, delta.y * remaining, delta.z * remaining)
            } else bounds
        } else super.getBoundingBox(state, world, pos)
    }

    // ----------------------------------------------------------------------- //

    override fun rarity(stack: ItemStack): EnumRarity {
        val data = RobotData(stack)
        return Rarity.byTier(data.tier)
    }

    override fun tooltipHead(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        super.tooltipHead(metadata, stack, world, tooltip, advanced)
        addLines(stack, tooltip)
    }

    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        tooltip.addAll(Tooltip.get("robot"))
    }

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, flag)
        if (KeyBindings.showExtendedTooltips) {
            val info = RobotData(stack)
            val components = info.containers + info.components
            if (components.isNotEmpty()) {
                tooltip.addAll(Tooltip.get("server.Components"))
                for (component in components) {
                    if (!component.isEmpty) {
                        tooltip.add("- " + component.displayName)
                    }
                }
            }
        }
    }

    private fun addLines(stack: ItemStack, tooltip: MutableList<String>) {
        if (stack.hasTagCompound()) {
            if (stack.tagCompound!!.hasKey(Settings.namespace + "xp")) {
                val xp = stack.tagCompound!!.getDouble(Settings.namespace + "xp")
                val level = minOf((Math.pow(xp - Settings.get.baseXpToLevel, 1.0 / Settings.get.exponentialXpGrowth) / Settings.get.constantXpGrowth).toInt(), 30)
                if (level > 0) {
                    tooltip.addAll(Tooltip.get(translationKey + "_level", level))
                }
            }
            if (stack.tagCompound!!.hasKey(Settings.namespace + "storedEnergy")) {
                val energy = stack.tagCompound!!.getInteger(Settings.namespace + "storedEnergy")
                if (energy > 0) {
                    tooltip.addAll(Tooltip.get(translationKey + "_storedenergy", energy))
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int): TERobotProxy {
        val robot = moving.get()
        return if (robot != null) TERobotProxy(robot) else TERobotProxy()
    }

    // ----------------------------------------------------------------------- //

    override fun getExplosionResistance(entity: Entity): Float = 10f

    override fun getDrops(world: IBlockAccess, pos: BlockPos, state: IBlockState, fortune: Int): ArrayList<ItemStack> {
        val list = ArrayList<ItemStack>()

        // Superspecial hack... usually this will not work, because Minecraft calls
        // this method *after* the block has already been destroyed. Meaning we
        // won't have access to the tile entity.
        // However! Some mods with block breakers, specifically AE2's annihilation
        // plane, will call *only* this method (don't use a fake player to call
        // removedByPlayer), but call it *before* the block was destroyed. So in
        // general it *should* be safe to generate the item here if the tile entity
        // still exists, and always spawn the stack in removedByPlayer... if some
        // mod calls this before the block is broken *and* calls removedByPlayer
        // this will lead to dupes, but in some initial testing this wasn't the
        // case anywhere (TE autonomous activator, CC turtles).
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TERobotProxy) {
            val robot = tileEntity.robot
            val node = robot.node()
            if (node != null) {
                // Update: even more special hack! As discussed here http://git.io/IcNAyg
                // some mods call this even when they're not about to actually break the
                // block... soooo we need a whitelist to know when to generate a *proper*
                // drop (i.e. with file systems closed / open handles not saved, e.g.).
                if (gettingDropsForActualDrop) {
                    node.remove()
                    robot.saveComponents()
                }
                list.add(robot.info.createItemStack())
            }
        }

        return list
    }

    private val getDropForRealDropCallers = setOf(
        "appeng.parts.automation.PartAnnihilationPlane.EatBlock"
    )

    private val gettingDropsForActualDrop: Boolean
        get() = Exception().stackTrace.any { element -> getDropForRealDropCallers.contains(element.className + "." + element.methodName) }

    override fun collisionRayTrace(state: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult? {
        val bounds = getCollisionBoundingBox(state, world, pos)
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TERobotProxy && tileEntity.robot.animationTicksLeft <= 0 && bounds != null && bounds.contains(start)) {
            null
        } else super.collisionRayTrace(state, world, pos, start, end)
    }

    // ----------------------------------------------------------------------- //

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (!player.isSneaking) {
            if (!world.isRemote) {
                // We only send slot changes to nearby players, so if there was no slot
                // change since this player got into range he might have the wrong one,
                // so we send him the current one just in case.
                val tileEntity = world.getTileEntity(pos)
                if (tileEntity is TERobotProxy && tileEntity.robot.node()!!.network() != null) {
                    PacketSender.sendRobotSelectedSlotChange(tileEntity.robot)
                    player.openGui(OpenComputers, GuiType.Robot.id, world, pos.x, pos.y, pos.z)
                }
            }
            return true
        } else if (heldItem.isEmpty) {
            if (!world.isRemote) {
                val tileEntity = world.getTileEntity(pos)
                if (tileEntity is TERobotProxy && !tileEntity.machine().isRunning && tileEntity.isUsableByPlayer(player)) {
                    tileEntity.machine().start()
                }
            }
            return true
        }
        return false
    }

    override fun onBlockPlacedBy(world: World, pos: BlockPos, state: IBlockState, entity: EntityLivingBase, stack: ItemStack) {
        super.onBlockPlacedBy(world, pos, state, entity, stack)
        if (!world.isRemote) {
            val tileEntity = world.getTileEntity(pos)
            val info = when {
                entity is Player && tileEntity is TERobotProxy -> Triple(tileEntity.robot, entity.agent.ownerName(), entity.agent.ownerUUID())
                entity is EntityPlayer && tileEntity is TERobotProxy -> Triple(tileEntity.robot, entity.name, entity.gameProfile.id)
                else -> null
            }
            info?.let { (robot, owner, uuid) ->
                robot.ownerName = owner
                robot.ownerUUID = Player.determineUUID(uuid)
                robot.info.load(stack)
                robot.bot!!.node.changeBuffer(robot.info.robotEnergy - robot.bot.node.localBuffer())
                robot.updateInventorySize()
            }
        }
    }

    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, player: EntityPlayer, willHarvest: Boolean): Boolean {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TERobotProxy) {
            val robot = tileEntity.robot
            // Only allow breaking creative tier robots by allowed users.
            // Unlike normal robots, griefing isn't really a valid concern
            // here, because to get a creative robot you need creative
            // mode in the first place.
            if (robot.isCreative && (!player.capabilities.isCreativeMode || !robot.canInteract(player.name))) return false
            if (!world.isRemote) {
                if (robot.player() == player) return false
                robot.node()?.remove()
                robot.saveComponents()
                InventoryUtils.spawnStackInWorld(BlockPosition(pos, world), robot.info.createItemStack())
            }
            robot.moveFrom?.let { fromPos ->
                if (world.getBlockState(fromPos).block == Constants.BlockInfo.RobotAfterimage.block()) {
                    world.setBlockState(fromPos, Blocks.AIR.defaultState, 1)
                }
            }
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest)
    }

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        if (moving.get() == null) {
            super.breakBlock(world, pos, state)
        }
    }
}
