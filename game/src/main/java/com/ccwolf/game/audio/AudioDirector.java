package com.ccwolf.game.audio;

import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.event.GameEvent;
import com.ccwolf.core.sim.GameWorld;
import com.ccwolf.game.fx.FxDirector;
import com.ccwolf.game.fx.ProjectileLayer;
import com.ccwolf.game.render.Camera;

/**
 * Turns the simulation's event stream into things you can hear — the FxDirector of sound.
 *
 * <p>The same charter word for word: nothing here can affect the game. It reads the events the
 * effects layer reads, after the effects layer reads them, and plays cues into the mixer; if it
 * were deleted the match would play out identically, only silently.
 *
 * <p>Three rules give it its manners. The fog rule: a positional sound plays only where the
 * viewer could see it happen, or audio becomes a wallhack. The distance rule: pan and level
 * follow the camera, and a fight more than a screen and a half away is somebody else's problem.
 * The restraint rule: every cue has a cooldown and a voice cap, because two hundred rifles
 * firing in a tick is one battle, not two hundred sounds.
 */
public final class AudioDirector implements FxDirector.ImpactListener {

    /** Beyond this many viewport-widths from the screen edge, a sound is inaudible. */
    private static final float EARSHOT_SCREENS = 1.5f;

    /** The classic interval: the base can burn, but the alarm nags at most this often. */
    private static final float UNDER_ATTACK_COOLDOWN = 15f;

    /** At most this many voices of one cue at once; further triggers are the same battle. */
    private static final int MAX_VOICES_PER_CUE = 4;

    /** A shell whistle this long is baked; the fall is cued to end exactly at impact. */
    private static final float WHISTLE_SECONDS = 1.0f;

    private static final int MAX_PENDING = 16;

    private final Mixer mixer;
    private final Ambience ambience;

    /** Per-cue cooldown clocks, decayed in update. */
    private final float[] cooldown = new float[SoundBank.Cue.values().length];

    /** Scheduled one-shots (shell whistles, gas arrival): seconds left, position, cue. */
    private final float[] pendingAt = new float[MAX_PENDING];
    private final float[] pendingX = new float[MAX_PENDING];
    private final float[] pendingY = new float[MAX_PENDING];
    private final SoundBank.Cue[] pendingCue = new SoundBank.Cue[MAX_PENDING];
    private final float[] pendingGain = new float[MAX_PENDING];

    /** The frame's listening position, refreshed each consume. */
    private Camera camera;
    private WorldView view;

    public AudioDirector(long seed, Mixer mixer) {
        this.mixer = mixer;
        this.ambience = new Ambience(mixer, seed);
    }

    // --- event handling -------------------------------------------------------------------

    /** Reads everything that happened this frame and plays what the viewer should hear. */
    public void consume(java.util.List<GameEvent> events, int viewerPlayerId, WorldView view,
            Camera camera) {
        this.camera = camera;
        this.view = view;
        for (int i = 0; i < events.size(); i++) {
            GameEvent e = events.get(i);
            switch (e.type()) {
                case SHOT_FIRED:
                    onShot(e);
                    break;
                case SHELL_IMPACT:
                    playAt(SoundBank.Cue.EXPLOSION_BIG, e.x(), e.y(), 1f, 1f);
                    ambience.onLoudNoise();
                    break;
                case ENTITY_DESTROYED:
                    onDeath(e);
                    break;
                case UNIT_TRAINED:
                case SQUAD_TRAINED:
                    playForPlayer(SoundBank.Cue.UNIT_TRAINED, e, viewerPlayerId, 0.5f);
                    break;
                case BUILDING_COMPLETED:
                    playForPlayer(SoundBank.Cue.BUILDING_UP, e, viewerPlayerId, 0.6f);
                    break;
                case PLACEMENT_READY:
                    playForPlayer(SoundBank.Cue.UI_READY, e, viewerPlayerId, 0.6f);
                    break;
                case INSUFFICIENT_FUNDS:
                    playForPlayer(SoundBank.Cue.UI_ERROR, e, viewerPlayerId, 0.5f);
                    break;
                case POWER_LOST:
                    playForPlayer(SoundBank.Cue.POWER_DOWN, e, viewerPlayerId, 0.6f);
                    break;
                case UNDER_ATTACK:
                    playForPlayer(SoundBank.Cue.UNDER_ATTACK_STING, e, viewerPlayerId, 0.55f);
                    break;
                default:
                    break;
            }
        }
    }

