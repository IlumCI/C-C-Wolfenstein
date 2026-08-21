package com.ccwolf.game;


import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.game.fx.DecalLayer;
import com.ccwolf.game.fx.FxDirector;
import com.ccwolf.game.fx.ParticleSystem;
import com.ccwolf.game.fx.ProjectileLayer;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.gfx.Colors;
import com.ccwolf.gfx.Surface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Renders combat with the effects layer running, and checks the layer cannot run away.
 *
 * <p>The frames written to build/test-frames are the only way to see whether a tracer reads
 * against grass or a blood decal is the wrong colour, on a machine with no emulator.
 */
public class CombatFxTest {

    static {
        // Sprites bake at class-load time, so the image backend has to be in place first.
        Frame.useAwtBackend();
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    public void everyWeaponClassStagesItsOwnEffects() throws IOException {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 21L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.world().setFogEnabled(false);

        int[] spawn = session.world().map().spawnPoint(session.playerId());
        float cx = spawn[0] + 6f;
        float cy = spawn[1] + 8f;
        session.camera().centerOn(cx, cy);

        // One shot of every class, fanned out so none of them overlap in the frame.
        List<GameEvent> events = new ArrayList<GameEvent>();
        WeaponClass[] classes = WeaponClass.values();
        for (int i = 0; i < classes.length; i++) {
            float sx = cx - 6f;
            float sy = cy - 4f + i * 1.4f;
            events.add(GameEvent.shot(session.playerId(), 1, sx, sy, sx + 7f, sy, 20,
                    classes[i], GameEvent.TargetKind.values()[1 + (i % 3)]));
        }
        session.fx().consume(events, session.playerId());

        // Let the rounds travel most of the way, then draw.
        for (int i = 0; i < 12; i++) {
            session.fx().update(1f / 60f);
        }

        Frame frame = render(session, hud, renderer);
        assertTrue("effects should have produced something to look at",
                session.fx().particles().liveCount() > 20);
        frame.save("fx-weapons.png");
    }

    @Test
    public void deathsLeaveGoreWreckageAndScorchedGround() throws IOException {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 22L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.world().setFogEnabled(false);

        int[] spawn = session.world().map().spawnPoint(session.playerId());
        float cx = spawn[0] + 6f;
        float cy = spawn[1] + 8f;
        session.camera().centerOn(cx, cy);

        List<GameEvent> events = new ArrayList<GameEvent>();
        for (int i = 0; i < 6; i++) {
            events.add(GameEvent.destroyed(1, 100 + i, cx - 4f + i * 1.6f, cy - 2f,
                    GameEvent.TargetKind.INFANTRY));
        }
        events.add(GameEvent.destroyed(1, 200, cx - 2f, cy + 2f,
                GameEvent.TargetKind.VEHICLE));
        events.add(GameEvent.destroyed(1, 201, cx + 3f, cy + 3f,
                GameEvent.TargetKind.STRUCTURE));
        session.fx().consume(events, session.playerId());

        for (int i = 0; i < 20; i++) {
            session.fx().update(1f / 60f);
        }

        assertTrue("a structure collapse should shake the camera",
                session.fx().shake() > 0f);
        assertTrue("blood and scorch should be on the ground",
                session.fx().decals().liveCount() >= 6);
        Frame frame = render(session, hud, renderer);
        frame.save("fx-deaths.png");
    }

    @Test
    public void aRealBattleRendersWithEffectsRunning() throws IOException {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 23L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.world().setFogEnabled(false);

        // Far enough in for the AI's first wave to be on the move, but not so far that the
        // passive human player has already been overrun and the match is over.
        for (int i = 0; i < 1500 && !session.view().isGameOver(); i++) {
            session.update(1f / 20f);
        }
        assertFalse("the match should still be running for a combat frame",
                session.view().isGameOver());

        // Follow the fighting: centre on the nearest hostile to our base.
        int[] spawn = session.world().map().spawnPoint(session.playerId());
        com.ccwolf.core.entity.Entity focus = session.world().findNearestEnemyAnywhere(
                session.playerId(), spawn[0], spawn[1], false);
        if (focus != null) {
            session.camera().centerOn(focus.x(), focus.y());
        }
        for (int i = 0; i < 40; i++) {
            session.update(1f / 60f);
        }

        Frame frame = render(session, hud, renderer);
        assertTrue(countDistinctColours(frame) > 12);
        frame.save("fx-battle.png");
    }

    @Test
    public void theParticlePoolCannotRunAway() {
        FxDirector director = new FxDirector(7L);
        List<GameEvent> events = new ArrayList<GameEvent>();

        // Far more effect spawns than a real battle produces, all at once.
        for (int i = 0; i < 400; i++) {
            events.add(GameEvent.destroyed(0, i, 10f + (i % 20), 10f + (i / 20f),
                    GameEvent.TargetKind.STRUCTURE));
        }
        director.consume(events, 0);
        director.update(1f / 60f);

        assertTrue("particles must stay inside the pool",
                director.particles().liveCount() <= ParticleSystem.CAPACITY);
        assertTrue("decals must stay inside their cap",
                director.decals().liveCount() <= DecalLayer.CAPACITY);
        assertTrue("projectiles must stay inside their cap",
                director.projectiles().liveCount() <= ProjectileLayer.CAPACITY);
    }

    @Test
    public void effectsExpireRatherThanAccumulating() {
        FxDirector director = new FxDirector(9L);
        List<GameEvent> events = new ArrayList<GameEvent>();
        events.add(GameEvent.destroyed(0, 1, 10f, 10f, GameEvent.TargetKind.VEHICLE));
        director.consume(events, 0);
        assertTrue(director.particles().liveCount() > 0);

        // Ten seconds later everything transient should be gone.
        for (int i = 0; i < 600; i++) {
            director.update(1f / 60f);
        }

        assertEquals(0, director.particles().liveCount());
        assertEquals(0, director.projectiles().liveCount());
        assertEquals(0f, director.shake(), 0.0001f);
    }

    @Test
    public void shakeIsClampedHoweverMuchBlowsUp() {
        FxDirector director = new FxDirector(11L);
        List<GameEvent> events = new ArrayList<GameEvent>();
        for (int i = 0; i < 50; i++) {
            events.add(GameEvent.destroyed(0, i, 10f, 10f, GameEvent.TargetKind.STRUCTURE));
        }
        director.consume(events, 0);

        assertTrue("an unclamped shake would make the game unplayable",
                director.shake() <= 0.55f + 0.0001f);
    }

    // --- helpers --------------------------------------------------------------------------

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
