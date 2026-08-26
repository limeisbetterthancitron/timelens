package io.github.limeisbetterthancitron.timelens.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetParserTest {

    /** Comfortably after every calendar moment used below, so none of them look like the future. */
    private static final long NOW = Instant.parse("2026-12-01T12:00:00Z").toEpochMilli();

    @ParameterizedTest(name = "{0} is read as an age")
    @ValueSource(strings = {"30s", "10m", "2h", "7d", "2w"})
    void agesStayRelative(String input) throws Exception {
        assertInstanceOf(HistoryTarget.Relative.class, TargetParser.parse(input, NOW));
    }

    @Test
    @DisplayName("an age is measured back from the moment it was asked")
    void ageIsMeasuredFromNow() throws Exception {
        HistoryTarget target = TargetParser.parse("7d", NOW);

        assertEquals(NOW - Duration.ofDays(7).toMillis(), target.timestampMillis(NOW));
        assertEquals("7 days ago", target.describe());
    }

    @Test
    @DisplayName("a bare date means the start of that day")
    void bareDateStartsAtMidnight() throws Exception {
        HistoryTarget target = TargetParser.parse("2026-08-20", NOW);

        assertEquals(LocalDate.of(2026, 8, 20).atStartOfDay(), moment(target));
        assertEquals("2026-08-20 00:00", target.describe());
    }

    @ParameterizedTest(name = "{0} is read as 2026-08-20 14:30")
    @ValueSource(strings = {"2026-08-20 14:30", "2026-08-20T14:30", "2026-08-20  14:30"})
    void dateAndTimeAreAccepted(String input) throws Exception {
        assertEquals(LocalDateTime.of(2026, 8, 20, 14, 30), moment(TargetParser.parse(input, NOW)));
    }

    @Test
    @DisplayName("seconds may be given as well")
    void secondsAreAccepted() throws Exception {
        assertEquals(LocalDateTime.of(2026, 8, 20, 14, 30, 45),
                moment(TargetParser.parse("2026-08-20 14:30:45", NOW)));
    }

    @Test
    @DisplayName("a calendar moment resolves in the server's own time zone")
    void absoluteUsesServerZone() throws Exception {
        HistoryTarget target = TargetParser.parse("2026-08-20 14:30", NOW);

        long expected = LocalDateTime.of(2026, 8, 20, 14, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(expected, target.timestampMillis(NOW));
    }

    @Test
    @DisplayName("a bare clock time means earlier today")
    void bareTimeMeansToday() throws Exception {
        // Anchored two days ahead so the time of day is always already past.
        long farFuture = System.currentTimeMillis() + Duration.ofDays(2).toMillis();

        LocalDateTime parsed = moment(TargetParser.parse("14:30", farFuture));

        assertEquals(LocalDate.now(), parsed.toLocalDate());
        assertEquals(LocalTime.of(14, 30), parsed.toLocalTime());
    }

    @Test
    @DisplayName("a moment that has not happened yet is refused")
    void futureMomentIsRefused() {
        InvalidTimeException failure = assertThrows(InvalidTimeException.class,
                () -> TargetParser.parse("2026-08-20", Instant.parse("2026-08-19T12:00:00Z").toEpochMilli()));

        assertTrue(failure.getMessage().contains("future"), failure.getMessage());
    }

    @ParameterizedTest(name = "{0} is rejected")
    @ValueSource(strings = {
            "hello",
            "2026-13-40",
            "2026-08-20 99:99",
            "2026-08-20 banana",
            "-",
            ":",
            "20-08-2026",
            ""
    })
    void rejectsUnusableInput(String input) {
        assertThrows(InvalidTimeException.class, () -> TargetParser.parse(input, NOW));
    }

    @Test
    @DisplayName("failures suggest both an age and a date")
    void failureMessagesShowBothForms() {
        InvalidTimeException failure =
                assertThrows(InvalidTimeException.class, () -> TargetParser.parse("nonsense", NOW));

        assertTrue(failure.getMessage().contains("7d"), failure.getMessage());
        assertTrue(failure.getMessage().contains("2026-08-20"), failure.getMessage());
    }

    @Test
    @DisplayName("null is refused rather than treated as now")
    void nullIsRefused() {
        assertThrows(InvalidTimeException.class, () -> TargetParser.parse(null, NOW));
    }

    private static LocalDateTime moment(HistoryTarget target) {
        return assertInstanceOf(HistoryTarget.Absolute.class, target).moment();
    }
}
