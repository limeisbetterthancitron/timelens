package io.github.limeisbetterthancitron.timelens.history;

import java.io.Serial;

/**
 * Thrown when a query matched more recorded changes than TimeLens is willing to reconstruct.
 *
 * <p>Unlike a general lookup failure this is actionable by the player: a shorter time range or
 * a smaller radius will bring the result set back under the limit.
 */
public class HistoryResultLimitException extends HistoryLookupException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int matched;
    private final int limit;

    public HistoryResultLimitException(int matched, int limit) {
        super("Lookup matched " + matched + " changes, which exceeds the configured limit of " + limit);
        this.matched = matched;
        this.limit = limit;
    }

    public int matched() {
        return matched;
    }

    public int limit() {
        return limit;
    }
}
