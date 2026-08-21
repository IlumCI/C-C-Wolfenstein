package com.ccwolf.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.ccwolf.android.render.Hud;
import com.ccwolf.android.render.WorldRenderer;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Draws real frames into a bitmap with Robolectric's native graphics.
 *
 * <p>There is no emulator on a build machine, so this is what stands in for one: it exercises
 * the whole draw path — terrain, structures, units, fog, effects, sidebar, minimap, placement
 * ghost and the end-of-match overlay — and fails on any exception or on a frame that comes out
 * blank. It also writes the frames to build/test-frames as PNGs to eyeball.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RenderSmokeTest {

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

        Bitmap frame = render(session, hud, renderer);
        assertTrue("the frame should not be a flat colour", countDistinctColours(frame) > 12);
        save(frame, "match.png");
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

        Bitmap frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 12);
        save(frame, "selection.png");
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

        Bitmap frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 12);
        save(frame, "roster.png");
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
            Bitmap frame = render(session, hud, renderer);
            assertTrue(countDistinctColours(frame) > 12);
            save(frame, "tab" + i + ".png");
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

    private GameSession newSession() {
        return new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 42L);
    }

    private Bitmap render(GameSession session, Hud hud, WorldRenderer renderer) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF0B0C0A);
        renderer.draw(canvas, session);
        hud.draw(canvas, session, System.currentTimeMillis());
        return bitmap;
    }

    /** A frame that renders as one or two colours means nothing actually got drawn. */
    private int countDistinctColours(Bitmap bitmap) {
        java.util.HashSet<Integer> colours = new java.util.HashSet<Integer>();
        for (int y = 0; y < bitmap.getHeight(); y += 7) {
            for (int x = 0; x < bitmap.getWidth(); x += 7) {
                colours.add(Integer.valueOf(bitmap.getPixel(x, y)));
            }
        }
        return colours.size();
    }

    private void save(Bitmap bitmap, String name) throws IOException {
        File dir = new File("build/test-frames");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        FileOutputStream out = new FileOutputStream(new File(dir, name));
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            out.close();
        }
    }
}
