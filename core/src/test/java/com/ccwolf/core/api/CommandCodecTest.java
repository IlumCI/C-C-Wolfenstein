package com.ccwolf.core.api;

import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.squad.Formation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The save format's alphabet: every command must survive the trip to text and back.
 *
 * <p>Round-tripping is checked by re-encoding the decoded command and comparing lines — a
 * representation equality that needs no equals() on the commands and fails loudly the day a
 * command gains a field its encoding forgot.
 */
public class CommandCodecTest {

    private static void roundTrip(PlayerCommand c) {
        String line = PlayerCommand.Codec.encode(c);
        PlayerCommand back = PlayerCommand.Codec.decode(line);
        assertEquals(line, PlayerCommand.Codec.encode(back), "lossy codec for " + line);
        assertEquals(c.getClass(), back.getClass());
    }

    @Test
    public void everyCommandRoundTrips() {
        int[] ids = {3, 17, 204};
        roundTrip(new PlayerCommand.SquadMove(ids, 12, 34));
        roundTrip(new PlayerCommand.SquadAttackMove(ids, 5, 6));
        roundTrip(new PlayerCommand.SquadAttack(ids, 99));
        roundTrip(new PlayerCommand.SquadStop(ids));
        roundTrip(new PlayerCommand.SquadEntrench(ids, 7, 8));
        roundTrip(new PlayerCommand.SquadEntrench(ids));
        roundTrip(new PlayerCommand.SetFormation(ids, Formation.values()[0]));
        roundTrip(new PlayerCommand.SplitSquad(4, ids));
        roundTrip(new PlayerCommand.Reinforce(11));
        roundTrip(new PlayerCommand.QueueSquad(UnitType.PARTISAN));
        roundTrip(new PlayerCommand.Move(ids, 1, 2, true));
        roundTrip(new PlayerCommand.Move(new int[0], 1, 2, false));
        roundTrip(new PlayerCommand.Entrench(ids, 3, 4));
        roundTrip(new PlayerCommand.Bombard(ids, 60, 61));
        roundTrip(new PlayerCommand.Attack(ids, 42));
        roundTrip(new PlayerCommand.AttackMove(ids, 9, 10));
        roundTrip(new PlayerCommand.Harvest(ids));
        roundTrip(new PlayerCommand.Stop(ids));
        roundTrip(new PlayerCommand.Infiltrate(ids, 55));
        roundTrip(new PlayerCommand.SetRally(21, 30, 31));
        roundTrip(new PlayerCommand.Sell(22));
        roundTrip(new PlayerCommand.Repair(23, true));
        roundTrip(new PlayerCommand.Repair(23, false));
        roundTrip(new PlayerCommand.SetPrimary(24));
        roundTrip(new PlayerCommand.QueueUnit(UnitType.GYROCOPTER));
        roundTrip(new PlayerCommand.QueueBuilding(BuildingType.HELIPAD));
        roundTrip(new PlayerCommand.CancelQueue(PlayerCommand.Line.VEHICLE));
        roundTrip(new PlayerCommand.PlaceBuilding(100, 200));
    }

    @Test
    public void everyUnitAndBuildingTypeSurvivesByName() {
        // The codec stores enums by name; a rename would orphan old saves. This does not
        // forbid renames - it makes them a decision someone takes while looking at this test.
        for (UnitType type : UnitType.values()) {
            roundTrip(new PlayerCommand.QueueUnit(type));
        }
        for (BuildingType type : BuildingType.values()) {
            roundTrip(new PlayerCommand.QueueBuilding(type));
        }
    }

    @Test
    public void garbageIsRefusedNotGuessed() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                PlayerCommand.Codec.decode("Teleport 1 2 3");
            }
        });
    }
}
