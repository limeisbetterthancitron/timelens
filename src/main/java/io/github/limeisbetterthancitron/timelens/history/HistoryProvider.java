package io.github.limeisbetterthancitron.timelens.history;

import java.util.List;

/**
 * A source of recorded block changes.
 *
 * <p>This is the only seam that knows where history comes from. CoreProtect is the single
 * implementation in v0.1.0; keeping the rest of TimeLens behind this interface is what allows
 * another backend to be added later without touching reconstruction, sessions or rendering.
 */
public interface HistoryProvider {

    /**
     * Returns every recorded block change inside the query's bounds, newest first.
     *
     * <p>Called on a worker thread — implementations must not touch Bukkit world state.
     *
     * @throws HistoryResultLimitException if more changes matched than the query allows
     * @throws HistoryLookupException      if the backend could not be queried
     */
    List<HistoryEvent> lookup(HistoryQuery query) throws HistoryLookupException;

    /**
     * A short human-readable name for the backend, used in startup logging.
     */
    String backendDescription();
}
