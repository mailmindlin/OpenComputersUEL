package li.cil.oc.common.tileentity

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.network.Node
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.util.ExtendedAABB
import li.cil.oc.util.rotateTowards
import li.cil.oc.util.setNewCompoundTag
import li.cil.oc.util.volume
import net.minecraft.init.SoundEvents
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.RedstoneAware as TraitRedstoneAware
import li.cil.oc.common.tileentity.traits.RotatableTile as TraitRotatableTile

class Print @JvmOverloads constructor(
    internal val canToggle: (() -> Boolean)? = null,
    internal val scheduleUpdate: ((Int) -> Unit)? = null,
    internal val onStateChange: (() -> Unit)? = null
) : TileEntityBase.TEEnvironmentBase(), TraitRedstoneAware, TraitRotatableTile {
    override val rotatableDelegate: TraitRotatableTile.Delegate = register(TraitRotatableTile::Delegate)
    override val redstoneDelegate: RedstoneAware.Delegate = register(RedstoneAware::Delegate)
    override fun node(): Node? = null

    init {
        redstoneDelegate._isOutputEnabled = true
    }

    @JvmField
    val data = PrintData()

    @JvmField
    var boundsOff: AxisAlignedBB = ExtendedAABB.unitBounds

    @JvmField
    var boundsOn: AxisAlignedBB = ExtendedAABB.unitBounds

    @JvmField
    var state = false

    val bounds: AxisAlignedBB get() = if (state) boundsOn else boundsOff
    val noclip: Boolean get() = if (state) data.noclipOn else data.noclipOff
    val shapes: MutableSet<PrintData.Shape> get() = if (state) data.stateOn else data.stateOff

    fun isSideSolid(side: EnumFacing): Boolean {
        for (shape in shapes) {
            if (!Strings.isNullOrEmpty(shape.texture)) {
                val bounds = shape.bounds.rotateTowards(facing()!!)
                val fullX = bounds.minX == 0.0 && bounds.maxX == 1.0
                val fullY = bounds.minY == 0.0 && bounds.maxY == 1.0
                val fullZ = bounds.minZ == 0.0 && bounds.maxZ == 1.0
                val isSolid = when (side) {
                    EnumFacing.DOWN -> bounds.minY == 0.0 && fullX && fullZ
                    EnumFacing.UP -> bounds.maxY == 1.0 && fullX && fullZ
                    EnumFacing.NORTH -> bounds.minZ == 0.0 && fullX && fullY
                    EnumFacing.SOUTH -> bounds.maxZ == 1.0 && fullX && fullY
                    EnumFacing.WEST -> bounds.minX == 0.0 && fullY && fullZ
                    EnumFacing.EAST -> bounds.maxX == 1.0 && fullY && fullZ
                    else -> false
                }
                if (isSolid) return true
            }
        }
        return false
    }

    fun addCollisionBoxesToList(mask: AxisAlignedBB?, list: MutableList<AxisAlignedBB>, pos: BlockPos = BlockPos.ORIGIN) {
        if (!noclip) {
            if (shapes.isEmpty()) {
                val unitBounds = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).offset(pos)
                if (mask == null || unitBounds.intersects(mask)) {
                    list.add(unitBounds)
                }
            } else {
                for (shape in shapes) {
                    val bounds = shape.bounds.rotateTowards(facing()!!).offset(pos)
                    if (mask == null || bounds.intersects(mask)) {
                        list.add(bounds)
                    }
                }
            }
        }
    }

    fun rayTrace(start: Vec3d, end: Vec3d, pos: BlockPos = BlockPos.ORIGIN): RayTraceResult? {
        var closestDistance = Double.POSITIVE_INFINITY
        var closest: RayTraceResult? = null
        if (shapes.isEmpty()) {
            val bounds = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).offset(pos)
            val hit = bounds.calculateIntercept(start, end)
            if (hit != null) {
                val distance = hit.hitVec.distanceTo(start)
                if (distance < closestDistance) {
                    closestDistance = distance
                    closest = hit
                }
            }
        } else {
            for (shape in shapes) {
                val bounds = shape.bounds.rotateTowards(facing()!!).offset(pos)
                val hit = bounds.calculateIntercept(start, end)
                if (hit != null) {
                    val distance = hit.hitVec.distanceTo(start)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closest = hit
                    }
                }
            }
        }
        return closest?.let { hit -> RayTraceResult(hit.hitVec, hit.sideHit, pos) }
    }

    fun activate(): Boolean {
        if (data.hasActiveState) {
            if (!state || !data.isButtonMode) {
                toggleState()
                return true
            }
        }
        return false
    }

    fun toggleState() {
        if (canToggle?.invoke() != false) {
            state = !state
            world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3f, if (state) 0.6f else 0.5f)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            updateRedstone()
            if (state && data.isButtonMode) {
                val block = Constants.BlockInfo.Print.block()
                val delay = block.tickRate(world)
                if (scheduleUpdate != null) {
                    scheduleUpdate.invoke(delay)
                } else {
                    world.scheduleUpdate(pos, block, delay)
                }
            }
            onStateChange?.invoke()
        }
    }

    fun updateBounds() {
        boundsOff = data.stateOff.drop(1).fold(
            data.stateOff.firstOrNull()?.bounds ?: ExtendedAABB.unitBounds
        ) { a, b -> a.union(b.bounds) }
        if (boundsOff.volume == 0) boundsOff = ExtendedAABB.unitBounds
        else boundsOff = boundsOff.rotateTowards(facing()!!)

        boundsOn = data.stateOn.drop(1).fold(
            data.stateOn.firstOrNull()?.bounds ?: ExtendedAABB.unitBounds
        ) { a, b -> a.union(b.bounds) }
        boundsOn = if (boundsOn.volume == 0) ExtendedAABB.unitBounds
        else boundsOn.rotateTowards(facing()!!)
    }

    fun updateRedstone() {
        if (data.emitRedstone) {
            setOutput(RedstoneValues(if (data.emitRedstone(state)) data.redstoneLevel else 0))
        }
    }

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        val newState = args.newValue > 0
        if (!data.emitRedstone && data.hasActiveState && state != newState) {
            toggleState()
        }
    }

    override fun onRotationChanged() {
        super.onRotationChanged()
        updateBounds()
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val DataTag = Settings.namespace + "data"
        private val DataTagCompat = "data"
        private val StateTag = Settings.namespace + "state"
        private val StateTagCompat = "state"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        if (nbt.hasKey(DataTagCompat))
            data.load(nbt.getCompoundTag(DataTagCompat))
        else
            data.load(nbt.getCompoundTag(DataTag))
        if (nbt.hasKey(StateTagCompat))
            state = nbt.getBoolean(StateTagCompat)
        else
            state = nbt.getBoolean(StateTag)
        updateBounds()
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setNewCompoundTag(DataTag) { data.save(it) }
        nbt.setBoolean(StateTag, state)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        data.load(nbt.getCompoundTag(DataTag))
        state = nbt.getBoolean(StateTag)
        updateBounds()
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            if (data.emitLight) world.checkLight(pos)
        }
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setNewCompoundTag(DataTag) { data.save(it) }
        nbt.setBoolean(StateTag, state)
    }
}
