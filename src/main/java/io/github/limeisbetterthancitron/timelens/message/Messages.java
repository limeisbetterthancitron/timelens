package io.github.limeisbetterthancitron.timelens.message;

import io.github.limeisbetterthancitron.timelens.session.TimelineSession;
import io.github.limeisbetterthancitron.timelens.util.BlockPosition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Every player-facing string, built as Adventure components.
 *
 * <p>Keeping them together makes the voice of the plugin consistent and keeps formatting out of
 * the command and listener classes. Only the prefix is configurable in v0.1.0; making each
 * sentence configurable would be bloat before anyone has asked for it.
 */
public final class Messages {

    private static final NamedTextColor BODY = NamedTextColor.GRAY;
    private static final NamedTextColor HIGHLIGHT = NamedTextColor.GREEN;
    private static final NamedTextColor DETAIL = NamedTextColor.AQUA;
    private static final NamedTextColor PROBLEM = NamedTextColor.RED;

    private static final String FALLBACK_PREFIX = "TimeLens > ";

    /** Root locale keeps thousands separators the same regardless of the server's locale. */
    private static final NumberFormat COUNT_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);

    private static final int SECONDS_PER_MINUTE = 60;

    private final Component prefix;

    public Messages(String prefixMarkup, Logger logger) {
        this.prefix = parsePrefix(prefixMarkup, logger);
    }

    private static Component parsePrefix(String markup, Logger logger) {
        try {
            return MiniMessage.miniMessage().deserialize(markup);
        } catch (RuntimeException exception) {
            logger.warning("messages.prefix is not valid MiniMessage (" + exception.getMessage()
                    + "); using a plain prefix instead.");
            return Component.text(FALLBACK_PREFIX, NamedTextColor.GREEN);
        }
    }

    private Component line(Component body) {
        return prefix.append(body);
    }

    public Component loading(String moment) {
        return line(Component.text("Loading world history from ", BODY)
                .append(Component.text(moment, DETAIL))
                .append(Component.text("...", BODY)));
    }

    public Component viewing(String moment) {
        return line(Component.text("Viewing the world from ", BODY)
                .append(Component.text(moment, HIGHLIGHT))
                .append(Component.text(".", BODY)));
    }

    /**
     * The radius is reported because a view that looks incomplete is nearly always a radius that
     * was smaller than the build, and the number is the only clue the player has.
     */
    public Component rendered(int changes, int radius) {
        return line(Component.text(COUNT_FORMAT.format(changes), HIGHLIGHT)
                .append(Component.text(" blocks restored within ", BODY))
                .append(Component.text(radius + " blocks", DETAIL))
                .append(Component.text(" of you.", BODY)));
    }

    public Component radiusHint(int maximumRadius) {
        return line(Component.text("Missing part of a build? Widen the view with ", BODY)
                .append(Component.text("/timelens <when> <radius>", DETAIL))
                .append(Component.text(" up to " + maximumRadius + ".", BODY)));
    }

    public Component invalidRadius(String given, int maximumRadius) {
        return line(Component.text("'" + given + "' is not a usable radius. Give a whole number "
                + "between 1 and " + maximumRadius + ".", PROBLEM));
    }

    public Component exitHint() {
        return line(Component.text("Use ", BODY)
                .append(Component.text("/timelens exit", DETAIL))
                .append(Component.text(" to return to the present.", BODY)));
    }

    public Component noHistory() {
        return line(Component.text("No recorded block changes were found in this area for that period.", BODY));
    }

    public Component alreadyLoading() {
        return line(Component.text("A historical view is already loading.", BODY));
    }

    public Component returnedToPresent() {
        return line(Component.text("Returned to the present.", HIGHLIGHT));
    }

    public Component notViewing() {
        return line(Component.text("You are not viewing the past. Try ", BODY)
                .append(Component.text("/timelens 7d", DETAIL))
                .append(Component.text(".", BODY)));
    }

    public Component viewEnded(String reason) {
        return line(Component.text("Your historical view ended because you " + reason + ".", BODY));
    }

    public Component invalidTime(String explanation) {
        return line(Component.text(explanation, PROBLEM));
    }

    public Component lookbackTooLong(String requested, String maximum) {
        return line(Component.text("Cannot look back ", PROBLEM)
                .append(Component.text(requested, PROBLEM))
                .append(Component.text(". This server allows at most ", PROBLEM))
                .append(Component.text(maximum, DETAIL))
                .append(Component.text(".", PROBLEM)));
    }

    public Component tooManyChanges(int matched, int limit) {
        return line(Component.text("That view covers ", PROBLEM)
                .append(Component.text(COUNT_FORMAT.format(matched), PROBLEM))
                .append(Component.text(" changes, over this server's limit of ", PROBLEM))
                .append(Component.text(COUNT_FORMAT.format(limit), DETAIL))
                .append(Component.text(". Try a shorter time range, such as ", PROBLEM))
                .append(Component.text("/timelens 1h", DETAIL))
                .append(Component.text(".", PROBLEM)));
    }

    public Component tooManyBlocks(int positions, int limit) {
        return line(Component.text("That view works out to ", PROBLEM)
                .append(Component.text(COUNT_FORMAT.format(positions), PROBLEM))
                .append(Component.text(" blocks, more than TimeLens will render at once (", PROBLEM))
                .append(Component.text(COUNT_FORMAT.format(limit), DETAIL))
                .append(Component.text("). Try a smaller radius or a shorter time range.", PROBLEM)));
    }

    public Component lookupFailed() {
        return line(Component.text("Could not read world history. Ask an administrator to check the server console.",
                PROBLEM));
    }

    public Component playersOnly() {
        return line(Component.text("TimeLens can only be used by a player in a world.", PROBLEM));
    }

    public Component noPermission() {
        return line(Component.text("You do not have permission to use TimeLens.", PROBLEM));
    }

    public Component interactionBlocked() {
        return Component.text("Blocked while viewing the past — use /timelens exit first.", BODY);
    }

    public Component help() {
        return line(Component.text("Commands:", BODY))
                .append(Component.newline())
                .append(Component.text("  /timelens <when> [radius]", DETAIL))
                .append(Component.text("  view the past", BODY))
                .append(Component.newline())
                .append(Component.text("  /timelens exit", DETAIL))
                .append(Component.text("             return to the present", BODY))
                .append(Component.newline())
                .append(Component.text("  /timelens status", DETAIL))
                .append(Component.text("           show your current view", BODY))
                .append(Component.newline())
                .append(Component.text("<when> ", BODY))
                .append(Component.text("30m 2h 7d 2w", DETAIL))
                .append(Component.text(" or a date ", BODY))
                .append(Component.text("2026-08-20 14:30", DETAIL));
    }

    public Component status(TimelineSession session, long nowMillis) {
        BlockPosition center = session.center();
        String elapsed = describeElapsed((nowMillis - session.startedAtMillis()) / 1000L);
        return line(Component.text("Viewing ", BODY)
                .append(Component.text(session.targetDescription(), HIGHLIGHT))
                .append(Component.text(" at ", BODY))
                .append(Component.text(center.x() + ", " + center.y() + ", " + center.z(), DETAIL))
                .append(Component.text(" in ", BODY))
                .append(Component.text(session.worldName(), DETAIL))
                .append(Component.text(".", BODY)))
                .append(Component.newline())
                .append(line(Component.text(COUNT_FORMAT.format(session.renderedPositions().size()), HIGHLIGHT)
                        .append(Component.text(" blocks shown, started " + elapsed + " ago.", BODY))));
    }

    private static String describeElapsed(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        if (safeSeconds < SECONDS_PER_MINUTE) {
            return safeSeconds + "s";
        }
        return (safeSeconds / SECONDS_PER_MINUTE) + "m " + (safeSeconds % SECONDS_PER_MINUTE) + "s";
    }
}
