package com.ccwolf.android.art;

import android.graphics.Bitmap;
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

    private final Map<String, Bitmap> sprites = new HashMap<String, Bitmap>();

    private SpriteAtlas() {
        bakeUnits();
        bakeBuildings();
        bakeTerrain();
        bakeEffects();
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
                        sprites.put(unitKey(type, faction, facing, frame),
                                UnitSprites.render(type, faction, facing, frame).toBitmap());
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
                    sprites.put(buildingKey(type, faction, damage),
                            BuildingSprites.render(type, faction, damage).toBitmap());
                }
            }
        }
        for (int facing = 0; facing < UnitSprites.FACINGS; facing++) {
            sprites.put("barrel:" + facing, BuildingSprites.flakBarrel(facing).toBitmap());
        }
    }

    private void bakeTerrain() {
        for (Terrain terrain : Terrain.values()) {
            for (int variant = 0; variant < TerrainSprites.VARIANTS; variant++) {
                sprites.put(terrainKey(terrain, variant),
                        TerrainSprites.render(terrain, variant).toBitmap());
            }
        }
        for (int variant = 0; variant < TerrainSprites.VARIANTS; variant++) {
            for (int level = 0; level < TerrainSprites.ORE_LEVELS; level++) {
                sprites.put(oreKey(variant, level),
                        TerrainSprites.renderOre(variant, level).toBitmap());
            }
        }
    }

    private void bakeEffects() {
        for (int frame = 0; frame < EffectSprites.EXPLOSION_FRAMES; frame++) {
            sprites.put("boom:" + frame, EffectSprites.explosion(frame).toBitmap());
        }
        for (int frame = 0; frame < 2; frame++) {
            sprites.put("flash:" + frame, EffectSprites.muzzleFlash(frame).toBitmap());
        }
        for (int variant = 0; variant < 4; variant++) {
            sprites.put("wreck:" + variant, EffectSprites.wreck(variant).toBitmap());
        }
    }

    // --- lookup ---------------------------------------------------------------------------

    public Bitmap unit(UnitType type, Faction faction, int facing, int frame) {
        Faction owner = type.availableTo(faction) ? faction
                : (type.faction() == null ? faction : type.faction());
        int f = ((facing % UnitSprites.FACINGS) + UnitSprites.FACINGS) % UnitSprites.FACINGS;
        int frames = frameCount(type);
        return sprites.get(unitKey(type, owner, f, ((frame % frames) + frames) % frames));
    }

    public Bitmap building(BuildingType type, Faction faction, int damageState) {
        int damage = Math.max(0, Math.min(BuildingSprites.DAMAGE_STATES - 1, damageState));
        return sprites.get(buildingKey(type, faction, damage));
    }

    public Bitmap flakBarrel(int facing) {
        int f = ((facing % UnitSprites.FACINGS) + UnitSprites.FACINGS) % UnitSprites.FACINGS;
        return sprites.get("barrel:" + f);
    }

    /** Terrain variant is chosen from the tile position, so a given tile always looks the same. */
    public Bitmap terrain(Terrain terrain, int tileX, int tileY) {
        int variant = variantFor(tileX, tileY);
        return sprites.get(terrainKey(terrain, variant));
    }

    /** @param ore uranium remaining in the tile, which decides how much crystal is drawn */
    public Bitmap ore(int tileX, int tileY, int ore) {
        int level = ore > 450 ? 2 : (ore > 150 ? 1 : 0);
        return sprites.get(oreKey(variantFor(tileX, tileY), level));
    }

    public static int variantFor(int tileX, int tileY) {
        // A cheap spatial hash: no pattern the eye can pick out, same answer every frame.
        int h = tileX * 73856093 ^ tileY * 19349663;
        return Math.abs(h) % TerrainSprites.VARIANTS;
    }

    public Bitmap explosion(float progress) {
        int frame = (int) (progress * EffectSprites.EXPLOSION_FRAMES);
        return sprites.get("boom:" + Math.max(0,
                Math.min(EffectSprites.EXPLOSION_FRAMES - 1, frame)));
    }

    public Bitmap muzzleFlash(int frame) {
        return sprites.get("flash:" + (frame & 1));
    }

    public Bitmap wreck(int variant) {
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
