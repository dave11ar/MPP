import kotlinx.atomicfu.*

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val curTail = tail.value
            val enqIdx = curTail.enqIdx.getAndIncrement()

            if (enqIdx >= SEGMENT_SIZE) {
                val newTail = Segment(x)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                    return
                }

                val nextTail = curTail.next.value ?: continue // null is impossible
                tail.compareAndSet(curTail, nextTail)
            } else {
                if (curTail.elements[enqIdx].compareAndSet(null, x)) return
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val curHead = head.value
            val deqInx = curHead.deqIdx.getAndIncrement()

            if (deqInx >= SEGMENT_SIZE) {
                val newHead = curHead.next.value ?: return null
                head.compareAndSet(curHead, newHead)
                continue
            }

            val res = curHead.elements[deqInx].getAndSet(DONE)
            if (res != null) return res as T?
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean get() {
        while (true) {
            val curHead = head.value
            if (curHead.isEmpty) {
                if (curHead.next.value == null) return true

                val nextHead = curHead.next.value ?: continue // null is impossible
                head.compareAndSet(curHead, nextHead)
                continue
            }
            return false
        }
    }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx: AtomicInt = atomic(0) // index for the next enqueue operation
    val deqIdx: AtomicInt = atomic(0) // index for the next dequeue operation
    val elements: AtomicArray<Any?> = atomicArrayOfNulls(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.getAndIncrement()
        elements[0].compareAndSet(null, x)
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE
}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
