/**
 * @author :TODO: Davydov Artyom
 */
public class Solution implements AtomicCounter {
    // объявите здесь нужные вам поля
    final Node root = new Node(0);
    final ThreadLocal<Node> last = ThreadLocal.withInitial(() -> root);

    public int getAndAdd(int x) {
        // напишите здесь код
        while (true){
            final int oldVal = last.get().val;
            final int updVal = oldVal + x;
            final Node node = new Node(updVal);
            last.set(last.get().next.decide(node));

            if (last.get() == node) {
                return oldVal;
            }
        }
    }

    // вам наверняка потребуется дополнительный класс
    private static class Node {
        final int val;
        final Consensus<Node> next;

        Node(final int val) {
            this.val = val;
            this.next = new Consensus<>();
        }
    }
}
