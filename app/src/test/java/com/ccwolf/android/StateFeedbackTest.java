package com.ccwolf.android;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.ccwolf.android.render.Hud;
import com.ccwolf.android.render.WorldRenderer;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.sim.GameWorld;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders the two states Part 2 added that the player otherwise cannot see.
 *
 * <p>Sabotage and stealth are invisible mechanics: without feedback a player taps a Saboteur
 * onto a power station, nothing appears to happen, and the feature may as well not exist. The
 * frame written here is how the arcs and the ghosting get reviewed on a machine with no
 * emulator.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StateFeedbackTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    public void sabotagedThingsCrackleAndOwnStealthUnitsAreGhosted() throws IOException {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 9L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);

        GameWorld world = session.world();
        world.setFogEnabled(false);
        int[] spawn = world.map().spawnPoint(session.playerId());
        int bx = spawn[0] + 4;
        int by = spawn[1] + 4;

        // A structure and a walker, both switched off, next to live counterparts so the
        // difference is visible rather than having to be remembered.
        Building dead = world.placeBuilding(session.playerId(), BuildingType.GENERATOR,
                bx, by, true);
        Building live = world.placeBuilding(session.playerId(), BuildingType.GENERATOR,
                bx + 4, by, true);
        Unit stunned = world.spawnUnitNear(session.playerId(), UnitType.UBERSOLDAT, bx, by + 4);
        Unit awake = world.spawnUnitNear(session.playerId(), UnitType.UBERSOLDAT, bx + 5, by + 4);

        // Concealed: stealthy, stationary and not recently firing.
        Unit hidden = world.spawnUnitNear(session.playerId(), UnitType.MARKSMAN, bx, by + 7);
        Unit seen = world.spawnUnitNear(session.playerId(), UnitType.PARTISAN, bx + 5, by + 7);

        dead.disableUntil(world.tick() + 400);
        dead.setSabotaged(true);
        stunned.disableUntil(world.tick() + 400);

        // Let concealment settle; the sim will not move any of these because none has an order.
        for (int i = 0; i < Unit.CONCEAL_DELAY_TICKS + 10; i++) {
            world.step();
        }

        assertTrue("the sabotaged generator should still be switched off",
                session.view().isDisabled(dead));
        assertTrue("the live generator is the control and must stay on",
                !session.view().isDisabled(live));
        assertTrue("the stunned walker should still be frozen",
                session.view().isDisabled(stunned) && !session.view().isDisabled(awake));
        assertTrue("a stationary marksman should be concealed from its own player's view too",
                session.view().isHiddenAlly(hidden) && !session.view().isHiddenAlly(seen));

        session.camera().centerOn(bx + 2.5f, by + 4f);
        Bitmap frame = render(session, hud, renderer);
        save(frame, "state-feedback.png");
        // Blown up, because at 1:1 the question "does this read as switched off" cannot be
        // answered from a screenshot.
        save(zoom(frame, 340, 180, 360, 340, 3), "state-feedback-zoom.png");
    }

    private Bitmap render(GameSession session, Hud hud, WorldRenderer renderer) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF0B0C0A);
        renderer.draw(canvas, session);
        hud.draw(canvas, session, System.currentTimeMillis());
        return bitmap;
    }

    /** Nearest-neighbour crop and magnify, so review sees what the pixels actually are. */
    private Bitmap zoom(Bitmap source, int x, int y, int w, int h, int scale) {
        Bitmap out = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setFilterBitmap(false);
        canvas.drawBitmap(source, new android.graphics.Rect(x, y, x + w, y + h),
                new android.graphics.Rect(0, 0, w * scale, h * scale), paint);
        return out;
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
