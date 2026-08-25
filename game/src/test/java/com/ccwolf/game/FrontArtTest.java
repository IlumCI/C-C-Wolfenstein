package com.ccwolf.game;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.Palette;
import com.ccwolf.game.render.WorldRenderer;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A look at the front: the control wash over the ground, and the contour on the minimap.
 *
 * <p>Two of these are written to be looked at rather than asserted on — the thing being judged
 * is whether the two tints read as two sides and whether the line sits where the fighting is.
 * The third is a real assertion, and it is the reason this file has assertions at all: the
 * influence field counts every unit on the map whether or not this player has seen it, so a wash
 * drawn over the fog would quietly hand the player the enemy's positions. That is pinned here
 * rather than left to the comment on the pass order.
 */
public class FrontArtTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    public void heldGroundAndTheLineBetweenIt() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.world().setFogEnabled(false);

        // Forty-five seconds: long enough for both sides to have armies out of their bases and
        // meeting somewhere, and short enough that there is still a front to look at. Nobody is
        // playing our side in a test, so left running the AI takes the whole map - at two
        // minutes the field measures three cells ours against two hundred and fifty theirs, and
        // the picture is a corner rather than a line.
        for (int i = 0; i < 900; i++) {
            session.update(1f / 20f);
        }
        assertTrue("the match should still be running", !session.world().isGameOver());
        session.camera().zoomBy(0.01f, WIDTH / 2f, HEIGHT / 2f);   // clamps to the whole map

        Frame frame = render(session, hud, renderer);
        frame.save("front-overlay.png");

        // And the same field with the development toggle on, which is what a reviewer wants to
        // look at: the play wash is deliberately too faint to read a gradient off.
        renderer.toggleControlDetail();
        Frame raw = render(session, hud, renderer);
        raw.save("front-overlay-raw.png");

        com.ccwolf.gfx.Rect box = hud.minimap().bounds();
        Frame.write(raw.zoom((int) box.left, (int) box.top,
                (int) box.width(), (int) box.height(), 4), "front-minimap.png");
    }

    /**
     * The intel leak, pinned.
     *
     * <p>Unexplored ground is filled with an opaque colour by both the world pass and the
     * minimap. If either drew the wash afterwards the pixel would be tinted, so an exact match
     * against that colour is the whole test.
     */
    @Test
    public void noWashReachesGroundThisPlayerHasNeverSeen() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        for (int i = 0; i < 1200; i++) {
            session.update(1f / 20f);
        }
        assertTrue("this test is about fog", session.world().isFogEnabled());

        // Zoom right out so the enemy half of the map is on screen at all.
        session.camera().zoomBy(0.01f, WIDTH / 2f, HEIGHT / 2f);

        WorldView view = session.view();
        FogGrid fog = session.world().fogFor(session.playerId());
        int checked = 0;
        Frame frame = render(session, hud, renderer);
        for (int y = 0; y < view.map().height(); y++) {
            for (int x = 0; x < view.map().width(); x++) {
                if (fog.isExplored(x, y) || Math.abs(view.control(x, y)) < 20f) {
                    continue;
                }
                float px = session.camera().screenX(x + 0.5f);
                float py = session.camera().screenY(y + 0.5f);
                if (px < 2 || py < 2 || px > hud.sidebarLeft() - 2 || py > HEIGHT - 2) {
                    continue;
                }
                assertEquals("unexplored ground at " + x + "," + y + " is tinted by the wash",
                        Palette.FOG_UNEXPLORED, frame.pixel((int) px, (int) py));
                checked++;
            }
        }
        assertTrue("no unexplored tile carried any control, so nothing was actually tested",
                checked > 20);
        frame.save("front-fog.png");
    }

    // --- helpers --------------------------------------------------------------------------

    private GameSession newSession() {
        return new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 42L);
    }

    private Frame render(GameSession session, Hud hud, WorldRenderer renderer) {
        Frame frame = new Frame(WIDTH, HEIGHT);
        frame.surface().clear(0xFF0B0C0A);
        renderer.draw(frame.surface(), session);
        hud.draw(frame.surface(), session, System.currentTimeMillis());
        return frame;
    }
}
