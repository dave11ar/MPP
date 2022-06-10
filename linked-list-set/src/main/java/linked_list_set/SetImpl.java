package linked_list_set;

import kotlinx.atomicfu.AtomicRef;

public class SetImpl implements Set {
    private interface NodeI {}

    private static class Node implements NodeI{
        final int key;
        final AtomicRef<NodeI> next;

        protected Node(final int key, final NodeI next) {
            this.key = key;
            this.next = new AtomicRef<>(next);
        }
    }

    private static class Removed implements NodeI{
        final Node node;

        Removed(final Node node) {
            this.node = node;
        }
    }

    private static class Window {
        public final Node cur, next, nextN;

        Window(final Node cur, final Node next, final Node nextN) {
            this.cur = cur;
            this.next = next;
            this.nextN = nextN;
        }
    }

    private final Node head = new Node(Integer.MIN_VALUE, new Node(Integer.MAX_VALUE, null));

    /**
     * Returns the {@link Window}, where cur.x < x <= next.x and nextN is a next.next
     */
    private Window findWindow(final int key) {
        retry : while (true) {
            Node cur = head, next = (Node) cur.next.getValue();

            while (next.key < key) {
                final NodeI nextN = next.next.getValue();
                if (nextN instanceof Removed) {
                    final Node nextNode = ((Removed) nextN).node;

                    if (!cur.next.compareAndSet(next, nextNode)) {
                        continue retry;
                    }
                    next = nextNode;
                } else {
                    cur = next;
                    next = (Node) nextN;
                }
            }

            final NodeI nextN = next.next.getValue();
            if (nextN instanceof Removed) {
                cur.next.compareAndSet(next, ((Removed) nextN).node);
                continue;
            }

            return new Window(cur, next, (Node) nextN);
        }
    }

    @Override
    public boolean add(final int key) {
        while (true) {
            final Window w = findWindow(key);

            if (w.next.key == key) {
                return false;
            }

            if (w.cur.next.compareAndSet(w.next, new Node(key, w.next))) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(final int key) {
        while (true) {
            final Window w = findWindow(key);

            if (w.next.key != key) {
                return false;
            }

            if (w.next.next.compareAndSet(w.nextN, new Removed(w.nextN))) {
                w.cur.next.compareAndSet(w.next, w.nextN);
                return true;
            }
        }
    }

    @Override
    public boolean contains(int key) {
        final Window w = findWindow(key);
        return w.next.key == key && !(w.next.next.getValue() instanceof Removed);
    }
}