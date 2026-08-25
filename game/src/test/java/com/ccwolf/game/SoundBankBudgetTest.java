package com.ccwolf.game;

import com.ccwolf.game.audio.SoundBank;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * What the sound bank costs and whether every recipe actually made a sound.
 *
 * <p>The bank is synthesized eagerly like the sprite atlas, and held to a budget the same way.
 * The per-cue checks matter more than the total: a recipe edit that silences a cue (a filter
 * eating everything, an envelope multiplied to zero) fails here at build time instead of being
 * discovered by an eerily quiet firefight.
 */
public class SoundBankBudgetTest {

    /** ~95 seconds of mono 22 kHz. The whole bank is under half of this today. */
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    @Test
    public void theBankFitsItsBudgetAndEveryCueSpeaks() {
        long started = System.nanoTime();
        SoundBank bank = SoundBank.get();
        long bakeMs = (System.nanoTime() - started) / 1_000_000L;
        System.out.println("SOUNDBANK cues=" + SoundBank.Cue.values().length
                + " bytes=" + (bank.bytes() / 1024) + "KB bake=" + bakeMs + "ms");

        assertTrue("the sound bank has outgrown its budget: " + bank.bytes(),
                bank.bytes() < MAX_BYTES);

        for (SoundBank.Cue cue : SoundBank.Cue.values()) {
            short[] pcm = bank.samples(cue);
            assertTrue(cue + " baked to nothing", pcm.length > 0);
            int peak = 0;
            for (short s : pcm) {
                peak = Math.max(peak, Math.abs((int) s));
            }
            // Normalisation targets 0.9 of full scale; anything far below it means the
            // recipe collapsed and the normaliser amplified silence.
            assertTrue(cue + " is effectively silent (peak " + peak + ")", peak > 20000);
            assertTrue(cue + " clips (peak " + peak + ")", peak <= 32767);
        }
    }

    @Test
    public void loopsAreLongEnoughToLoop() {
        SoundBank bank = SoundBank.get();
        for (SoundBank.Cue cue : SoundBank.Cue.values()) {
            if (cue.isLoop()) {
                // A loop under a couple of seconds is audible as a loop; that is a recipe
                // bug, not a taste question.
                assertTrue(cue + " is too short to pass as weather",
                        bank.samples(cue).length > 2 * 22050);
            }
        }
    }
}
