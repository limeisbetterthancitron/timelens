package io.github.limeisbetterthancitron.timelens.util;

import java.io.Serial;

/**
 * Thrown when a lookback argument cannot be understood.
 *
 * <p>The message is written for the person who typed the value, so it is safe to show directly
 * to a player alongside an example of valid input.
 */
public class InvalidTimeException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String input;

    public InvalidTimeException(String input, String message) {
        super(message);
        this.input = input;
    }

    /**
     * The raw text that failed to parse, for echoing back to the player.
     */
    public String input() {
        return input;
    }
}
