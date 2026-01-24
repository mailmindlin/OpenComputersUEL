package li.cil.oc.common

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.IMC as ApiIMC
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.template.AssemblerTemplates
import li.cil.oc.common.template.DisassemblerTemplates
import li.cil.oc.integration.util.ItemCharge
import li.cil.oc.integration.util.Wrench
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.machine.ProgramLocations
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.common.event.FMLInterModComms.IMCEvent
import net.minecraftforge.fml.common.event.FMLInterModComms.IMCMessage
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object IMC {
    private inline fun IMCMessage.withNBT(f: (NBTTagCompound) -> Unit) {
        if (!this.isNBTMessage) return
        f(this.nbtValue)
    }
    private inline fun IMCMessage.withString(f: (String) -> Unit) {
        if (!this.isStringMessage) return
        f(this.stringValue)
    }
    @JvmStatic
    fun handleEvent(e: IMCEvent) {
        for (message in e.messages) {
            when (message.key) {
                ApiIMC.REGISTER_ASSEMBLER_TEMPLATE -> message.withNBT { value ->
                    if (value.hasKey("name", NBT.TAG_STRING))
                        OpenComputers.log.debug("Registering new assembler template '${value.getString("name")}' from mod ${message.sender}.")
                    else
                        OpenComputers.log.debug("Registering new, unnamed assembler template from mod ${message.sender}.")
                    try {
                        AssemblerTemplates.add(value)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering assembler template.", t)
                    }
                }
                ApiIMC.REGISTER_DISASSEMBLER_TEMPLATE -> message.withNBT { value ->
                    if (value.hasKey("name", NBT.TAG_STRING))
                        OpenComputers.log.debug("Registering new disassembler template '${value.getString("name")}' from mod ${message.sender}.")
                    else
                        OpenComputers.log.debug("Registering new, unnamed disassembler template from mod ${message.sender}.")
                    try {
                        DisassemblerTemplates.add(value)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering disassembler template.", t)
                    }
                }
                ApiIMC.REGISTER_TOOL_DURABILITY_PROVIDER -> message.withString { value ->
                    OpenComputers.log.debug("Registering new tool durability provider '${value}' from mod ${message.sender}.")
                    try {
                        ToolDurabilityProviders.add(getStaticMethod(value, ItemStack::class.java))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering tool durability provider.", t)
                    }
                }
                ApiIMC.REGISTER_WRENCH_TOOL -> message.withString { value ->
                    OpenComputers.log.debug("Registering new wrench usage '${value}' from mod ${message.sender}.")
                    try {
                        Wrench.addUsage(getStaticMethod(value, EntityPlayer::class.java, BlockPos::class.java, Boolean::class.javaPrimitiveType!!))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering wrench usage.", t)
                    }
                }
                ApiIMC.REGISTER_WRENCH_TOOL_CHECK -> message.withString { value ->
                    OpenComputers.log.debug("Registering new wrench tool check '${value}' from mod ${message.sender}.")
                    try {
                        Wrench.addCheck(getStaticMethod(value, ItemStack::class.java))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering wrench check.", t)
                    }
                }
                ApiIMC.REGISTER_ITEM_CHARGE -> message.withNBT { value ->
                    OpenComputers.log.debug("Registering new item charge implementation '${value.getString("name")}' from mod ${message.sender}.")
                    try {
                        ItemCharge.add(
                            getStaticMethod(value.getString("canCharge"), ItemStack::class.java),
                            getStaticMethod(value.getString("charge"), ItemStack::class.java, Double::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
                        )
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering item charge implementation.", t)
                    }
                }
                ApiIMC.BLACKLIST_PERIPHERAL -> message.withString { value ->
                    OpenComputers.log.debug("Blacklisting CC peripheral '${value}' as requested by mod ${message.sender}.")
                    if (value !in Settings.get.peripheralBlacklist) {
                        Settings.get.peripheralBlacklist.add(value)
                    }
                }
                ApiIMC.BLACKLIST_HOST -> message.withNBT { value ->
                    OpenComputers.log.debug("Blacklisting component '${value.getString("name")}' for host '${value.getString("host")}' as requested by mod ${message.sender}.")
                    try {
                        Registry.blacklistHost(ItemStack(value.getCompoundTag("item")), Class.forName(value.getString("host")))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed blacklisting component.", t)
                    }
                }
                ApiIMC.REGISTER_ASSEMBLER_FILTER -> message.withString { value ->
                    OpenComputers.log.debug("Registering new assembler template filter '${value}' from mod ${message.sender}.")
                    try {
                        AssemblerTemplates.addFilter(value)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering assembler template filter.", t)
                    }
                }
                ApiIMC.REGISTER_INK_PROVIDER -> message.withString { value ->
                    OpenComputers.log.debug("Registering new ink provider '${value}' from mod ${message.sender}.")
                    try {
                        PrintData.addInkProvider(getStaticMethod(value, ItemStack::class.java))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering ink provider.", t)
                    }
                }
                ApiIMC.REGISTER_PROGRAM_DISK_LABEL -> message.withNBT { value ->
                    OpenComputers.log.debug("Registering new program location mapping for program '${value.getString("program")}' being on disk '${value.getString("label")}' from mod ${message.sender}.")
                    val architectures = value.getTagList("architectures", NBT.TAG_STRING)
                    val archArray = (0 until architectures.tagCount()).map { (architectures.get(it) as NBTTagString).string }.toTypedArray()
                    ProgramLocations.addMapping(value.getString("program"), value.getString("label"), *archArray)
                }
                else -> {
                    OpenComputers.log.warn("Got an unrecognized or invalid IMC message '${message.key}' from mod ${message.sender}.")
                }
            }
        }
    }

    @JvmStatic
    fun getStaticMethod(name: String, vararg signature: Class<*>): Method {
        val nameSplit = name.lastIndexOf('.')
        val className = name.substring(0, nameSplit)
        val methodName = name.substring(nameSplit + 1)
        val clazz = Class.forName(className)
        val method = clazz.getDeclaredMethod(methodName, *signature)
        if (!Modifier.isStatic(method.modifiers)) throw IllegalArgumentException("Method $name is not static.")
        return method
    }

    @JvmStatic
    fun <T> tryInvokeStatic(method: Method, vararg args: Any?, default: T): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            method.invoke(null, *args) as T
        } catch (t: Throwable) {
            OpenComputers.log.warn("Error invoking callback ${method.declaringClass.canonicalName}.${method.name}.", t)
            default
        }
    }

    @JvmStatic
    fun tryInvokeStaticVoid(method: Method, vararg args: Any?) {
        try {
            method.invoke(null, *args)
        } catch (t: Throwable) {
            OpenComputers.log.warn("Error invoking callback ${method.declaringClass.canonicalName}.${method.name}.", t)
        }
    }
}
