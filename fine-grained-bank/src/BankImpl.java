/**
 * Bank implementation.
 *
 * <p>:TODO: This implementation has to be made thread-safe.
 *
 * @author :TODO: Davydov Artyom
 */
import java.util.concurrent.locks.ReentrantLock;

public class BankImpl implements Bank {
    /**
     * An array of accounts by index.
     */
    private final Account[] accounts;
    /**
     * Creates new bank instance.
     * @param n the number of accounts (numbered from 0 to n-1).
     */
    public BankImpl(int n) {
        accounts = new Account[n];
        for (int i = 0; i < n; i++) {
            accounts[i] = new Account();
        }
    }

    @Override
    public int getNumberOfAccounts() {
        return accounts.length;
    }

    /**
     * <p>:TODO: This method has to be made thread-safe.
     */
    @Override
    public long getAmount(int index) {
        accounts[index].lock();
        final long result = accounts[index].amount;
        accounts[index].unlock();
        return result;
    }

    /**
     * <p>:TODO: This method has to be made thread-safe.
     */
    @Override
    public long getTotalAmount() {
        long sum = 0;
        for (Account account : accounts) {
            account.lock();
            sum += account.amount;
        }
        for (int i = accounts.length - 1; i >= 0; --i) {
            accounts[i].unlock();
        }
        return sum;
    }

    /**
     * <p>:TODO: This method has to be made thread-safe.
     */
    @Override
    public long deposit(int index, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        Account account = accounts[index];
        account.lock();
        if (amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT) {
            account.unlock();
            throw new IllegalStateException("Overflow");
        }
        account.amount += amount;
        final long result = account.amount;
        account.unlock();
        return result;
    }

    /**
     * <p>:TODO: This method has to be made thread-safe.
     */
    @Override
    public long withdraw(int index, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        Account account = accounts[index];
        account.lock();
        if (account.amount - amount < 0) {
            account.unlock();
            throw new IllegalStateException("Underflow");
        }
        account.amount -= amount;
        final long result = account.amount;
        account.unlock();
        return result;
    }

    /**
     * <p>:TODO: This method has to be made thread-safe.
     */
    @Override
    public void transfer(int fromIndex, int toIndex, long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Invalid amount: " + amount);
        if (fromIndex == toIndex)
            throw new IllegalArgumentException("fromIndex == toIndex");
        Account from = accounts[fromIndex];
        Account to = accounts[toIndex];

        Account firstLock = accounts[Math.min(fromIndex, toIndex)];
        Account secondLock = accounts[Math.max(fromIndex, toIndex)];

        firstLock.lock();
        secondLock.lock();

        try {
            if (amount > from.amount) {
                throw new IllegalStateException("Underflow");
            } else if (amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT) {
                throw new IllegalStateException("Overflow");
            }

            from.amount -= amount;
            to.amount += amount;
        } finally {
            secondLock.unlock();
            firstLock.unlock();
        }
    }

    /**
     * Private account data structure.
     */
    static class Account {
        /**
         * Amount of funds in this account.
         */
        long amount;

        private final ReentrantLock lock = new ReentrantLock();

        public void lock() {
            lock.lock();
        }

        public void unlock() {
            lock.unlock();
        }
    }
}
