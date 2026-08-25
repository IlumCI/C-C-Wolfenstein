package com.ccwolf.game.audio;

/**
 * Every sound in the game, baked once at first use — the SpriteAtlas of audio.
 *
 * <p>Each cue is a {@link WaveCanvas} recipe: layered noise, partials, filters and envelopes,
 * seeded so the bank is byte-identical on every machine. The recipes aim for the same register
 * as the art — early-90s, a little dirty, more menace than fidelity. A rifle here is a filtered
 * crack, not a recording of one, and that is the point: it sits beside pixel sprites the way a
 * sampled rifle never would.
 */
public final class SoundBank {

    /** The vocabulary of cues. Loops are marked; everything else is a one-shot. */
    public enum Cue {
        // Weapons, by voice rather than by unit: several weapons share a cue and differ by
        // pitch at play time.
        RIFLE(false),
        SNIPER(false),
        CANNON(false),
        ROCKET_LAUNCH(false),
        GRENADE_TOSS(false),
        FLAME_BURST(false),
        OCCULT_ZAP(false),
        ARTY_FIRE(false),
        GAS_POP(false),
        // Impacts by material.
        IMPACT_FLESH(false),
        IMPACT_ARMOR(false),
        IMPACT_MASONRY(false),
        IMPACT_DIRT(false),
        // The big punctuation.
        EXPLOSION_SMALL(false),
        EXPLOSION_BIG(false),
        STRUCTURE_COLLAPSE(false),
        SHELL_WHISTLE(false),
        // Interface and base life.
        UI_CLICK(false),
        UI_ERROR(false),
        UI_READY(false),
        UNIT_TRAINED(false),
        BUILDING_UP(false),
        POWER_DOWN(false),
        UNDER_ATTACK_STING(false),
        // The weather this world is condemned to.
        RAIN_BED(true),
        WIND_BED(true),
        DRONE_BED(true),
        THUNDER_1(false),
        THUNDER_2(false),
        THUNDER_3(false);

        private final boolean loop;

        Cue(boolean loop) {
            this.loop = loop;
        }

        public boolean isLoop() {
            return loop;
        }
    }

    private static SoundBank instance;

    private final short[][] pcm = new short[Cue.values().length][];

    private SoundBank() {
        for (Cue cue : Cue.values()) {
            pcm[cue.ordinal()] = bake(cue);
        }
    }

    public static synchronized SoundBank get() {
        if (instance == null) {
            instance = new SoundBank();
        }
        return instance;
    }

    public short[] samples(Cue cue) {
        return pcm[cue.ordinal()];
    }

    /** Total baked bytes, for the budget test. */
    public long bytes() {
        long total = 0;
        for (short[] s : pcm) {
            total += s.length * 2L;
        }
        return total;
    }

    // --- the recipes ----------------------------------------------------------------------

