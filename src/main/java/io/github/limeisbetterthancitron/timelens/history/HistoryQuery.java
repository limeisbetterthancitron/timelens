package io.github.limeisbetterthancitron.timelens.history;

import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import io.github.limeisbetterthancitron.timelens.util.HistoryTarget;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * An immutable description of the history a player asked for.
 *
 * <p>Built on the server thread and then handed to a {@link HistoryProvider} running on a
 * worker thread, so everything it exposes is either a value or a defensive copy. The world
 * name is resolved up front because reading it from the {@link Location} later would be a
 * Bukkit call on the wrong thread.
 *
 * <p>The requested moment is carried both as an instant and as an age, because reconstruction
 * needs the instant while history backends ask for "how far back".
 *
 * @param center                the block the view is centred on, already detached from the caller
 * @param worldName             the world the centre belongs to
 * @param horizontalRadius      how far the view reaches along X and Z
 * @param verticalRadius        how far the view reaches along Y
 * @param targetTimestampMillis the moment being reconstructed
 * @param lookbackSeconds       how far back that moment is from when the request was made
 * @param targetDescription     how to name the moment to the player
 * @param maxResults            abort rather than reconstruct more recorded changes than this
 */
public record HistoryQuery(Location center,
                           String worldName,
                           int horizontalRadius,
                           int verticalRadius,
                           long targetTimestampMillis,
                           long lookbackSeconds,
                           String targetDescription,
                           int maxResults) {

    private static final long MILLIS_PER_SECOND = 1_000L;

    public HistoryQuery {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(targetDescription, "targetDescription");
    }

    /**
     * Must be called on the server thread: it reads the world out of the supplied location.
     */
    public static HistoryQuery around(Location center,
                                      int horizontalRadius,
                                      int verticalRadius,
                                      HistoryTarget target,
                                      long nowMillis,
                                      int maxResults) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(target, "target");
        World world = center.getWorld();
        Objects.requireNonNull(world, "center must belong to a loaded world");

        long targetTimestampMillis = target.timestampMillis(nowMillis);
        // Rounded up, and never below one second, so a very recent moment still asks the backend
        // for a window rather than for nothing at all.
        long lookbackSeconds = Math.max(1L,
                (nowMillis - targetTimestampMillis + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND);

        return new HistoryQuery(center.clone(), world.getName(), horizontalRadius, verticalRadius,
                targetTimestampMillis, lookbackSeconds, target.describe(), maxResults);
    }

    public BlockPosition centerBlock() {
        return new BlockPosition(center.getBlockX(), center.getBlockY(), center.getBlockZ());
    }

    /**
     * The largest radius along any axis, used where a backend only accepts a single value.
     */
    public int enclosingRadius() {
        return Math.max(horizontalRadius, verticalRadius);
    }

    public boolean contains(int x, int y, int z) {
        return Math.abs(x - center.getBlockX()) <= horizontalRadius
                && Math.abs(y - center.getBlockY()) <= verticalRadius
                && Math.abs(z - center.getBlockZ()) <= horizontalRadius;
    }
}
