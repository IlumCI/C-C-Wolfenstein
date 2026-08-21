package com.ccwolf.android.fx;

import android.graphics.Canvas;
import com.ccwolf.android.render.Camera;
import com.ccwolf.core.combat.WeaponClass;
import com.ccwolf.core.event.GameEvent;
import java.util.List;
import java.util.Random;

/**
 * Turns the simulation's event stream into things you can see.
 *
 * <p>This is the whole of the effects policy in one place: which weapon throws which round,
 * what an impact looks like against flesh as opposed to armour or masonry, how a tank dies
 * differently from a rifleman, and how hard the camera shakes when a structure goes up.
 *
 * <p>Nothing here can affect the game. It reads events and draws; if it were deleted the match
 * would play out identically.
 */
public final class FxDirector {

    /** Camera shake decays this fast, in units per second. */
    private static final float SHAKE_DECAY = 5.5f;

    /** Hard ceiling on shake, so a big battle never makes the game unplayable. */
    private static final float MAX_SHAKE = 0.55f;

    private final ParticleSystem particles;
    private final DecalLayer decals;
    private final ProjectileLayer projectiles;
    private final Random random;

    private float shake;

    public FxDirector(long seed) {
        this.particles = new ParticleSystem(seed);
        this.decals = new DecalLayer(seed ^ 0x5EED);
        this.projectiles = new ProjectileLayer();
        this.random = new Random(seed ^ 0xF00D);
    }

    public ParticleSystem particles() {
        return particles;
    }

    public DecalLayer decals() {
        return decals;
    }

    public ProjectileLayer projectiles() {
        return projectiles;
    }

    /** Current shake amplitude in tiles, for the camera to offset by. */
    public float shake() {
        return shake;
    }

    public void clear() {
        particles.clear();
        decals.clear();
        projectiles.clear();
        shake = 0f;
    }

    // --- event handling ---------------------------------------------------------------------

    /** Reads everything that happened this frame and stages the effects for it. */
    public void consume(List<GameEvent> events, int viewerPlayerId) {
        for (int i = 0; i < events.size(); i++) {
            GameEvent e = events.get(i);
            switch (e.type()) {
                case SHOT_FIRED:
                    onShot(e);
                    break;
                case ENTITY_DESTROYED:
                    onDeath(e);
                    break;
                case SABOTAGED:
                    onSabotage(e);
                    break;
                case HIJACKED:
                    onHijack(e);
                    break;
                case BUILDING_SOLD:
                    decals.add(DecalLayer.Kind.SCORCH, e.x(), e.y(), 1.2f, 20f);
                    particles.puff(ParticleSystem.Kind.DUST, e.x(), e.y(), 18, 1.6f, 0.9f, 0.1f);
                    break;
                default:
                    break;
            }
        }
    }

    private void onShot(GameEvent e) {
        WeaponClass weapon = e.weaponClass();

        // Muzzle: a flash of sparks and, for the heavier weapons, a wash of smoke.
        float dx = e.toX() - e.x();
        float dy = e.toY() - e.y();
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 0.001f) {
            dx /= length;
            dy /= length;
        }

        if (weapon == WeaponClass.FLAME) {
            // No projectile: a licking cone of fire that pools where it lands.
            particles.burst(ParticleSystem.Kind.FLAME, e.x() + dx * 0.4f, e.y() + dy * 0.4f,
                    dx, dy, 14, 3.4f, 0.7f, 0.5f, 0.13f);
            particles.burst(ParticleSystem.Kind.SMOKE, e.x() + dx * 0.8f, e.y() + dy * 0.8f,
                    dx, dy, 4, 1.4f, 0.9f, 0.9f, 0.12f);
            decals.add(DecalLayer.Kind.BURN, e.toX(), e.toY(), 0.6f, 9f);
            onImpact(ProjectileLayer.Kind.BULLET, e.toX(), e.toY(), e.targetKind());
            return;
        }
        if (weapon == WeaponClass.MELEE) {
            onImpact(ProjectileLayer.Kind.BULLET, e.toX(), e.toY(), e.targetKind());
            return;
        }

