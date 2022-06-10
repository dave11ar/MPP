import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val array = Array(size) {Ref(initialValue)}

    fun get(index: Int) = array[index].get()

    fun set(index: Int, value: E) {
        array[index].set(value)
    }

    fun cas(index: Int, expected: E, update: E) = array[index].cas(expected, update)

    fun cas2(indexA: Int, expectA: E, updateA: E,
             indexB: Int, expectB: E, updateB: E): Boolean {
        if (indexA == indexB) {
            if (expectA != expectB) return false
            return cas(indexA, expectA, updateB)
        }

        val a = array[indexA]
        val b = array[indexB]
        val descriptor: DescriptorCAS2
        if (indexA > indexB) {
            descriptor = DescriptorCAS2(a, expectA, updateA, b, expectB, updateB)
            if (!a.cas(expectA, descriptor)) return false
        } else {
            descriptor = DescriptorCAS2(b, expectB, updateB, a, expectA, updateA)
            if (!b.cas(expectB, descriptor)) return false
        }

        return getDescriptorRes(descriptor)
    }

    private fun dcss(a: Ref, expectA: Any?, updateA: Any?,
                     b: Ref, expectB: Any?) : Boolean {
        val descriptor = DescriptorDCSS(a, expectA, updateA, b, expectB)
        if (!a.cas(expectA, descriptor)) return false

        return getDescriptorRes(descriptor)
    }

    private fun getDescriptorRes(descriptor: Descriptor): Boolean{
        descriptor.complete()
        return descriptor.consensus.get()!! as Boolean
    }

    private abstract inner class Descriptor {
        val consensus = Ref(null)
        abstract fun complete()
    }

    private inner class DescriptorDCSS(val a: Ref, val expectA: Any?, val updateA: Any?,
                                       val b: Ref, val expectB: Any?) : Descriptor() {
        override fun complete() {
            consensus.v.compareAndSet(null, b.get() == expectB)
            if (consensus.v.value as Boolean) {
                a.v.compareAndSet(this, updateA)
            } else {
                a.v.compareAndSet(this, expectA)
            }
        }
    }

    private inner class DescriptorCAS2(val a: Ref, val expectA: Any?, val updateA: Any?,
                                       val b: Ref, val expectB: Any?, val updateB: Any?) : Descriptor() {
        override fun complete() {
            val res = b.v.value == this || dcss(b, expectB, this, consensus, null)
            consensus.v.compareAndSet(null, res)
            if (consensus.v.value as Boolean) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                a.v.compareAndSet(this, expectA)
                b.v.compareAndSet(this, expectB)
            }
        }
    }

    private inner class Ref(initial: Any?) {
        val v = atomic(initial)

        fun get(): Any? {
            v.loop {
                if (it is AtomicArray<*>.Descriptor) it.complete() else return it
            }
        }

        fun set(update: E) {
            v.loop {
                if (it is AtomicArray<*>.Descriptor) it.complete() else if (v.compareAndSet(it, update)) return
            }
        }

        fun cas(expected: Any?, update: Any?): Boolean {
            while (true) {
                if (v.compareAndSet(expected, update)) return true

                val curValue = v.value
                if (curValue is AtomicArray<*>.Descriptor) {
                    curValue.complete()
                } else {
                    if (curValue != expected) return false
                }
            }
        }
    }
}