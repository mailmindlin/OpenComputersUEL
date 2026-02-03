package li.cil.oc.server.component

import li.cil.oc.Settings
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.RobotProxy
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.entity.Entity
import net.minecraft.entity.IMerchant
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.village.MerchantRecipe
import net.minecraftforge.common.DimensionManager
import java.lang.ref.WeakReference
import java.util.*

class Trade(val info: TradeInfo) : AbstractValue() {
    @Suppress("unused") // For deserialization
    private constructor() : this(TradeInfo())

    constructor(upgrade: UpgradeTrading, merchant: IMerchant, recipeID: Int, merchantID: Int) :
            this(TradeInfo(upgrade.host, merchant, recipeID, merchantID))

    val maxRange get() = Settings.get.tradingRange

    val isInRange: Boolean
        get() {
            val merchantOpt = info.merchant.get()
            val hostOpt = info.host
            return if (merchantOpt is Entity && hostOpt != null) {
                merchantOpt.getDistanceSq(hostOpt.xPosition(), hostOpt.yPosition(), hostOpt.zPosition()) < maxRange * maxRange
            } else {
                false
            }
        }

    // Queue the load because when load is called we can't access the world yet
    // and we need to access it to get the Robot's TileEntity / Drone's Entity.
    override fun load(nbt: NBTTagCompound) {
        EventHandler.scheduleServer { info.load(nbt) }
    }

