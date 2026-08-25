package com.ccwolf.game.audio;

import java.util.Random;

/**
 * The PixelCanvas of sound: a mono float buffer and the primitives to sculpt a cue into it.
 *
 * <p>Sounds are recipes, not files — the same rule the sprites live by. Every cue in the game
 * is synthesized from these primitives at startup: noise for anything that burns, breaks or
 * rains; a handful of partials for anything that hums or beeps; envelopes and filters to give
 * each its shape. Nothing is recorded, nothing is shipped, and a cue is retuned by editing the
 * recipe, exactly like repainting a sprite.
 *
 * <p>Everything works in float samples in [-1, 1] until {@link #bake()}, which normalises
 * headroom and quantises to 16-bit. All randomness comes from a caller-seeded {@link Random} so
 * a bank bakes identically on every machine — not because the sim needs it (audio is inert),
 * but because "the same build sounds the same" is worth having for free.
 */
final class WaveCanvas {

    static final int SAMPLE_RATE = 22050;

    private final float[] samples;
    private final Random random;

    WaveCanvas(float seconds, long seed) {
        this.samples = new float[Math.max(1, (int) (seconds * SAMPLE_RATE))];
        this.random = new Random(seed);
    }

    int length() {
        return samples.length;
    }

    float[] data() {
        return samples;
    }

    // --- generators (all additive, so layers stack like paint) ----------------------------

    /** White noise: the raw material of gunfire, rain and collapse. */
    WaveCanvas noise(float gain) {
        for (int i = 0; i < samples.length; i++) {
            samples[i] += (random.nextFloat() * 2f - 1f) * gain;
        }
        return this;
    }

    /**
     * A sine partial whose pitch glides exponentially from {@code fromHz} to {@code toHz}.
     * Constant pitch is the degenerate case; the glide is what makes a whistle fall and an
     * engine spool.
     */
    WaveCanvas sine(float fromHz, float toHz, float gain) {
        return partial(fromHz, toHz, gain, 0);
    }

    /** A sawtooth partial — buzzy, for klaxons and machinery. */
    WaveCanvas saw(float fromHz, float toHz, float gain) {
        return partial(fromHz, toHz, gain, 1);
    }

    /** A square partial — hollow, for radio beeps and UI. */
    WaveCanvas square(float fromHz, float toHz, float gain) {
        return partial(fromHz, toHz, gain, 2);
    }

