package li.cil.oc.util

inline fun <T, reified R> Array<out T>.mapArray(f: (T) -> R): Array<R> {
    val result = arrayOfNulls<R>(this.size)
    for (i in this.indices) {
        result[i] = f(this[i])
    }
    assert(result.all { it != null })
    @Suppress("UNCHECKED_CAST")
    return result as Array<R>
}