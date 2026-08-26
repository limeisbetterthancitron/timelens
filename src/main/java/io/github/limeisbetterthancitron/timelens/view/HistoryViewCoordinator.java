package io.github.limeisbetterthancitron.timelens.view;

import io.github.limeisbetterthancitron.timelens.config.TimeLensConfig;
import io.github.limeisbetterthancitron.timelens.history.HistoryEvent;
import io.github.limeisbetterthancitron.timelens.history.HistoryLookupException;
import io.github.limeisbetterthancitron.timelens.history.HistoryProvider;
import io.github.limeisbetterthancitron.timelens.history.HistoryQuery;
import io.github.limeisbetterthancitron.timelens.history.HistoryResultLimitException;
import io.github.limeisbetterthancitron.timelens.message.Messages;
import io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalReconstructor;
import io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalSnapshot;
import io.github.limeisbetterthancitron.timelens.render.HistoricalRenderer;
import io.github.limeisbetterthancitron.timelens.session.TimelineSession;
import io.github.limeisbetterthancitron.timelens.session.TimelineSessionManager;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import io.github.limeisbetterthancitron.timelens.util.HistoryTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives the whole request: validate, query off-thread, reconstruct, then render on the server
 * thread.
 *
 * <p>The split matters. History lookups hit a database and must never run on the server thread,
 * while reading world state and sending packets must only ever happen there. Everything that
 * crosses between the two is an immutable value.
 */
public final class HistoryViewCoordinator {

    private final Plugin plugin;
    private final HistoryProvider provider;
    private final HistoricalReconstructor reconstructor;
    private final HistoricalRenderer renderer;
    private final TimelineSessionManager sessions;
    private final Messages messages;
    private final Logger logger;

    public HistoryViewCoordinator(Plugin plugin,
                                  HistoryProvider provider,
                                  HistoricalReconstructor reconstructor,
                                  HistoricalRenderer renderer,
                                  TimelineSessionManager sessions,
                                  Messages messages,
                                  Logger logger) {
        this.plugin = plugin;
        this.provider = provider;
        this.reconstructor = reconstructor;
        this.renderer = renderer;
        this.sessions = sessions;
        this.messages = messages;
        this.logger = logger;
    }

