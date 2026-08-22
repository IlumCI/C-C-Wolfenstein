package com.ccwolf.game.art;

import com.ccwolf.gfx.Image;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.Terrain;
import java.util.HashMap;
import java.util.Map;

/**
 * Bakes every sprite once and hands out the bitmaps.
 *
 * <p>Baking is deliberately eager and up front: several hundred small buffers cost a few tens
 * of milliseconds at startup, and doing it lazily would mean the first tank on screen stalls a
 * frame. Nothing here is drawn per frame.
 */
public final class SpriteAtlas {

    private static SpriteAtlas instance;

    private final Map<String, Image> sprites = new HashMap<String, Image>();

    /** Pixels baked, and how long it took. Both are budgets rather than curiosities. */
    private long pixels;
    private long bakeMillis;

    private SpriteAtlas() {
        long start = System.nanoTime();
        bakeUnits();
        bakeBuildings();
        bakeTerrain();
        bakeEffects();
        bakeMillis = (System.nanoTime() - start) / 1_000_000L;
    }

    /** Every sprite goes through here, so the pixel count cannot drift from what was baked. */
    private void put(String key, PixelCanvas canvas) {
        pixels += (long) canvas.pixelWidth() * canvas.pixelHeight();
        sprites.put(key, canvas.toImage());
    }

    /**
     * Roughly how much memory the atlas holds, at four bytes a pixel.
     *
     * <p>Worth measuring rather than assuming: raising the authoring resolution multiplies this
     * by the square of the change, and an eager bake a desktop shrugs at is a phone running out
     * of heap behind the splash screen.
     */
    public long bytes() {
        return pixels * 4L;
    }

    /** How long the eager bake took. The reason it is eager is that it is supposed to be short. */
    public long bakeMillis() {
        return bakeMillis;
    }

    /** The atlas is shared: the sprites are immutable once baked. */
    public static synchronized SpriteAtlas get() {
        if (instance == null) {
            instance = new SpriteAtlas();
        }
        return instance;
    }

    public int size() {
        return sprites.size();
    }

    // --- baking ---------------------------------------------------------------------------

    private void bakeUnits() {
        for (UnitType type : UnitType.values()) {
            for (Faction faction : Faction.values()) {
                if (!type.availableTo(faction)) {
                    continue;
                }
                for (int facing = 0; facing < UnitSprites.FACINGS; facing++) {
                    for (int frame = 0; frame < frameCount(type); frame++) {
                        put(unitKey(type, faction, facing, frame),
                                UnitSprites.render(type, faction, facing, frame));
                    }
                }
            }
        }
    }

    /** Harvesters use their frames for cargo level, so they need one more than the rest. */
    private static int frameCount(UnitType type) {
        return type.isHarvester() ? 3 : UnitSprites.WALK_FRAMES;
    }

    private void bakeBuildings() {
        for (BuildingType type : BuildingType.values()) {
            for (Faction faction : Faction.values()) {
                for (int damage = 0; damage < BuildingSprites.DAMAGE_STATES; damage++) {
                    put(buildingKey(type, faction, damage),
                            BuildingSprites.render(type, faction, damage));
                }
            }
        }
        for (int facing = 0; facing < UnitSprites.FACINGS; facing++) {
            put("barrel:" + facing, BuildingSprites.flakBarrel(facing));
        }
    }

    private void bakeTerrain() {
        for (Terrain terrain : Terrain.values()) {
            for (int variant = 0; variant < TerrainSprites.VARIANTS; variant++) {
                put(terrainKey(terrain, variant), TerrainSprites.render(terrain, variant));
            }
        }
        for (int variant = 0; variant < TerrainSprites.VARIANTS; variant++) {
            for (int level = 0; level < TerrainSprites.ORE_LEVELS; level++) {
                put(oreKey(variant, level), TerrainSprites.renderOre(variant, level));
            }
        }
    }

    private void bakeEffects() {
        for (int frame = 0; frame < EffectSprites.EXPLOSION_FRAMES; frame++) {
            put("boom:" + frame, EffectSprites.explosion(frame));
        }
        for (int frame = 0; frame < 2; frame++) {
            put("flash:" + frame, EffectSprites.muzzleFlash(frame));
        }
        for (int variant = 0; variant < 4; variant++) {
            put("wreck:" + variant, EffectSprites.wreck(variant));
        }
    }

    // --- lookup ---------------------------------------------------------------------------

    public Image unit(UnitType type, Faction faction, int facing, int frame) {
        Faction owner = type.availableTo(faction) ? faction
                : (type.faction() == null ? faction : type.faction());
        int f = ((facing % UnitSprites.FACINGS) + UnitSprites.FACINGS) % UnitSprites.FACINGS;
        int frames = frameCount(type);
        return sprites.get(unitKey(type, owner, f, ((frame % frames) + frames) % frames));
    }

    public Image building(BuildingType type, Faction faction, int damageState) {
        int damage = Math.max(0, Math.min(BuildingSprites.DAMAGE_STATES - 1, damageState));
        return sprites.get(buildingKey(type, faction, damage));
    }

    public Image flakBarrel(int facing) {
        int f = ((facing % UnitSprites.FACINGS) + UnitSprites.FACINGS) % UnitSprites.FACINGS;
        return sprites.get("barrel:" + f);
    }

    /** Terrain variant is chosen from the tile position, so a given tile always looks the same. */
    public Image terrain(Terrain terrain, int tileX, int tileY) {
        int variant = variantFor(tileX, tileY);
        return sprites.get(terrainKey(terrain, variant));
    }

    /** @param ore uranium remaining in the tile, which decides how much crystal is drawn */
    public Image ore(int tileX, int tileY, int ore) {
        int level = ore > 450 ? 2 : (ore > 150 ? 1 : 0);
        return sprites.get(oreKey(variantFor(tileX, tileY), level));
    }

    public static int variantFor(int tileX, int tileY) {
        // A cheap spatial hash: no pattern the eye can pick out, same answer every frame.
        int h = tileX * 73856093 ^ tileY * 19349663;
        return Math.abs(h) % TerrainSprites.VARIANTS;
    }

    public Image explosion(float progress) {
        int frame = (int) (progress * EffectSprites.EXPLOSION_FRAMES);
        return sprites.get("boom:" + Math.max(0,
                Math.min(EffectSprites.EXPLOSION_FRAMES - 1, frame)));
    }

    public Image muzzleFlash(int frame) {
        return sprites.get("flash:" + (frame & 1));
    }

    public Image wreck(int variant) {
        return sprites.get("wreck:" + (variant & 3));
    }

    // --- keys -----------------------------------------------------------------------------

    private static String unitKey(UnitType type, Faction faction, int facing, int frame) {
        return "u:" + type.ordinal() + ':' + faction.ordinal() + ':' + facing + ':' + frame;
    }

    private static String buildingKey(BuildingType type, Faction faction, int damage) {
        return "b:" + type.ordinal() + ':' + faction.ordinal() + ':' + damage;
    }

    private static String terrainKey(Terrain terrain, int variant) {
        return "t:" + terrain.ordinal() + ':' + variant;
    }

    private static String oreKey(int variant, int level) {
        return "o:" + variant + ':' + level;
    }
}
