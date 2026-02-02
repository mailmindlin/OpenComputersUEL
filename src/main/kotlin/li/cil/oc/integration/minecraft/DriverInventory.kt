package li.cil.oc.integration.minecraft

import li.cil.oc.Settings.Companion.get
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.BlockPosition.Companion.invoke
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock
import net.minecraftforge.fml.common.eventhandler.Event
import kotlin.math.max
import kotlin.math.min

class DriverInventory : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IInventory::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment? {
        val te = world.getTileEntity(pos) ?: return null
        if (te !is IInventory) return null
        return Environment(te, world)
    }

    class Environment<TE>(tileEntity: TE, world: World) : ManagedTileEntityEnvironment<IInventory>(tileEntity, "inventory")
    where TE: TileEntity, TE: IInventory
    {
        private val fakePlayer: EntityPlayer =
            FakePlayerFactory.get(world as WorldServer, get.fakePlayerProfile)
        private val position = invoke(tileEntity.pos, world)

        @Callback(doc = "function():string -- Get the name of this inventory.")
        fun getInventoryName(context: Context?, args: Arguments?): Array<Any?> {
            if (notPermitted()) return arrayOf(null, "permission denied")
            return arrayOf(tileEntity.name)
        }

        @Callback(doc = "function():number -- Get the number of slots in this inventory.")
        fun getInventorySize(context: Context?, args: Arguments?): Array<Any?> {
            if (notPermitted()) return arrayOf(null, "permission denied")
            return arrayOf(tileEntity.sizeInventory)
        }

        @Callback(doc = "function(slot:number):number -- Get the stack size of the item stack in the specified slot.")
        fun getSlotStackSize(context: Context?, args: Arguments): Array<Any?> {
            if (notPermitted()) return arrayOf(null, "permission denied")
            val slot = checkSlot(args, 0)
            val stack = tileEntity.getStackInSlot(slot)
            return if (!stack.isEmpty) {
                arrayOf(stack.count)
            } else {
                arrayOf(0)
            }
        }

        @Callback(doc = "function(slot:number):number -- Get the maximum stack size of the item stack in the specified slot.")
        fun getSlotMaxStackSize(context: Context?, args: Arguments): Array<Any?> {
            if (notPermitted()) return arrayOf(null, "permission denied")
            val slot = checkSlot(args, 0)
            val stack = tileEntity.getStackInSlot(slot)
            return if (!stack.isEmpty) {
                arrayOf(
                    min(
                        tileEntity.inventoryStackLimit.toDouble(),
                        stack.maxStackSize.toDouble()
                    )
                )
            } else {
                arrayOf(tileEntity.inventoryStackLimit)
            }
        }

        @Callback(doc = "function(slotA:number, slotB:number):boolean -- Compare the two item stacks in the specified slots for equality.")
        fun compareStacks(context: Context?, args: Arguments): Array<Any?> {
            if (notPermitted()) return arrayOf(null, "permission denied")
            val slotA = checkSlot(args, 0)
            val slotB = checkSlot(args, 1)
            if (slotA == slotB) {
                return arrayOf(true)
            }
            val stackA = tileEntity.getStackInSlot(slotA)
            val stackB = tileEntity.getStackInSlot(slotB)
            return if (stackA.isEmpty && stackB.isEmpty) {
                arrayOf(true)
            } else if (!stackA.isEmpty && !stackB.isEmpty) {
                arrayOf(itemEquals(stackA, stackB))
            } else {
                arrayOf(false)
            }
        }

        @Callback(doc = "function(slotA:number, slotB:number[, count:number=math.huge]):boolean -- Move up to the specified number of items from the first specified slot to the second.")
        fun transferStack(context: Context?, args: Arguments): Array<Any?> {
            if (notPermitted()) return arrayOf(null, "permission denied")
            val slotA = checkSlot(args, 0)
            val slotB = checkSlot(args, 1)
            val count = max(
                0.0,
                min(
                    (if (args.count() > 2 && args.checkAny(2) != null) args.checkInteger(2) else 64).toDouble(),
                    tileEntity.inventoryStackLimit.toDouble()
                )
            ).toInt()
            if (slotA == slotB || count == 0) {
                return arrayOf(true)
            }
            val stackA = tileEntity.getStackInSlot(slotA)
            val stackB = tileEntity.getStackInSlot(slotB)
            if (stackA.isEmpty) {
                // Empty.
                return arrayOf(false)
            } else if (stackB.isEmpty) {
                // Move.
                tileEntity.setInventorySlotContents(slotB, tileEntity.decrStackSize(slotA, count))
                return arrayOf(true)
            } else if (itemEquals(stackA, stackB)) {
                // Pile.
                val space = (min(
                    tileEntity.inventoryStackLimit.toDouble(),
                    stackB.maxStackSize.toDouble()
                ) - stackB.count).toInt()
                val amount =
                    min(count.toDouble(), min(space.toDouble(), stackA.count.toDouble())).toInt()
                if (amount > 0) {
                    // Some.
                    stackA.count = stackA.count - amount
                    stackB.count = stackB.count + amount
                    if (stackA.count == 0) {
                        tileEntity.setInventorySlotContents(slotA, ItemStack.EMPTY)
                    }
                    tileEntity.markDirty()
                    return arrayOf(true)
                }
            } else if (count >= stackA.count) {
                // Swap.
                tileEntity.setInventorySlotContents(slotB, stackA)
                tileEntity.setInventorySlotContents(slotA, stackB)
                return arrayOf(true)
            }
            // Fail.
            return arrayOf(false)
        }

        @Suppress("unused_parameter")
        @Callback(doc = "function(slot:number):table -- Get a description of the item stack in the specified slot.")
        fun getStackInSlot(context: Context?, args: Arguments): Array<Any?> {
            if (get.allowItemStackInspection) {
                if (notPermitted()) return arrayOf(null, "permission denied")
                return arrayOf(tileEntity.getStackInSlot(checkSlot(args, 0)))
            } else {
                return arrayOf(null, "not enabled in config")
            }
        }

        @Callback(doc = "function():table -- Get a list of descriptions for all item stacks in this inventory.")
        fun getAllStacks(context: Context?, args: Arguments?): Array<Any?> {
            if (get.allowItemStackInspection) {
                if (notPermitted()) return arrayOf(null, "permission denied")
                val allStacks = arrayOfNulls<ItemStack>(tileEntity.sizeInventory)
                for (i in 0 until tileEntity.sizeInventory) {
                    allStacks[i] = tileEntity.getStackInSlot(i)
                }
                return arrayOf(allStacks)
            } else {
                return arrayOf(null, "not enabled in config")
            }
        }

        private fun checkSlot(args: Arguments, number: Int): Int {
            val slot = args.checkInteger(number) - 1
            require(!(slot < 0 || slot >= tileEntity.sizeInventory)) { "slot index out of bounds" }
            return slot
        }

        private fun itemEquals(stackA: ItemStack, stackB: ItemStack): Boolean {
            return stackA.item == stackB.item && !stackA.hasSubtypes || stackA.itemDamage == stackB.itemDamage
        }

        private fun notPermitted(): Boolean {
            synchronized(fakePlayer) {
                fakePlayer.setPosition(position.toVec3().x, position.toVec3().y, position.toVec3().z)
                val event =
                    RightClickBlock(fakePlayer, EnumHand.MAIN_HAND, position.toBlockPos(), EnumFacing.DOWN, null)
                MinecraftForge.EVENT_BUS.post(event)
                return !event.isCanceled && event.useBlock != Event.Result.DENY && !tileEntity.isUsableByPlayer(
                    fakePlayer
                )
            }
        }
    }
}
