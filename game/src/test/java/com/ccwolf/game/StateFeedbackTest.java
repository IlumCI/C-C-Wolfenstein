package com.ccwolf.game;


import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.gfx.Surface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Renders the two states Part 2 added that the player otherwise cannot see.
 *
 * <p>Sabotage and stealth are invisible mechanics: without feedback a player taps a Saboteur
 * onto a power station, nothing appears to happen, and the feature may as well not exist. The
 * frame written here is how the arcs and the ghosting get reviewed on a machine with no
 * emulator.
 */
public class StateFeedbackTest {

    static {
        // Sprites bake at class-load time, so the image backend has to be in place first.
        Frame.useAwtBackend();
    }

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
        Frame frame = render(session, hud, renderer);
        frame.save("state-feedback.png");
        // Blown up, because at 1:1 the question "does this read as switched off" cannot be
        // answered from a screenshot.
        Frame.write(frame.zoom(340, 180, 360, 340, 3), "state-feedback-zoom.png");
    }

    private Frame render(GameSession session, Hud hud, WorldRenderer renderer) {
        Frame frame = new Frame(WIDTH, HEIGHT);
        frame.surface().clear(0xFF0B0C0A);
        renderer.draw(frame.surface(), session);
        hud.draw(frame.surface(), session, System.currentTimeMillis());
        return frame;
    }
}
