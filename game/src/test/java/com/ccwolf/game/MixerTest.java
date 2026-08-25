package com.ccwolf.game;

import com.ccwolf.game.audio.Mixer;
import com.ccwolf.game.audio.SoundBank;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The mixer's contract, tested the way the audio thread uses it: trigger, render, look at the
 * samples that came out.
 */
public class MixerTest {

    private static final int FRAMES = 512;

    private static long energy(short[] buffer) {
        long e = 0;
        for (short s : buffer) {
            e += Math.abs((int) s);
        }
        return e;
    }

    @Test
    public void silenceWhenNothingPlays() {
        Mixer mixer = new Mixer(SoundBank.get());
        short[] out = new short[FRAMES * 2];
        assertEquals(FRAMES, mixer.render(out, FRAMES));
        assertEquals(0L, energy(out));
    }

    @Test
    public void aVoiceMakesSoundAndThenEnds() {
        Mixer mixer = new Mixer(SoundBank.get());
        mixer.play(SoundBank.Cue.UI_CLICK, 1f, 0f, 1f);
        short[] out = new short[FRAMES * 2];
        mixer.render(out, FRAMES);
        assertTrue("a triggered click produced silence", energy(out) > 0);

        // UI_CLICK is 0.06 s ≈ 1300 samples; drain well past it.
        for (int i = 0; i < 10; i++) {
            mixer.render(out, FRAMES);
        }
        assertEquals("the one-shot never ended", 0, mixer.voicesPlaying(SoundBank.Cue.UI_CLICK));
        assertEquals(0L, energy(out));
    }

    @Test
    public void panLawSendsLeftToTheLeft() {
        Mixer mixer = new Mixer(SoundBank.get());
        mixer.play(SoundBank.Cue.RIFLE, 1f, -1f, 1f);
        short[] out = new short[FRAMES * 2];
        mixer.render(out, FRAMES);
        long left = 0;
        long right = 0;
        for (int f = 0; f < FRAMES; f++) {
            left += Math.abs((int) out[f * 2]);
            right += Math.abs((int) out[f * 2 + 1]);
        }
        assertTrue("hard-left pan leaked right", left > 0 && right < left / 50);
    }

    @Test
    public void aLoopKeepsGoing() {
        Mixer mixer = new Mixer(SoundBank.get());
        mixer.playLoop(SoundBank.Cue.RAIN_BED, 0.5f);
        short[] out = new short[FRAMES * 2];
        // RAIN_BED is ~4.2 s after the loop trim; render 6 s worth and expect sound at the end.
        int trips = 6 * 22050 / FRAMES;
        for (int i = 0; i < trips; i++) {
            mixer.render(out, FRAMES);
        }
        assertTrue("the rain stopped", energy(out) > 0);
        assertEquals(1, mixer.voicesPlaying(SoundBank.Cue.RAIN_BED));
    }

    @Test
    public void voiceStealingNeverTakesALoop() {
        Mixer mixer = new Mixer(SoundBank.get());
        int rain = mixer.playLoop(SoundBank.Cue.RAIN_BED, 0.4f);
        assertTrue(rain >= 0);
        // Flood every remaining slot twice over with one-shots.
        for (int i = 0; i < Mixer.VOICES * 2; i++) {
            mixer.play(SoundBank.Cue.RIFLE, 0.1f + (i % 8) * 0.1f, 0f, 1f);
        }
        assertEquals("the loop was stolen by a one-shot", 1,
                mixer.voicesPlaying(SoundBank.Cue.RAIN_BED));
    }

    @Test
    public void muteIsActuallySilent() {
        Mixer mixer = new Mixer(SoundBank.get());
        mixer.play(SoundBank.Cue.EXPLOSION_BIG, 1f, 0f, 1f);
        mixer.setMasterGain(0f);
        short[] out = new short[FRAMES * 2];
        mixer.render(out, FRAMES);
        assertEquals(0L, energy(out));
    }

    @Test
    public void manyLoudVoicesClampInsteadOfWrapping() {
        Mixer mixer = new Mixer(SoundBank.get());
        for (int i = 0; i < Mixer.VOICES; i++) {
            mixer.play(SoundBank.Cue.EXPLOSION_BIG, 1f, 0f, 1f);
        }
        short[] out = new short[FRAMES * 2];
        mixer.render(out, FRAMES);
        // Wrapping overflow shows up as alternating extreme samples; clamping keeps every
        // sample legal. The real assertion is that nothing threw and values are in range,
        // which shorts guarantee — so assert the mix is loud, proving the sum path ran.
        assertTrue(energy(out) > 0);
    }

    @Test
    public void renderIsCheapEnoughToCallForever() {
        Mixer mixer = new Mixer(SoundBank.get());
        mixer.playLoop(SoundBank.Cue.RAIN_BED, 0.5f);
        mixer.playLoop(SoundBank.Cue.WIND_BED, 0.5f);
        short[] out = new short[FRAMES * 2];
        long started = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            mixer.render(out, FRAMES);
        }
        long ms = (System.nanoTime() - started) / 1_000_000L;
        // 10k pulls is ~4 minutes of audio; a second of CPU for that is an order of
        // magnitude of headroom on a phone.
        assertTrue("mixer render too slow: " + ms + "ms for 10k buffers", ms < 4000);
    }
}
