package io.github.limeisbetterthancitron.timelens.session;

/**
 * Lifecycle of a single viewing session.
 *
 * <p>Loading is not a state here: no session exists until a snapshot has actually been rendered,
 * so an in-flight request is tracked separately by {@link TimelineSessionManager}.
 */
public enum SessionState {

    /** The viewer is currently being shown historical blocks. */
    ACTIVE,

    /** The session has been torn down; it must never be restored or reused again. */
    ENDED
}
