package buutti.buffer.util;

import buutti.buffer.interfaces.SyncBuffer;

import java.io.Closeable;

/**
 * Abstract syncBuffer, implements closable for easier use with try-resource.
 * @param <T>
 */
public abstract class AbstractSyncBuffer<T> implements SyncBuffer<T>, Closeable {
    boolean supplierIsExhausted = false;

    @Override
    public void supplierIsExhausted() {
        supplierIsExhausted = true;
    }

    @Override
    public void close() {
        supplierIsExhausted();
    }
}
