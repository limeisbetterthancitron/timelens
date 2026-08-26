package io.github.limeisbetterthancitron.timelens.session;

import io.github.limeisbetterthancitron.timelens.render.HistoricalRenderer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who is viewing the past, and who is waiting for a view to load.
 *
 * <p>Sessions are created and destroyed on the server thread. The in-flight request set is the
 * one piece of state a worker thread may touch, so both collections are concurrent and the
 * "claim" is an atomic add rather than a check followed by a write.
 */
public final class TimelineSessionManager {

    /** Vanilla movement speeds, used only to rescue a player found stuck at zero. */
    public static final float DEFAULT_WALK_SPEED = 0.2F;
    public static final float DEFAULT_FLY_SPEED = 0.1F;

    private final Map<UUID, TimelineSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    private final Server server;
    private final HistoricalRenderer renderer;

    public TimelineSessionManager(Server server, HistoricalRenderer renderer) {
        this.server = server;
        this.renderer = renderer;
    }

    /**
     * Claims the right to run one history query for a player.
     *
     * @return {@code false} if a query for that player is already in flight
     */
    public boolean beginRequest(UUID playerId) {
        return loading.add(playerId);
    }

    public void finishRequest(UUID playerId) {
        loading.remove(playerId);
    }

    public boolean isLoading(UUID playerId) {
        return loading.contains(playerId);
    }

    public Optional<TimelineSession> find(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /**
     * Replaces any existing view with a new one, restoring the old view first so fake states
     * never stack on top of each other.
     */
    public void start(Player viewer, TimelineSession session) {
        stop(viewer);
        sessions.put(session.playerId(), session);
    }

    /**
     * Ends a player's view, returning their client to the present and giving their movement back.
     *
     * @return {@code true} if there was a view to end
     */
    public boolean stop(Player viewer) {
        TimelineSession session = sessions.remove(viewer.getUniqueId());
        if (session == null) {
            return false;
        }
        restore(viewer, session);
        session.markEnded();
        return true;
    }

    /**
     * Ends a view without sending block updates, for a player who is leaving the area or the
     * server and will be sent fresh chunks anyway.
     */
    public void release(Player viewer) {
        discard(viewer.getUniqueId());
    }

    /**
     * Drops a session without sending anything. Nothing about a viewer persists beyond the
     * session, so forgetting it is enough to fully release them.
     */
    public void discard(UUID playerId) {
        TimelineSession session = sessions.remove(playerId);
        if (session != null) {
            session.markEnded();
        }
    }

    /**
     * Ends every active view, used on shutdown so no one is left looking at stale blocks.
     */
    public void stopAll() {
        for (UUID playerId : Set.copyOf(sessions.keySet())) {
            Player viewer = server.getPlayer(playerId);
            if (viewer == null) {
                discard(playerId);
            } else {
                stop(viewer);
            }
        }
        loading.clear();
    }


    /**
     * Restoring is best effort: if the world went away there is nothing meaningful to send, and
     * the viewer will receive real chunks the next time they load the area.
     */
    private void restore(Player viewer, TimelineSession session) {
        World world = server.getWorld(session.worldId());
        if (world == null || !world.getUID().equals(viewer.getWorld().getUID())) {
            return;
        }
        renderer.restore(viewer, world, session.renderedPositions());
    }
}
