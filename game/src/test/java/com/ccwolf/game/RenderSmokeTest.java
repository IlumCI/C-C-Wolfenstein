package com.ccwolf.game;


import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.gfx.Surface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Draws real frames into a bitmap with Robolectric's native graphics.
 *
 * <p>There is no emulator on a build machine, so this is what stands in for one: it exercises
 * the whole draw path — terrain, structures, units, fog, effects, sidebar, minimap, placement
 * ghost and the end-of-match overlay — and fails on any exception or on a frame that comes out
 * blank. It also writes the frames to build/test-frames as PNGs to eyeball.
 */
public class RenderSmokeTest {

    static {
        // Sprites bake at class-load time, so the image backend has to be in place first.
        Frame.useAwtBackend();
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    public void drawsAFrameOfALiveMatch() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);

        // Play a couple of minutes so there are units, structures and shooting to draw.
        for (int i = 0; i < 2400; i++) {
            session.update(1f / 20f);
        }

        Frame frame = render(session, hud, renderer);
        assertTrue("the frame should not be a flat colour", countDistinctColours(frame) > 12);
        frame.save("match.png");
    }

    @Test
    public void drawsSelectionPlacementAndFogWithoutFalloverr() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        for (int i = 0; i < 200; i++) {
            session.update(1f / 20f);
        }

        // Select everything we own, arm a placement ghost, drag a selection box.
        session.selectInBox(0, 0, 64, 64);
        assertTrue("the starting base should give us something to select",
                session.hasSelection());
        session.setPlacing(BuildingType.BARRACKS);
        session.setPlaceTile(12, 12);
        renderer.setSelectionBox(true, 40, 40, 400, 300);

        Frame frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 12);
        frame.save("selection.png");
    }

    @Test
    public void drawsEveryStructureAndUnitType() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.world().setFogEnabled(false);

        int[] spawn = session.world().map().spawnPoint(session.playerId());
        int x = spawn[0];
        int y = spawn[1] + 12;
        for (com.ccwolf.core.entity.UnitType type : com.ccwolf.core.entity.UnitType.values()) {
            session.world().spawnUnitNear(session.playerId(), type, x, y);
            session.world().spawnUnitNear(1 - session.playerId(), type, x, y + 2);
            x += 2;
        }
        session.camera().centerOn(spawn[0] + 6, spawn[1] + 12);
        session.update(1f / 20f);

        Frame frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 12);
        frame.save("roster.png");
    }

    @Test
    public void everySidebarTabDraws() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.update(1f / 20f);

        for (int i = 0; i < 3; i++) {
            hud.handleTap(hud.tabRect(i).centerX(), hud.tabRect(i).centerY(), session, false);
            assertEquals("tapping a tab should switch to it", Hud.Tab.values()[i], hud.tab());
            Frame frame = render(session, hud, renderer);
            assertTrue(countDistinctColours(frame) > 12);
            frame.save("tab" + i + ".png");
        }
    }

    @Test
    public void tapsOutsideTheSidebarAreNotSwallowed() {
        GameSession session = newSession();
        Hud hud = new Hud();
        hud.layout(WIDTH, HEIGHT, 2f);

        assertTrue(hud.contains(hud.sidebarLeft() + 5, 100));
        assertTrue(!hud.contains(hud.sidebarLeft() - 5, 100));
        assertEquals(false, hud.handleTap(10, 10, session, false));
    }

    // --- helpers --------------------------------------------------------------------------

    /**
     * A gassed trench line, on screen.
     *
     * <p>The layer shipped with its overlay unlooked-at, which is the exact mistake this
     * project keeps paying for. So: a dug line, a cloud on it, men in the cloud, and a frame -
     * the question is whether the wash reads as the thing that kills men rather than as fog,
     * smoke or the control wash.
     */
    @Test
    public void drawsAGassedTrenchLine() throws IOException {
        GameSession session = newSession();
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        for (int i = 0; i < 200; i++) {
            session.update(1f / 20f);
        }

        com.ccwolf.core.sim.GameWorld world = session.world();
        int[] spawn = world.map().spawnPoint(session.playerId());
        int x = spawn[0] + 6;
        int y = spawn[1] + 2;
        for (int i = 0; i < 5; i++) {
            world.map().addCover(x + i, y, 3);
            world.spawnUnit(session.playerId(),
                    com.ccwolf.core.entity.UnitType.PARTISAN, x + i + 0.5f, y + 0.5f);
        }
        world.gas().release(x + 2, y, com.ccwolf.core.sim.GasLayer.PER_SHELL);
        world.gas().release(x + 4, y, com.ccwolf.core.sim.GasLayer.PER_SHELL);
        // A few ticks so the cloud settles along the diggings and the doses start landing.
        for (int i = 0; i < 15; i++) {
            session.update(1f / 20f);
        }
        session.camera().centerOn(x + 2f, y + 0.5f);

        Frame frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 12);
        frame.save("match-gas.png");
    }

    /**
     * A frame of the large map, at the front.
     *
     * <p>The two-hundred-and-fifty-six map earns its place only if the game on it looks like
     * the game: terrain cache serving blocks, overlays clamped to the window, the city and the
     * river actually on screen. The camera is walked to the middle of the map, because the
     * middle is what the map is about.
     */
    /** The newer grounds get the same treatment: a review frame each, aimed at their point. */
    @Test
    public void drawsAFrameOfTheNewMaps() throws IOException {
        String[][] shots = {
            {com.ccwolf.core.map.MapCatalog.SCHWARZBRUCK, "64", "64", "match-schwarzbruck"},
            {com.ccwolf.core.map.MapCatalog.ASCHEFELD, "48", "44", "match-aschefeld"},
        };
        for (String[] shot : shots) {
            GameSession session = new GameSession(shot[0], Faction.RESISTANCE,
                    Difficulty.VETERAN, 42L, null, null);
            Hud hud = new Hud();
            WorldRenderer renderer = new WorldRenderer();
            hud.layout(WIDTH, HEIGHT, 2f);
            session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
            for (int i = 0; i < 400; i++) {
                session.update(1f / 20f);
            }
            session.world().setFogEnabled(false);
            session.camera().centerOn(Float.parseFloat(shot[1]), Float.parseFloat(shot[2]));

            Frame frame = render(session, hud, renderer);
            assertTrue(countDistinctColours(frame) > 40);
            frame.save(shot[3] + ".png");
        }
    }

    @Test
    public void drawsAFrameOfTheFrontlineMap() throws IOException {
        GameSession session = new GameSession(com.ccwolf.core.map.MapCatalog.FRONTLINE,
                Faction.RESISTANCE, Difficulty.VETERAN, 42L, null, null);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        for (int i = 0; i < 400; i++) {
            session.update(1f / 20f);
        }
        // Fog off for the review frame: the first version of this test proudly rendered the
        // middle of the map under unexplored fog, and the sheet was a black rectangle that
        // passed its colour count on the sidebar alone.
        session.world().setFogEnabled(false);
        session.camera().centerOn(130f, 128f);

        Frame frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 40);
        frame.save("match-frontline.png");
    }

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

    /** A frame that renders as one or two colours means nothing actually got drawn. */
    private int countDistinctColours(Frame frame) {
        java.util.HashSet<Integer> colours = new java.util.HashSet<Integer>();
        for (int y = 0; y < frame.height(); y += 7) {
            for (int x = 0; x < frame.width(); x += 7) {
                colours.add(Integer.valueOf(frame.pixel(x, y)));
            }
        }
        return colours.size();
    }

}
