package io.github.limeisbetterthancitron.timelens.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses lookback arguments such as {@code 30m}, {@code 2h}, {@code 7d} or {@code 2w}.
 *
 * <p>Parsing lives here rather than in the command so the same rules apply to player input and
 * to {@code history.maximum-lookback} in the configuration file.
 */
public final class DurationParser {

    /**
     * Ten digits is the widest amount that cannot overflow a {@code long} once multiplied by
     * the longest supported unit, so oversized input is rejected before any arithmetic runs.
     */
    private static final int MAX_AMOUNT_DIGITS = 10;

    private static final Pattern AMOUNT_AND_UNIT = Pattern.compile("^(\\d+)\\s*([a-zA-Z]+)$");

    private static final String EXAMPLES = "Try values such as 30m, 2h, 7d, or 2w.";

    private DurationParser() {
    }

    /**
     * @param input raw text such as {@code "7d"}; surrounding whitespace is ignored
     * @throws InvalidTimeException if the text is not a positive amount followed by a
     *                                  supported unit, with a message safe to show a player
     */
    public static HistoryDuration parse(String input) throws InvalidTimeException {
        String candidate = input == null ? "" : input.trim();
        if (candidate.isEmpty()) {
            throw new InvalidTimeException(candidate, "No time was given. " + EXAMPLES);
        }

        Matcher matcher = AMOUNT_AND_UNIT.matcher(candidate);
        if (!matcher.matches()) {
            throw new InvalidTimeException(candidate, "Invalid time '" + candidate + "'. " + EXAMPLES);
        }

        String digits = matcher.group(1);
        String unitText = matcher.group(2);

        // A multi-letter unit is almost always a real word, so naming it back is more useful
        // than repeating the whole argument.
        if (unitText.length() != 1) {
            throw new InvalidTimeException(candidate,
                    "Unknown time unit '" + unitText + "'. " + EXAMPLES);
        }

        Optional<DurationUnit> unit = DurationUnit.fromSuffix(unitText.charAt(0));
        if (unit.isEmpty()) {
            throw new InvalidTimeException(candidate,
                    "Invalid time '" + candidate + "'. " + EXAMPLES);
        }

        if (digits.length() > MAX_AMOUNT_DIGITS) {
            throw new InvalidTimeException(candidate,
                    "Time '" + candidate + "' is far too large. " + EXAMPLES);
        }

        long amount = Long.parseLong(digits);
        if (amount == 0L) {
            throw new InvalidTimeException(candidate,
                    "Time must be greater than zero. " + EXAMPLES);
        }

        return new HistoryDuration(amount, unit.get());
    }
}
