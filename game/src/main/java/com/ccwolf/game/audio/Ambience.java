package com.ccwolf.game.audio;

import java.util.Random;

/**
 * The weather this world is condemned to, as sound: rain, wind, a low drone, and thunder.
 *
 * <p>The art direction says it is never a clear day, and the beds run unconditionally to match.
 * Their levels drift slowly on a seeded random walk so an hour of it never settles into an
 * obvious loop, and the whole layer ducks when the fighting gets loud — the storm is the floor
 * of the mix, not a competitor in it.
 */
final class Ambience {

    private static final float RAIN_LEVEL = 0.16f;
    private static final float WIND_LEVEL = 0.14f;
    private static final float DRONE_LEVEL = 0.10f;
    /** How far below their level the beds sit while combat is loud. */
    private static final float COMBAT_DUCK = 0.5f;

    private final Mixer mixer;
    private final Random random;

    private int rainVoice = -1;
    private int windVoice = -1;
    private int droneVoice = -1;

    private float rainDrift;
    private float windDrift;
    private float nextThunder;
    /** Rises when explosions land, decays on its own; above 1 the beds duck. */
    private float combatHeat;

    private boolean started;

    Ambience(Mixer mixer, long seed) {
        this.mixer = mixer;
        this.random = new Random(seed ^ 0x57082AL);
        this.nextThunder = 8f + random.nextFloat() * 20f;
    }

    /** An explosion landed somewhere audible; the weather gets out of its way for a while. */
    void onLoudNoise() {
        combatHeat = Math.min(4f, combatHeat + 0.6f);
    }

    void update(float dt) {
        if (!started) {
            rainVoice = mixer.playLoop(SoundBank.Cue.RAIN_BED, RAIN_LEVEL);
            windVoice = mixer.playLoop(SoundBank.Cue.WIND_BED, WIND_LEVEL);
            droneVoice = mixer.playLoop(SoundBank.Cue.DRONE_BED, DRONE_LEVEL);
            started = true;
        }

        combatHeat = Math.max(0f, combatHeat - dt * 0.4f);
        float duck = combatHeat > 1f ? COMBAT_DUCK : 1f;

        // Random walk, reflected at the walls: gusts arrive and pass, rain thickens and thins.
        rainDrift = drift(rainDrift, dt);
        windDrift = drift(windDrift, dt);
        mixer.setVoiceGain(rainVoice, RAIN_LEVEL * (1f + rainDrift) * duck, 0f);
        mixer.setVoiceGain(windVoice, WIND_LEVEL * (1f + windDrift) * duck, 0f);
        mixer.setVoiceGain(droneVoice, DRONE_LEVEL * duck, 0f);

        nextThunder -= dt;
        if (nextThunder <= 0f) {
            SoundBank.Cue roll;
            switch (random.nextInt(3)) {
                case 0:
                    roll = SoundBank.Cue.THUNDER_1;
                    break;
                case 1:
                    roll = SoundBank.Cue.THUNDER_2;
                    break;
                default:
                    roll = SoundBank.Cue.THUNDER_3;
                    break;
            }
            // Off to one side, never centred: the storm surrounds the valley, it is not in it.
            float pan = (random.nextFloat() - 0.5f) * 1.4f;
            mixer.play(roll, 0.25f + random.nextFloat() * 0.2f, pan,
                    0.9f + random.nextFloat() * 0.2f);
            nextThunder = 20f + random.nextFloat() * 40f;
        }
    }

    private float drift(float value, float dt) {
        float next = value + (random.nextFloat() - 0.5f) * dt * 0.5f;
        return Math.max(-0.5f, Math.min(0.5f, next));
    }
}
