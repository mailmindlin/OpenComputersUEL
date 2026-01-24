package li.cil.oc.util

import net.minecraft.item.ItemStack

sealed class StackOption : Iterable<ItemStack> {
    abstract val isEmpty: Boolean
    abstract fun get(): ItemStack

    val isDefined: Boolean get() = !isEmpty
    val nonEmpty: Boolean get() = isDefined

    fun getOrElse(default: ItemStack): ItemStack = if (isEmpty) default else get()

    fun orEmpty(): ItemStack = if (isEmpty) ItemStack.EMPTY else get()

    fun map(f: (ItemStack) -> ItemStack): StackOption = if (isEmpty) EmptyStack else SomeStack(f(get()))

    fun <B> fold(ifEmpty: () -> B, f: (ItemStack) -> B): B = if (isEmpty) ifEmpty() else f(get())

    fun <B> flatMap(f: (ItemStack) -> B?): B? = if (isEmpty) null else f(get())

    fun filter(p: (ItemStack) -> Boolean): StackOption = if (isEmpty || p(get())) this else EmptyStack

    fun filterNot(p: (ItemStack) -> Boolean): StackOption = if (isEmpty || !p(get())) this else EmptyStack

    fun contains(elem: ItemStack): Boolean = !isEmpty && get() == elem

    fun exists(p: (ItemStack) -> Boolean): Boolean = !isEmpty && p(get())

    fun forall(p: (ItemStack) -> Boolean): Boolean = isEmpty || p(get())

    inline fun forEach(f: (ItemStack) -> Unit) {
        if (!isEmpty) f(get())
    }

    fun <B> collect(pf: (ItemStack) -> B?): B? = if (!isEmpty) pf(get()) else null

    fun orElse(alternative: () -> StackOption): StackOption =
        if (isEmpty) alternative() else this

    override fun iterator(): Iterator<ItemStack> =
        if (isEmpty) emptyList<ItemStack>().iterator() else listOf(get()).iterator()

    fun toList(): List<ItemStack> =
        if (isEmpty) emptyList() else listOf(get())

    fun <X> toRight(left: () -> X): Either<X, ItemStack> =
        if (isEmpty) Either.Left(left()) else Either.Right(get())

    fun <X> toLeft(right: () -> X): Either<ItemStack, X> =
        if (isEmpty) Either.Right(right()) else Either.Left(get())

    companion object {
        @JvmStatic
        @JvmName("apply")
        operator fun invoke(stack: ItemStack?): StackOption =
            if (stack == null || stack.isEmpty) EmptyStack else SomeStack(stack)

        @JvmStatic
        fun empty(): StackOption = EmptyStack
    }
}

object EmptyStack : StackOption() {
    override val isEmpty: Boolean = true
    override fun get(): ItemStack = ItemStack.EMPTY
}

data class SomeStack(val stack: ItemStack) : StackOption() {
    override val isEmpty: Boolean = stack.isEmpty
    override fun get(): ItemStack = stack
}

// Simple Either implementation for compatibility
sealed class Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>()
    data class Right<R>(val value: R) : Either<Nothing, R>()
}

fun ItemStack?.asStackOption(): StackOption = StackOption(this)
fun ItemStack.asStackOption(): StackOption = SomeStack(this)

fun ItemStack?.notEmpty(): ItemStack? = if (this == null || this.isEmpty) null else this
fun ItemStack.notEmpty(): ItemStack? = if (this.isEmpty) null else this
