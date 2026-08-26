package io.github.limeisbetterthancitron.timelens.util;

import java.util.Optional;

/**
 * The time units TimeLens accepts in a lookback argument.
 *
 * <p>Months and years are deliberately absent: their length is ambiguous, so a request such as
 * {@code 1mo} could not be resolved to an exact instant without inventing a convention.
 */
public enum DurationUnit {

    SECONDS('s', 1L, "second"),
    MINUTES('m', 60L, "minute"),
    HOURS('h', 3_600L, "hour"),
    DAYS('d', 86_400L, "day"),
    WEEKS('w', 604_800L, "week");

    private final char suffix;
    private final long seconds;
    private final String singularLabel;

    DurationUnit(char suffix, long seconds, String singularLabel) {
        this.suffix = suffix;
        this.seconds = seconds;
        this.singularLabel = singularLabel;
    }

    public char suffix() {
        return suffix;
    }

    public long seconds() {
        return seconds;
    }

    /**
     * Renders an amount of this unit as English text, for example {@code "7 days"}.
     */
    public String describe(long amount) {
        return amount + " " + (amount == 1L ? singularLabel : singularLabel + "s");
    }

    public static Optional<DurationUnit> fromSuffix(char candidate) {
        char normalised = Character.toLowerCase(candidate);
        for (DurationUnit unit : values()) {
            if (unit.suffix == normalised) {
                return Optional.of(unit);
            }
        }
        return Optional.empty();
    }
}
