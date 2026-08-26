package io.github.limeisbetterthancitron.timelens.history;

import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads block history out of CoreProtect and translates it into TimeLens domain events.
 *
 * <p>This class is the only place in TimeLens that imports anything from CoreProtect. It runs
 * entirely on a worker thread and never touches world state.
 */
public final class CoreProtectHistoryProvider implements HistoryProvider {

    /**
     * CoreProtect's documented lookup action ids. They are part of its public API contract, so
     * they are restated here rather than taken from its internal model package.
     */
    private static final int ACTION_BLOCK_REMOVE = 0;
    private static final int ACTION_BLOCK_PLACE = 1;

    private static final List<Integer> BLOCK_ACTIONS = List.of(ACTION_BLOCK_REMOVE, ACTION_BLOCK_PLACE);

    private final CoreProtectAPI api;
    private final Logger logger;

    public CoreProtectHistoryProvider(CoreProtectAPI api, Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    @Override
    public List<HistoryEvent> lookup(HistoryQuery query) throws HistoryLookupException {
        int lookbackSeconds = toLookupSeconds(query);

        List<String[]> rows;
        try {
            // CoreProtect's radius is a coarse cuboid pre-filter around the centre; the exact
            // per-axis bounds the player configured are applied when rows are converted below.
            rows = api.performLookup(lookbackSeconds, null, null, null, null,
                    mutableBlockActions(), query.enclosingRadius(), query.center());
        } catch (RuntimeException exception) {
            throw new HistoryLookupException("CoreProtect lookup failed", exception);
        }

        if (rows == null) {
            // CoreProtect returns null instead of throwing when its database is unavailable.
            throw new HistoryLookupException("CoreProtect returned no result set; its database may be unavailable");
        }
        if (rows.size() > query.maxResults()) {
            throw new HistoryResultLimitException(rows.size(), query.maxResults());
        }

        return convert(rows, query);
    }

    @Override
    public String backendDescription() {
        return "CoreProtect API v" + api.APIVersion();
    }

    /**
     * CoreProtect edits the action list it is handed rather than copying it. It appends to the
     * list and strips entries above id 3, so the list must be both mutable and freshly built. A
     * shared constant would throw on the first lookup and be silently corrupted on later ones.
     */
    private static List<Integer> mutableBlockActions() {
        return new ArrayList<>(BLOCK_ACTIONS);
    }

    /**
     * CoreProtect takes the lookback as seconds in an {@code int}, so an over-long configured
     * maximum has to be refused here rather than silently wrapping into a negative window.
     */
    private static int toLookupSeconds(HistoryQuery query) throws HistoryLookupException {
        long seconds = query.lookbackSeconds();
        if (seconds > Integer.MAX_VALUE) {
            throw new HistoryLookupException("Lookback of " + seconds + "s exceeds what CoreProtect can query");
        }
        return (int) seconds;
    }

    /**
     * CoreProtect returns rows newest first ({@code ORDER BY time DESC, id DESC}); that order is
     * preserved here because second-resolution timestamps cannot by themselves separate a
     * removal from the placement that replaced it in the same second.
     */
    private List<HistoryEvent> convert(List<String[]> rows, HistoryQuery query) {
        List<HistoryEvent> events = new ArrayList<>(rows.size());
        int malformed = 0;

        for (String[] row : rows) {
            if (row == null) {
                malformed++;
                continue;
            }
            try {
                convertRow(row, query).ifPresent(events::add);
            } catch (RuntimeException exception) {
                // One unreadable row must not sink an otherwise usable snapshot.
                malformed++;
            }
        }

        if (malformed > 0) {
            logger.log(Level.WARNING, "Skipped {0} unreadable CoreProtect entries while building a view in {1}",
                    new Object[]{malformed, query.worldName()});
        }
        return events;
    }

    private Optional<HistoryEvent> convertRow(String[] row, HistoryQuery query) {
        CoreProtectAPI.ParseResult parsed = api.parseResult(row);
        if (parsed == null) {
            return Optional.empty();
        }

        // A rolled-back entry is no longer reflected in the current world, so reversing it
        // would move the position away from the truth rather than towards it.
        if (parsed.isRolledBack()) {
            return Optional.empty();
        }

        String worldName = parsed.worldName();
        if (worldName == null || !worldName.equalsIgnoreCase(query.worldName())) {
            return Optional.empty();
        }

        Optional<HistoryAction> action = toAction(parsed.getActionId());
        if (action.isEmpty()) {
            return Optional.empty();
        }

        int x = parsed.getX();
        int y = parsed.getY();
        int z = parsed.getZ();
        if (!query.contains(x, y, z)) {
            return Optional.empty();
        }

        return Optional.of(new HistoryEvent(
                new BlockPosition(x, y, z),
                action.get(),
                parsed.getTimestamp(),
                toState(parsed)));
    }

    private static Optional<HistoryAction> toAction(int actionId) {
        return switch (actionId) {
            case ACTION_BLOCK_REMOVE -> Optional.of(HistoryAction.REMOVE);
            case ACTION_BLOCK_PLACE -> Optional.of(HistoryAction.PLACE);
            default -> Optional.empty();
        };
    }

    /**
     * Resolving block data is a registry lookup rather than world access, which is why it is
     * safe on the worker thread CoreProtect itself expects lookups to run on. It still returns
     * nothing for short or legacy rows, so callers must cope with an absent state.
     */
    private static Optional<HistoricalBlockState> toState(CoreProtectAPI.ParseResult parsed) {
        BlockData blockData = parsed.getBlockData();
        if (blockData != null) {
            return Optional.of(new HistoricalBlockState(blockData.getAsString()));
        }

        Material type = parsed.getType();
        if (type != null && type.isBlock()) {
            return Optional.of(new HistoricalBlockState(type.createBlockData().getAsString()));
        }
        return Optional.empty();
    }
}
