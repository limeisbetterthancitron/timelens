package io.github.limeisbetterthancitron.timelens.config;

import io.github.limeisbetterthancitron.timelens.util.DurationParser;
import io.github.limeisbetterthancitron.timelens.util.DurationUnit;
import io.github.limeisbetterthancitron.timelens.util.HistoryDuration;
import io.github.limeisbetterthancitron.timelens.util.InvalidTimeException;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Validated view of {@code config.yml}.
 *
 * <p>Loading never fails: an unusable value is reported and replaced with the shipped default,
 * so a typo in the configuration cannot stop the plugin from enabling.
 *
 * @param radius            how far the view reaches along X and Z
 * @param verticalRadius    how far the view reaches along Y
 * @param maximumRadius     the ceiling a server owner allows for either radius
 * @param maximumLookback   the furthest back a player may ask to see
 * @param maximumResults    refuse to reconstruct a view larger than this many recorded changes
 * @param freezeMovement    hold the viewer at the spot the view was taken from
 * @param blockInteractions stop the viewer breaking, placing and interacting while viewing
 * @param messagePrefix     MiniMessage markup placed in front of every player-facing message
 */
public record TimeLensConfig(int radius,
                             int verticalRadius,
                             int maximumRadius,
                             HistoryDuration maximumLookback,
                             int maximumResults,
                             boolean freezeMovement,
                             boolean blockInteractions,
                             String messagePrefix) {

    private static final int DEFAULT_RADIUS = 48;
    private static final int DEFAULT_VERTICAL_RADIUS = 48;
    private static final int DEFAULT_MAXIMUM_RADIUS = 96;
    private static final int DEFAULT_MAXIMUM_RESULTS = 25_000;
    private static final HistoryDuration DEFAULT_MAXIMUM_LOOKBACK =
            new HistoryDuration(30L, DurationUnit.DAYS);
    private static final boolean DEFAULT_FREEZE_MOVEMENT = true;
    private static final boolean DEFAULT_BLOCK_INTERACTIONS = true;
    private static final String DEFAULT_PREFIX = "<green>TimeLens <dark_gray>›</dark_gray> ";

    /**
     * A hard ceiling for the configured maximum. Radius is a poor proxy for cost, because what
     * actually matters is how much recorded history sits inside the volume, so
     * {@code history.maximum-results} is the real brake and this only stops absurd values.
     */
    private static final int ABSOLUTE_MAX_RADIUS = 256;

    /** Far past the point where a single view is worth rendering; a backstop against typos. */
    private static final int ABSOLUTE_MAX_RESULTS = 1_000_000;

    private static final String RADIUS_PATH = "view.radius";
    private static final String VERTICAL_RADIUS_PATH = "view.vertical-radius";
    private static final String MAXIMUM_RADIUS_PATH = "view.maximum-radius";
    private static final String FREEZE_MOVEMENT_PATH = "view.freeze-movement";
    private static final String BLOCK_INTERACTIONS_PATH = "view.block-interactions";
    private static final String MAXIMUM_LOOKBACK_PATH = "history.maximum-lookback";
    private static final String MAXIMUM_RESULTS_PATH = "history.maximum-results";
    private static final String PREFIX_PATH = "messages.prefix";

    public TimeLensConfig {
        Objects.requireNonNull(maximumLookback, "maximumLookback");
        Objects.requireNonNull(messagePrefix, "messagePrefix");
    }

    public static TimeLensConfig load(ConfigurationSection config, Logger logger) {
        int maximumRadius = readBoundedInt(config, MAXIMUM_RADIUS_PATH,
                DEFAULT_MAXIMUM_RADIUS, 1, ABSOLUTE_MAX_RADIUS, logger);

        // The two view radii are capped by maximum-radius, so it has to be resolved first.
        int radius = readBoundedInt(config, RADIUS_PATH, DEFAULT_RADIUS, 1, maximumRadius, logger);
        int verticalRadius = readBoundedInt(config, VERTICAL_RADIUS_PATH,
                DEFAULT_VERTICAL_RADIUS, 1, maximumRadius, logger);

        return new TimeLensConfig(radius,
                verticalRadius,
                maximumRadius,
                readDuration(config, MAXIMUM_LOOKBACK_PATH, DEFAULT_MAXIMUM_LOOKBACK, logger),
                readBoundedInt(config, MAXIMUM_RESULTS_PATH, DEFAULT_MAXIMUM_RESULTS,
                        1, ABSOLUTE_MAX_RESULTS, logger),
                config.getBoolean(FREEZE_MOVEMENT_PATH, DEFAULT_FREEZE_MOVEMENT),
                config.getBoolean(BLOCK_INTERACTIONS_PATH, DEFAULT_BLOCK_INTERACTIONS),
                readPrefix(config, logger));
    }

    private static int readBoundedInt(ConfigurationSection config,
                                      String path,
                                      int fallback,
                                      int minimum,
                                      int maximum,
                                      Logger logger) {
        if (!config.contains(path)) {
            return fallback;
        }
        if (!config.isInt(path)) {
            logger.warning(path + " must be a whole number; using " + fallback + " instead.");
            return fallback;
        }

        int value = config.getInt(path);
        if (value < minimum || value > maximum) {
            int clamped = Math.clamp(value, minimum, maximum);
            logger.warning(path + " must be between " + minimum + " and " + maximum
                    + "; using " + clamped + " instead of " + value + ".");
            return clamped;
        }
        return value;
    }

    private static HistoryDuration readDuration(ConfigurationSection config,
                                                String path,
                                                HistoryDuration fallback,
                                                Logger logger) {
        String raw = config.getString(path);
        if (raw == null) {
            return fallback;
        }
        try {
            return DurationParser.parse(raw);
        } catch (InvalidTimeException exception) {
            logger.warning(path + " is not a valid duration (" + exception.getMessage()
                    + "); using " + fallback.describe() + " instead.");
            return fallback;
        }
    }



    private static String readPrefix(ConfigurationSection config, Logger logger) {
        String prefix = config.getString(PREFIX_PATH);
        if (prefix == null) {
            return DEFAULT_PREFIX;
        }
        if (prefix.isBlank()) {
            logger.warning(PREFIX_PATH + " is empty; using the default prefix instead.");
            return DEFAULT_PREFIX;
        }
        return prefix;
    }
}
