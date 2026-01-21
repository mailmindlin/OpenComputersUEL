package li.cil.oc.common

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.FileSystem as ApiFileSystem
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.common.init.Items
import li.cil.oc.util.Color
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.util.Random
import java.util.concurrent.Callable

object Loot {
    @JvmField
    val factories = mutableMapOf<String, Callable<FileSystem>>()

    @JvmField
    val globalDisks = mutableListOf<Pair<ItemStack, Int>>()

    @JvmField
    val worldDisks = mutableListOf<Pair<ItemStack, Int>>()

    @JvmStatic
    fun disksForCycling(): MutableList<ItemStack> = if (disksForCyclingClient.isNotEmpty()) disksForCyclingClient else disksForCyclingServer

    @JvmField
    val disksForCyclingServer = mutableListOf<ItemStack>()

    @JvmField
    val disksForCyclingClient = mutableListOf<ItemStack>()

    @JvmField
    val disksForSampling = mutableListOf<ItemStack>()

    @JvmField
    val disksForClient = mutableListOf<ItemStack>()

    @JvmStatic
    fun isLootDisk(stack: ItemStack): Boolean {
        return ApiItems.get(stack) == ApiItems.get(Constants.ItemName.Floppy) &&
            stack.hasTagCompound() &&
            stack.tagCompound!!.hasKey(Settings.namespace + "lootFactory", NBT.TAG_STRING)
    }

    @JvmStatic
    fun randomDisk(rng: Random): ItemStack? {
        return if (disksForSampling.isNotEmpty()) {
            disksForSampling[rng.nextInt(disksForSampling.size)]
        } else {
            null
        }
    }

    @JvmStatic
    fun registerLootDisk(name: String, color: EnumDyeColor, factory: Callable<FileSystem>, doRecipeCycling: Boolean): ItemStack {
        val mod = Loader.instance().activeModContainer()!!.modId

        OpenComputers.log.debug("Registering loot disk '$name' from mod $mod.")

        val modSpecificName = "$mod:$name"

        val data = NBTTagCompound()
        data.setString(Settings.namespace + "fs.label", name)

        val nbt = NBTTagCompound()
        nbt.setTag(Settings.namespace + "data", data)

        // Store this top level, so it won't get wiped on save.
        nbt.setString(Settings.namespace + "lootFactory", modSpecificName)
        nbt.setInteger(Settings.namespace + "color", color.dyeDamage)

        val stack = Items.get(Constants.ItemName.Floppy)!!.createItemStack(1)
        stack.tagCompound = nbt

        factories[modSpecificName] = factory

        if (doRecipeCycling) {
            disksForCyclingServer.add(stack)
        }

        return stack.copy()
    }

    @JvmStatic
    fun init() {
        val list = Properties()
        val listStream = javaClass.getResourceAsStream("/assets/${Settings.resourceDomain}/loot/loot.properties")
        list.load(listStream)
        listStream.close()
        parseLootDisks(list, globalDisks, external = false)
    }

    @SubscribeEvent
    @Suppress("unused")
    fun initForWorld(e: WorldEvent.Load) {
        if (!e.world.isRemote && e.world.provider.dimension == 0) {
            worldDisks.clear()
            disksForSampling.clear()
            if (!e.world.isRemote) {
                val path = File(DimensionManager.getCurrentSaveRootDirectory(), Settings.savePath + "loot/")
                if (path.exists() && path.isDirectory) {
                    val listFile = File(path, "loot.properties")
                    if (listFile.exists() && listFile.isFile) {
                        try {
                            val listStream = FileInputStream(listFile)
                            val list = Properties()
                            list.load(listStream)
                            listStream.close()
                            parseLootDisks(list, worldDisks, external = true)
                        } catch (t: Throwable) {
                            OpenComputers.log.warn("Failed opening loot descriptor file in saves folder.")
                        }
                    }
                }
            }
            for (entry in globalDisks) {
                if (!worldDisks.contains(entry)) {
                    worldDisks.add(entry)
                }
            }
            for ((stack, count) in worldDisks) {
                repeat(count) {
                    disksForSampling.add(stack)
                }
            }
        }
    }

    private fun parseLootDisks(list: Properties, acc: MutableList<Pair<ItemStack, Int>>, external: Boolean) {
        for (key in list.stringPropertyNames()) {
            val value = list.getProperty(key)
            try {
                val parts = value.split(":")
                when (parts.size) {
                    3 -> acc.add(createLootDisk(parts[0], key, external, Color.byOreName[parts[2]]) to parts[1].toInt())
                    2 -> acc.add(createLootDisk(parts[0], key, external) to parts[1].toInt())
                    else -> acc.add(createLootDisk(value, key, external) to 1)
                }
            } catch (t: Throwable) {
                OpenComputers.log.warn("Bad loot descriptor: $value", t)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun createLootDisk(name: String, path: String, external: Boolean, color: EnumDyeColor? = null): ItemStack {
        val callable = if (external) {
            Callable { ApiFileSystem.asReadOnly(ApiFileSystem.fromSaveDirectory("loot/$path", 0, false)) }
        } else {
            Callable { ApiFileSystem.fromClass(OpenComputers::class.java, Settings.resourceDomain, "loot/$path") }
        }
        val stack = registerLootDisk(path, color ?: EnumDyeColor.SILVER, callable, doRecipeCycling = true)
        stack.setStackDisplayName(name)
        if (!external) {
            Items.registerStack(stack, path)
        }
        return stack
    }
}
