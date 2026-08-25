package com.ccwolf.game;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.game.audio.AudioDirector;
import com.ccwolf.game.audio.Mixer;
import com.ccwolf.game.audio.SoundBank;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The audio direction policy, tested the way CombatFxTest tests the visual one: hand the
 * director a stream of events and look at which voices are sounding afterwards.
 *
 * <p>Each test gets its own mixer, so a voice left ringing by one scenario cannot alibi the
 * next. The session is only borrowed for a correctly configured camera and fog view.
 */
public class AudioDirectorTest {

    static {
        Frame.useAwtBackend();
    }

    /** A session, its camera aimed at its own spawn, plus a private mixer and director. */
    private GameSession session;
    private Mixer mixer;
    private AudioDirector director;
    private float cx;
    private float cy;

    private void setUp(long seed) {
        session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, seed);
        session.camera().setViewport(0, 0, 1000, 720);
        int[] spawn = session.world().map().spawnPoint(session.playerId());
        cx = spawn[0] + 4f;
        cy = spawn[1] + 4f;
        session.camera().centerOn(cx, cy);
        mixer = new Mixer(SoundBank.get());
        director = new AudioDirector(seed, mixer);
    }

    private void consume(List<GameEvent> events) {
        director.consume(events, session.playerId(), session.view(), session.camera());
    }

    private static List<GameEvent> one(GameEvent e) {
        List<GameEvent> list = new ArrayList<GameEvent>();
        list.add(e);
        return list;
    }

    /** Renders the mixer forward, both to advance voices and to prove nothing throws. */
    private void renderSeconds(float seconds) {
        short[] scratch = new short[512 * 2];
        int trips = (int) (seconds * 22050 / 512) + 1;
        for (int i = 0; i < trips; i++) {
            mixer.render(scratch, 512);
        }
    }

    @Test
    public void everyWeaponClassSpeaksInItsOwnVoice() {
        setUp(31L);
        session.world().setFogEnabled(false);
        List<GameEvent> events = new ArrayList<GameEvent>();
        for (WeaponClass w : WeaponClass.values()) {
            events.add(GameEvent.shot(session.playerId(), 1, cx, cy, cx + 4f, cy, 10, w,
                    GameEvent.TargetKind.INFANTRY));
        }
        consume(events);

        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.RIFLE));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.SNIPER));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.CANNON));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.ROCKET_LAUNCH));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.FLAME_BURST));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.OCCULT_ZAP));
        // ARTILLERY and GAS share the heavy gun's report; only the arrival differs.
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.ARTY_FIRE));
    }

    @Test
    public void theFogIsNotAnAudioWallhack() {
        setUp(32L);
        // Sight is computed by the tick, and before the first tick nothing is visible at all
        // — not even home. One step lights the spawn without exploring the far corner.
        session.update(GameWorld.TICK_SECONDS + 0.001f);
        assertTrue("home should be visible after a tick", session.view().isVisible(
                (int) cx, (int) cy));
        // Fog stays on; fire a cannon somewhere the player has never seen.
        int farX = session.world().map().width() - 4;
        int farY = session.world().map().height() - 4;
        assertFalse("test needs an unseen tile", session.view().isVisible(farX, farY));

        consume(one(GameEvent.shot(2, 1, farX, farY, farX + 3f, farY, 10, WeaponClass.CANNON,
                GameEvent.TargetKind.VEHICLE)));
        assertEquals("a shot in unexplored fog was audible", 0,
                mixer.voicesPlaying(SoundBank.Cue.CANNON));

        // The same shot where the player can see is heard.
        session.camera().centerOn(cx, cy);
        consume(one(GameEvent.shot(session.playerId(), 1, cx, cy, cx + 3f, cy, 10,
                WeaponClass.CANNON, GameEvent.TargetKind.VEHICLE)));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.CANNON));
    }

    @Test
    public void twoHundredRiflesAreOneBattleNotTwoHundredSounds() {
        setUp(33L);
        session.world().setFogEnabled(false);
        List<GameEvent> events = new ArrayList<GameEvent>();
        for (int i = 0; i < 200; i++) {
            events.add(GameEvent.shot(session.playerId(), i, cx + (i % 10) * 0.3f, cy,
                    cx + 4f, cy, 10, WeaponClass.SMALL_ARMS, GameEvent.TargetKind.INFANTRY));
        }
        consume(events);
        int voices = mixer.voicesPlaying(SoundBank.Cue.RIFLE);
        assertTrue("the volley was silent", voices >= 1);
        assertTrue("the volley stacked into a wall: " + voices + " voices", voices <= 4);
    }

    @Test
    public void aShellArrivingIsTheLoudestThingInTheGame() {
        setUp(34L);
        session.world().setFogEnabled(false);
        consume(one(GameEvent.at(GameEvent.Type.SHELL_IMPACT, 2, 1, cx, cy)));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.EXPLOSION_BIG));
    }

    @Test
    public void theWhistleWaitsForItsShell() {
        setUp(35L);
        session.world().setFogEnabled(false);
        // A three-second flight (60 ticks). The whistle is a one-second fall cued to end at
        // the impact, so it must start at two seconds and not before.
        consume(one(GameEvent.shot(session.playerId(), 1, cx - 6f, cy, cx, cy, 60,
                WeaponClass.ARTILLERY, GameEvent.TargetKind.NONE)));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.ARTY_FIRE));
        assertEquals(0, mixer.voicesPlaying(SoundBank.Cue.SHELL_WHISTLE));

        director.update(1.5f);
        assertEquals("the whistle came early", 0,
                mixer.voicesPlaying(SoundBank.Cue.SHELL_WHISTLE));
        director.update(0.6f);
        assertEquals("the whistle never fell", 1,
                mixer.voicesPlaying(SoundBank.Cue.SHELL_WHISTLE));
    }

    @Test
    public void interfaceCuesBelongToTheirPlayerAlone() {
        setUp(36L);
        int me = session.playerId();
        int them = me == 1 ? 2 : 1;

        consume(one(GameEvent.at(GameEvent.Type.INSUFFICIENT_FUNDS, them, 0, 0, 0)));
        assertEquals("the enemy's empty wallet buzzed at us", 0,
                mixer.voicesPlaying(SoundBank.Cue.UI_ERROR));

        consume(one(GameEvent.at(GameEvent.Type.INSUFFICIENT_FUNDS, me, 0, 0, 0)));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.UI_ERROR));
    }

    @Test
    public void theAlarmNagsAtMostEveryFifteenSeconds() {
        setUp(37L);
        int me = session.playerId();
        consume(one(GameEvent.at(GameEvent.Type.UNDER_ATTACK, me, 0, cx, cy)));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.UNDER_ATTACK_STING));

        // Let the sting finish sounding, then report another attack too soon.
        renderSeconds(1.5f);
        director.update(5f);
        consume(one(GameEvent.at(GameEvent.Type.UNDER_ATTACK, me, 0, cx, cy)));
        assertEquals("the alarm repeated inside its cooldown", 0,
                mixer.voicesPlaying(SoundBank.Cue.UNDER_ATTACK_STING));

        // Past the cooldown it may speak again.
        director.update(11f);
        consume(one(GameEvent.at(GameEvent.Type.UNDER_ATTACK, me, 0, cx, cy)));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.UNDER_ATTACK_STING));
    }

    @Test
    public void theStormNeverStops() {
        setUp(38L);
        // Ambience starts on the first frame and its beds are loops the stealer cannot take.
        director.update(1f / 60f);
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.RAIN_BED));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.WIND_BED));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.DRONE_BED));

        // A minute of frames later it is all still falling, and still only one of each.
        for (int i = 0; i < 3600; i++) {
            director.update(1f / 60f);
        }
        renderSeconds(2f);
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.RAIN_BED));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.WIND_BED));
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.DRONE_BED));
    }

    @Test
    public void distantFightsAreQuietAndOffscreenOnesFadeOut() {
        setUp(39L);
        session.world().setFogEnabled(false);
        // A shot well past a screen and a half from the viewport is dropped entirely.
        float farX = cx + 60f;
        consume(one(GameEvent.shot(session.playerId(), 1, farX, cy, farX + 3f, cy, 10,
                WeaponClass.CANNON, GameEvent.TargetKind.VEHICLE)));
        assertEquals("a fight two screens away was audible", 0,
                mixer.voicesPlaying(SoundBank.Cue.CANNON));
    }
}
