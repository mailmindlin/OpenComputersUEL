package li.cil.oc.server.component.traits

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.util.*
import li.cil.oc.util.ExtendedBlock.getBlock
import li.cil.oc.util.ExtendedBlock.getBlockMetadata
import li.cil.oc.util.ExtendedBlock.isAir
import li.cil.oc.util.ExtendedBlock.isReplaceable
import li.cil.oc.util.ExtendedBlock.getCollisionBoundingBoxFromPool
import li.cil.oc.util.ExtendedWorld.bounds
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityMinecart
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.world.WorldServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraft.util.EnumHand

interface WorldAware {
    val position: BlockPosition

    val world get() = position.world.get()

    val fakePlayer: EntityPlayer
        get() {
            val player = FakePlayerFactory.get(world as WorldServer, Settings.get.fakePlayerProfile)
            player.posX = position.x + 0.5
            player.posY = position.y + 0.5
            player.posZ = position.z + 0.5
            return player
        }

    fun mayInteract(blockPos: BlockPosition, face: EnumFacing): Boolean {
        return try {
            val event = PlayerInteractEvent.RightClickBlock(fakePlayer, EnumHand.MAIN_HAND, blockPos.toBlockPos(), face, null)
            MinecraftForge.EVENT_BUS.post(event)
            !event.isCanceled && event.useBlock != Event.Result.DENY
        } catch (t: Throwable) {
            OpenComputers.log.warn("Some event handler threw up while checking for permission to access a block.", t)
            true
        }
    }

    fun mayInteract(entity: Entity): Boolean {
        return try {
            val event = PlayerInteractEvent.EntityInteract(fakePlayer, EnumHand.MAIN_HAND, entity)
            MinecraftForge.EVENT_BUS.post(event)
            !event.isCanceled
        } catch (t: Throwable) {
            OpenComputers.log.warn("Some event handler threw up while checking for permission to access an entity.", t)
            true
        }
    }

    fun mayInteract(inv: InventorySource): Boolean {
        val inventoryUsable = when (val inventory = inv.inventory) {
            is InvWrapper -> inventory.inv?.isUsableByPlayer(fakePlayer) ?: true
            else -> true
        }

        val sourceAccessible = when (inv) {
            is BlockInventorySource -> mayInteract(inv.position, inv.side)
            is EntityInventorySource -> mayInteract(inv.entity)
            else -> true
        }

        return inventoryUsable && sourceAccessible
    }

    fun <T : Entity> entitiesInBounds(clazz: Class<T>, bounds: AxisAlignedBB): List<T> {
        return world.getEntitiesWithinAABB(clazz, bounds)
    }

    fun <T : Entity> entitiesInBlock(clazz: Class<T>, blockPos: BlockPosition): List<T> {
        return entitiesInBounds(clazz, blockPos.bounds())
    }

    fun <T : Entity> entitiesOnSide(clazz: Class<T>, side: EnumFacing): List<T> {
        return entitiesInBlock(clazz, position.offset(side))
    }

    fun <T : Entity> closestEntity(clazz: Class<T>, side: EnumFacing): T? {
        val blockPos = position.offset(side)
        return world.findNearestEntityWithinAABB(clazz, blockPos.bounds(), fakePlayer)
    }

    fun blockContent(side: EnumFacing): Pair<Boolean, String> {
        closestEntity(Entity::class.java, side)?.let { entity ->
            if (entity is EntityLivingBase || entity is EntityMinecart) {
                return Pair(true, "entity")
            }
        }

        val blockPos = position.offset(side)
        val block = world.getBlock(blockPos)
        val metadata = world.getBlockMetadata(blockPos)

        return when {
            block.isAir(blockPos) -> {
                Pair(false, "air")
            }
            FluidRegistry.lookupFluidForBlock(block) != null -> {
                val event = BlockEvent.BreakEvent(world, blockPos.toBlockPos(), metadata, fakePlayer)
                MinecraftForge.EVENT_BUS.post(event)
                Pair(event.isCanceled, "liquid")
            }
            block.isReplaceable(blockPos) -> {
                val event = BlockEvent.BreakEvent(world, blockPos.toBlockPos(), metadata, fakePlayer)
                MinecraftForge.EVENT_BUS.post(event)
                Pair(event.isCanceled, "replaceable")
            }
            block.getCollisionBoundingBoxFromPool(blockPos) == null -> Pair(true, "passable")
            else -> Pair(true, "solid")
        }
    }
}
