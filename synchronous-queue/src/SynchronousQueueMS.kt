import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SynchronousQueueMS<E> : SynchronousQueue<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override suspend fun send(element: E) {
        while (true) {
            val curHead = head.value
            val curTail = tail.value

            if (curHead === curTail || curTail is Sender) { // enqueue
                val res = sus<Unit>(curTail) {Sender(element, it)}
                if (res != null) return
            } else {                                        // dequeue
                val next = curHead.next.value as? Receiver ?: continue

                if (head.compareAndSet(curHead, next)) {
                    next.cont.resume(element)
                    return
                }
            }
        }
    }

    override suspend fun receive(): E {
        while (true) {
            val curHead = head.value
            val curTail = tail.value

            if (curHead === curTail || curTail is Receiver) { // enqueue
                val res = sus<E?>(curTail) {Receiver(it)}
                if (res != null) return res
            } else {                                          // dequeue
                val next = curHead.next.value as? Sender ?: continue

                if (head.compareAndSet(curHead, next)) {
                    next.cont.resume(Unit)
                    return next.element
                }
            }
        }
    }

    private suspend fun <T> sus(curTail: Node, constr: (Continuation<T>) -> Node) : T? {
        return suspendCoroutine sc@ {
            val next = curTail.next.value
            if (next == null) {
                val newTail = constr(it)
                if (curTail.next.compareAndSet(null, newTail)) {
                    tail.compareAndSet(curTail, newTail)
                    return@sc
                }
            } else {
                tail.compareAndSet(curTail, next)
            }

            it.resume(null)
            return@sc
        }
    }

    private open inner class Node(val next: AtomicRef<Node?> = atomic(null))
    private inner class Sender(val element: E, val cont: Continuation<Unit>) : Node()
    private inner class Receiver(val cont: Continuation<E>) : Node()
}
