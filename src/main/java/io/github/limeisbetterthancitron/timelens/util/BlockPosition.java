package io.github.limeisbetterthancitron.timelens.util;

/**
 * An immutable block coordinate.
 *
 * <p>No world is stored here: a TimeLens snapshot never spans more than one world, so the
 * owning session carries the world and every position inside it is implicitly relative to it.
 * Keeping this type free of Bukkit types is what lets reconstruction run off the server
 * thread and be unit tested without a running server.
 */
public record BlockPosition(int x, int y, int z) {
}
