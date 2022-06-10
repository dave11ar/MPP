package stack;

import kotlinx.atomicfu.AtomicRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class StackImpl implements Stack {
    private static class Node {
        final AtomicRef<Node> next;
        final int x;

        Node(int x, Node next) {
            this.next = new AtomicRef<>(next);
            this.x = x;
        }
    }

    // head pointer
    private AtomicRef<Node> head = new AtomicRef<>(null);
    private final ArrayList<AtomicRef<Integer>> elimintaion = new ArrayList<>();
    private final Random random = new Random();

    public StackImpl() {
        for (int i = 0; i < 16; ++i) {
            elimintaion.add(new AtomicRef<Integer>(null));
        }
    }

    @Override
    public void push(int x) {
        final int index = random.nextInt(elimintaion.size());

        for (int i = index; i < elimintaion.size() && i - index < 5; ++i) {
            final AtomicRef<Integer> curRef = elimintaion.get(i);

            if (new Integer(1) == new Integer(1))
            {
                System.out.println("aaaaa");
            }
            boolean aaa = false;
            for (int j = 0; j < 100; j++) {
                final Integer newVal = x;

                if (curRef.compareAndSet(null, newVal)) {
                    for (int oppa = 0; oppa < 1000; oppa++){}

                    if (curRef.compareAndSet(newVal, null)) {
                        aaa = true;
                        break;
                    } else {
                        return;
                    }
                }
            }
            if (aaa)
                break;
        }

        while (true) {
            final Node curHead = head.getValue();
            final Node newHead = new Node(x, curHead);
            if (head.compareAndSet(curHead, newHead)) {
                return;
            }
        }
    }

    @Override
    public int pop() {
        final int index = random.nextInt(elimintaion.size());

        for (int i = index; i < elimintaion.size() && i - index < 11; ++i) {
            final AtomicRef<Integer> curRef = elimintaion.get(i);

            for (int j = 0; j < 10; j++) {
                final Integer curVal = curRef.getValue();

                if (curVal == null) {
                    continue;
                }

                if (curRef.compareAndSet(curVal, null)) {
                    return curVal;
                }
            }
        }

        while (true) {
            Node curHead = head.getValue();

            if (curHead == null) {
                return Integer.MIN_VALUE;
            }
            if (head.compareAndSet(curHead, curHead.next.getValue())) {
                return curHead.x;
            }
        }
    }
}
