package li.cil.oc.integration.computercraft

import com.google.common.collect.Iterables
import li.cil.oc.api.machine.Arguments
import java.io.UnsupportedEncodingException
import java.util.*

class CallableHelper(methods: Array<String>) {
    private val _methods: List<String> = methods.asList()

    @Throws(NoSuchMethodException::class)
    fun methodIndex(method: String): Int {
        val index = _methods.indexOf(method)
        if (index < 0) {
            throw NoSuchMethodException()
        }
        return index
    }

    @Throws(UnsupportedEncodingException::class)
    fun convertArguments(args: Arguments): Array<Any> {
        val argArray = Iterables.toArray(args, Any::class.java)
        for (i in argArray.indices) {
            if (argArray[i] is ByteArray) {
                argArray[i] = String((argArray[i] as ByteArray), charset("UTF-8"))
            }
        }
        return argArray
    }
}
