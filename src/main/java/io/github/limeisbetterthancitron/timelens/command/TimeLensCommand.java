package io.github.limeisbetterthancitron.timelens.command;

import io.github.limeisbetterthancitron.timelens.config.TimeLensConfig;
import io.github.limeisbetterthancitron.timelens.message.Messages;
import io.github.limeisbetterthancitron.timelens.session.TimelineSessionManager;
import io.github.limeisbetterthancitron.timelens.util.HistoryTarget;
import io.github.limeisbetterthancitron.timelens.util.InvalidTimeException;
import io.github.limeisbetterthancitron.timelens.util.TargetParser;
import io.github.limeisbetterthancitron.timelens.view.HistoryViewCoordinator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Parses {@code /timelens} and hands the work to {@link HistoryViewCoordinator}.
 *
 * <p>Deliberately thin: argument handling and permissions live here, the pipeline does not.
 *
 * <p>The grammar is {@code /timelens <when> [radius]}. A date and a clock time arrive as two
 * separate words, so they are rejoined before parsing; a colon in the second word is what
 * distinguishes {@code 2026-08-20 14:30} from {@code 7d 64}.
 */
public final class TimeLensCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERMISSION = "timelens.use";

    private static final String HELP = "help";
    private static final String EXIT = "exit";
    private static final String STATUS = "status";

    private static final List<String> SUBCOMMANDS = List.of(EXIT, STATUS, HELP);
    private static final List<String> WHEN_EXAMPLES = List.of("30m", "2h", "1d", "7d", "2w");
    private static final List<String> RADIUS_EXAMPLES = List.of("16", "32", "64", "128");

    /** date, optional clock time, optional radius. */
    private static final int MAX_ARGUMENTS = 3;

    private static final long MILLIS_PER_SECOND = 1_000L;

    private final TimeLensConfig config;
    private final HistoryViewCoordinator coordinator;
    private final TimelineSessionManager sessions;
    private final Messages messages;

    public TimeLensCommand(TimeLensConfig config,
                           HistoryViewCoordinator coordinator,
                           TimelineSessionManager sessions,
                           Messages messages) {
        this.config = config;
        this.coordinator = coordinator;
        this.sessions = sessions;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            sender.sendMessage(messages.noPermission());
            return true;
        }
        if (args.length == 0 || args.length > MAX_ARGUMENTS) {
            sender.sendMessage(messages.help());
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (HELP.equals(first)) {
            sender.sendMessage(messages.help());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.playersOnly());
            return true;
        }

        switch (first) {
            case EXIT -> coordinator.closeView(player);
            case STATUS -> sendStatus(player);
            default -> openView(player, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(USE_PERMISSION)) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> matching(whenSuggestions(), args[0]);
            case 2 -> isSubcommand(args[0]) ? List.of() : matching(RADIUS_EXAMPLES, args[1]);
            default -> List.of();
        };
    }

    private static List<String> whenSuggestions() {
        List<String> suggestions = new ArrayList<>(SUBCOMMANDS);
        suggestions.addAll(WHEN_EXAMPLES);
        // Today's date, so the calendar form is discoverable without reading the help.
        suggestions.add(LocalDate.now().toString());
        return suggestions;
    }

    private static List<String> matching(List<String> options, String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private static boolean isSubcommand(String argument) {
        return SUBCOMMANDS.contains(argument.toLowerCase(Locale.ROOT));
    }

    private void openView(Player player, String[] args) {
        String when = args[0];
        int radiusIndex = 1;
        if (args.length >= 2 && args[1].indexOf(':') >= 0) {
            when = args[0] + " " + args[1];
            radiusIndex = 2;
        }

        int radius = config.radius();
        int verticalRadius = config.verticalRadius();
        if (args.length > radiusIndex) {
            OptionalInt requested = parseRadius(args[radiusIndex]);
            if (requested.isEmpty()) {
                player.sendMessage(messages.invalidRadius(args[radiusIndex], config.maximumRadius()));
                return;
            }
            radius = requested.getAsInt();
            verticalRadius = requested.getAsInt();
        }

        long now = System.currentTimeMillis();
        HistoryTarget target;
        try {
            target = TargetParser.parse(when, now);
        } catch (InvalidTimeException exception) {
            player.sendMessage(messages.invalidTime(exception.getMessage()));
            return;
        }

        long lookbackSeconds = (now - target.timestampMillis(now)) / MILLIS_PER_SECOND;
        if (lookbackSeconds > config.maximumLookback().toSeconds()) {
            player.sendMessage(messages.lookbackTooLong(target.describe(), config.maximumLookback().describe()));
            return;
        }

        coordinator.openView(player, target, radius, verticalRadius, config);
    }

    private OptionalInt parseRadius(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 1 || value > config.maximumRadius()) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(value);
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private void sendStatus(Player player) {
        sessions.find(player.getUniqueId()).ifPresentOrElse(
                session -> player.sendMessage(messages.status(session, System.currentTimeMillis())),
                () -> player.sendMessage(messages.notViewing()));
    }
}
