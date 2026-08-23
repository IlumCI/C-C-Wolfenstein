package com.ccwolf.game;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Faction;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The setup screen: choices in, a correctly configured match out.
 *
 * <p>Driven the way a finger drives it — by tapping the option boxes — because the screen's whole
 * job is that its geometry and its state agree. The taps land on coordinates computed from the
 * same layout the drawing uses, so a drifted hitbox fails here before anyone ships it.
 */
public class MatchSetupTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int W = 1280;
    private static final int H = 720;

    @Test
    public void theDefaultMatchIsTheOldMatch() {
        MatchSetup setup = new MatchSetup();
        setup.layout(W, H, 2f);
        assertEquals(Faction.RESISTANCE, setup.faction());
        assertNull(setup.doctrine());
        assertEquals(Difficulty.VETERAN, setup.difficulty());
        assertFalse(setup.isStarted());
    }

    @Test
    public void pickingAFactionResetsTheDoctrine() {
        MatchSetup setup = new MatchSetup();
        setup.layout(W, H, 2f);

        // Tap every point in a coarse grid; find the taps that select TIEFBAU.
        selectDoctrine(setup, Doctrine.TIEFBAU);
        assertEquals(Doctrine.TIEFBAU, setup.doctrine());

        // Now switch sides: the doctrine must reset rather than translate, because doctrines
        // do not translate - each belongs to one faction and the world refuses a mismatch.
        selectFaction(setup, Faction.REGIME);
        assertEquals(Faction.REGIME, setup.faction());
        assertNull(setup.doctrine());

        selectDoctrine(setup, Doctrine.GASKRIEG);
        assertEquals(Doctrine.GASKRIEG, setup.doctrine());
    }

    @Test
    public void theScreenDrawsAndStarts() throws IOException {
        MatchSetup setup = new MatchSetup();
        setup.layout(W, H, 2f);
        selectFaction(setup, Faction.REGIME);
        selectDoctrine(setup, Doctrine.GASKRIEG);

        Frame frame = new Frame(W, H);
        setup.draw(frame.surface());
        frame.save("setup-screen.png");

        assertFalse(setup.isStarted());
        // The start button is the bottom-most tappable thing; sweep the lower band.
        for (int y = H - 1; y > H / 2 && !setup.isStarted(); y -= 8) {
            setup.tap(W / 2f, y);
        }
        assertTrue(setup.isStarted());
    }

    // --- driving the screen like a finger -----------------------------------------------------

    private void selectFaction(MatchSetup setup, Faction want) {
        for (int x = 0; x < W && setup.faction() != want; x += 16) {
            for (int y = 0; y < H / 2 && setup.faction() != want; y += 8) {
                setup.tap(x, y);
                if (setup.isStarted()) {
                    throw new IllegalStateException("hit start while hunting a faction box");
                }
            }
        }
        assertEquals(want, setup.faction());
    }

    private void selectDoctrine(MatchSetup setup, Doctrine want) {
        for (int y = 0; y < H && setup.doctrine() != want; y += 6) {
            setup.tap(W / 2f, y);
            if (setup.isStarted()) {
                throw new IllegalStateException("hit start while hunting a doctrine row");
            }
        }
        assertEquals(want, setup.doctrine());
    }

}
