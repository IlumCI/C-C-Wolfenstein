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

    private final GameWorld world;
    private CommandResult lastResult = CommandResult.accepted();
    private PlayerCommand lastCommand;
    private int commandCount;

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
        return lastResult;
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
