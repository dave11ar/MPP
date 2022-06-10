import java.util.concurrent.atomic.*

class Solution(val env: Environment) : Lock<Solution.Node> {
    val tail = AtomicReference<Node?>(null)

    override fun lock(): Node {
        val my = Node()
        val pred = tail.getAndSet(my)
        if (pred != null) {
            pred.next.set(my)
            while (my.locked.get()) env.park()
        }

        return my
    }

    override fun unlock(node: Node) {
        if (node.next.get() == null) {
            if (tail.compareAndSet(node, null)) return
            while (node.next.get() == null) {}
        }

        val next = node.next.get()!!
        next.locked.set(false)
        env.unpark(next.thread)
    }

    class Node {
        val locked = AtomicReference(true)
        val next = AtomicReference<Node?>(null)
        val thread = Thread.currentThread()
    }
}