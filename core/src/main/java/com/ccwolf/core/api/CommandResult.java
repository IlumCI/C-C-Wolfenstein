package com.ccwolf.core.api;

/**
 * What came of submitting a {@link PlayerCommand}.
 *
 * <p>A rejection carries a reason written for the player, not for a log file, so the HUD can
 * put it straight on screen instead of inventing its own wording.
 */
public final class CommandResult {

    private static final CommandResult ACCEPTED = new CommandResult(true, null);

    private final boolean accepted;
    private final String reason;

    private CommandResult(boolean accepted, String reason) {
        this.accepted = accepted;
        this.reason = reason;
    }

    public static CommandResult accepted() {
        return ACCEPTED;
    }

    public static CommandResult rejected(String reason) {
        return new CommandResult(false, reason);
    }

    public boolean isAccepted() {
        return accepted;
    }

    /** Player-facing explanation, or null when the command was accepted. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return accepted ? "accepted" : "rejected: " + reason;
    }
}
