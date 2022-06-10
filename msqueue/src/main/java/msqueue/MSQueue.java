package msqueue;

import kotlinx.atomicfu.AtomicRef;

public class MSQueue implements Queue {
    private AtomicRef<Node> head;
    private AtomicRef<Node> tail;

    public MSQueue() {
        Node dummy = new Node(0);
        this.head = new AtomicRef<>(dummy);
        this.tail = new AtomicRef<>(dummy);
    }

    @Override
    public void enqueue(int x) {
        Node newTail = new Node(x);

        while (true) {
            Node curTail = tail.getValue();
            Node next = curTail.next.getValue();

            if (curTail == tail.getValue()) {
                if (next == null) {
                    if (curTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(curTail, newTail);
                        return;
                    }
                } else {
                    tail.compareAndSet(curTail, next);
                }
            }
        }
    }

    @Override
    public int dequeue() {
        while (true) {
            Node curHead = head.getValue();
            Node curTail = tail.getValue();
            Node next = curHead.next.getValue();

            if (curHead == head.getValue()) {
                if (curHead == curTail) {
                    if (next == null) {
                        return Integer.MIN_VALUE;
                    }

                    tail.compareAndSet(curTail, next);
                } else {
                    int val = next.x;

                    if (head.compareAndSet(curHead, next)) {
                        return val;
                    }
                }
            }
        }
    }

    @Override
    public int peek() {
        AtomicRef<Node> next = head.getValue().next;

        if (next.getValue() == null) {
            return Integer.MIN_VALUE;
        } else {
            return next.getValue().x;
        }
    }

    private class Node {
        final int x;
        AtomicRef<Node> next;

        Node(int x) {
            this.x = x;
            this.next = new AtomicRef<>(null);
        }
    }
}