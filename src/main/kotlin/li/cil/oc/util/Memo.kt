package li.cil.oc.util

internal class Memo<K, V>(private val mapper: (K) -> V) {
    private val mapping = mutableMapOf<K, V>()
    operator fun invoke(key: K): V {
        return this.mapping.getOrPut(key) { mapper(key) }
    }
}

internal class DefaultMap<K, V>(private val internal: MutableMap<K, V>, private val makeDefault: () -> V): Map<K, V> by internal {
    constructor(makeDefault: () -> V): this(mutableMapOf(), makeDefault)

    override fun get(key: K): V = internal.getOrPut(key, makeDefault)
    override fun getOrDefault(key: K, defaultValue: V): V = get(key)
}