    /**
     * Starts building a view of the past for one player. Server thread only.
     *
     * @param horizontalRadius how far the view should reach along X and Z
     * @param verticalRadius   how far the view should reach along Y
     */
    public void openView(Player viewer,
                         HistoryTarget target,
                         int horizontalRadius,
                         int verticalRadius,
                         TimeLensConfig config) {
        UUID playerId = viewer.getUniqueId();
        if (!sessions.beginRequest(playerId)) {
            viewer.sendMessage(messages.alreadyLoading());
            return;
        }

        HistoryQuery query = HistoryQuery.around(viewer.getLocation(), horizontalRadius, verticalRadius,
                target, System.currentTimeMillis(), config.maximumResults());
        String viewerName = viewer.getName();

        viewer.sendMessage(messages.loading(query.targetDescription()));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            LookupOutcome outcome = lookupOffThread(query, viewerName);
            // A disable between the query starting and finishing would make scheduling throw,
            // so the claim is released directly rather than through the server thread.
            if (!plugin.isEnabled()) {
                sessions.finishRequest(playerId);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> applyOutcome(playerId, query, config, outcome));
        });
    }

    /**
     * Ends a player's view. Server thread only.
     */
    public void closeView(Player viewer) {
        if (sessions.stop(viewer)) {
            viewer.sendMessage(messages.returnedToPresent());
        } else {
            viewer.sendMessage(messages.notViewing());
        }
    }

    /**
     * Runs on a worker thread and never throws: every failure becomes a message for the player
     * plus a technical record in the console.
     */
    private LookupOutcome lookupOffThread(HistoryQuery query, String viewerName) {
        try {
            List<HistoryEvent> events = provider.lookup(query);
            return new LookupOutcome.Success(
                    reconstructor.reconstruct(query.targetTimestampMillis(), events));
        } catch (HistoryResultLimitException exception) {
            BlockPosition center = query.centerBlock();
            logger.warning("Refused a view for " + viewerName + " in " + query.worldName()
                    + " at " + center.x() + ", " + center.y() + ", " + center.z()
                    + " from " + query.targetDescription() + " with radius " + query.horizontalRadius()
                    + ": " + exception.matched() + " changes exceed the configured limit of "
                    + exception.limit() + ".");
            return new LookupOutcome.Failure(messages.tooManyChanges(exception.matched(), exception.limit()));
        } catch (HistoryLookupException exception) {
            logger.log(Level.SEVERE, "History lookup failed for " + viewerName, exception);
            return new LookupOutcome.Failure(messages.lookupFailed());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Unexpected error building a historical view for " + viewerName, exception);
            return new LookupOutcome.Failure(messages.lookupFailed());
        }
    }

    /**
     * Runs on the server thread. The claim is released here whatever happens, so a failure can
     * never leave a player permanently unable to request another view.
     */
    private void applyOutcome(UUID playerId, HistoryQuery query, TimeLensConfig config, LookupOutcome outcome) {
        try {
            Player viewer = plugin.getServer().getPlayer(playerId);
            if (viewer == null) {
                return;
            }
            switch (outcome) {
                case LookupOutcome.Failure failure -> viewer.sendMessage(failure.playerMessage());
                case LookupOutcome.Success success -> present(viewer, query, config, success.snapshot());
            }
        } finally {
            sessions.finishRequest(playerId);
        }
    }

    private void present(Player viewer, HistoryQuery query, TimeLensConfig config, HistoricalSnapshot snapshot) {
        World world = viewer.getWorld();
        if (!world.getName().equals(query.worldName())) {
            viewer.sendMessage(messages.viewEnded("changed world while it was loading"));
            return;
        }
        if (snapshot.isEmpty()) {
            // Nothing to show, so an existing view is left alone rather than closed for nothing.
            viewer.sendMessage(messages.noHistory());
            return;
        }
        // Checked after reconstruction because only now is the real number of blocks known; the
        // row limit that got us here is a poor predictor of it.
        if (snapshot.size() > HistoricalRenderer.MAX_RENDER_POSITIONS) {
            logger.warning("Refused a view for " + viewer.getName() + " in " + query.worldName()
                    + " from " + query.targetDescription() + " with radius " + query.horizontalRadius()
                    + ": " + snapshot.size() + " blocks exceeds the render limit of "
                    + HistoricalRenderer.MAX_RENDER_POSITIONS + ".");
            viewer.sendMessage(messages.tooManyBlocks(snapshot.size(),
                    HistoricalRenderer.MAX_RENDER_POSITIONS));
            return;
        }

        // Any previous view has to be retired before the new one is drawn: its restoration
        // packets carry live block states and would otherwise overwrite part of what we send.
        sessions.stop(viewer);

        Set<BlockPosition> rendered = renderer.render(viewer, world, snapshot);
        if (rendered.isEmpty()) {
            // Everything that changed has since changed back, so the present already looks right.
            viewer.sendMessage(messages.noHistory());
            return;
        }

        sessions.start(viewer, TimelineSession.start(viewer, query, rendered, System.currentTimeMillis()));

        viewer.sendMessage(messages.viewing(query.targetDescription()));
        viewer.sendMessage(messages.rendered(rendered.size(), query.horizontalRadius()));
        // Only worth suggesting while there is headroom left to widen into.
        if (query.horizontalRadius() < config.maximumRadius()) {
            viewer.sendMessage(messages.radiusHint(config.maximumRadius()));
        }
        viewer.sendMessage(messages.exitHint());
    }

    /**
     * What the worker thread hands back: either a snapshot to render, or the message to show.
     */
    private sealed interface LookupOutcome {

        record Success(HistoricalSnapshot snapshot) implements LookupOutcome {
        }

        record Failure(Component playerMessage) implements LookupOutcome {
        }
    }
}
