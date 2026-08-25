package com.ccwolf.game;

import com.ccwolf.game.art.SpriteAtlas;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * What the sprite atlas costs, held to a budget rather than left to be discovered on a phone.
 *
 * <p>Baking is eager: every facing of every unit, every damage state of every structure and
 * every terrain variant is drawn at startup, on the argument that a few tens of milliseconds up
 * front beats a stall the first time a tank appears. That argument only holds while the numbers
 * stay small, and the numbers scale with the square of the authoring resolution — so they are
 * printed on every run and asserted against a ceiling.
 */
public class AtlasBudgetTest {

    static {
        Frame.useAwtBackend();
    }

    /** Generous for a desktop, survivable on a phone, and far below where an APK falls over. */
    private static final long MAX_BYTES = 192L * 1024 * 1024;

    @Test
    public void theAtlasFitsInItsBudget() {
        SpriteAtlas atlas = SpriteAtlas.get();
        System.out.println("ATLAS sprites=" + atlas.size()
                + " bytes=" + (atlas.bytes() / (1024 * 1024)) + "MB"
                + " bake=" + atlas.bakeMillis() + "ms");
        assertTrue("the atlas has outgrown its budget: " + atlas.bytes(),
                atlas.bytes() < MAX_BYTES);
    }
}