    private void onShot(GameEvent e) {
        WeaponClass weapon = e.weaponClass();
        if (weapon == null) {
            return;
        }
        switch (weapon) {
            case SMALL_ARMS:
                playAt(SoundBank.Cue.RIFLE, e.x(), e.y(), 0.55f, pitchWobble(e));
                break;
            case SNIPER:
                playAt(SoundBank.Cue.SNIPER, e.x(), e.y(), 0.7f, 1f);
                break;
            case CANNON:
                playAt(SoundBank.Cue.CANNON, e.x(), e.y(), 0.85f, pitchWobble(e));
                break;
            case ROCKET:
                playAt(SoundBank.Cue.ROCKET_LAUNCH, e.x(), e.y(), 0.7f, 1f);
                break;
            case GRENADE:
                playAt(SoundBank.Cue.GRENADE_TOSS, e.x(), e.y(), 0.5f, 1f);
                break;
            case FLAME:
                playAt(SoundBank.Cue.FLAME_BURST, e.x(), e.y(), 0.7f, pitchWobble(e));
                break;
            case OCCULT:
                playAt(SoundBank.Cue.OCCULT_ZAP, e.x(), e.y(), 0.8f, 1f);
                break;
            case MELEE:
                playAt(SoundBank.Cue.IMPACT_FLESH, e.toX(), e.toY(), 0.5f, 0.9f);
                break;
            case ARTILLERY:
            case GAS:
                playAt(SoundBank.Cue.ARTY_FIRE, e.x(), e.y(), 0.8f, 1f);
                scheduleArrival(e, weapon);
                break;
            default:
                break;
        }
    }

    /**
     * Cues the falling shell to the simulation's own clock. The event's amount is the flight
     * time in ticks — the same number the effects layer flies its shell by — so the whistle
     * ends, and the gas canister coughs, at the exact tick the damage happens.
     */
    private void scheduleArrival(GameEvent e, WeaponClass weapon) {
        float flight = Math.max(0.05f, e.amount() / (float) GameWorld.TICKS_PER_SECOND);
        if (weapon == WeaponClass.ARTILLERY) {
            schedule(SoundBank.Cue.SHELL_WHISTLE, e.toX(), e.toY(),
                    Math.max(0f, flight - WHISTLE_SECONDS), 0.5f);
        } else {
            schedule(SoundBank.Cue.GAS_POP, e.toX(), e.toY(), flight, 0.7f);
        }
    }

    private void onDeath(GameEvent e) {
        switch (e.targetKind()) {
            case INFANTRY:
                playAt(SoundBank.Cue.IMPACT_FLESH, e.x(), e.y(), 0.7f, 0.8f);
                break;
            case VEHICLE:
                playAt(SoundBank.Cue.EXPLOSION_SMALL, e.x(), e.y(), 0.9f, 1f);
                ambience.onLoudNoise();
                break;
            case STRUCTURE:
                playAt(SoundBank.Cue.STRUCTURE_COLLAPSE, e.x(), e.y(), 1f, 1f);
                ambience.onLoudNoise();
                break;
            default:
                playAt(SoundBank.Cue.EXPLOSION_SMALL, e.x(), e.y(), 0.7f, 1f);
                break;
        }
    }

    /** The projectile layer's rounds arriving — the hitscan half of the impact picture. */
    @Override
    public void impact(ProjectileLayer.Kind round, float x, float y, GameEvent.TargetKind hit) {
        switch (round) {
            case ROCKET:
            case GRENADE:
                playAt(SoundBank.Cue.EXPLOSION_SMALL, x, y, 0.8f, 1f);
                ambience.onLoudNoise();
                return;
            case PLASMA:
                playAt(SoundBank.Cue.OCCULT_ZAP, x, y, 0.5f, 0.7f);
                return;
            default:
                break;
        }
        switch (hit) {
            case INFANTRY:
                playAt(SoundBank.Cue.IMPACT_FLESH, x, y, 0.4f, 1f);
                break;
            case VEHICLE:
                playAt(SoundBank.Cue.IMPACT_ARMOR, x, y, 0.45f, 1f);
                break;
            case STRUCTURE:
                playAt(SoundBank.Cue.IMPACT_MASONRY, x, y, 0.45f, 1f);
                break;
            default:
                playAt(SoundBank.Cue.IMPACT_DIRT, x, y, 0.35f, 1f);
                break;
        }
    }

    /** Interface taps and pings, requested by the session rather than by an event. */
    public void uiClick() {
        play(SoundBank.Cue.UI_CLICK, 0.4f, 0f, 1f, 0.03f);
    }

    // --- frame ------------------------------------------------------------------------------

