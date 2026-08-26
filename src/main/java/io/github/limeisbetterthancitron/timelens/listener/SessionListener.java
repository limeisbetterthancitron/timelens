package io.github.limeisbetterthancitron.timelens.listener;

import io.github.limeisbetterthancitron.timelens.config.TimeLensConfig;
import io.github.limeisbetterthancitron.timelens.message.Messages;
import io.github.limeisbetterthancitron.timelens.session.TimelineSession;
import io.github.limeisbetterthancitron.timelens.session.TimelineSessionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Keeps sessions honest: tears them down when the viewer leaves the context they were built
 * for, and stops the viewer acting on blocks that are not really there.
 *
 * <p>The historical blocks exist only on the viewer's client. The server still uses the real
 * world for collision and interaction, so letting someone mine a wall they can see but the
 * server cannot would desynchronise them immediately. Interaction is therefore blocked by
 * default, and {@code view.freeze-movement} holds the viewer still so what they see and what
 * they collide with cannot drift apart.
 *
 * <p>The hold is done purely by rewriting movement events back to the anchor. Nothing about the
 * player is altered, neither gamemode nor abilities nor speeds, so there is no TimeLens state that
 * could survive a crash and follow them into their next session. {@code /timelens exit} always
 * remains available.
 */
public final class SessionListener implements Listener {

    /**
     * How far a teleport may move the viewer before the view is considered abandoned. Only a
     * nudge, since anything further has left the area the snapshot covers.
     */
    private static final double ANCHOR_LEASH_BLOCKS = 1.0D;

    private final TimelineSessionManager sessions;
    private final Messages messages;
    private final TimeLensConfig config;
    private final Logger logger;

    public SessionListener(TimelineSessionManager sessions,
                           Messages messages,
                           TimeLensConfig config,
                           Logger logger) {
        this.sessions = sessions;
        this.messages = messages;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Rescues anyone found stuck at zero movement speed. TimeLens never sets it, but speed is
     * written to a player's saved profile, so a player who was frozen by some earlier build or
     * by another plugin would otherwise rejoin unable to move at all.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWalkSpeed() != 0.0F && player.getFlySpeed() != 0.0F) {
            return;
        }
        player.setWalkSpeed(TimelineSessionManager.DEFAULT_WALK_SPEED);
        player.setFlySpeed(TimelineSessionManager.DEFAULT_FLY_SPEED);
        logger.warning("Restored default movement speed for " + player.getName()
                + ", who joined unable to move.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // No restoration packets are worth sending to someone who has already left; a
        // reconnecting player is sent real chunks by the server anyway.
        sessions.release(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (sessions.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        // The new world's chunks are already on their way, so only the bookkeeping needs clearing.
        sessions.release(player);
        player.sendMessage(messages.viewEnded("changed world"));
    }

    /**
     * Runs before the teleport happens, so the viewer is still standing in the world whose
     * blocks need restoring.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Optional<TimelineSession> session = sessions.find(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }

        Location destination = event.getTo();
        if (destination == null) {
            return;
        }
        // A short hop keeps the view; anything further leaves the area the snapshot covers.
        double allowance = config.freezeMovement() ? ANCHOR_LEASH_BLOCKS : config.radius();
        if (session.get().isNearAnchor(destination, allowance)) {
            return;
        }
        if (sessions.stop(player)) {
            player.sendMessage(messages.viewEnded("teleported away"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (sessions.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        // Respawning re-sends the surrounding chunks, so nothing needs restoring by hand.
        sessions.release(player);
        player.sendMessage(messages.viewEnded("died"));
    }

    /**
     * Holds the viewer at the exact spot the view was taken from.
     *
     * <p>The destination is rewritten rather than the event cancelled: cancelling reverts the
     * viewer's facing along with their position, which makes simply looking around feel like it
     * is fighting back. Rewriting keeps the yaw and pitch they asked for and replaces only the
     * coordinates, so the head turns freely while the body cannot move at all.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.freezeMovement()) {
            return;
        }
        Optional<TimelineSession> session = sessions.find(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }

        Location to = event.getTo();
        Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        event.setTo(session.get().lockedTo(to));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        blockWhileViewing(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        blockWhileViewing(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        blockWhileViewing(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        blockWhileViewing(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }
        blockWhileViewing(event.getPlayer(), event);
    }

    private void blockWhileViewing(Player player, Cancellable event) {
        if (!config.blockInteractions()) {
            return;
        }
        if (sessions.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        // The action bar keeps this out of chat, which the player may want to read.
        player.sendActionBar(messages.interactionBlocked());
    }
}
