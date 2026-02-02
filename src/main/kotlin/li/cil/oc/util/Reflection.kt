package li.cil.oc.util

import java.lang.reflect.InvocationTargetException

internal object Reflection {
    fun getClass(name: String): Class<*>? {
        return try {
            Class.forName(name)
        } catch (ignored: ClassNotFoundException) {
            null
        }
    }

    fun get(instance: Any, fieldName: String): Any? {
        try {
            val field = instance.javaClass.getField(fieldName)
            return field[instance]
        } catch (ignored: IllegalAccessException) {
            return null
        } catch (ignored: NoSuchFieldException) {
            return null
        }
    }

    fun set(instance: Any, fieldName: String, value: Any?) {
        try {
            val field = instance.javaClass.getField(fieldName)
            field[instance] = value
        } catch (ignored: IllegalAccessException) {
        } catch (ignored: NoSuchFieldException) {
        }
    }

    @Throws(Throwable::class)
    fun <T> invoke(instance: Any, methodName: String, vararg args: Any): T? {
        try {
            outer@ for (method in instance.javaClass.methods) {
                if (method.name == methodName && method.parameterTypes.size == args.size) {
                    val argTypes = method.parameterTypes
                    for (i in argTypes.indices) {
                        val have = argTypes[i]
                        val given: Class<*> = args[i].javaClass
                        // Fail if not assignable and not assignable to primitive.
                        if (!have.isAssignableFrom(given) && (!have.isPrimitive
                                    || (!(Byte::class.javaPrimitiveType == have && Byte::class.java == given)
                                    && !(Short::class.javaPrimitiveType == have && Short::class.java == given)
                                    && !(Int::class.javaPrimitiveType == have && Int::class.java == given)
                                    && !(Long::class.javaPrimitiveType == have && Long::class.java == given)
                                    && !(Float::class.javaPrimitiveType == have && Float::class.java == given)
                                    && !(Double::class.javaPrimitiveType == have && Double::class.java == given)
                                    && !(Boolean::class.javaPrimitiveType == have && Boolean::class.java == given)
                                    && !(Char::class.javaPrimitiveType == have && Char::class.java == given)))
                        ) {
                            continue@outer
                        }
                    }
                    return method.invoke(instance, *args) as T
                }
            }
            return null
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        } catch (e: IllegalAccessException) {
            return null
        } catch (e: ClassCastException) {
            return null
        }
    }

    fun <T> tryInvoke(instance: Any, methodName: String, vararg args: Any): T? {
        return try {
            invoke<T>(instance, methodName, *args)
        } catch (ignored: Throwable) {
            null
        }
    }
}
