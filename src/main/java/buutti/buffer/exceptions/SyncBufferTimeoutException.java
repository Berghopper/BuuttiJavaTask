package buutti.buffer.exceptions;

import java.io.IOException;

/**
 * SyncBufferTimeoutException, thrown when the syncBuffer is timed out by a wait. This occurs when an IO operation could not be completed
 */
public class SyncBufferTimeoutException extends IOException {
}
