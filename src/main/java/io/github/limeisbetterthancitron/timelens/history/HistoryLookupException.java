package io.github.limeisbetterthancitron.timelens.history;

import java.io.Serial;

/**
 * Thrown when a history backend could not answer a query.
 *
 * <p>Carries technical detail for the console. Players are shown a generic failure message
 * instead, because the cause is usually a server-side problem they cannot act on.
 */
public class HistoryLookupException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public HistoryLookupException(String message) {
        super(message);
    }

    public HistoryLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
