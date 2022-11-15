package buutti.buffer.interfaces;

import buutti.buffer.exceptions.SyncBufferTimeoutException;

import java.io.IOException;

public interface SyncBuffer<T> {
    /**
     * The exhausted supplier flag. Used to prevent endless waiting.
     * Consumer does not know when to stop otherwise if no timeout is set.
     */
    boolean supplierIsExhausted = false;

    /**
     * Supply generic Object into buffer
     * @param object Generic Object T
     * @exception SyncBufferTimeoutException on supply timeout, when buffer is full.
     */
    void supply(T object) throws InterruptedException, IOException;

    /**
     * Consume the first generic Object that was supply in the buffer
     * @exception SyncBufferTimeoutException on supply timeout, when buffer is empty.
     * @return Generic Object T
     */
    T consume() throws InterruptedException, IOException;

    /**
     * Checks whether the buffer is full.
     * @return boolean
     */
    boolean isFull();

    /**
     * Checks whether the buffer is empty.
     * @return boolean
     */
    boolean isEmpty();

    /**
     * Checks whether the supplier of the buffer is exhausted.
     *
     * @return boolean
     */
    default boolean isSupplierIsExhausted() {
        return supplierIsExhausted;
    }

    /**
     * Set the exhausted flag of the buffer to true.
     */
    void supplierIsExhausted();
}
