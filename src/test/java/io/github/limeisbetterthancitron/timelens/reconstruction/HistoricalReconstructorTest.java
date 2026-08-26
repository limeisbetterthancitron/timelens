package io.github.limeisbetterthancitron.timelens.reconstruction;

import io.github.limeisbetterthancitron.timelens.history.HistoricalBlockState;
import io.github.limeisbetterthancitron.timelens.history.HistoryAction;
import io.github.limeisbetterthancitron.timelens.history.HistoryEvent;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reconstruction rules, expressed as the scenarios TimeLens has to get right.
 *
 * <p>Everything here is plain data, so the algorithm is exercised without a server.
 */
class HistoricalReconstructorTest {

    private static final long TARGET = 1_000_000L;
    private static final BlockPosition POSITION = new BlockPosition(10, 64, -20);

    private static final String AIR = "minecraft:air";
    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String DIAMOND_BLOCK = "minecraft:diamond_block";

    private final HistoricalReconstructor reconstructor = new HistoricalReconstructor();

    @Test
    @DisplayName("A: a block placed after the target was not there at the target")
    void blockPlacedAfterTargetReconstructsToAir() {
        HistoricalSnapshot snapshot = reconstruct(place(TARGET + 1_000L, STONE));

        assertEquals(AIR, stateAt(snapshot));
    }

    @Test
    @DisplayName("B: a block removed after the target was still there at the target")
    void blockRemovedAfterTargetReconstructsToTheRemovedBlock() {
        HistoricalSnapshot snapshot = reconstruct(remove(TARGET + 1_000L, STONE));

        assertEquals(STONE, stateAt(snapshot));
    }

    @Test
    @DisplayName("C: a replacement reconstructs to the block that was replaced")
    void replacementReconstructsToTheOlderBlock() {
        HistoricalSnapshot snapshot = reconstruct(
                place(TARGET + 2_000L, STONE),
                remove(TARGET + 1_000L, DIRT));

        assertEquals(DIRT, stateAt(snapshot));
    }

    @Test
    @DisplayName("D: a long chain of edits reconstructs to the block present at the target")
    void multipleEditsReconstructToTheOldestKnownBlock() {
        HistoricalSnapshot snapshot = reconstruct(
                place(TARGET + 4_000L, DIAMOND_BLOCK),
                remove(TARGET + 3_000L, STONE),
                place(TARGET + 2_000L, STONE),
                remove(TARGET + 1_000L, OAK_PLANKS));

        assertEquals(OAK_PLANKS, stateAt(snapshot));
    }

    @Test
    @DisplayName("E: a position with no changes after the target is left out of the snapshot")
    void changesBeforeTargetAreIgnored() {
        HistoricalSnapshot snapshot = reconstruct(
                place(TARGET - 5_000L, STONE),
                remove(TARGET - 9_000L, DIRT));

        assertTrue(snapshot.isEmpty(), "positions that did not change must not be rendered");
    }

    @Test
    @DisplayName("a replacement logged within a single second keeps the provider's ordering")
    void sameSecondReplacementUsesProviderOrdering() {
        long sameInstant = TARGET + 1_000L;

        // History timestamps have one-second resolution, so the removal that exposed the old
        // block and the placement that covered it share an instant. The provider hands them over
        // newest first, and that order is what separates them.
        HistoricalSnapshot snapshot = reconstruct(
                place(sameInstant, STONE),
                remove(sameInstant, DIRT));

        assertEquals(DIRT, stateAt(snapshot));
    }

    @Test
    @DisplayName("events that arrive out of order are still reconstructed correctly")
    void unorderedEventsAreSortedBeforeReversal() {
        HistoricalSnapshot snapshot = reconstruct(
                place(TARGET + 2_000L, STONE),
                remove(TARGET + 1_000L, OAK_PLANKS),
                place(TARGET + 4_000L, DIAMOND_BLOCK),
                remove(TARGET + 3_000L, STONE));

        assertEquals(OAK_PLANKS, stateAt(snapshot));
    }

    @Test
    @DisplayName("a change exactly at the target instant is treated as having happened since")
    void changeAtTargetInstantIsReversed() {
        HistoricalSnapshot snapshot = reconstruct(place(TARGET, STONE));

        assertEquals(AIR, stateAt(snapshot));
    }

    @Test
    @DisplayName("positions are reconstructed independently of one another")
    void separatePositionsDoNotInterfere() {
        BlockPosition other = new BlockPosition(11, 64, -20);
        HistoricalSnapshot snapshot = reconstructor.reconstruct(TARGET, List.of(
                new HistoryEvent(POSITION, HistoryAction.PLACE, TARGET + 1_000L, state(STONE)),
                new HistoryEvent(other, HistoryAction.REMOVE, TARGET + 1_000L, state(DIRT))));

        assertEquals(2, snapshot.size());
        assertEquals(AIR, snapshot.states().get(POSITION).blockData());
        assertEquals(DIRT, snapshot.states().get(other).blockData());
    }

    @Test
    @DisplayName("a removal with no recorded block is dropped rather than guessed at")
    void removalWithoutRecordedBlockIsExcluded() {
        HistoricalSnapshot snapshot = reconstructor.reconstruct(TARGET, List.of(
                new HistoryEvent(POSITION, HistoryAction.REMOVE, TARGET + 1_000L, Optional.empty())));

        assertTrue(snapshot.isEmpty(), "an unknown removed block must not be rendered as a guess");
    }

    @Test
    @DisplayName("an unrecorded removal only discards the position it belongs to")
    void unrecordedRemovalDoesNotDiscardOtherPositions() {
        BlockPosition other = new BlockPosition(12, 64, -20);
        HistoricalSnapshot snapshot = reconstructor.reconstruct(TARGET, List.of(
                new HistoryEvent(POSITION, HistoryAction.REMOVE, TARGET + 1_000L, Optional.empty()),
                new HistoryEvent(other, HistoryAction.PLACE, TARGET + 1_000L, state(STONE))));

        assertFalse(snapshot.states().containsKey(POSITION));
        assertEquals(AIR, snapshot.states().get(other).blockData());
    }

    @Test
    @DisplayName("the snapshot records the moment it reconstructs")
    void snapshotKeepsItsTargetTimestamp() {
        assertEquals(TARGET, reconstruct(place(TARGET + 1_000L, STONE)).targetTimestampMillis());
    }

    @Test
    @DisplayName("no history at all produces an empty snapshot")
    void noEventsProduceAnEmptySnapshot() {
        assertTrue(reconstructor.reconstruct(TARGET, List.of()).isEmpty());
    }

    private HistoricalSnapshot reconstruct(HistoryEvent... newestFirst) {
        return reconstructor.reconstruct(TARGET, List.of(newestFirst));
    }

    private static String stateAt(HistoricalSnapshot snapshot) {
        HistoricalBlockState state = snapshot.states().get(POSITION);
        assertNotNull(state, "expected a reconstructed state at " + POSITION);
        return state.blockData();
    }

    private static HistoryEvent place(long timestampMillis, String blockData) {
        return new HistoryEvent(POSITION, HistoryAction.PLACE, timestampMillis, state(blockData));
    }

    private static HistoryEvent remove(long timestampMillis, String blockData) {
        return new HistoryEvent(POSITION, HistoryAction.REMOVE, timestampMillis, state(blockData));
    }

    private static Optional<HistoricalBlockState> state(String blockData) {
        return Optional.of(new HistoricalBlockState(blockData));
    }
}
