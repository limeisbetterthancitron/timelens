package io.github.limeisbetterthancitron.timelens.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * The moment a player asked to see.
 *
 * <p>Two shapes are supported, because the two questions people actually ask are different:
 * "what did this look like a week ago" and "what did this look like on the 20th". Both collapse
 * to an instant, but they are kept distinct so messages can echo the request the way it was
 * made rather than translating one into the other.
 */
public sealed interface HistoryTarget {

    /** The instant being reconstructed, in milliseconds since the epoch. */
    long timestampMillis(long nowMillis);

    /** How to name this moment to a player, for example {@code "7 days ago"}. */
    String describe();

    /**
     * A lookback expressed as an age, such as {@code 7d}.
     */
    record Relative(HistoryDuration duration) implements HistoryTarget {

        public Relative {
            Objects.requireNonNull(duration, "duration");
        }

        @Override
        public long timestampMillis(long nowMillis) {
            return nowMillis - duration.toDuration().toMillis();
        }

        @Override
        public String describe() {
            return duration.describe() + " ago";
        }
    }

    /**
     * A lookback expressed as a calendar moment, such as {@code 2026-08-20 14:30}.
     *
     * <p>Interpreted in the server's own time zone, which is the clock a player reading the
     * server's logs or talking to its owner would be using.
     */
    record Absolute(LocalDateTime moment) implements HistoryTarget {

        private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        public Absolute {
            Objects.requireNonNull(moment, "moment");
        }

        @Override
        public long timestampMillis(long nowMillis) {
            return moment.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }

        @Override
        public String describe() {
            return DISPLAY.format(moment);
        }
    }
}
