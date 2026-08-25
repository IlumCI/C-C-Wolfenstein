package com.ccwolf.game.audio;

import com.ccwolf.audio.AudioSource;

/**
 * The software mixer: a fixed pool of voices summed into the platform's pull buffer.
 *
 * <p>All policy lives on the game side of the {@link AudioSource} seam, so this class is the
 * entire audio engine: voices, panning, pitch, stealing, master gain. The platform backend
 * below it only moves finished samples to a speaker.
 *
 * <p>Voices are flat parallel arrays, the FxDirector idiom: no per-shot allocation, ever. The
 * game thread triggers voices and the audio thread renders them; both take the same monitor,
 * but each holds it for microseconds — a trigger is a few stores, and render's work is reading
 * short arrays that never change after bake.
 */
public final class Mixer implements AudioSource {

    /**
     * Enough that a full battle and the weather can speak at once; few enough that a phone
     * mixes it without noticing. Stealing below keeps the cap honest.
     */
    public static final int VOICES = 24;

    private final SoundBank bank;

    private final boolean[] active = new boolean[VOICES];
    private final short[][] cue = new short[VOICES][];
    private final SoundBank.Cue[] cueId = new SoundBank.Cue[VOICES];
    /** Playback position in source samples, advanced by {@code step} per output frame. */
    private final double[] cursor = new double[VOICES];
    private final double[] step = new double[VOICES];
    private final float[] gainL = new float[VOICES];
    private final float[] gainR = new float[VOICES];
    private final boolean[] looping = new boolean[VOICES];

    private float masterGain = 1f;
    private float duckGain = 1f;

    /** Accumulator for render, grown to the largest pull ever asked for; never per-call. */
    private int[] mixScratch = new int[0];

    public Mixer(SoundBank bank) {
        this.bank = bank;
    }

    /**
     * Starts a one-shot voice. {@code pan} in [-1, 1], {@code pitch} multiplies playback rate
     * (1 = as baked). Returns the voice index, or -1 if every slot was a loop (never happens
     * with a sane loop count).
     */
    public synchronized int play(SoundBank.Cue c, float gain, float pan, float pitch) {
        int v = freeVoice();
        if (v < 0) {
            return -1;
        }
        return startVoice(v, c, gain, pan, pitch, false);
    }

    /** Starts a looping voice; the caller keeps the handle to ramp or stop it. */
    public synchronized int playLoop(SoundBank.Cue c, float gain) {
        int v = freeVoice();
        if (v < 0) {
            return -1;
        }
        return startVoice(v, c, gain, 0f, 1f, true);
    }

    /** Retunes a running voice's gain (loops mostly); ignores dead handles harmlessly. */
    public synchronized void setVoiceGain(int voice, float gain, float pan) {
        if (voice < 0 || voice >= VOICES || !active[voice]) {
            return;
        }
        applyPan(voice, gain, pan);
    }

    public synchronized void stopVoice(int voice) {
        if (voice >= 0 && voice < VOICES) {
            active[voice] = false;
        }
    }

    public synchronized void stopAll() {
        for (int i = 0; i < VOICES; i++) {
            active[i] = false;
        }
    }

    /** How many voices are currently sounding with this cue — the anti-stack query. */
    public synchronized int voicesPlaying(SoundBank.Cue c) {
        int n = 0;
        for (int i = 0; i < VOICES; i++) {
            if (active[i] && cueId[i] == c) {
                n++;
            }
        }
        return n;
    }

    public synchronized void setMasterGain(float gain) {
        masterGain = gain;
    }

    /** Ducking is a second, independent fader so pause can dim without forgetting volume. */
    public synchronized void setDuckGain(float gain) {
        duckGain = gain;
    }

    public synchronized float masterGain() {
        return masterGain;
    }

    // --- the pull side --------------------------------------------------------------------

    @Override
    public synchronized int render(short[] out, int frames) {
        int n = frames * 2;
        float master = masterGain * duckGain;
        for (int i = 0; i < n; i++) {
            out[i] = 0;
        }
        if (master <= 0f) {
            return frames;
        }
        // Sum every voice into a wide accumulator and clamp exactly once at the end.
        // Clamping per voice would distort the mix by an amount that depends on the order
        // voices happen to occupy their slots — the kind of fault nobody ever traces.
        if (mixScratch.length < n) {
            mixScratch = new int[n];
        }
        int[] acc = mixScratch;
        for (int i = 0; i < n; i++) {
            acc[i] = 0;
        }
        for (int v = 0; v < VOICES; v++) {
            if (!active[v]) {
                continue;
            }
            short[] src = cue[v];
            double pos = cursor[v];
            double advance = step[v];
            float gl = gainL[v] * master;
            float gr = gainR[v] * master;
            for (int f = 0; f < frames; f++) {
                int idx = (int) pos;
                if (idx >= src.length) {
                    if (looping[v]) {
                        pos -= src.length;
                        idx = (int) pos;
                    } else {
                        active[v] = false;
                        break;
                    }
                }
                float s = src[idx];
                int o = f * 2;
                acc[o] += (int) (s * gl);
                acc[o + 1] += (int) (s * gr);
                pos += advance;
            }
            cursor[v] = pos;
        }
        for (int i = 0; i < n; i++) {
            out[i] = clamp(acc[i]);
        }
        return frames;
    }

    // --- internals ------------------------------------------------------------------------

    private int startVoice(int v, SoundBank.Cue c, float gain, float pan, float pitch,
            boolean loop) {
        active[v] = false;
        cue[v] = bank.samples(c);
        cueId[v] = c;
        cursor[v] = 0;
        step[v] = Math.max(0.25f, pitch);
        looping[v] = loop;
        applyPan(v, gain, pan);
        active[v] = true;
        return v;
    }

    private void applyPan(int v, float gain, float pan) {
        // Constant-power pan: equal loudness at every position, -3 dB in the centre.
        float p = Math.max(-1f, Math.min(1f, pan));
        double angle = (p + 1f) * Math.PI / 4.0;
        gainL[v] = gain * (float) Math.cos(angle);
        gainR[v] = gain * (float) Math.sin(angle);
    }

    /** A free slot, else the quietest one-shot to steal. Loops are never stolen. */
    private int freeVoice() {
        int quietest = -1;
        float quietestGain = Float.MAX_VALUE;
        for (int i = 0; i < VOICES; i++) {
            if (!active[i]) {
                return i;
            }
            if (!looping[i]) {
                float g = gainL[i] + gainR[i];
                if (g < quietestGain) {
                    quietestGain = g;
                    quietest = i;
                }
            }
        }
        return quietest;
    }

    private static short clamp(int s) {
        if (s > 32767) {
            return 32767;
        }
        if (s < -32768) {
            return -32768;
        }
        return (short) s;
    }
}