    private WaveCanvas partial(float fromHz, float toHz, float gain, int shape) {
        double phase = 0;
        double ratio = toHz / (double) fromHz;
        int n = samples.length;
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1 == 0 ? 1 : n - 1);
            double hz = fromHz * Math.pow(ratio, t);
            phase += hz / SAMPLE_RATE;
            double p = phase - Math.floor(phase);
            float v;
            switch (shape) {
                case 1:
                    v = (float) (2.0 * p - 1.0);
                    break;
                case 2:
                    v = p < 0.5 ? 1f : -1f;
                    break;
                default:
                    v = (float) Math.sin(2.0 * Math.PI * p);
                    break;
            }
            samples[i] += v * gain;
        }
        return this;
    }

    /**
     * Sparse crackle: individual impulses at an average rate, each with a short decaying tail.
     * Fire, debris, rain on a helmet — anything granular.
     */
    WaveCanvas crackle(float perSecond, float gain) {
        float chance = perSecond / SAMPLE_RATE;
        for (int i = 0; i < samples.length; i++) {
            if (random.nextFloat() < chance) {
                float amp = gain * (0.4f + 0.6f * random.nextFloat());
                int tail = 30 + random.nextInt(90);
                for (int j = 0; j < tail && i + j < samples.length; j++) {
                    samples[i + j] += amp * (1f - j / (float) tail)
                            * (random.nextFloat() * 2f - 1f);
                }
            }
        }
        return this;
    }

    // --- shaping --------------------------------------------------------------------------

    /** Exponential decay from full to {@code -60 dB} over {@code seconds}. The percussive one. */
    WaveCanvas decay(float seconds) {
        double k = Math.log(0.001) / (seconds * SAMPLE_RATE);
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= (float) Math.exp(k * i);
        }
        return this;
    }

    /** Linear attack-sustain-release envelope, times in seconds. */
    WaveCanvas envelope(float attack, float release) {
        int a = Math.max(1, (int) (attack * SAMPLE_RATE));
        int r = Math.max(1, (int) (release * SAMPLE_RATE));
        int n = samples.length;
        for (int i = 0; i < n; i++) {
            float g = 1f;
            if (i < a) {
                g = i / (float) a;
            }
            if (i >= n - r) {
                g = Math.min(g, (n - 1 - i) / (float) r);
            }
            samples[i] *= g;
        }
        return this;
    }

    /** One-pole low-pass: rounds off the hiss, leaves the thud. */
    WaveCanvas lowPass(float cutoffHz) {
        float k = coefficient(cutoffHz);
        float y = 0f;
        for (int i = 0; i < samples.length; i++) {
            y += k * (samples[i] - y);
            samples[i] = y;
        }
        return this;
    }

    /** One-pole high-pass: removes the rumble, leaves the crack. */
    WaveCanvas highPass(float cutoffHz) {
        float k = coefficient(cutoffHz);
        float y = 0f;
        for (int i = 0; i < samples.length; i++) {
            y += k * (samples[i] - y);
            samples[i] = samples[i] - y;
        }
        return this;
    }

    /**
     * Resonant band-pass (state-variable filter). This is the workhorse of character: a noise
     * burst through a resonant band is a ricochet, a drum, a voice of machinery, depending
     * only on where the band sits.
     */
    WaveCanvas bandPass(float centreHz, float resonance) {
        float f = (float) (2.0 * Math.sin(Math.PI * Math.min(centreHz, SAMPLE_RATE * 0.45f)
                / SAMPLE_RATE));
        float q = 1f / Math.max(0.5f, resonance);
        float low = 0f;
        float band = 0f;
        for (int i = 0; i < samples.length; i++) {
            low += f * band;
            float high = samples[i] - low - q * band;
            band += f * high;
            samples[i] = band;
        }
        return this;
    }

    /** Soft-clip distortion: what turns a polite thump into ordnance. */
    WaveCanvas drive(float amount) {
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.tanh(samples[i] * amount);
        }
        return this;
    }

    /** Multiplies the whole buffer by a constant. */
    WaveCanvas gain(float g) {
        for (int i = 0; i < samples.length; i++) {
            samples[i] *= g;
        }
        return this;
    }

    /** Slow amplitude wobble — wind gusts, engine unevenness. Depth in [0,1]. */
    WaveCanvas tremolo(float hz, float depth) {
        for (int i = 0; i < samples.length; i++) {
            float m = (float) (1.0 - depth * 0.5 * (1.0
                    + Math.sin(2.0 * Math.PI * hz * i / SAMPLE_RATE)));
            samples[i] *= m;
        }
        return this;
    }

    // --- composition ----------------------------------------------------------------------

    /** Mixes another canvas in at an offset (seconds), clipping to this buffer's length. */
    WaveCanvas mixIn(WaveCanvas other, float atSeconds, float gain) {
        int at = (int) (atSeconds * SAMPLE_RATE);
        float[] src = other.samples;
        for (int i = 0; i < src.length && at + i < samples.length; i++) {
            if (at + i >= 0) {
                samples[at + i] += src[i] * gain;
            }
        }
        return this;
    }

    /**
     * Crossfades the tail into the head so the cue loops without a seam. Call last, on loop
     * beds only — it eats {@code seconds} off the audible length.
     */
    WaveCanvas loopable(float seconds) {
        int fade = Math.min((int) (seconds * SAMPLE_RATE), samples.length / 2);
        int n = samples.length;
        for (int i = 0; i < fade; i++) {
            float t = i / (float) fade;
            samples[i] = samples[i] * t + samples[n - fade + i] * (1f - t);
        }
        // The blended region replaced the head; the tail it was built from is cut off by
        // baking only the first n - fade samples.
        trimmedLength = n - fade;
        return this;
    }

    private int trimmedLength = -1;

    // --- output ---------------------------------------------------------------------------

    /**
     * Normalises to 0.9 peak and quantises to 16-bit mono. Normalising per-cue means a recipe
     * never has to worry about absolute level — relative loudness is set at play time.
     */
    short[] bake() {
        int n = trimmedLength > 0 ? trimmedLength : samples.length;
        float peak = 1e-6f;
        for (int i = 0; i < n; i++) {
            peak = Math.max(peak, Math.abs(samples[i]));
        }
        float scale = 0.9f / peak;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) (samples[i] * scale * 32767f);
        }
        return out;
    }

    private static float coefficient(float cutoffHz) {
        double x = 2.0 * Math.PI * cutoffHz / SAMPLE_RATE;
        return (float) (x / (x + 1.0));
    }
}
