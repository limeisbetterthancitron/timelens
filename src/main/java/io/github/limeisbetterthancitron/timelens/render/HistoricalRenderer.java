package io.github.limeisbetterthancitron.timelens.render;

import io.github.limeisbetterthancitron.timelens.history.HistoricalBlockState;
import io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalSnapshot;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows and withdraws historical block states for a single viewer.
 *
 * <p>Every method here must run on the server thread: they read live world state and send
 * packets. Nothing in this class writes to the world. The states are delivered straight to one
 * player's client with {@link Player#sendMultiBlockChange(Map)}, which Paper documents as
 * faking a packet per chunk section without changing the world. Other players, and the server
 * itself, continue to see and use the real blocks.
 */
public final class HistoricalRenderer {

    /** Chunk coordinates are block coordinates shifted by four bits. */
    private static final int CHUNK_SHIFT = 4;

    /**
     * The most positions a single view may render.
     *
     * <p>A second brake, independent of {@code history.maximum-results}. That limit counts
     * recorded changes, and the relationship between changes and rendered blocks is not fixed:
     * thousands of rows can collapse onto a handful of coordinates, or spread across as many
     * unique positions plus a derived partner each. Since main-thread cost scales with rendered
     * blocks rather than with rows, the row limit alone cannot bound it.
     *
     * <p>Measured at 0.5 to 1.2 microseconds per block, so this caps preparation at roughly a
     * third of a tick at the worst observed rate. It does not bound what the packet costs the
     * client, which is a separate question and not yet measured. Not configurable: it protects
     * the server rather than expressing a preference.
     */
    public static final int MAX_RENDER_POSITIONS = 12_000;

    private final Logger logger;

    public HistoricalRenderer(Logger logger) {
        this.logger = logger;
    }

    /**
     * The block changes a snapshot works out to, ready to be sent.
     *
     * @param changes   what to send, keyed by position
     * @param positions the same positions in TimeLens terms, which is what must later be restored
     */
    public record PreparedView(Map<Position, BlockData> changes, Set<BlockPosition> positions) {

        public boolean isEmpty() {
            return changes.isEmpty();
        }
    }

    /**
     * Sends the parts of a snapshot that actually differ from the live world.
     *
     * @return the positions the viewer was shown, which is what must later be restored
     */
    public Set<BlockPosition> render(Player viewer, World world, HistoricalSnapshot snapshot) {
        PreparedView prepared = prepare(world, snapshot);
        if (!prepared.isEmpty()) {
            viewer.sendMultiBlockChange(prepared.changes());
        }
        return prepared.positions();
    }

    /**
     * Works out what a viewer would be sent, without sending it.
     *
     * <p>Separated from {@link #render} because this half is where the cost is, being a world
     * read and a block-data parse per position, while the send itself is one call. Splitting them
     * makes that cost measurable on its own. Server thread only: it reads live world state.
     */
    public PreparedView prepare(World world, HistoricalSnapshot snapshot) {
        Map<Position, BlockData> changes = new HashMap<>();
        Set<BlockPosition> rendered = new LinkedHashSet<>();
        BlockData air = Material.AIR.createBlockData();
        int unreadable = 0;

        for (Map.Entry<BlockPosition, HistoricalBlockState> entry : snapshot.states().entrySet()) {
            BlockPosition position = entry.getKey();
            if (!isReadable(world, position)) {
                continue;
            }

            String historicalData = entry.getValue().blockData();
            BlockData current = blockDataAt(world, position);
            // Sending a block the client already shows correctly is pure waste.
            if (current.getAsString().equals(historicalData)) {
                continue;
            }

            BlockData displayed;
            try {
                displayed = Bukkit.createBlockData(historicalData);
            } catch (IllegalArgumentException exception) {
                // Block data recorded by an older server version may no longer parse.
                unreadable++;
                continue;
            }

            changes.put(toPaperPosition(position), displayed);
            rendered.add(position);
            linkCompanions(world, snapshot, position, displayed, current, air, changes, rendered);
        }

        if (unreadable > 0) {
            logger.log(Level.WARNING, "Skipped {0} historical blocks that this server version cannot parse",
                    unreadable);
        }
        return new PreparedView(changes, rendered);
    }

    /**
     * Keeps two-block structures whole.
     *
     * <p>History records a door, bed or tall plant only at its base block. Replaying that alone
     * leaves the other half showing whatever the live world has there, which reads as a broken
     * door or a floating bed. Two things are needed: the partner of whatever is now displayed,
     * and the removal of the partner of whatever used to stand here.
     */
    private static void linkCompanions(World world,
                                       HistoricalSnapshot snapshot,
                                       BlockPosition position,
                                       BlockData displayed,
                                       BlockData current,
                                       BlockData air,
                                       Map<Position, BlockData> changes,
                                       Set<BlockPosition> rendered) {
        Optional<MultiBlockCompanion> fromDisplayed = MultiBlockCompanion.of(position, displayed);
        fromDisplayed.ifPresent(companion ->
                putCompanion(world, snapshot, companion.position(), companion.data(), changes, rendered));

        Optional<BlockPosition> leftover = MultiBlockCompanion.of(position, current)
                .map(MultiBlockCompanion::position);
        // When the displayed block already claims that position, it has been handled correctly
        // above and must not be blanked out again.
        if (leftover.isPresent()
                && !leftover.equals(fromDisplayed.map(MultiBlockCompanion::position))) {
            putCompanion(world, snapshot, leftover.get(), air, changes, rendered);
        }
    }

    /**
     * A position the snapshot reconstructed for itself is authoritative, so a derived partner
     * never overwrites one.
     */
    private static void putCompanion(World world,
                                     HistoricalSnapshot snapshot,
                                     BlockPosition position,
                                     BlockData data,
                                     Map<Position, BlockData> changes,
                                     Set<BlockPosition> rendered) {
        if (snapshot.states().containsKey(position) || !isReadable(world, position)) {
            return;
        }
        changes.put(toPaperPosition(position), data);
        rendered.add(position);
    }

    /**
     * Sends the live state of the given positions, returning the viewer to the present.
     *
     * <p>The current world is read here rather than replaying a cached "before" state, because
     * the world may legitimately have changed while the viewer was looking at the past.
     */
    public void restore(Player viewer, World world, Collection<BlockPosition> positions) {
        Map<Position, BlockData> changes = new HashMap<>();
        for (BlockPosition position : positions) {
            if (!isReadable(world, position)) {
                continue;
            }
            changes.put(toPaperPosition(position), blockDataAt(world, position));
        }
        if (!changes.isEmpty()) {
            viewer.sendMultiBlockChange(changes);
        }
    }

    /**
     * Guards against forcing a synchronous chunk load, and against coordinates outside the
     * world's build limits, either of which would cost far more than the block is worth.
     */
    private static boolean isReadable(World world, BlockPosition position) {
        if (position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight()) {
            return false;
        }
        return world.isChunkLoaded(position.x() >> CHUNK_SHIFT, position.z() >> CHUNK_SHIFT);
    }

    private static BlockData blockDataAt(World world, BlockPosition position) {
        return world.getBlockAt(position.x(), position.y(), position.z()).getBlockData();
    }

    private static Position toPaperPosition(BlockPosition position) {
        return Position.block(position.x(), position.y(), position.z());
    }
}
