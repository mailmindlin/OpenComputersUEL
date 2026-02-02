package li.cil.oc.common.asm.template

import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.SimpleComponent
import net.minecraft.nbt.NBTTagCompound

/**
 * This interface defines the names to which existing or placeholders for
 * existing methods will be moved. This allows transparent injection of our
 * functionality, i.e. existing validate() etc. methods will be called as
 * if we didn't inject our code.
 *
 * Yes, the names are not "conventional", but that is by design, to avoid
 * naming collisions.
 */
@Suppress("FunctionName")
interface SimpleComponentImpl : Environment, SimpleComponent {
    fun validate_OpenComputers()

    fun invalidate_OpenComputers()

    fun onChunkUnload_OpenComputers()

    fun readFromNBT_OpenComputers(nbt: NBTTagCompound)

    fun writeToNBT_OpenComputers(nbt: NBTTagCompound): NBTTagCompound

    companion object {
        const val PostFix: String = "_OpenComputers"
    }
}
