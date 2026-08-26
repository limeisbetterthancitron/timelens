package io.github.limeisbetterthancitron.timelens.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Turns whatever a player typed for "when" into a {@link HistoryTarget}.
 *
 * <p>Accepts an age ({@code 7d}) or a calendar moment ({@code 2026-08-20},
 * {@code 2026-08-20 14:30}, or just {@code 14:30} for earlier today). Which one was meant is
 * decided by shape rather than by a flag, because the two forms cannot be confused: an age
 * always ends in a unit letter, and a calendar moment always contains a dash or a colon.
 */
public final class TargetParser {

    private static final Pattern LOOKS_LIKE_CALENDAR = Pattern.compile(".*[-:].*");

    private static final String EXAMPLES =
            "Try an age such as 30m, 2h, 7d or 2w, or a date such as 2026-08-20 or 2026-08-20 14:30.";

    private static final String DATE_HINT =
            "You can also give a date, such as 2026-08-20 or 2026-08-20 14:30.";

    private TargetParser() {
    }

    /**
     * @param input       the raw "when" text, already joined if the player typed a date and time
     *                    as two words
     * @param nowMillis   the current time, used to reject moments that have not happened yet
     * @throws InvalidTimeException if the text is neither a valid age nor a valid moment, with a
     *                              message safe to show a player
     */
    public static HistoryTarget parse(String input, long nowMillis) throws InvalidTimeException {
        String candidate = input == null ? "" : input.trim();
        if (candidate.isEmpty()) {
            throw new InvalidTimeException(candidate, "No time was given. " + EXAMPLES);
        }

        if (!LOOKS_LIKE_CALENDAR.matcher(candidate).matches()) {
            try {
                return new HistoryTarget.Relative(DurationParser.parse(candidate));
            } catch (InvalidTimeException exception) {
                // The duration parser only knows about ages, but a date would have been just as
                // valid here, so its advice has to be widened before a player sees it.
                throw new InvalidTimeException(candidate, exception.getMessage() + " " + DATE_HINT);
            }
        }

        LocalDateTime moment = parseMoment(candidate);
        HistoryTarget target = new HistoryTarget.Absolute(moment);
        if (target.timestampMillis(nowMillis) > nowMillis) {
            throw new InvalidTimeException(candidate,
                    "The world has no history from " + target.describe() + " yet. That is in the future.");
        }
        return target;
    }

    private static LocalDateTime parseMoment(String candidate) throws InvalidTimeException {
        String normalised = candidate.replace('T', ' ').replaceAll("\\s+", " ");

        // Longest form first: anything shorter is a prefix of it, so trying the other way round
        // would quietly discard the time part of a full timestamp.
        try {
            return LocalDateTime.parse(normalised.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
            // Not a full date-and-time; fall through to the shorter forms.
        }

        int split = normalised.indexOf(' ');
        if (split > 0) {
            try {
                LocalDate date = LocalDate.parse(normalised.substring(0, split));
                return date.atTime(parseTime(normalised.substring(split + 1), candidate));
            } catch (DateTimeParseException exception) {
                throw invalid(candidate);
            }
        }

        try {
            return LocalDate.parse(normalised).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // Not a bare date either; the last possibility is a time on its own.
        }

        if (normalised.indexOf(':') >= 0) {
            return LocalDate.now().atTime(parseTime(normalised, candidate));
        }
        throw invalid(candidate);
    }

    /**
     * Accepts {@code HH:mm} and {@code HH:mm:ss}, which is what {@link LocalTime#parse} already
     * handles.
     */
    private static LocalTime parseTime(String text, String original) throws InvalidTimeException {
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw invalid(original);
        }
    }

    private static InvalidTimeException invalid(String candidate) {
        return new InvalidTimeException(candidate, "Invalid time '" + candidate + "'. " + EXAMPLES);
    }
}
