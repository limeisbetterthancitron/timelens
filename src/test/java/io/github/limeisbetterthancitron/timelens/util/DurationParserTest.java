package io.github.limeisbetterthancitron.timelens.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @ParameterizedTest(name = "{0} parses as {1} x {2}")
    @CsvSource({
            "30s, 30, SECONDS",
            "10m, 10, MINUTES",
            "2h,  2,  HOURS",
            "7d,  7,  DAYS",
            "2w,  2,  WEEKS",
            "1s,  1,  SECONDS"
    })
    void parsesSupportedUnits(String input, long expectedAmount, DurationUnit expectedUnit) throws Exception {
        HistoryDuration parsed = DurationParser.parse(input);

        assertEquals(expectedAmount, parsed.amount());
        assertEquals(expectedUnit, parsed.unit());
    }

    @ParameterizedTest(name = "{0} is accepted")
    @ValueSource(strings = {"7D", "2W", "30S", "10M", "2H"})
    void unitSuffixIsCaseInsensitive(String input) throws Exception {
        assertTrue(DurationParser.parse(input).toSeconds() > 0L);
    }

    @ParameterizedTest(name = "surrounding whitespace in {0} is ignored")
    @ValueSource(strings = {" 7d", "7d ", "  7d  "})
    void surroundingWhitespaceIsIgnored(String input) throws Exception {
        assertEquals(7L, DurationParser.parse(input).amount());
    }

    @Test
    @DisplayName("a space between amount and unit is accepted")
    void spaceBetweenAmountAndUnitIsAccepted() throws Exception {
        assertEquals(DurationUnit.DAYS, DurationParser.parse("7 d").unit());
    }

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(strings = {
            "hello",
            "30",
            "-2d",
            "0d",
            "0s",
            "2months",
            "999999999999999999d",
            "d",
            "7dd",
            "7x",
            "7.5d",
            "",
            "   "
    })
    void rejectsInvalidInput(String input) {
        assertThrows(InvalidTimeException.class, () -> DurationParser.parse(input));
    }

    @ParameterizedTest
    @NullSource
    void rejectsNullInput(String input) {
        assertThrows(InvalidTimeException.class, () -> DurationParser.parse(input));
    }

    @Test
    @DisplayName("an amount too large to be meaningful is refused before any arithmetic")
    void oversizedAmountIsRefused() {
        InvalidTimeException failure =
                assertThrows(InvalidTimeException.class, () -> DurationParser.parse("999999999999999999d"));

        assertTrue(failure.getMessage().contains("too large"));
    }

    @Test
    @DisplayName("the largest accepted amount still converts without overflowing")
    void largestAcceptedAmountDoesNotOverflow() throws Exception {
        HistoryDuration parsed = DurationParser.parse("9999999999w");

        assertTrue(parsed.toSeconds() > 0L, "conversion must stay positive");
    }

    @Test
    @DisplayName("failure messages name the offending input and show valid examples")
    void failureMessagesAreActionable() {
        InvalidTimeException failure =
                assertThrows(InvalidTimeException.class, () -> DurationParser.parse("7x"));

        assertEquals("7x", failure.input());
        assertTrue(failure.getMessage().contains("7x"), "message should quote the bad value");
        assertTrue(failure.getMessage().contains("7d"), "message should suggest a valid value");
    }

    @Test
    @DisplayName("an unknown multi-letter unit is named in the failure")
    void unknownUnitIsNamed() {
        InvalidTimeException failure =
                assertThrows(InvalidTimeException.class, () -> DurationParser.parse("2months"));

        assertTrue(failure.getMessage().contains("months"));
    }

    @ParameterizedTest(name = "{0} converts to {1} seconds")
    @CsvSource({
            "30s, 30",
            "10m, 600",
            "2h,  7200",
            "7d,  604800",
            "2w,  1209600"
    })
    void convertsToSeconds(String input, long expectedSeconds) throws Exception {
        assertEquals(expectedSeconds, DurationParser.parse(input).toSeconds());
    }

    @ParameterizedTest(name = "{0} reads as \"{1}\"")
    @CsvSource({
            "7d,  7 days",
            "1d,  1 day",
            "1w,  1 week",
            "2w,  2 weeks",
            "30m, 30 minutes",
            "1s,  1 second"
    })
    void describesItselfInPlainEnglish(String input, String expected) throws Exception {
        assertEquals(expected, DurationParser.parse(input).describe());
    }

    @Test
    @DisplayName("a zero or negative amount cannot be constructed directly either")
    void durationRejectsNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryDuration(0L, DurationUnit.DAYS));
        assertThrows(IllegalArgumentException.class, () -> new HistoryDuration(-1L, DurationUnit.DAYS));
    }

    @Test
    @DisplayName("every supported suffix maps back to its unit, and nothing else does")
    void suffixLookupCoversExactlyTheSupportedUnits() {
        for (DurationUnit unit : DurationUnit.values()) {
            assertEquals(unit, DurationUnit.fromSuffix(unit.suffix()).orElseThrow());
            assertEquals(unit, DurationUnit.fromSuffix(Character.toUpperCase(unit.suffix())).orElseThrow());
        }
        assertFalse(DurationUnit.fromSuffix('y').isPresent(), "years are deliberately unsupported");
        assertFalse(DurationUnit.fromSuffix('o').isPresent(), "months are deliberately unsupported");
    }
}
