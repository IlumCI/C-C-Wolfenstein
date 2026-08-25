package com.ccwolf.core.api;

import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.core.sim.Player;

/**
 * The only way into the simulation from outside.
 *
 * <p>Everything a player does arrives here as a {@link PlayerCommand}, gets checked once —
 * is the match still running, is this player still in it — and is then applied. Nothing
 * upstream of this class can reach in and move a unit or spend a credit directly, which is
 * what lets the whole game be driven from a test with no screen attached.
 */
public final class CommandBus {

    /**
     * Told about every command the world accepted, with the tick it landed on.
     *
     * <p>This is the save system's tap into the match: a deterministic simulation plus the
     * accepted-command log IS the saved game. Rejected commands are deliberately not
     * reported — they changed nothing, so replaying them would only re-ask questions the
     * world already answered no to.
     */
    public interface Listener {
        void onAccepted(long tick, int playerId, PlayerCommand command);
    }

    private final GameWorld world;
    private CommandResult lastResult = CommandResult.accepted();
    private PlayerCommand lastCommand;
    private int commandCount;
    private Listener listener;

    public CommandBus(GameWorld world) {
        this.world = world;
    }

    public GameWorld world() {
        return world;
    }

    /**
     * Validates and applies a command.
     *
     * @return whether it was accepted, and if not, a reason fit to show the player
     */
    public CommandResult submit(int playerId, PlayerCommand command) {
        lastCommand = command;
        lastResult = dispatch(playerId, command);
        commandCount++;
        if (listener != null && lastResult.isAccepted()) {
            listener.onAccepted(world.tick(), playerId, command);
        }
        return lastResult;
    }

    /** At most one listener; the observation cannot affect the simulation. */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private CommandResult dispatch(int playerId, PlayerCommand command) {
        if (command == null) {
            return CommandResult.rejected("No command");
        }
        if (world.isGameOver()) {
            return CommandResult.rejected("The match is over");
        }
        if (playerId < 0 || playerId >= world.players().size()) {
            return CommandResult.rejected("No such player");
        }
        Player player = world.player(playerId);
        if (player.isDefeated()) {
            return CommandResult.rejected("You have been defeated");
        }
        return command.execute(world, playerId);
    }

    /** The most recent outcome, for a HUD that wants to show why something did not happen. */
    public CommandResult lastResult() {
        return lastResult;
    }

    public PlayerCommand lastCommand() {
        return lastCommand;
    }

    /** How many commands have been submitted; handy in tests and for a debug overlay. */
    public int commandCount() {
        return commandCount;
    }
}
