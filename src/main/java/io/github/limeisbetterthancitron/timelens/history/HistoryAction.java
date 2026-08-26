package io.github.limeisbetterthancitron.timelens.history;

/**
 * The kinds of recorded change TimeLens can reverse.
 *
 * <p>Only block state matters in v0.1.0, so container transactions, chat, commands, sessions
 * and interactions are filtered out before events reach this type.
 */
public enum HistoryAction {

    /** A block was added at a position. */
    PLACE,

    /** A block was taken away from a position. */
    REMOVE
}
