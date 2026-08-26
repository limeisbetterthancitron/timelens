package io.github.limeisbetterthancitron.timelens.session;

import io.github.limeisbetterthancitron.timelens.history.HistoryQuery;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * One player's active view into the past.
 *
 * <p>Holds the viewer's {@link UUID} rather than a {@link Player}, so a session left behind by a
 * bug cannot pin a disconnected player object in memory. The world is likewise held by id.
 *
 * <p>Owned by the server thread: {@link TimelineSessionManager} creates, mutates and discards
 * sessions there and nowhere else.
 */
public final class TimelineSession {

    private final UUID playerId;
    private final UUID worldId;
    private final String worldName;
    private final String targetDescription;
    private final long targetTimestampMillis;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;
    private final BlockPosition center;
    private final Set<BlockPosition> renderedPositions;
    private final long startedAtMillis;

    private SessionState state = SessionState.ACTIVE;

    private TimelineSession(UUID playerId,
                            UUID worldId,
                            String worldName,
                            String targetDescription,
                            long targetTimestampMillis,
                            Location anchor,
                            BlockPosition center,
                            Set<BlockPosition> renderedPositions,
                            long startedAtMillis) {
        this.playerId = playerId;
        this.worldId = worldId;
        this.worldName = worldName;
        this.targetDescription = targetDescription;
        this.targetTimestampMillis = targetTimestampMillis;
        this.anchorX = anchor.getX();
        this.anchorY = anchor.getY();
        this.anchorZ = anchor.getZ();
        this.center = center;
        this.renderedPositions = Set.copyOf(renderedPositions);
        this.startedAtMillis = startedAtMillis;
    }

    /**
     * Must be called on the server thread; reads the viewer's current world.
     *
     * @param renderedPositions the positions actually shown, which are the ones to restore later
     */
    public static TimelineSession start(Player viewer,
                                        HistoryQuery query,
                                        Set<BlockPosition> renderedPositions,
                                        long startedAtMillis) {
        World world = viewer.getWorld();
        return new TimelineSession(viewer.getUniqueId(),
                world.getUID(),
                query.worldName(),
                query.targetDescription(),
                query.targetTimestampMillis(),
                query.center(),
                query.centerBlock(),
                renderedPositions,
                startedAtMillis);
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID worldId() {
        return worldId;
    }

    public String worldName() {
        return worldName;
    }

    /** How the requested moment is named to the player, e.g. "7 days ago" or "2026-08-20 14:30". */
    public String targetDescription() {
        return targetDescription;
    }

    public long targetTimestampMillis() {
        return targetTimestampMillis;
    }

    public BlockPosition center() {
        return center;
    }

    public Set<BlockPosition> renderedPositions() {
        return renderedPositions;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public SessionState state() {
        return state;
    }

    public void markEnded() {
        this.state = SessionState.ENDED;
    }

    /**
     * The same place the view was anchored to, carrying the viewer's current facing.
     *
     * <p>Used to hold a viewer still without touching anything that persists: their position is
     * overwritten each move, while yaw and pitch are passed through so looking around stays
     * completely free.
     */
    public Location lockedTo(Location attempted) {
        return new Location(attempted.getWorld(), anchorX, anchorY, anchorZ,
                attempted.getYaw(), attempted.getPitch());
    }

    /**
     * Whether a location is still close enough to where the view was anchored.
     *
     * <p>Compared without allocating or calling {@link Location#distanceSquared}, because this
     * runs for every movement packet the viewer sends.
     */
    public boolean isNearAnchor(Location location, double maxDistance) {
        World world = location.getWorld();
        if (world == null || !world.getUID().equals(worldId)) {
            return false;
        }
        double deltaX = location.getX() - anchorX;
        double deltaY = location.getY() - anchorY;
        double deltaZ = location.getZ() - anchorZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= maxDistance * maxDistance;
    }
}