    override fun save(nbt: NBTTagCompound) {
        info.save(nbt)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function():number -- Returns a sort index of the merchant that provides this trade")
    fun getMerchantId(context: Context, arguments: Arguments): Result =
        result(info.merchantID)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function():table, table -- Returns the items the merchant wants for this trade.")
    fun getInput(context: Context, arguments: Arguments): Result {
        val recipe = info.recipe
        return result(
            recipe?.itemToBuy?.copy(),
            if (recipe?.hasSecondItemToBuy() == true) recipe.secondItemToBuy?.copy() else null
        )
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function():table -- Returns the item the merchant offers for this trade.")
    fun getOutput(context: Context, arguments: Arguments): Result =
        result(info.recipe?.itemToSell?.copy())

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function():boolean -- Returns whether the merchant currently wants to trade this.")
    fun isEnabled(context: Context, arguments: Arguments): Result {
        val merchant = info.merchant.get()
        val recipe = info.recipe
        return result(merchant != null && recipe?.isRecipeDisabled != true) // Make sure merchant is neither dead/gone nor the recipe has been disabled.
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function():boolean, string -- Returns true when trade succeeds and nil, error when not.")
    fun trade(context: Context, arguments: Arguments): Result {
        // Make sure we can access an inventory.
        val inventory = info.inventory ?: return result(false, "trading requires an inventory upgrade to be installed")

        // Make sure merchant hasn't died, it somehow gone or moved out of range and still wants to trade this.
        val merchant = info.merchant.get()
        if (merchant !is Entity || !merchant.isEntityAlive || !isInRange) {
            return when {
                merchant == null || merchant !is Entity -> result(false, "trade has become invalid")
                !merchant.isEntityAlive -> result(false, "trader died")
                !isInRange -> result(false, "out of range")
                else -> result(false, "trade has become invalid")
            }
        }

        val recipe = info.recipe ?: return result(false, "trade has become invalid")
        if (recipe.isRecipeDisabled) {
            return result(false, "trade is disabled")
        }
        if (!hasRoomForRecipe(inventory, recipe)) {
            return result(false, "not enough inventory space to trade")
        }
        return if (completeTrade(inventory, recipe, exact = true) || completeTrade(inventory, recipe, exact = false)) {
            result(true)
        } else {
            result(false, "not enough items to trade")
        }
    }

    fun hasRoomForRecipe(inventory: IInventory, recipe: MerchantRecipe): Boolean {
        val remainder = recipe.itemToSell.copy()
        InventoryUtils.insertIntoInventory(remainder, InventoryUtils.asItemHandler(inventory), remainder.count, simulate = true)
        return remainder.count == 0
    }

    fun completeTrade(inventory: IInventory, recipe: MerchantRecipe, exact: Boolean): Boolean {
        // Now we'll check if we have enough items to perform the trade, caching first
        val merchant = info.merchant.get() ?: return false

        val firstInputStack = recipe.itemToBuy
        val secondInputStack = if (recipe.hasSecondItemToBuy()) recipe.secondItemToBuy else null

        fun containsAccumulativeItemStack(stack: ItemStack): Boolean =
            InventoryUtils.extractFromInventory(stack, inventory, null, simulate = true, exact = exact).count == 0

        // Check if we have enough to perform the trade.
        if (!containsAccumulativeItemStack(firstInputStack) || (secondInputStack != null && !containsAccumulativeItemStack(secondInputStack))) {
            return false
        }

        // Now we need to check if we have enough inventory space to accept the item we get for the trade.
        val outputStack = recipe.itemToSell.copy()

        // We established that out inventory allows to perform the trade, now actually do the trade.
        InventoryUtils.extractFromInventory(firstInputStack, InventoryUtils.asItemHandler(inventory), exact = exact)
        secondInputStack?.let { InventoryUtils.extractFromInventory(it, InventoryUtils.asItemHandler(inventory), exact = exact) }
        InventoryUtils.insertIntoInventory(outputStack, InventoryUtils.asItemHandler(inventory), outputStack.count)

        // Tell the merchant we used the recipe, so MC can disable it and/or enable more recipes.
        merchant.useRecipe(recipe)
        return true
    }
}

class TradeInfo(var host: EnvironmentHost?, var merchant: WeakReference<IMerchant>, var recipeID: Int, var merchantID: Int) {
    constructor() : this(null, WeakReference(null), -1, -1)

    constructor(host: EnvironmentHost, merchant: IMerchant, recipeID: Int, merchantID: Int) :
            this(host, WeakReference(merchant), recipeID, merchantID)

    val recipe: MerchantRecipe?
        get() = merchant.get()?.getRecipes(null)?.getOrNull(recipeID)

    val inventory: IInventory?
        get() = (host as? Agent)?.mainInventory()

    private val HostIsEntityTag = "hostIsEntity"
    private val MerchantUUIDMostTag = "merchantUUIDMost"
    private val MerchantUUIDLeastTag = "merchantUUIDLeast"
    private val DimensionIDTag = "dimensionID"
    private val HostUUIDMost = "hostUUIDMost"
    private val HostUUIDLeast = "hostUUIDLeast"
    private val HostXTag = "hostX"
    private val HostYTag = "hostY"
    private val HostZTag = "hostZ"
    private val RecipeID = "recipeID"
    private val MerchantID = "merchantID"

    fun load(nbt: NBTTagCompound) {
        val isEntity = nbt.getBoolean(HostIsEntityTag)
        // If drone we find it again by its UUID, if Robot we know the X/Y/Z of the TileEntity.
        host = if (isEntity) loadHostEntity(nbt) else loadHostTileEntity(nbt)
        merchant = WeakReference(loadEntity(nbt, UUID(nbt.getLong(MerchantUUIDMostTag), nbt.getLong(MerchantUUIDLeastTag))) as? IMerchant)
        recipeID = nbt.getInteger(RecipeID)
        merchantID = if (nbt.hasKey(MerchantID)) nbt.getInteger(MerchantID) else -1
    }

    fun save(nbt: NBTTagCompound) {
        when (val h = host) {
            is Entity -> {
                nbt.setBoolean(HostIsEntityTag, true)
                nbt.setInteger(DimensionIDTag, h.world.provider.dimension)
                nbt.setLong(HostUUIDLeast, h.persistentID.leastSignificantBits)
                nbt.setLong(HostUUIDMost, h.persistentID.mostSignificantBits)
            }
            is TileEntity -> {
                nbt.setBoolean(HostIsEntityTag, false)
                nbt.setInteger(DimensionIDTag, h.world.provider.dimension)
                nbt.setInteger(HostXTag, h.pos.x)
                nbt.setInteger(HostYTag, h.pos.y)
                nbt.setInteger(HostZTag, h.pos.z)
            }
        }
        (merchant.get() as? Entity)?.let { entity ->
            nbt.setLong(MerchantUUIDLeastTag, entity.persistentID.leastSignificantBits)
            nbt.setLong(MerchantUUIDMostTag, entity.persistentID.mostSignificantBits)
        }
        nbt.setInteger(RecipeID, recipeID)
        nbt.setInteger(MerchantID, merchantID)
    }

    private fun loadEntity(nbt: NBTTagCompound, uuid: UUID): Entity? {
        val dimension = nbt.getInteger(DimensionIDTag)
        val world = DimensionManager.getWorld(dimension) ?: return null

        return world.loadedEntityList.find { entity ->
            entity is Entity && entity.persistentID == uuid
        } as? Entity
    }

    private fun loadHostEntity(nbt: NBTTagCompound): EnvironmentHost? {
        return loadEntity(nbt, UUID(nbt.getLong(HostUUIDMost), nbt.getLong(HostUUIDLeast))) as? EnvironmentHost
    }

    private fun loadHostTileEntity(nbt: NBTTagCompound): EnvironmentHost? {
        val dimension = nbt.getInteger(DimensionIDTag)
        val world = DimensionManager.getWorld(dimension) ?: return null

        val x = nbt.getInteger(HostXTag)
        val y = nbt.getInteger(HostYTag)
        val z = nbt.getInteger(HostZTag)

        return when (val te = world.getTileEntity(BlockPos(x, y, z))) {
            is RobotProxy -> te.robot
            is Agent -> te
            else -> null
        }
    }
}
