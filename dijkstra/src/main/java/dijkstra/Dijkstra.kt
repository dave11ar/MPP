package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> Integer.compare(o1!!.distance, o2!!.distance) }

class MultiQueue(threadAmount: Int, nodeComparator: Comparator<Node>) {
    private val comparator: Comparator<Node>
    private val locks: ArrayList<ReentrantLock> = ArrayList()
    private val multiQueue: ArrayList<PriorityQueue<Node>> = ArrayList()

    init {
        comparator = nodeComparator
        for (i in 0..threadAmount * 2) {
            locks.add(ReentrantLock())
            multiQueue.add(PriorityQueue(comparator))
        }
    }

    private fun rand(): Int {
        return ThreadLocalRandom.current().nextInt(multiQueue.size)
    }

    private fun min(queue0: PriorityQueue<Node>, queue1:  PriorityQueue<Node>): Node? {
        val node0 = queue0.peek()
        val node1 = queue1.peek()

        return if (node0 == null && node1 == null) {
            null
        } else if (node0 == null) {
            queue1.poll()
        } else if (node1 == null) {
            queue0.poll()
        } else if (comparator.compare(node0, node1) <= 0) queue0.poll() else queue1.poll()
    }

    fun add(node: Node) {
        while (true){
            val index = rand()

            if (locks[index].tryLock()){
                multiQueue[index].add(node)
                locks[index].unlock()
                break
            }
        }
    }

    fun extractMin(): Node? {
        while (true) {
            val index0 = rand()
            if (locks[index0].tryLock()) {
                while (true) {
                    val index1 = rand()
                    if (index0 != index1 && locks[index1].tryLock()) {
                        val result = min(multiQueue[index0], multiQueue[index1])
                        locks[index1].unlock()
                        locks[index0].unlock()

                        return result
                    }
                }
            }
        }
    }
}

// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    // The distance to the start node is `0`
    start.distance = 0
    // Create a priority (by distance) queue and add the start node into it
    val queue = MultiQueue(workers, NODE_DISTANCE_COMPARATOR) // TODO replace me with a multi-queue based PQ!
    queue.add(start)
    // Run worker threads and wait until the total work is done
    val onFinish = Phaser(workers + 1) // `arrive()` should be invoked at the end by each worker

    val activeNodes = AtomicInteger(1)
    repeat(workers) {
        thread {
            while (activeNodes.get() > 0) {
                // TODO Write the required algorithm here,
                // TODO break from this loop when there is no more node to process.
                // TODO Be careful, "empty queue" != "all nodes are processed".
                val curNode = queue.extractMin() ?: continue
                val fromDistance = curNode.distance

                for (edge in curNode.outgoingEdges) {
                    val newDistance = fromDistance + edge.weight

                    while (true) {
                        val toDistance = edge.to.distance

                        if (newDistance >= toDistance) {
                            break
                        }

                        if (edge.to.casDistance(toDistance, newDistance)) {
                            queue.add(edge.to)
                            activeNodes.incrementAndGet()
                            break
                        }
                    }
                }
                activeNodes.decrementAndGet()
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}