    private static short[] bake(Cue cue) {
        // Each cue seeds its own canvas off its ordinal, so editing one recipe never
        // re-rolls the noise in another.
        long seed = 0xACC0157EEDL + cue.ordinal() * 7919L;
        switch (cue) {
            case RIFLE:
                // A crack is almost all attack: a driven noise pop with the lows cut.
                return new WaveCanvas(0.22f, seed)
                        .noise(1f).highPass(900f).drive(3.5f).decay(0.05f)
                        .mixIn(new WaveCanvas(0.18f, seed + 1)
                                .noise(0.7f).bandPass(300f, 2f).decay(0.12f), 0.008f, 0.8f)
                        .bake();
            case SNIPER:
                // Bigger round, longer report, a touch of ringing barrel.
                return new WaveCanvas(0.5f, seed)
                        .noise(1f).highPass(500f).drive(4f).decay(0.09f)
                        .mixIn(new WaveCanvas(0.45f, seed + 1)
                                .noise(0.8f).bandPass(180f, 3f).decay(0.3f), 0.01f, 0.9f)
                        .bake();
            case CANNON:
                // Tank gun: deep thump under the crack, all of it clipped hard.
                return new WaveCanvas(0.7f, seed)
                        .noise(1f).lowPass(1400f).drive(5f).decay(0.12f)
                        .mixIn(new WaveCanvas(0.6f, seed + 1)
                                .sine(110f, 45f, 1f).decay(0.35f), 0f, 1f)
                        .drive(1.8f)
                        .bake();
            case ROCKET_LAUNCH:
                // Whoosh that rises then chokes: noise through a sweeping band.
                return new WaveCanvas(0.7f, seed)
                        .noise(1f).bandPass(700f, 1.2f).envelope(0.03f, 0.45f)
                        .mixIn(new WaveCanvas(0.55f, seed + 1)
                                .saw(140f, 320f, 0.35f).lowPass(900f)
                                .envelope(0.05f, 0.4f), 0.02f, 0.7f)
                        .bake();
            case GRENADE_TOSS:
                // Almost nothing: a grunt of cloth and a pin of metal.
                return new WaveCanvas(0.2f, seed)
                        .noise(0.6f).bandPass(1200f, 1.5f).decay(0.08f)
                        .mixIn(new WaveCanvas(0.08f, seed + 1)
                                .sine(2600f, 2200f, 0.5f).decay(0.05f), 0.01f, 0.6f)
                        .bake();
            case FLAME_BURST:
                // The dragon cough: low roaring noise with crackle riding it.
                return new WaveCanvas(0.9f, seed)
                        .noise(1f).lowPass(900f).tremolo(11f, 0.5f)
                        .crackle(60f, 0.8f)
                        .envelope(0.06f, 0.5f).drive(2f)
                        .bake();
            case OCCULT_ZAP:
                // The resonance weapon: nothing natural — a falling scream of partials.
                return new WaveCanvas(0.6f, seed)
                        .saw(1900f, 240f, 0.6f)
                        .sine(950f, 120f, 0.5f)
                        .noise(0.25f).bandPass(1400f, 4f)
                        .envelope(0.01f, 0.4f).drive(2.5f)
                        .bake();
            case ARTY_FIRE:
                // Distant heavy gun: soft attack, long boom, no crack at all.
                return new WaveCanvas(1.1f, seed)
                        .noise(1f).lowPass(500f).drive(3f)
                        .mixIn(new WaveCanvas(1f, seed + 1)
                                .sine(70f, 32f, 1f).decay(0.7f), 0f, 1.1f)
                        .envelope(0.015f, 0.8f)
                        .bake();
            case GAS_POP:
                // A canister cough and a hiss that outlives it.
                return new WaveCanvas(0.9f, seed)
                        .noise(0.9f).lowPass(2600f).highPass(700f)
                        .envelope(0.02f, 0.75f)
                        .mixIn(new WaveCanvas(0.15f, seed + 1)
                                .noise(1f).lowPass(800f).drive(3f).decay(0.07f), 0f, 1.2f)
                        .bake();
            case IMPACT_FLESH:
                // Dull and wet: low thud, no ring.
                return new WaveCanvas(0.18f, seed)
                        .noise(1f).lowPass(500f).drive(2f).decay(0.07f)
                        .bake();
            case IMPACT_ARMOR:
                // Ring of plate: resonant metallic bands over a clank.
                return new WaveCanvas(0.4f, seed)
                        .noise(1f).bandPass(2300f, 6f).decay(0.18f)
                        .mixIn(new WaveCanvas(0.3f, seed + 1)
                                .noise(0.9f).bandPass(1150f, 5f).decay(0.2f), 0f, 0.8f)
                        .mixIn(new WaveCanvas(0.1f, seed + 2)
                                .noise(1f).lowPass(900f).drive(2f).decay(0.05f), 0f, 1f)
                        .bake();
            case IMPACT_MASONRY:
                // Chunks of wall: mid thud plus gritty debris tail.
                return new WaveCanvas(0.5f, seed)
                        .noise(1f).lowPass(1100f).decay(0.1f)
                        .mixIn(new WaveCanvas(0.45f, seed + 1)
                                .crackle(120f, 0.9f).lowPass(2000f).decay(0.35f), 0.03f, 0.8f)
                        .bake();
            case IMPACT_DIRT:
                // A shovelful of ground: soft, brief, low.
                return new WaveCanvas(0.25f, seed)
                        .noise(1f).lowPass(700f).decay(0.11f)
                        .bake();
            case EXPLOSION_SMALL:
                // Grenade-sized: crack into boom into a short debris hiss.
                return new WaveCanvas(1f, seed)
                        .noise(1f).lowPass(2200f).drive(4f).decay(0.16f)
                        .mixIn(new WaveCanvas(0.9f, seed + 1)
                                .sine(95f, 38f, 1f).decay(0.5f), 0f, 1f)
                        .mixIn(new WaveCanvas(0.7f, seed + 2)
                                .crackle(90f, 0.7f).lowPass(3000f).decay(0.5f), 0.08f, 0.5f)
                        .bake();
            case EXPLOSION_BIG:
                // Shell-sized: the same shape, longer, deeper, driven harder.
                return new WaveCanvas(1.8f, seed)
                        .noise(1f).lowPass(1600f).drive(5f).decay(0.28f)
                        .mixIn(new WaveCanvas(1.6f, seed + 1)
                                .sine(75f, 28f, 1.1f).decay(0.9f), 0f, 1.1f)
                        .mixIn(new WaveCanvas(1.3f, seed + 2)
                                .crackle(70f, 0.8f).lowPass(2400f).decay(0.9f), 0.12f, 0.6f)
                        .drive(1.5f)
                        .bake();
            case STRUCTURE_COLLAPSE:
                // A building giving up: grinding rumble with masonry raining through it.
                return new WaveCanvas(1.9f, seed)
                        .noise(1f).lowPass(420f).tremolo(7f, 0.4f)
                        .crackle(140f, 1f)
                        .envelope(0.08f, 1.2f).drive(2f)
                        .bake();
            case SHELL_WHISTLE:
                // The falling shell, pitch dropping the whole way. Cut dead so the impact's
                // own report lands in silence.
                return new WaveCanvas(1f, seed)
                        .sine(2100f, 700f, 0.7f)
                        .noise(0.15f).bandPass(1600f, 3f)
                        .envelope(0.15f, 0.06f)
                        .bake();
            case UI_CLICK:
                return new WaveCanvas(0.06f, seed)
                        .square(1300f, 1300f, 0.4f).decay(0.03f)
                        .bake();
            case UI_ERROR:
                // The "no" buzz: two low square notes.
                return new WaveCanvas(0.28f, seed)
                        .square(220f, 220f, 0.4f).tremolo(14f, 0.9f)
                        .envelope(0.01f, 0.1f)
                        .bake();
            case UI_READY:
                // Rising confirmation chirp.
                return new WaveCanvas(0.22f, seed)
                        .square(620f, 940f, 0.35f)
                        .envelope(0.01f, 0.1f)
                        .bake();
            case UNIT_TRAINED:
                // Boots on the step and a radio blip.
                return new WaveCanvas(0.3f, seed)
                        .noise(0.5f).bandPass(900f, 2f).decay(0.09f)
                        .mixIn(new WaveCanvas(0.12f, seed + 1)
                                .square(760f, 760f, 0.35f).decay(0.08f), 0.12f, 1f)
                        .bake();
            case BUILDING_UP:
                // Hammer, hammer, ratchet: construction in three hits.
                return new WaveCanvas(0.55f, seed)
                        .crackle(24f, 0.9f).bandPass(1700f, 3f)
                        .mixIn(new WaveCanvas(0.5f, seed + 1)
                                .noise(0.6f).lowPass(600f).tremolo(8f, 0.8f)
                                .envelope(0.05f, 0.3f), 0f, 0.7f)
                        .bake();
            case POWER_DOWN:
                // The grid browning out: a saw sagging down two octaves.
                return new WaveCanvas(0.8f, seed)
                        .saw(320f, 70f, 0.5f).lowPass(1200f)
                        .envelope(0.02f, 0.5f)
                        .bake();
            case UNDER_ATTACK_STING:
                // The alarm: two harsh klaxon swells.
                return new WaveCanvas(0.9f, seed)
                        .saw(440f, 440f, 0.4f).tremolo(4.4f, 0.95f)
                        .noise(0.12f).bandPass(880f, 2f)
                        .envelope(0.02f, 0.25f).drive(1.6f)
                        .bake();
            case RAIN_BED:
                // Steady rain: hissy noise with droplet crackle, looped seamlessly.
                return new WaveCanvas(5f, seed)
                        .noise(0.7f).lowPass(4500f).highPass(400f)
                        .crackle(160f, 0.35f)
                        .tremolo(0.23f, 0.25f)
                        .loopable(0.8f)
                        .bake();
            case WIND_BED:
                // Wind through wire and ruin: low band-passed noise, slowly gusting.
                return new WaveCanvas(6f, seed)
                        .noise(1f).bandPass(340f, 1.1f).lowPass(900f)
                        .tremolo(0.13f, 0.6f).tremolo(0.31f, 0.3f)
                        .loopable(1f)
                        .bake();
            case DRONE_BED:
                // The dread underneath: detuned low partials, barely moving.
                return new WaveCanvas(8f, seed)
                        .sine(55f, 55f, 0.5f)
                        .sine(55.7f, 55.7f, 0.4f)
                        .sine(110.3f, 110.3f, 0.15f)
                        .tremolo(0.07f, 0.35f)
                        .loopable(1.5f)
                        .bake();
            case THUNDER_1:
            case THUNDER_2:
            case THUNDER_3:
                // Three rolls of different lengths so the storm never repeats itself.
                float len = 1.6f + 0.5f * (cue.ordinal() - Cue.THUNDER_1.ordinal());
                return new WaveCanvas(len, seed)
                        .noise(1f).lowPass(240f).drive(3f)
                        .tremolo(2.1f, 0.5f)
                        .mixIn(new WaveCanvas(len * 0.6f, seed + 1)
                                .crackle(30f, 0.6f).lowPass(700f), 0.15f, 0.5f)
                        .envelope(0.05f, len * 0.6f)
                        .bake();
            default:
                throw new IllegalStateException("No recipe for " + cue);
        }
    }
}
