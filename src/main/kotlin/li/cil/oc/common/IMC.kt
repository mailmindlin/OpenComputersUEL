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
import net.minecraft.nbt.NBTTagString
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.common.event.FMLInterModComms.IMCEvent
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object IMC {
    @JvmStatic
    fun handleEvent(e: IMCEvent) {
        for (message in e.messages) {
            when {
                message.key == ApiIMC.REGISTER_ASSEMBLER_TEMPLATE && message.isNBTMessage -> {
                    if (message.nbtValue.hasKey("name", NBT.TAG_STRING))
                        OpenComputers.log.debug("Registering new assembler template '${message.nbtValue.getString("name")}' from mod ${message.sender}.")
                    else
                        OpenComputers.log.debug("Registering new, unnamed assembler template from mod ${message.sender}.")
                    try {
                        AssemblerTemplates.add(message.nbtValue)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering assembler template.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_DISASSEMBLER_TEMPLATE && message.isNBTMessage -> {
                    if (message.nbtValue.hasKey("name", NBT.TAG_STRING))
                        OpenComputers.log.debug("Registering new disassembler template '${message.nbtValue.getString("name")}' from mod ${message.sender}.")
                    else
                        OpenComputers.log.debug("Registering new, unnamed disassembler template from mod ${message.sender}.")
                    try {
                        DisassemblerTemplates.add(message.nbtValue)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering disassembler template.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_TOOL_DURABILITY_PROVIDER && message.isStringMessage -> {
                    OpenComputers.log.debug("Registering new tool durability provider '${message.stringValue}' from mod ${message.sender}.")
                    try {
                        ToolDurabilityProviders.add(getStaticMethod(message.stringValue, ItemStack::class.java))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering tool durability provider.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_WRENCH_TOOL && message.isStringMessage -> {
                    OpenComputers.log.debug("Registering new wrench usage '${message.stringValue}' from mod ${message.sender}.")
                    try {
                        Wrench.addUsage(getStaticMethod(message.stringValue, EntityPlayer::class.java, BlockPos::class.java, Boolean::class.javaPrimitiveType!!))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering wrench usage.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_WRENCH_TOOL_CHECK && message.isStringMessage -> {
                    OpenComputers.log.debug("Registering new wrench tool check '${message.stringValue}' from mod ${message.sender}.")
                    try {
                        Wrench.addCheck(getStaticMethod(message.stringValue, ItemStack::class.java))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering wrench check.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_ITEM_CHARGE && message.isNBTMessage -> {
                    OpenComputers.log.debug("Registering new item charge implementation '${message.nbtValue.getString("name")}' from mod ${message.sender}.")
                    try {
                        ItemCharge.add(
                            getStaticMethod(message.nbtValue.getString("canCharge"), ItemStack::class.java),
                            getStaticMethod(message.nbtValue.getString("charge"), ItemStack::class.java, Double::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)
                        )
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering item charge implementation.", t)
                    }
                }
                message.key == ApiIMC.BLACKLIST_PERIPHERAL && message.isStringMessage -> {
                    OpenComputers.log.debug("Blacklisting CC peripheral '${message.stringValue}' as requested by mod ${message.sender}.")
                    if (!Settings.get.peripheralBlacklist.contains(message.stringValue)) {
                        Settings.get.peripheralBlacklist.add(message.stringValue)
                    }
                }
                message.key == ApiIMC.BLACKLIST_HOST && message.isNBTMessage -> {
                    OpenComputers.log.debug("Blacklisting component '${message.nbtValue.getString("name")}' for host '${message.nbtValue.getString("host")}' as requested by mod ${message.sender}.")
                    try {
                        Registry.blacklistHost(ItemStack(message.nbtValue.getCompoundTag("item")), Class.forName(message.nbtValue.getString("host")))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed blacklisting component.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_ASSEMBLER_FILTER && message.isStringMessage -> {
                    OpenComputers.log.debug("Registering new assembler template filter '${message.stringValue}' from mod ${message.sender}.")
                    try {
                        AssemblerTemplates.addFilter(message.stringValue)
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering assembler template filter.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_INK_PROVIDER && message.isStringMessage -> {
                    OpenComputers.log.debug("Registering new ink provider '${message.stringValue}' from mod ${message.sender}.")
                    try {
                        PrintData.addInkProvider(getStaticMethod(message.stringValue, ItemStack::class.java))
                    } catch (t: Throwable) {
                        OpenComputers.log.warn("Failed registering ink provider.", t)
                    }
                }
                message.key == ApiIMC.REGISTER_PROGRAM_DISK_LABEL && message.isNBTMessage -> {
                    OpenComputers.log.debug("Registering new program location mapping for program '${message.nbtValue.getString("program")}' being on disk '${message.nbtValue.getString("label")}' from mod ${message.sender}.")
                    val architectures = message.nbtValue.getTagList("architectures", NBT.TAG_STRING)
                    val archArray = (0 until architectures.tagCount()).map { (architectures.get(it) as NBTTagString).string }.toTypedArray()
                    ProgramLocations.addMapping(message.nbtValue.getString("program"), message.nbtValue.getString("label"), *archArray)
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
