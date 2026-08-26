package io.github.limeisbetterthancitron.timelens.util;

import java.time.Duration;
import java.util.Objects;

/**
 * A validated lookback such as {@code 7d}.
 *
 * <p>The amount and unit are kept separately rather than collapsed into a {@link Duration} so
 * messages can echo the request in the same shape the player typed it ("7 days ago" rather
 * than "168 hours ago").
 */
public record HistoryDuration(long amount, DurationUnit unit) {

    public HistoryDuration {
        Objects.requireNonNull(unit, "unit");
        if (amount <= 0L) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
    }

    public long toSeconds() {
        return Math.multiplyExact(amount, unit.seconds());
    }

    public Duration toDuration() {
        return Duration.ofSeconds(toSeconds());
    }

    /**
     * Renders this duration as English text, for example {@code "7 days"}.
     */
    public String describe() {
        return unit.describe(amount);
    }
}
