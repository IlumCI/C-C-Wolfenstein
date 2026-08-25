package com.ccwolf.core.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccwolf.core.entity.UnitType;
import org.junit.jupiter.api.Test;

/**
 * The one constraint a formation must not break.
 *
 * <p>Slots closer together than two unit radii put members permanently inside one another.
 * Separation steering then fights the formation every tick — it pushes them apart, the
 * formation pulls them back — and the squad becomes a jitter generator that never settles.
 * It is a quiet failure: nothing crashes, the squad just vibrates.
 */
public class FormationTest {

    @Test
    public void everySlotIsClearOfEveryOtherInEveryFormation() {
        // The widest body that forms squads decides the constraint for all of them.
        float radius = UnitType.PARTISAN.radius();
        float minimum = radius * 2f;

        for (Formation formation : Formation.values()) {
            for (int a = 0; a < Formation.MAX_SLOTS; a++) {
                for (int b = a + 1; b < Formation.MAX_SLOTS; b++) {
                    float dx = formation.offsetX(a) - formation.offsetX(b);
                    float dy = formation.offsetY(a) - formation.offsetY(b);
                    float gap = (float) Math.sqrt(dx * dx + dy * dy);
                    assertTrue(gap > minimum,
                            formation + " puts slots " + a + " and " + b + " only " + gap
                                    + " apart, inside two radii (" + minimum + ") - separation "
                                    + "steering and the formation will fight each other");
                }
            }
        }
    }

    @Test
    public void slotsAreStableAcrossCalls() {
        // The tables are built lazily on first use; a second read must give the same answer.
        for (Formation formation : Formation.values()) {
            for (int slot = 0; slot < Formation.MAX_SLOTS; slot++) {
                assertEquals(formation.offsetX(slot), formation.offsetX(slot), 0f);
                assertEquals(formation.offsetY(slot), formation.offsetY(slot), 0f);
            }
        }
    }

    @Test
    public void aThinnedLineStaysCentredOnItsAnchor() {
        // LINE fills outwards from the middle, so losing the outer slots leaves the survivors
        // still straddling the anchor rather than drifting off to one side.
        Formation line = Formation.LINE;
        assertEquals(0f, line.offsetY(0), 1e-6f);
        assertTrue(line.offsetY(1) * line.offsetY(2) < 0f,
                "the first two flankers should be on opposite sides");
    }

    @Test
    public void columnRunsBackwardsFromTheAnchor() {
        // A column follows its leader, so every slot is behind the anchor, not in front of it.
        for (int slot = 1; slot < Formation.MAX_SLOTS; slot++) {
            assertTrue(Formation.COLUMN.offsetX(slot) < 0f,
                    "column slot " + slot + " is ahead of the anchor");
        }
    }
}
