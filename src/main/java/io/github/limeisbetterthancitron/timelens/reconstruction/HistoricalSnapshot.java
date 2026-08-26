package io.github.limeisbetterthancitron.timelens.reconstruction;

import io.github.limeisbetterthancitron.timelens.history.HistoricalBlockState;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;

import java.util.Map;
import java.util.Objects;

/**
 * How an area is believed to have looked at one moment in the past.
 *
 * <p>Only positions that some recorded change touched appear here; everywhere else the present
 * world is already the correct answer. The states are what the reconstruction concluded, not
 * yet a diff against the live world. That comparison happens on the server thread at render
 * time, because the world may have moved on since the query started.
 *
 * @param targetTimestampMillis the moment being reconstructed, in milliseconds since the epoch
 * @param states                reconstructed block state per affected position
 */
public record HistoricalSnapshot(long targetTimestampMillis,
                                 Map<BlockPosition, HistoricalBlockState> states) {

    public HistoricalSnapshot {
        Objects.requireNonNull(states, "states");
        states = Map.copyOf(states);
    }

    public int size() {
        return states.size();
    }

    public boolean isEmpty() {
        return states.isEmpty();
    }
}
