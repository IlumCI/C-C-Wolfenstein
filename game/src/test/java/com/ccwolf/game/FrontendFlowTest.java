package com.ccwolf.game;

import com.ccwolf.game.audio.GameAudio;
import com.ccwolf.game.audio.SoundBank;
import com.ccwolf.game.input.InputController;
import com.ccwolf.game.input.PointerEvent;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The screen flow, driven the way a finger drives it: title to setup to match and back out.
 *
 * <p>The audio assertions matter as much as the screen ones. The flow's transitions are the
 * single place that silences the previous screen's voices, and the leak they guard against —
 * every abandoned match leaving its storm beds looping in the mixer forever — is exactly the
 * kind of fault that ships silently and is discovered as "the game gets louder every match".
 */
public class FrontendFlowTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int W = 1280;
    private static final int H = 720;

    private static Frontend boot() {
        Frontend frontend = new Frontend(11L, false);
        frontend.layout(W, H, 2f);
        return frontend;
    }

    /** Sweeps the title menu until SKIRMISH lands; the row is in the lower-middle band. */
    private static void tapSkirmish(Frontend frontend) {
        for (int y = (int) (H * 0.6f); y < H && frontend.screen() == Frontend.Screen.TITLE;
                y += 6) {
            frontend.tap(W / 2f, y);
        }
        assertEquals(Frontend.Screen.SETUP, frontend.screen());
    }

    /** Sweeps the setup screen's lower band until BEGIN starts the match. */
    private static void tapBegin(Frontend frontend) {
        for (int y = H - 1; y > H / 2 && frontend.screen() == Frontend.Screen.SETUP; y -= 8) {
            frontend.tap(W / 2f, y);
        }
        assertEquals(Frontend.Screen.MATCH, frontend.screen());
    }

    @Test
    public void titleToSetupToMatchAndBackOut() {
        Frontend frontend = boot();
        assertEquals(Frontend.Screen.TITLE, frontend.screen());
        assertNull(frontend.session());

        tapSkirmish(frontend);
        tapBegin(frontend);
        assertNotNull(frontend.session());
        assertTrue(frontend.session().world().units().size() > 0);

        frontend.abandonMatch();
        assertEquals(Frontend.Screen.TITLE, frontend.screen());
        assertNull(frontend.session());
    }

    @Test
    public void abandonedMatchesDoNotStackStormBeds() {
        Frontend frontend = boot();
        // Two full menu -> match -> abandon laps, pumping every screen so each owner's
        // ambience genuinely starts before the next transition kills it.
        for (int lap = 0; lap < 2; lap++) {
            for (int i = 0; i < 5; i++) {
                frontend.update(0.05f);
            }
            tapSkirmish(frontend);
            tapBegin(frontend);
            for (int i = 0; i < 5; i++) {
                frontend.update(0.05f);
            }
            frontend.abandonMatch();
        }
        frontend.update(0.05f);
        assertTrue("storm beds stacked across matches",
                GameAudio.mixer().voicesPlaying(SoundBank.Cue.RAIN_BED) <= 1);
    }

    @Test
    public void theDemoFightsOnBehindTheTitle() {
        Frontend frontend = boot();
        // Two simulated minutes of menu idling: the demo simulates, the flow never leaves
        // TITLE, and nothing (a demo game-over restart included) throws along the way.
        for (int i = 0; i < 1200; i++) {
            frontend.update(0.1f);
        }
        assertEquals(Frontend.Screen.TITLE, frontend.screen());
    }

    @Test
    public void thePausedOverlayOffersTheWayOut() throws IOException {
        Frontend frontend = boot();
        tapSkirmish(frontend);
        tapBegin(frontend);
        GameSession session = frontend.session();

        Hud hud = new Hud();
        hud.layout(W, H, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), H);
        InputController input = new InputController(session, hud, new WorldRenderer(), 2f);
        input.setAbandonListener(new Runnable() {
            @Override
            public void run() {
                frontend.abandonMatch();
            }
        });

        session.setPaused(true);
        Frame frame = new Frame(W, H);
        new WorldRenderer().draw(frame.surface(), session);
        hud.draw(frame.surface(), session, 1000L);
        frame.save("paused-overlay.png");

        // Drive it like a finger: sweep the battlefield's centre column until the abandon
        // button fires, proving hit regions and listener agree with what was drawn.
        PointerEvent pointer = new PointerEvent();
        float cx = hud.sidebarLeft() / 2f;
        for (int y = H / 3; y < H && frontend.screen() == Frontend.Screen.MATCH; y += 4) {
            if (hud.pausedAbandonHit(cx, y)) {
                input.onPointer(pointer.single(PointerEvent.Action.DOWN, cx, y));
            }
        }
        assertEquals(Frontend.Screen.TITLE, frontend.screen());
    }

    @Test
    public void theTitleScreenDraws() throws IOException {
        Frontend frontend = boot();
        WorldRenderer renderer = new WorldRenderer();
        // A few seconds so the demo has units moving and the storm has begun.
        for (int i = 0; i < 60; i++) {
            frontend.update(0.05f);
        }
        Frame frame = new Frame(W, H);
        frontend.draw(frame.surface(), renderer, 8_000L);
        frame.save("title-screen.png");

        // Lightning moment: cycle 0's flash window; draw again to exercise the bolt path.
        Frame flash = new Frame(W, H);
        frontend.draw(flash.surface(), renderer, 3_000L);
        flash.save("title-screen-flash.png");
    }

    @Test
    public void theManualOpensAndCloses() throws IOException {
        Frontend frontend = boot();
        WorldRenderer renderer = new WorldRenderer();
        // The HOW TO PLAY row sits between SKIRMISH and QUIT; hit it directly.
        float helpY = H * 0.62f + 26f * 2f + 10f * 2f + 5f;
        frontend.tap(W / 2f, helpY);
        assertEquals("help must not leave the title screen",
                Frontend.Screen.TITLE, frontend.screen());

        Frame frame = new Frame(W, H);
        frontend.draw(frame.surface(), renderer, 5_000L);
        frame.save("title-help.png");

        // Any tap closes the manual, and the next tap is a menu tap again.
        frontend.tap(W / 2f, H / 2f);
        tapSkirmish(frontend);
    }
}
