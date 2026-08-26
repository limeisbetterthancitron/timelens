package io.github.limeisbetterthancitron.timelens.history;

import java.util.Objects;

/**
 * A block state captured as its canonical Bukkit block-data string, for example
 * {@code minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]}.
 *
 * <p>A string is held instead of a Bukkit {@code BlockData} so reconstruction stays a pure
 * computation: it can run on a worker thread and be unit tested without a running server.
 * The string is turned back into real block data on the server thread, at render time.
 *
 * <p>Two states are equal when their strings are equal, so every string in the system must come
 * from the same source, {@code BlockData#getAsString()}, for comparisons to mean anything.
 */
public record HistoricalBlockState(String blockData) {

    /** The state of an empty position, and the result of undoing a placement. */
    public static final HistoricalBlockState AIR = new HistoricalBlockState("minecraft:air");

    public HistoricalBlockState {
        Objects.requireNonNull(blockData, "blockData");
    }

}
