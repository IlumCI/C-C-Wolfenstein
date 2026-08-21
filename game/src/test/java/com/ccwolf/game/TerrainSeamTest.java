package com.ccwolf.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import java.io.IOException;
import org.junit.Test;

/**
 * Checks the ground still covers the ground.
 *
 * <p>Terrain is drawn as 8x8 blocks rather than a tile at a time, which means a block's edges
 * are rounded to whole pixels once instead of eight times. At a zoom where a tile is not a
 * whole number of pixels wide, that is exactly the kind of change that opens a one-pixel seam
 * between blocks — and a hairline of background colour running across the map is the sort of
 * fault that is invisible in a screenshot and obvious in motion.
 *
 * <p>The camera zooms in multiplicative steps, so most reachable zoom levels are fractional.
 * These are the ones to worry about.
 */
public class TerrainSeamTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    /** The colour the frame is cleared to; any of it left showing is a hole. */
    private static final int BACKDROP = 0xFF0B0C0A;

    @Test
    public void theGroundHasNoSeamsAtAnyZoom() throws IOException {
        // Deliberately awkward: multiplying by 1.11 walks through zoom levels where a tile is
        // a fractional number of pixels wide, which is where seams would appear.
        float[] zoomSteps = {1f, 1.11f, 1.23f, 1.37f, 1.52f, 0.9f, 0.81f, 0.73f};

        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 11L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.camera().setMap(session.world().map());
        session.world().setFogEnabled(false);

        // Somewhere well inside the map, so the viewport is entirely over terrain.
        session.camera().centerOn(session.world().map().width() / 2f,
                session.world().map().height() / 2f);

        for (int i = 0; i < zoomSteps.length; i++) {
            session.camera().zoomBy(zoomSteps[i], WIDTH / 4f, HEIGHT / 2f);

            Frame frame = new Frame(WIDTH, HEIGHT);
            frame.surface().clear(BACKDROP);
            renderer.draw(frame.surface(), session);

            int holes = countBackdropPixels(frame, session);
            if (holes > 0) {
                frame.save("terrain-seam-zoom" + i + ".png");
            }
            assertEquals("seam at zoom step " + i + " (tile is "
                    + session.camera().tilePx() + " px) - see terrain-seam-zoom" + i + ".png",
                    0, holes);
        }
    }

    /**
     * Counts pixels inside the world viewport that are still the clear colour.
     *
     * <p>The map is bigger than the viewport and the camera is centred, so every pixel of the
     * world view should have ground under it. Anything left showing the backdrop is a gap
     * between blocks.
     */
    private int countBackdropPixels(Frame frame, GameSession session) {
        int right = session.camera().viewLeft() + session.camera().viewWidth();
        int bottom = session.camera().viewTop() + session.camera().viewHeight();
        int holes = 0;
        for (int y = session.camera().viewTop(); y < bottom; y++) {
            for (int x = session.camera().viewLeft(); x < right; x++) {
                if (frame.pixel(x, y) == BACKDROP) {
                    holes++;
                }
            }
        }
        return holes;
    }

    @Test
    public void aWorkedOutSeamStopsLookingLikeASeam() {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 13L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.camera().setMap(session.world().map());
        session.world().setFogEnabled(false);

        TileMap map = session.world().map();
        int seamX = -1;
        int seamY = -1;
        for (int y = 0; y < map.height() && seamX < 0; y++) {
            for (int x = 0; x < map.width(); x++) {
                if (map.terrain(x, y) == Terrain.ORE && map.ore(x, y) > 450) {
                    seamX = x;
                    seamY = y;
                    break;
                }
            }
        }
        assertTrue("the map should have a rich seam to mine out", seamX >= 0);

        session.camera().centerOn(seamX, seamY);
        Frame frame = new Frame(WIDTH, HEIGHT);
        renderer.draw(frame.surface(), session);
        int bakesAfterFirstDraw = renderer.terrainBakes();

        renderer.draw(frame.surface(), session);
        assertEquals("a settled view should not rebake", bakesAfterFirstDraw,
                renderer.terrainBakes());

        // Work the seam out. The block holding it is now showing crystals that are not there.
        map.takeOre(seamX, seamY, map.ore(seamX, seamY));
        renderer.draw(frame.surface(), session);
        assertTrue("mining a seam out should rebake the block holding it",
                renderer.terrainBakes() > bakesAfterFirstDraw);
    }

    @Test
    public void blocksAreReusedRatherThanRebakedEveryFrame() {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 12L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.camera().setMap(session.world().map());

        Frame frame = new Frame(WIDTH, HEIGHT);
        for (int i = 0; i < 30; i++) {
            renderer.draw(frame.surface(), session);
        }

        // Thirty frames of a stationary camera must not have baked thirty times over: the
        // whole point is that the ground is composited once and then copied.
        int bakes = renderer.terrainBakes();
        assertTrue("nothing was baked at all - the counter is not wired up", bakes > 0);
        assertEquals("a still camera should bake each visible block exactly once",
                renderer.terrainBlocksResident(), bakes);
    }
}
