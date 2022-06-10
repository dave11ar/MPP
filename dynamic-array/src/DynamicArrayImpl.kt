import kotlinx.atomicfu.*
import java.lang.IllegalArgumentException

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core: AtomicRef<Core<E>> = atomic(Core(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E {
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
        return core.value.put(index, element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            if (curCore.pushBack(element)) return

            core.compareAndSet(curCore, curCore.move())
        }
    }

    override val size: Int get() {
        return core.value.size
    }
}

private class Core<E>(capacity: Int, curSize: Int) {
    private val array: AtomicArray<Node<E>?> = atomicArrayOfNulls(capacity)
    private val next: AtomicRef<Core<E>> = atomic(this)
    private val size_: AtomicInt = atomic(curSize)

    fun get(index: Int): E {
        checkIndex(index)

        return when (val oldValue = array[index].value!!) {
            is Common<E> -> oldValue.value
            is Moving<E> -> oldValue.value
            else -> next.value.get(index)
        }
    }

    fun put(index: Int, value: E) {
        checkIndex(index)

        while (true) {
            when (val oldValue = array[index].value!!) {
                is Common<E> -> {
                    if (array[index].compareAndSet(oldValue, Common(value))) break
                }
                is Moving<E> -> {
                    next.value.array[index].compareAndSet(null, Common(oldValue.value))
                    array[index].compareAndSet(oldValue, Moved())
                    continue
                }
                else -> {
                    next.value.put(index, value)
                    break
                }
            }
        }
    }

    fun pushBack(value: E) : Boolean {
        while (true) {
            val oldSize = size
            if (oldSize == array.size) return false

            if (array[oldSize].compareAndSet(null, Common(value))) {
                size_.compareAndSet(oldSize, oldSize + 1)
                return true
            }
            size_.compareAndSet(oldSize, oldSize + 1)
        }
    }

    val size: Int get() {
        return size_.value
    }

    fun move(): Core<E> {
        if (next.value == this) next.compareAndSet(this, Core(array.size * 2, size))

        for(index in 0 until size) {
            while (true) {
                when (val oldValue = array[index].value!!) {
                    is Common<E> -> {
                        array[index].compareAndSet(oldValue, Moving(oldValue.value))
                    }
                    is Moving<E> -> {
                        next.value.array[index].compareAndSet(null, Common(oldValue.value))
                        array[index].compareAndSet(oldValue, Moved())
                        continue
                    }
                    else -> {
                        break
                    }
                }
            }
        }
        return next.value
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= size) throw IllegalArgumentException("index out of bound")
    }
}

private interface Node<E>

private class Common<E>(val value: E): Node<E>
private class Moving<E>(val value: E): Node<E>
private class Moved<E>: Node<E>

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME