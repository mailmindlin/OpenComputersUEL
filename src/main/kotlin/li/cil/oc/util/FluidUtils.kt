package li.cil.oc.util

import net.minecraft.block.Block
import net.minecraft.block.BlockDynamicLiquid
import net.minecraft.block.BlockLiquid
import net.minecraft.block.BlockStaticLiquid
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.IFluidBlock
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.FluidTankProperties
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandlerItem
import net.minecraftforge.fluids.capability.IFluidTankProperties
import kotlin.math.min

object FluidUtils {
    /**
     * Retrieves an actual fluid handler implementation for a specified world coordinate.
     *
     * This performs special handling for in-world liquids.
     */
    @JvmStatic
    fun fluidHandlerAt(position: BlockPosition, side: EnumFacing): IFluidHandler? {
        val world = position.world ?: return null
        if (!world.blockExists(position)) return null

        val tileEntity = world.getTileEntity(position)
        return when {
            tileEntity is IFluidHandler -> tileEntity
            tileEntity is TileEntity && tileEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side) -> {
                val handler = tileEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side)
                if (handler is IFluidHandler) handler else GenericBlockWrapper(position)
            }
            else -> GenericBlockWrapper(position)
        }
    }

    @JvmStatic
    fun fluidHandlerOf(stack: ItemStack?): IFluidHandlerItem? {
        if (stack == null) return null
        return if (stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)
        } else null
    }

    /**
     * Transfers some fluid between two fluid handlers.
     *
     * This will try to extract up the specified amount of fluid from any handler,
     * then insert it into the specified sink handler. If the insertion fails, the
     * fluid will remain in the source handler.
     *
     * This returns the amount of fluid transferred.
     */
    @JvmStatic
    @JvmOverloads
    fun transferBetweenFluidHandlers(source: IFluidHandler, sink: IFluidHandler, limit: Int = Fluid.BUCKET_VOLUME, sourceTank: Int = -1): Int {
        var stackToDrain: FluidStack? = null
        if (sourceTank >= 0) {
            val tankProperties = source.tankProperties
            if (tankProperties != null && tankProperties.size > sourceTank) {
                stackToDrain = tankProperties[sourceTank].contents
                if (stackToDrain != null) {
                    stackToDrain = stackToDrain.copy()
                    stackToDrain.amount = min(stackToDrain.amount, limit)
                }
            }
        }

        val drained = if (stackToDrain != null) {
            source.drain(stackToDrain, false)
        } else {
            source.drain(limit, false)
        }

        if (drained == null) {
            return 0
        }

        val filledAmount = sink.fill(drained, false)
        return if (stackToDrain != null) {
            val filledStack = drained.copy()
            filledStack.amount = filledAmount
            sink.fill(source.drain(filledStack, true), true)
        } else {
            sink.fill(source.drain(filledAmount, true), true)
        }
    }

    /**
     * Utility method for calling [transferBetweenFluidHandlers] on handlers
     * in the world.
     *
     * This uses the [fluidHandlerAt] method, and therefore handles special
     * cases such as fluid blocks.
     */
    @JvmStatic
    @JvmOverloads
    fun transferBetweenFluidHandlersAt(
        sourcePos: BlockPosition,
        sourceSide: EnumFacing,
        sinkPos: BlockPosition,
        sinkSide: EnumFacing,
        limit: Int = Fluid.BUCKET_VOLUME,
        sourceTank: Int = -1
    ): Int {
        val source = fluidHandlerAt(sourcePos, sourceSide) ?: return 0
        val sink = fluidHandlerAt(sinkPos, sinkSide) ?: return 0
        return transferBetweenFluidHandlers(source, sink, limit, sourceTank)
    }

    /**
     * Lookup fluid taking into account flowing liquid blocks...
     */
    @JvmStatic
    fun lookupFluidForBlock(block: Block): Fluid? {
        return when (block) {
            Blocks.FLOWING_LAVA -> FluidRegistry.LAVA
            Blocks.FLOWING_WATER -> FluidRegistry.WATER
            else -> FluidRegistry.lookupFluidForBlock(block)
        }
    }

    // ----------------------------------------------------------------------- //

    private class GenericBlockWrapper(private val position: BlockPosition) : IFluidHandler {
        fun canDrain(fluid: Fluid): Boolean =
            currentWrapper?.let { it.drain(FluidStack(fluid, 1), false)?.amount ?: 0 > 0 } ?: false

        override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? =
            currentWrapper?.drain(resource, doDrain)

        override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? =
            currentWrapper?.drain(maxDrain, doDrain)

        fun canFill(fluid: Fluid): Boolean =
            currentWrapper?.let { it.fill(FluidStack(fluid, 1), false) > 0 } ?: false

        override fun fill(resource: FluidStack?, doFill: Boolean): Int =
            currentWrapper?.fill(resource, doFill) ?: 0

        override fun getTankProperties(): Array<IFluidTankProperties> =
            currentWrapper?.tankProperties ?: emptyArray()

        private val currentWrapper: IFluidHandler?
            get() {
                val world = position.world ?: return null
                if (!world.blockExists(position)) return null
                val block = world.getBlock(position)
                return when {
                    block is IFluidBlock -> FluidBlockWrapper(position, block)
                    block is BlockStaticLiquid && lookupFluidForBlock(block) != null && isFullLiquidBlock -> LiquidBlockWrapper(position, block)
                    block is BlockDynamicLiquid && lookupFluidForBlock(block) != null && isFullLiquidBlock -> LiquidBlockWrapper(position, block)
                    block.isAir(position) || block.isReplaceable(position) -> AirBlockWrapper(position, block)
                    else -> null
                }
            }

        private val isFullLiquidBlock: Boolean
            get() {
                val state = position.world!!.getBlockState(position.toBlockPos())
                return state.getValue(BlockLiquid.LEVEL) == 0
            }
    }

    private interface BlockWrapperBase : IFluidHandler {
        fun uncheckedDrain(doDrain: Boolean): FluidStack?

        override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? {
            val drained = uncheckedDrain(false)
            return if (drained != null && (resource == null || (drained.fluid == resource.fluid && drained.amount <= resource.amount))) {
                uncheckedDrain(doDrain)
            } else null
        }

        override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? {
            val drained = uncheckedDrain(false)
            return if (drained != null && drained.amount <= maxDrain) {
                uncheckedDrain(doDrain)
            } else null
        }

        fun canFill(fluid: Fluid): Boolean = false

        override fun fill(resource: FluidStack?, doFill: Boolean): Int = 0
    }

    private class FluidBlockWrapper(
        private val position: BlockPosition,
        private val block: IFluidBlock
    ) : BlockWrapperBase {
        companion object {
            const val AssumedCapacity = Fluid.BUCKET_VOLUME
        }

        fun canDrain(fluid: Fluid): Boolean = block.canDrain(position)

        override fun getTankProperties(): Array<IFluidTankProperties> =
            arrayOf(FluidTankProperties(FluidStack(block.fluid, (block.getFilledPercentage(position) * AssumedCapacity).toInt()), AssumedCapacity))

        override fun uncheckedDrain(doDrain: Boolean): FluidStack? =
            block.drain(position, doDrain)
    }

    private class LiquidBlockWrapper(
        private val position: BlockPosition,
        block: BlockLiquid
    ) : BlockWrapperBase {
        private val fluid: Fluid = lookupFluidForBlock(block)!!

        fun canDrain(fluid: Fluid): Boolean = true

        override fun getTankProperties(): Array<IFluidTankProperties> =
            arrayOf(FluidTankProperties(FluidStack(fluid, Fluid.BUCKET_VOLUME), Fluid.BUCKET_VOLUME))

        override fun uncheckedDrain(doDrain: Boolean): FluidStack {
            if (doDrain) {
                position.world!!.setBlockToAir(position)
            }
            return FluidStack(fluid, Fluid.BUCKET_VOLUME)
        }
    }

    private class AirBlockWrapper(
        private val position: BlockPosition,
        private val block: Block
    ) : IFluidHandler {
        fun canDrain(fluid: Fluid): Boolean = false

        override fun drain(resource: FluidStack?, doDrain: Boolean): FluidStack? = null

        override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? = null

        fun canFill(fluid: Fluid): Boolean = fluid.canBePlacedInWorld()

        override fun fill(resource: FluidStack?, doFill: Boolean): Int {
            if (resource != null && resource.fluid.canBePlacedInWorld() && resource.fluid.block != null && resource.amount >= 1000) {
                if (doFill) {
                    val world = position.world!!
                    if (!world.isAirBlock(position) && !world.containsAnyLiquid(position.bounds))
                        world.breakBlock(position)
                    world.setBlock(position, resource.fluid.block)
                    // This fake neighbor update is required to get stills to start flowing.
                    world.notifyBlockOfNeighborChange(position, world.getBlock(position))
                }
                return Fluid.BUCKET_VOLUME
            }
            return 0
        }

        override fun getTankProperties(): Array<IFluidTankProperties> = emptyArray()
    }
}
