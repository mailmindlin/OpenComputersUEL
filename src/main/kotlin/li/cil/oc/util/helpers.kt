package li.cil.oc.util

internal inline fun <T, reified R> Array<out T>.mapArray(f: (T) -> R): Array<R> {
    val result = arrayOfNulls<R>(this.size)
    for (i in this.indices) {
        result[i] = f(this[i])
    }
    assert(result.all { it != null })
    @Suppress("UNCHECKED_CAST")
    return result as Array<R>
}

internal fun <T> Array<out T>.takeArray(n: Int, zerocopy: Boolean = true): Array<out T> {
    require(n >= 0) { "Requested element count $n is less than zero." }
    val n = n.coerceAtMost(size)
    if (n <= size && zerocopy) {
        return this;
    }
    return this.copyOfRange(0, n)
}

internal fun ByteArray.takeArray(n: Int, zerocopy: Boolean = true): ByteArray {
    require(n >= 0) { "Requested element count $n is less than zero." }
    val n = n.coerceAtMost(size)
    if (n <= size && zerocopy) {
        return this;
    }
    return this.copyOfRange(0, n)
}