import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlinx.atomicfu.atomicArrayOfNulls

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val array = atomicArrayOfNulls<Any?>(ARRAY_SIZE)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return doOperation(Operation.POLL, null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return doOperation(Operation.PEEK, null)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        doOperation(Operation.ADD, element)
    }

    private fun doOperation(operation: Operation, argument: E?): E? {
        var curIndex = -1
        while (true) {
            if (lock.tryLock()) {
                try {
                    if (curIndex != -1) {
                        val request = array[curIndex].value as Request<E>
                        array[curIndex].getAndSet(null)
                        if (request.ready) return request.result
                    }
                    checkArray()

                    var result: E? = null
                    if (operation == Operation.POLL) {
                        result = q.poll()
                    } else if (operation == Operation.PEEK) {
                        result = q.peek()
                    } else {
                        q.add(argument)
                    }

                    return result
                } finally {
                    lock.unlock()
                }
            }

            if (curIndex == -1) {
                for (index in 0 until ARRAY_SIZE) {
                    val request = Request(operation, argument)
                    if (array[index].compareAndSet(null, request)) {
                        curIndex = index
                        break
                    }
                }
            } else {
                val request = array[curIndex].value as Request<E>
                if (request.ready) {
                    array[curIndex].compareAndSet(request, null)
                    return request.result
                }
            }
        }
    }

    private fun checkArray() {
        for (index in 0 until ARRAY_SIZE) {
            val maybeRequest = array[index].value
            if (maybeRequest is Request<*>) {
                val request =  maybeRequest as Request<E>
                if (request.ready) continue

                if (request.operation == Operation.POLL) {
                    request.result = q.poll()
                } else if (request.operation == Operation.PEEK) {
                    request.result = q.peek()
                } else {
                    q.add(request.argument)
                }

                request.ready = true
            }
        }
    }

    private enum class Operation {
        POLL,
        PEEK,
        ADD
    }

    private class Request<E>(val operation: Operation, val argument: E?) {
        var result: E? = null
        var ready: Boolean = false
    }
}

private val ARRAY_SIZE = 32