    public void update(float dt) {
        for (int i = 0; i < cooldown.length; i++) {
            if (cooldown[i] > 0f) {
                cooldown[i] -= dt;
            }
        }
        for (int i = 0; i < MAX_PENDING; i++) {
            if (pendingCue[i] != null) {
                pendingAt[i] -= dt;
                if (pendingAt[i] <= 0f) {
                    playAt(pendingCue[i], pendingX[i], pendingY[i], pendingGain[i], 1f);
                    pendingCue[i] = null;
                }
            }
        }
        ambience.update(dt);
    }

    /** Pause dims the world rather than stopping it: the storm keeps falling, quietly. */
    public void setDucked(boolean ducked) {
        mixer.setDuckGain(ducked ? 0.25f : 1f);
    }

    // --- internals --------------------------------------------------------------------------

    /**
     * A sound tied to a place: gated by fog, panned and attenuated by the camera, throttled
     * by its cue's cooldown and voice cap.
     */
    private void playAt(SoundBank.Cue cue, float x, float y, float gain, float pitch) {
        if (view != null && !view.isVisible((int) x, (int) y)) {
            return;
        }
        if (camera == null) {
            play(cue, gain, 0f, pitch, cooldownFor(cue));
            return;
        }
        float sx = camera.screenX(x);
        float sy = camera.screenY(y);
        float left = camera.viewLeft();
        float top = camera.viewTop();
        float w = Math.max(1f, camera.viewWidth());
        float h = Math.max(1f, camera.viewHeight());

        float cx = left + w * 0.5f;
        float pan = Math.max(-1f, Math.min(1f, (sx - cx) / (w * 0.5f))) * 0.8f;

        // Distance past the nearest viewport edge, in screens; on-screen is zero.
        float dx = Math.max(0f, Math.max(left - sx, sx - (left + w))) / w;
        float dy = Math.max(0f, Math.max(top - sy, sy - (top + h))) / h;
        float off = (float) Math.sqrt(dx * dx + dy * dy);
        if (off >= EARSHOT_SCREENS) {
            return;
        }
        float attenuated = gain * (1f - off / EARSHOT_SCREENS);
        play(cue, attenuated, pan, pitch, cooldownFor(cue));
    }

    /** An unpositioned cue that belongs to one player's interface, not to the field. */
    private void playForPlayer(SoundBank.Cue cue, GameEvent e, int viewerPlayerId, float gain) {
        if (e.ownerId() != viewerPlayerId) {
            return;
        }
        float cd = cue == SoundBank.Cue.UNDER_ATTACK_STING ? UNDER_ATTACK_COOLDOWN
                : cue == SoundBank.Cue.UI_ERROR ? 1.5f
                : 0.8f;
        play(cue, gain, 0f, 1f, cd);
    }

    private void play(SoundBank.Cue cue, float gain, float pan, float pitch, float cd) {
        int i = cue.ordinal();
        if (cooldown[i] > 0f) {
            return;
        }
        if (mixer.voicesPlaying(cue) >= MAX_VOICES_PER_CUE) {
            return;
        }
        mixer.play(cue, gain, pan, pitch);
        cooldown[i] = cd;
    }

    private void schedule(SoundBank.Cue cue, float x, float y, float inSeconds, float gain) {
        for (int i = 0; i < MAX_PENDING; i++) {
            if (pendingCue[i] == null) {
                pendingCue[i] = cue;
                pendingX[i] = x;
                pendingY[i] = y;
                pendingAt[i] = inSeconds;
                pendingGain[i] = gain;
                return;
            }
        }
        // Sixteen shells already falling: one more whistle would not be heard anyway.
    }

    /** Battlefield cues repeat fast but not per-bullet; the numbers are restraint, not taste. */
    private static float cooldownFor(SoundBank.Cue cue) {
        switch (cue) {
            case RIFLE:
                return 0.09f;
            case IMPACT_FLESH:
            case IMPACT_ARMOR:
            case IMPACT_MASONRY:
            case IMPACT_DIRT:
                return 0.12f;
            case EXPLOSION_SMALL:
                return 0.15f;
            case EXPLOSION_BIG:
                return 0.2f;
            case STRUCTURE_COLLAPSE:
                return 0.5f;
            default:
                return 0.1f;
        }
    }

    /**
     * A touch of per-shot pitch variety, keyed to the shot's own coordinates rather than a
     * random draw — the same trick the sprite speckle uses, and just as inert.
     */
    private static float pitchWobble(GameEvent e) {
        int h = (int) (e.x() * 31f + e.y() * 17f);
        return 0.92f + ((h & 7) / 7f) * 0.16f;
    }
}
