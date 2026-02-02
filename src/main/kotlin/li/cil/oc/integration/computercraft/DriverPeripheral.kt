package li.cil.oc.integration.computercraft

import dan200.computercraft.ComputerCraft
import dan200.computercraft.api.filesystem.IMount
import dan200.computercraft.api.filesystem.IWritableMount
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.ILuaTask
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.FileSystem
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.*
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.integration.computercraft.DriverComputerCraftMedia.fromComputerCraft
import li.cil.oc.util.Reflection
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverPeripheral : DriverBlock {
    companion object {
        private var blacklist: MutableSet<Class<*>>? = null
    }

    private fun isBlacklisted(o: Any): Boolean {
        // Check for our interface first, as that has priority.
        if (o is BlacklistedPeripheral) {
            return o.isPeripheralBlacklisted
        }

        // Delayed initialization of the resolved classes to allow registering
        // additional entries via IMC.
        val blacklist = blacklist ?: run {
            val blacklist = mutableSetOf<Class<*>>()
            DriverPeripheral.blacklist = blacklist

            for (name in Settings.get.peripheralBlacklist) {
                val clazz = Reflection.getClass(name)
                if (clazz != null) {
                    blacklist.add(clazz)
                }
            }

            blacklist
        }

        return blacklist.any { it.isInstance(o) }
    }

    private fun findPeripheral(world: World, pos: BlockPos, side: EnumFacing): IPeripheral? {
        try {
            val p = ComputerCraft.getPeripheralAt(world, pos, side)
            if (!isBlacklisted(p)) {
                return p
            }
        } catch (e: Exception) {
            OpenComputers.log.warn(
                String.format("Error accessing ComputerCraft peripheral @ (%d, %d, %d).", pos.x, pos.y, pos.z),
                e
            )
        }
        return null
    }

    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return tileEntity != null // This ensures we don't get duplicate components, in case the
                // tile entity is natively compatible with OpenComputers.
                && !li.cil.oc.api.network.Environment::class.java.isAssignableFrom(tileEntity.javaClass) // The black list is used to avoid peripherals that are known
                // to be incompatible with OpenComputers when used directly.
                && !isBlacklisted(tileEntity) // Actual check if it's a peripheral.
                && findPeripheral(world, pos, side) != null
    }

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment {
        return Environment(findPeripheral(world, pos, side)!!)
    }

    class Environment(protected val peripheral: IPeripheral) : AbstractManagedEnvironment(),
        ManagedPeripheral, NamedBlock {
        protected val helper: CallableHelper = CallableHelper(peripheral.methodNames)

        protected val accesses: MutableMap<String?, FakeComputerAccess> = HashMap()

        init {
            setNode(Network.newNode(this, Visibility.Network)!!.create())
        }

        override fun methods(): Array<String> = peripheral.methodNames

        @Throws(Exception::class)
        override fun invoke(method: String, context: Context, args: Arguments): Array<Any>? {
            val index = helper.methodIndex(method)
            val argArray = helper.convertArguments(args)
            val access = if (accesses.containsKey(context.node().address())) {
                accesses[context.node().address()]
            } else {
                // The calling contexts is not visible to us, meaning we never got
                // an onConnect for it. Create a temporary access.
                FakeComputerAccess(this, context)
            }
            return peripheral.callMethod(access!!, UnsupportedLuaContext.instance(), index, argArray)
        }

        override fun onConnect(node: Node) {
            super.onConnect(node)
            if (node.host() is Context && !accesses.containsKey(node.address())) {
                val access = FakeComputerAccess(this, node.host() as Context)
                accesses[node.address()] = access
                peripheral.attach(access)
            }
        }

        override fun onDisconnect(node: Node) {
            super.onDisconnect(node)
            if (node.host() is Context) {
                val access = accesses.remove(node.address())
                if (access != null) {
                    peripheral.detach(access)
                }
            } else if (node === this.node()) {
                for (access in accesses.values) {
                    peripheral.detach(access)
                    access.close()
                }
                accesses.clear()
            }
        }

        override fun preferredName(): String = peripheral.type

        override fun priority(): Int = -1 // Lower than 'real' OC components

        /**
         * Map interaction with the computer to our format as good as we can.
         */
        class FakeComputerAccess(protected val owner: Environment, protected val context: Context) :
            IComputerAccess {
            protected val fileSystems: MutableMap<String?, ManagedEnvironment> = HashMap()

            fun close() {
                for (fileSystem in fileSystems.values) {
                    fileSystem.node()!!.remove()
                }
                fileSystems.clear()
            }

            override fun mount(desiredLocation: String, mount: IMount): String? {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null
                }
                return mount(desiredLocation, FileSystem.asManagedEnvironment(fromComputerCraft(mount)))
            }

            override fun mount(desiredLocation: String, mount: IMount, driveName: String): String? {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null
                }
                return mount(desiredLocation, FileSystem.asManagedEnvironment(fromComputerCraft(mount), driveName))
            }

            override fun mountWritable(desiredLocation: String, mount: IWritableMount): String? {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null
                }
                return mount(desiredLocation, FileSystem.asManagedEnvironment(fromComputerCraft(mount)))
            }

            override fun mountWritable(desiredLocation: String, mount: IWritableMount, driveName: String): String? {
                if (fileSystems.containsKey(desiredLocation)) {
                    return null
                }
                return mount(desiredLocation, FileSystem.asManagedEnvironment(fromComputerCraft(mount), driveName))
            }

            private fun mount(path: String, fileSystem: ManagedEnvironment): String {
                fileSystems[path] =
                    fileSystem //TODO This is per peripheral/Environment. It would be far better with per computer
                context.node().connect(fileSystem.node())
                return path
            }

            override fun unmount(location: String?) {
                val fileSystem = fileSystems.remove(location)
                if (fileSystem != null) {
                    fileSystem.node()!!.remove()
                }
            }

            override fun getID(): Int {
                return context.node().address().hashCode()
            }

            override fun queueEvent(event: String, arguments: Array<out Any?>?) {
                context.signal(event, *(arguments ?: emptyArray()))
            }

            override fun getAttachmentName(): String {
                return owner.node()!!.address()!!
            }
        }

        /**
         * Since we abstract away anything language specific, we cannot support the
         * Lua context specific operations ComputerCraft provides.
         */
        class UnsupportedLuaContext private constructor() : ILuaContext {
            @Throws(LuaException::class)
            override fun issueMainThreadTask(task: ILuaTask): Long {
                throw UnsupportedOperationException()
            }

            @Throws(LuaException::class, InterruptedException::class)
            override fun executeMainThreadTask(task: ILuaTask): Array<Any>? {
                throw UnsupportedOperationException()
            }

            @Throws(LuaException::class, InterruptedException::class)
            override fun pullEvent(filter: String?): Array<Any> {
                throw UnsupportedOperationException()
            }

            @Throws(InterruptedException::class)
            override fun pullEventRaw(filter: String?): Array<Any> {
                throw UnsupportedOperationException()
            }

            @Throws(InterruptedException::class)
            override fun yield(arguments: Array<Any>?): Array<Any> {
                throw UnsupportedOperationException()
            }

            companion object {
                protected val Instance: UnsupportedLuaContext = UnsupportedLuaContext()

                fun instance(): UnsupportedLuaContext {
                    return Instance
                }
            }
        }
    }
}
