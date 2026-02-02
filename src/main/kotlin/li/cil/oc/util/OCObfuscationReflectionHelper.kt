package li.cil.oc.util

import net.minecraftforge.fml.common.ObfuscationReflectionHelper

internal object OCObfuscationReflectionHelper {
    fun <T, E> getPrivateValue(classToAccess: Class<in E?>?, instance: E?, srgName: String): T {
        // HACK: Don't break compatibility with older Forge versions.
        // This also works around a Scala compiler crash: "trying to do lub/glb of typevar ?E".
        return ObfuscationReflectionHelper.getPrivateValue(classToAccess, instance, *arrayOf(srgName))
    }

    fun <T, E> setPrivateValue(classToAccess: Class<in T>?, instance: T, value: E, srgName: String) {
        // HACK: Don't break compatibility with older Forge versions.
        // This also works around a Scala compiler crash: "trying to do lub/glb of typevar ?E".
        ObfuscationReflectionHelper.setPrivateValue(classToAccess, instance, value, *arrayOf(srgName))
    }
}
