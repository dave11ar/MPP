import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class SkipListSet<E : Comparable<E>>(
    infinityLeft: E, infinityRight: E
) {
    private val head: Node<E>
    private val tail: NodeI<E>

    init {
        head = Node(infinityLeft, MAX_LEVEL + 1)
        tail = Node(infinityRight, MAX_LEVEL + 1)
        for (i in 0..MAX_LEVEL) head.next[i].value = tail
    }

    /**
     * Adds the specified [element] to this set if it is not already present.
     * Returns `true` if the [element] was not present, `false` otherwise.
     */
    fun add(element: E): Boolean {
        val topLevel = randomLevel()

        while (true) {
            var window = findWindow(element)
            if (window.found) return false

            val newNode = Node(element, topLevel)
            for (level in 0..topLevel) {
                val succ = window.succs[level].value
                newNode.next[level].value = succ
            }


            var pred = window.preds[0].value
            var succ = window.succs[0].value
            if (!getNode(pred).next[0].compareAndSet(succ, newNode)) continue

            for (level in 1..topLevel) {
                while (true) {
                    pred = window.preds[level].value
                    succ = window.succs[level].value
                    if (getNode(pred).next[level].compareAndSet(succ, newNode)) break
                    window = findWindow(element)
                }
            }
            return true
        }
    }


    /**
     * Removes the specified [element] from this set.
     * Returns `true` if the [element] was presented in this set,
     * `false` otherwise.
     */
    fun remove(element: E): Boolean {
        while (true) {
            val window = findWindow(element)
            if (!window.found) return false

            val nodeToRemove = window.succs[0].value as Node
            for (level in nodeToRemove.topLevel downTo 1) {
                while (true) {
                    val succ = nodeToRemove.next[level].value!!
                    if (succ is Node) {
                        if (nodeToRemove.next[level].compareAndSet(succ, Removed(succ))) break
                    } else {
                        break
                    }
                }
            }

            val succ = nodeToRemove.next[0].value
            if (succ is Node) {
                if (nodeToRemove.next[0].compareAndSet(succ, Removed(succ))) {
                    findWindow(element)
                    return true
                }
            } else return false
        }
    }

    /**
     * Returns `true` if this set contains the specified [element].
     */
    fun contains(element: E): Boolean {
        var pred: NodeI<E> = head
        var curr: NodeI<E> = head
        var succ: NodeI<E>?

        for (level in MAX_LEVEL downTo 0) {
            curr = getNode(pred).next[level].value!!
            while (true) {
                succ = getNode(curr).next[level].value
                while (succ is Removed) {
                    curr = succ
                    succ = getNode(curr).next[level].value
                }

                if (getNode(curr).element == element) return true
                if (getNode(curr).element < element) {
                    pred = curr
                    curr = succ!!
                } else break
            }
        }

        return getNode(curr).element == element
    }

    /**
     * Returns the [Window], where
     * `preds[l].x < x <= succs[l].x`
     * for every level `l`
     */
    private fun findWindow(element: E): Window<E> {
        retry@
        while (true) {
            val window = Window<E>()
            var pred: Node<E> = head
            var curr: Node<E> = pred.next[MAX_LEVEL].value as Node

            for (level in MAX_LEVEL downTo 0) {
                val maybeCurr = pred.next[level].value
                if (maybeCurr is Node) {
                    curr = maybeCurr
                } else continue@retry

                while (curr.element < element) {
                    val succ = curr.next[level].value

                    if (succ is Node) {
                        pred = curr
                        curr = succ
                    } else {
                        val succNode = getNode(succ)
                        if (!pred.next[level].compareAndSet(curr, succNode)) continue@retry
                        curr = succNode
                    }
                }

                var succ = curr.next[level].value
                while (succ is Removed) {
                    val succNode = getNode(succ)
                    if (!pred.next[level].compareAndSet(curr, succNode)) continue@retry
                    curr = succNode
                    succ = curr.next[level].value
                }

                window.preds[level].value = pred
                window.succs[level].value = curr
            }

            if (curr.element == element) window.levelFound = MAX_LEVEL
            return window
        }
    }

    private fun getNode(node: NodeI<E>?): Node<E> {
        return if (node is Node) node else (node as Removed).node
    }
}

private interface NodeI<E>

private class Node<E>(val element: E, val topLevel: Int): NodeI<E> {
    val next = atomicArrayOfNulls<NodeI<E>?>(topLevel + 1)
}
private class Removed<E>(val node: Node<E>): NodeI<E>

private class Window<E> {
    var levelFound = -1 // -1 if not found
    val preds = atomicArrayOfNulls<NodeI<E>>(MAX_LEVEL + 1)
    val succs = atomicArrayOfNulls<NodeI<E>>(MAX_LEVEL + 1)

    val found get() = levelFound != -1
}

private fun randomLevel(): Int = Random.nextInt(MAX_LEVEL)

private const val MAX_LEVEL = 30