        ProjectileLayer.Kind round = ProjectileLayer.kindFor(weapon);
        projectiles.fire(round, e.x(), e.y(), e.toX(), e.toY(), e.targetKind());

        particles.burst(ParticleSystem.Kind.SPARK, e.x() + dx * 0.45f, e.y() + dy * 0.45f,
                dx, dy, round == ProjectileLayer.Kind.BULLET ? 3 : 6, 2.2f, 0.8f, 0.16f, 0.05f);
        if (round == ProjectileLayer.Kind.SHELL || round == ProjectileLayer.Kind.ROCKET) {
            // Recoil smoke off the muzzle.
            particles.burst(ParticleSystem.Kind.SMOKE, e.x() + dx * 0.6f, e.y() + dy * 0.6f,
                    dx, dy, 5, 1.2f, 1.1f, 0.75f, 0.11f);
            shake(0.05f);
        }
    }

    /** Called by the projectile layer when a round arrives. */
    void onImpact(ProjectileLayer.Kind round, float x, float y, GameEvent.TargetKind hit) {
        switch (hit) {
            case INFANTRY:
                // Flesh: a spray, and a stain that stays.
                particles.puff(ParticleSystem.Kind.BLOOD, x, y, 9, 2.6f, 0.65f, 0.05f);
                decals.add(DecalLayer.Kind.BLOOD, x, y, 0.38f, 22f);
                break;
            case VEHICLE:
                // Armour: sparks off the plate and a chip or two of metal.
                particles.puff(ParticleSystem.Kind.SPARK, x, y, 8, 3.0f, 0.3f, 0.05f);
                particles.puff(ParticleSystem.Kind.DEBRIS, x, y, 3, 2.2f, 0.6f, 0.06f);
                break;
            case STRUCTURE:
                // Masonry: dust and chips.
                particles.puff(ParticleSystem.Kind.DUST, x, y, 8, 1.8f, 0.6f, 0.08f);
                particles.puff(ParticleSystem.Kind.DEBRIS, x, y, 3, 1.6f, 0.7f, 0.06f);
                break;
            case NONE:
            default:
                particles.puff(ParticleSystem.Kind.DUST, x, y, 5, 1.6f, 0.5f, 0.08f);
                break;
        }

        // The round itself adds its own signature on top of what it hit.
        switch (round) {
            case ROCKET:
                explosion(x, y, 1.5f);
                break;
            case GRENADE:
                explosion(x, y, 1.2f);
                break;
            case SHELL:
                particles.puff(ParticleSystem.Kind.DUST, x, y, 10, 2.4f, 0.6f, 0.09f);
                decals.add(DecalLayer.Kind.SCORCH, x, y, 0.6f, 14f);
                shake(0.04f);
                break;
            case PLASMA:
                // Occult discharge: green sparks and tendrils crawling outward.
                particles.puff(ParticleSystem.Kind.PLASMA, x, y, 14, 3.2f, 0.45f, 0.06f);
                break;
            case SNIPER_ROUND:
                particles.puff(ParticleSystem.Kind.SPARK, x, y, 4, 3.4f, 0.22f, 0.05f);
                break;
            case BULLET:
            default:
                break;
        }
    }

    private void onDeath(GameEvent e) {
        switch (e.targetKind()) {
            case INFANTRY:
                // A man comes apart: blood, gibs that arc and land, and a stain.
                particles.puff(ParticleSystem.Kind.BLOOD, e.x(), e.y(), 22, 3.4f, 0.9f, 0.06f);
                particles.puff(ParticleSystem.Kind.DEBRIS, e.x(), e.y(), 7, 2.8f, 1.0f, 0.06f);
                decals.add(DecalLayer.Kind.BLOOD, e.x(), e.y(), 0.7f, 30f);
                break;
            case VEHICLE:
                // A fireball, spinning plate, and burning oil on the ground.
                explosion(e.x(), e.y(), 2.0f);
                particles.puff(ParticleSystem.Kind.DEBRIS, e.x(), e.y(), 16, 4.2f, 1.3f, 0.08f);
                particles.puff(ParticleSystem.Kind.SMOKE, e.x(), e.y(), 14, 1.4f, 2.2f, 0.16f);
                decals.add(DecalLayer.Kind.OIL, e.x(), e.y(), 0.9f, 40f);
                decals.add(DecalLayer.Kind.SCORCH, e.x(), e.y(), 1.1f, 40f);
                shake(0.22f);
                break;
            case STRUCTURE:
                // Collapse: a dust plume first, then flame out of the holes, then rubble.
                particles.puff(ParticleSystem.Kind.DUST, e.x(), e.y(), 40, 3.0f, 1.8f, 0.16f);
                particles.puff(ParticleSystem.Kind.DEBRIS, e.x(), e.y(), 22, 4.6f, 1.6f, 0.09f);
                particles.puff(ParticleSystem.Kind.FLAME, e.x(), e.y(), 18, 2.2f, 1.0f, 0.14f);
                particles.puff(ParticleSystem.Kind.SMOKE, e.x(), e.y(), 22, 1.6f, 3.0f, 0.2f);
                decals.add(DecalLayer.Kind.CRATER, e.x(), e.y(), 1.6f, 60f);
                shake(0.45f);
                break;
            case NONE:
            default:
                explosion(e.x(), e.y(), 1.2f);
                break;
        }
    }

    private void onSabotage(GameEvent e) {
        // Arcing discharge over whatever just went dark.
        particles.puff(ParticleSystem.Kind.PLASMA, e.x(), e.y(), 18, 2.6f, 0.8f, 0.07f);
        particles.puff(ParticleSystem.Kind.SPARK, e.x(), e.y(), 10, 2.0f, 0.5f, 0.05f);
    }

    private void onHijack(GameEvent e) {
        particles.puff(ParticleSystem.Kind.SPARK, e.x(), e.y(), 12, 2.4f, 0.6f, 0.05f);
        particles.puff(ParticleSystem.Kind.SMOKE, e.x(), e.y(), 6, 1.0f, 1.0f, 0.12f);
    }

    /** A fireball with a smoke ring and a scorch mark, scaled by how big the thing was. */
    private void explosion(float x, float y, float scale) {
        particles.puff(ParticleSystem.Kind.FLAME, x, y, (int) (16 * scale), 2.6f * scale,
                0.5f, 0.11f * scale);
        particles.puff(ParticleSystem.Kind.SPARK, x, y, (int) (12 * scale), 4.0f * scale,
                0.4f, 0.05f);
        particles.puff(ParticleSystem.Kind.SMOKE, x, y, (int) (10 * scale), 1.2f * scale,
                1.6f, 0.15f * scale);
        decals.add(DecalLayer.Kind.SCORCH, x, y, 0.7f * scale, 25f);
        shake(0.09f * scale);
    }

    private void shake(float amount) {
        shake = Math.min(MAX_SHAKE, shake + amount);
    }

    // --- frame ------------------------------------------------------------------------------

    public void update(float dt) {
        projectiles.update(dt, this, particles);
        particles.update(dt);
        decals.update(dt);
        shake = Math.max(0f, shake - SHAKE_DECAY * shake * dt - 0.02f * dt);
    }

    /** Ground marks, drawn under everything that stands on the ground. */
    public void drawDecals(Canvas canvas, Camera camera) {
        decals.draw(canvas, camera);
    }

    /** Rounds and particles, drawn over the entities. */
    public void drawOverlay(Canvas canvas, Camera camera) {
        projectiles.draw(canvas, camera);
        particles.draw(canvas, camera);
    }

    /** A jittered offset in tiles for the camera, unique per frame. */
    public float shakeOffsetX() {
        return shake == 0f ? 0f : (random.nextFloat() - 0.5f) * shake;
    }

    public float shakeOffsetY() {
        return shake == 0f ? 0f : (random.nextFloat() - 0.5f) * shake;
    }
}
