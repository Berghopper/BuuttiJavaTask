package buutti.buffer.util;

import buutti.buffer.exceptions.SyncBufferSupplierExhaustedException;
import buutti.buffer.exceptions.SyncBufferTimeoutException;

import java.io.Closeable;
import java.io.IOException;

public class SyncBufferImpl<T> extends AbstractSyncBuffer<T> implements Closeable {
    /**
     * The object array buffer of N size.
     */
    private final T[] buffer;

    /**
     * ioTimeout, time to wait in millis for supply/consume operation. -1 = No timeout.
     */
    private final long ioTimeout;

    /**
     * Positional and size counters for navigating the buffer.
     */
    private int putPosition, takePosition, itemsInBuffer;

    private boolean supplierIsExhausted = false;

    /**
     * @param size Maximum size of the buffer for N generic objects
     * @param ioTimeout ioTimeout, time to wait in millis for supply/consume operation. -1 = No timeout.
     */
    public SyncBufferImpl(int size, long ioTimeout) {
        buffer = (T[]) new Object[size];
        this.ioTimeout = ioTimeout;
    }

    @Override
    public synchronized void supply(final T object) throws InterruptedException, IOException {
        if (isSupplierIsExhausted()) {
            throw new SyncBufferSupplierExhaustedException();
        }
        if (ioTimeout < 0L) {
            while (isFull()) {
                wait();
            }
        } else {
            if (isFull()) {
                wait(ioTimeout);
            }
            if (isFull()) {
                throw new SyncBufferTimeoutException();
            }
        }
        doSupply(object);
        notifyAll();
    }

    @Override
    public synchronized T consume() throws IOException, InterruptedException {
        if (ioTimeout < 0L) {
            while (isEmpty() && !supplierIsExhausted) {
                wait();
            }
            if (supplierIsExhausted && isEmpty()) {
                throw new SyncBufferSupplierExhaustedException();
            }
        } else {
            if (isEmpty() && !supplierIsExhausted) {
                wait(ioTimeout);
            }
            if (supplierIsExhausted && isEmpty()) {
                throw new SyncBufferSupplierExhaustedException();
            } else if (isEmpty()) {
                throw new SyncBufferTimeoutException();
            }
        }
        T element = doConsume();
        notifyAll();
        return element;
    }

    @Override
    public synchronized boolean isFull() {
        return itemsInBuffer == buffer.length;
    }

    @Override
    public synchronized boolean isEmpty() {
        return itemsInBuffer == 0;
    }

    @Override
    public synchronized boolean isSupplierIsExhausted() {
        return supplierIsExhausted;
    }

    @Override
    public synchronized void supplierIsExhausted() {
        if (!isSupplierIsExhausted()) {
            this.supplierIsExhausted = true;
            notifyAll();
        }
    }

    /**
     * Supply the buffer with a Generic object.
     * @param object T
     */
    private synchronized void doSupply(final T object) {
        buffer[putPosition] = object;
        if (++putPosition == buffer.length) {
            putPosition = 0;
        }
        ++itemsInBuffer;
    }

    /**
     * Consume a Generic object from the buffer
     * @return T
     */
    private synchronized T doConsume() {
        T element = buffer[takePosition];
        if (++takePosition == buffer.length) {
            takePosition = 0;
        }
        --itemsInBuffer;
        return element;
    }
}
