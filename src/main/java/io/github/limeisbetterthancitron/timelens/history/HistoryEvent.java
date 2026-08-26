package io.github.limeisbetterthancitron.timelens.history;

import io.github.limeisbetterthancitron.timelens.util.BlockPosition;

import java.util.Objects;
import java.util.Optional;

/**
 * One recorded block change, translated out of whatever the history backend stores natively.
 *
 * @param position        where the change happened
 * @param action          whether a block was added or taken away
 * @param timestampMillis when the change happened, in milliseconds since the epoch
 * @param state           the block the change concerns — the block that was placed for
 *                        {@link HistoryAction#PLACE}, the block that was taken away for
 *                        {@link HistoryAction#REMOVE}. Empty when the backend recorded the
 *                        change but not a usable block state; see
 *                        {@link io.github.limeisbetterthancitron.timelens.reconstruction.HistoricalReconstructor}
 *                        for how that gap is handled.
 */
public record HistoryEvent(BlockPosition position,
                           HistoryAction action,
                           long timestampMillis,
                           Optional<HistoricalBlockState> state) {

    public HistoryEvent {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(state, "state");
    }
}
