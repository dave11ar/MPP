import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * Int-to-Int hash map with open addressing and linear probes.
 */
class IntIntHashMap {
    private val core: AtomicRef<Core> = atomic(Core(INITIAL_CAPACITY))

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.value.getInternal(key))
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     * [Integer.MAX_VALUE] which is reserved.
     */
    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, REMOVED_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val curCore = core.value
            val oldValue = curCore.putInternal(key, value)
            if (oldValue != NEEDS_REHASH) return oldValue

            core.compareAndSet(curCore, curCore.rehash())
        }
    }

    private class Core(capacity: Int) {
        // Pairs of <key, value> here, the actual
        // size of the map is twice as big.
        private val map: AtomicIntArray = AtomicIntArray(2 * capacity)
        private val shift: Int
        private val next: AtomicRef<Core> = atomic(this)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) {
                if (map[index].value == NULL_KEY) return NULL_VALUE

                if (++probes >= MAX_PROBES) return NULL_VALUE
                index = nextIndex(index)
            }

            val oldValue = map[index + 1].value
            if (oldValue == DEL_VALUE) return next.value.getInternal(key)

            return if (isNeedToBeMoved(oldValue)) getFromNeedToBeMoved(oldValue) else oldValue
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0
            while (map[index].value != key) {
                map[index].compareAndSet(NULL_KEY, key)
                if (map[index].value == key) break

                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                index = nextIndex(index)
            }

            while (true) {
                when (val oldValue = map[index + 1].value) {
                    DEL_VALUE -> return next.value.putInternal(key, value)
                    else -> {
                        if (isNeedToBeMoved(oldValue)) {
                            next.value.helpMove(key, getFromNeedToBeMoved(oldValue))
                            map[index + 1].compareAndSet(oldValue, DEL_VALUE)
                            continue
                        }

                        if (map[index + 1].compareAndSet(oldValue, value)) return oldValue
                    }
                }
            }
        }

        fun rehash(): Core {
            if (next.value == this) next.compareAndSet(this, Core(map.size))

            for (index in 0 until map.size step 2) {
                while (true) {
                    when (val oldValue = map[index + 1].value) {
                        DEL_VALUE -> break
                        NULL_VALUE, REMOVED_VALUE -> if (map[index + 1].compareAndSet(oldValue, DEL_VALUE)) break
                        else -> {
                            if (isNeedToBeMoved(oldValue)) {
                                next.value.helpMove(map[index].value, getFromNeedToBeMoved(oldValue))
                                map[index + 1].compareAndSet(oldValue, DEL_VALUE)
                                continue
                            }

                            map[index + 1].compareAndSet(oldValue, needToBeMoved(oldValue))
                        }
                    }
                }
            }
            return next.value
        }

        private fun helpMove(key: Int, value: Int) {
            var index = index(key)
            while (true) {
                map[index].compareAndSet(NULL_KEY, key)
                if (map[index].value == key) {
                    map[index + 1].compareAndSet(NULL_VALUE, value)
                    return
                }

                index = nextIndex(index)
            }
        }

        private fun nextIndex(index: Int): Int  = (if (index == 0) map.size else index) - 2
        /**
         * Returns an initial index in map to look for a given key.
         */
        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647 // golden ratio
private const val INITIAL_CAPACITY = 2 // !!! DO NOT CHANGE INITIAL CAPACITY !!!
private const val MAX_PROBES = 8 // max number of probes to find an item
private const val NULL_KEY = 0 // missing key (initial value)
private const val NULL_VALUE = 0 // missing value (initial value)
private const val REMOVED_VALUE = Int.MIN_VALUE + 1
private const val DEL_VALUE = Int.MAX_VALUE // mark for removed value
private const val NEEDS_REHASH = Int.MIN_VALUE // returned by `putInternal` to indicate that rehash is needed

// Checks is the value is in the range of allowed values
private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

// Converts internal value to the public results of the methods
private fun toValue(value: Int): Int = if (isValue(value)) value else 0

private fun needToBeMoved(value: Int): Int = -value

private fun isNeedToBeMoved(value: Int): Boolean = -Int.MAX_VALUE < value && value < 0

private fun getFromNeedToBeMoved(value: Int): Int = -value