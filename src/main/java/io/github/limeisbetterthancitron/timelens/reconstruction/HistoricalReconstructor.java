package io.github.limeisbetterthancitron.timelens.reconstruction;

import io.github.limeisbetterthancitron.timelens.history.HistoricalBlockState;
import io.github.limeisbetterthancitron.timelens.history.HistoryEvent;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rebuilds how an area looked at a past moment by undoing everything recorded since then.
 *
 * <p>The engine walks each position's changes from newest to oldest and applies the inverse of
 * each one:
 *
 * <ul>
 *   <li>undoing a placement empties the position, because whatever the placement added was not
 *       there beforehand;</li>
 *   <li>undoing a removal puts back exactly the block that was recorded as removed.</li>
 * </ul>
 *
 * <p>Both inverses overwrite the position outright rather than adjusting it, so the value left
 * after the walk is the one contributed by the <em>oldest</em> change, which is precisely the
 * state the position held just before that change, i.e. at the requested time. A block that was
 * replaced shows up as a removal followed by a placement; undoing the placement clears it and
 * undoing the older removal then restores the block that was replaced.
 *
 * <p>This class is pure: it holds no state, touches no Bukkit API, and is safe to run on a
 * worker thread.
 */
public final class HistoricalReconstructor {

    /**
     * Ties are left in the order the provider supplied. History timestamps have one-second
     * resolution, so a removal and the placement that replaced it routinely share a timestamp
     * and only the backend's own ordering can separate them; {@link List#sort} is stable, so
     * that ordering survives.
     */
    private static final Comparator<HistoryEvent> NEWEST_FIRST =
            Comparator.comparingLong(HistoryEvent::timestampMillis).reversed();

    /**
     * @param targetTimestampMillis the moment to reconstruct, in milliseconds since the epoch
     * @param events                recorded changes, newest first, all from one world
     */
    public HistoricalSnapshot reconstruct(long targetTimestampMillis, List<HistoryEvent> events) {
        Map<BlockPosition, List<HistoryEvent>> byPosition = groupChangesSince(targetTimestampMillis, events);

        Map<BlockPosition, HistoricalBlockState> states = new LinkedHashMap<>(byPosition.size());
        byPosition.forEach((position, changes) -> {
            changes.sort(NEWEST_FIRST);
            reverseToTarget(changes).ifPresent(state -> states.put(position, state));
        });

        return new HistoricalSnapshot(targetTimestampMillis, states);
    }

    private static Map<BlockPosition, List<HistoryEvent>> groupChangesSince(long targetTimestampMillis,
                                                                            List<HistoryEvent> events) {
        Map<BlockPosition, List<HistoryEvent>> byPosition = new LinkedHashMap<>();
        for (HistoryEvent event : events) {
            // Changes older than the target already happened by then and must not be undone.
            if (event.timestampMillis() < targetTimestampMillis) {
                continue;
            }
            byPosition.computeIfAbsent(event.position(), unused -> new ArrayList<>()).add(event);
        }
        return byPosition;
    }

    /**
     * Applies the inverse of every change in turn, oldest last, and returns what the position
     * held at the target time.
     *
     * <p>Returns nothing when the deciding change is a removal whose block the backend never
     * recorded: the position is knowingly dropped from the snapshot rather than rendered as
     * something that was probably never there.
     */
    private static Optional<HistoricalBlockState> reverseToTarget(List<HistoryEvent> newestFirst) {
        Optional<HistoricalBlockState> state = Optional.empty();
        for (HistoryEvent event : newestFirst) {
            state = switch (event.action()) {
                case PLACE -> Optional.of(HistoricalBlockState.AIR);
                case REMOVE -> event.state();
            };
        }
        return state;
    }
}
