package com.ccwolf.game.render;

import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.SpriteAtlas;
import com.ccwolf.game.art.TerrainSprites;
import com.ccwolf.gfx.Image;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The ground, baked into blocks instead of redrawn a tile at a time.
 *
 * <p>Drawing terrain used to be one scaled blit per visible tile, every frame — 475 of them at
 * a normal zoom, and 84% of the frame. Nothing about that scales with what is happening in the
 * game: it was the same cost with four units on the map as it would be with a thousand.
 *
 * <p>So tiles are composited once into 8x8 blocks and the blocks are drawn instead. Same
 * pixels, roughly forty times fewer draw calls.
 *
 * <h2>Why blocks rather than one image</h2>
 *
 * <p>Baking the whole map would be simpler, but a 128x128 map at native sprite resolution is
 * 4096x4096 — sixty-four megabytes of bitmap that a phone should not be asked to hold, most of
 * it off-screen. Blocks are baked on demand and the least recently seen are dropped, so what is
 * resident tracks what is visible rather than how big the map is.
 *
 * <h2>What invalidates a block</h2>
 *
 * <p>Terrain does not change, but two things on top of it do. Uranium is mined out, and a seam
 * that is worked out has to stop looking like a seam. And men dig, so ground that was open
 * grows a trench across it. Each block remembers both readings it was baked with and rebakes
 * when either moves — which stays cheap because both are coarse whole numbers, not the exact
 * amounts underneath them, so most ticks of mining and most ticks of digging change nothing.
 */
public final class TerrainCache {

    /** Tiles per block edge. Small enough that memory tracks the viewport, large enough to matter. */
    static final int BLOCK_TILES = 8;

    /**
     * How many blocks stay resident.
     *
     * <p>Sized for the worst case that actually happens: fully zoomed out, a viewport is about
     * nine blocks across and seven down. Past that the least recently drawn go.
     */
    private static final int MAX_RESIDENT = 80;

    private final Map<Long, Block> blocks = new HashMap<Long, Block>();

    private int mapWidth;
    private int mapHeight;
    private long useCounter;
    private int bakes;

    /** One baked block, and enough about it to know when it has gone stale. */
    private static final class Block {
        Image image;
        /** Coarse ore level per tile at bake time; null for a block with no seams in it. */
        byte[] oreLevels;
        /** Dug depth per tile at bake time; null for a block nobody has broken ground in. */
        byte[] digLevels;
        /** Which side's works were drawn, per tile. Alongside depth, because either can change. */
        byte[] builders;
        /** Pixels per tile this block was baked at, so a zoom change rebakes instead of blurs. */
        int resolution;
        long lastUsed;
    }

    /** Drops everything. Call when the map itself changes. */
    public void clear() {
        blocks.clear();
    }

    public int blocksResident() {
        return blocks.size();
    }

    /** Blocks composited since startup. Should stop climbing once the view settles. */
    public int bakes() {
        return bakes;
    }

    /**
     * The baked block containing a tile, baking it if needed.
     *
     * @param blockX block column, i.e. tileX / BLOCK_TILES
     */
    /**
     * Pixels per tile to bake at, for a camera showing tiles this big.
     *
     * <p>Terrain is authored at {@link TerrainSprites#TILE}, which is sized for the closest the
     * camera ever gets. Baking every block at that size regardless of zoom would be the worst of
     * both worlds: fully zoomed out the whole map is on screen, a block would be a megabyte of
     * pixels being squeezed into a thumbnail, and eighty of them resident is a third of a
     * gigabyte. So blocks are baked at the smallest size that is still at least as big as the
     * camera is asking for, and the choice is part of the cache key.
     *
     * <p>Powers of two only, so the box filter divides evenly and a downscale is an exact
     * average rather than a resampling with its own artefacts.
     */
    static int resolutionFor(float tilePx) {
        int res = TerrainSprites.TILE;
        while (res > 32 && res / 2 >= tilePx) {
            res /= 2;
        }
        return res;
    }

    public Image block(TileMap map, int blockX, int blockY) {
        return block(map, blockX, blockY, TerrainSprites.TILE);
    }

    public Image block(TileMap map, int blockX, int blockY, int resolution) {
        if (map.width() != mapWidth || map.height() != mapHeight) {
            // A different map: nothing cached can be about this one.
            clear();
            mapWidth = map.width();
            mapHeight = map.height();
        }

        Long key = Long.valueOf(((long) blockX << 32) ^ (blockY & 0xFFFFFFFFL));
        Block block = blocks.get(key);
        if (block != null && block.resolution == resolution
                && !isStale(map, block, blockX, blockY)) {
            block.lastUsed = ++useCounter;
            return block.image;
        }

        Block baked = bake(map, blockX, blockY, resolution);
        baked.lastUsed = ++useCounter;
        blocks.put(key, baked);
        evictIfCrowded();
        return baked.image;
    }

    private Block bake(TileMap map, int blockX, int blockY, int resolution) {
        int tile = resolution;
        int shrink = TerrainSprites.TILE / resolution;
        int originX = blockX * BLOCK_TILES;
        int originY = blockY * BLOCK_TILES;

        PixelCanvas canvas = new PixelCanvas(BLOCK_TILES * tile, BLOCK_TILES * tile);
        byte[] levels = null;
        byte[] digs = null;
        byte[] builders = null;

        for (int dy = 0; dy < BLOCK_TILES; dy++) {
            for (int dx = 0; dx < BLOCK_TILES; dx++) {
                int x = originX + dx;
                int y = originY + dy;
                Terrain terrain = map.terrain(x, y);
                int variant = SpriteAtlas.variantFor(x, y);

                PixelCanvas sprite;
                if (terrain == Terrain.ORE && map.ore(x, y) > 0) {
                    int level = oreLevel(map.ore(x, y));
                    if (levels == null) {
                        levels = new byte[BLOCK_TILES * BLOCK_TILES];
                        // -1 marks "not a seam", so a plain tile never looks stale.
                        java.util.Arrays.fill(levels, (byte) -1);
                    }
                    levels[dy * BLOCK_TILES + dx] = (byte) level;
                    sprite = TerrainSprites.renderOre(variant, level);
                } else {
                    sprite = TerrainSprites.render(terrain, variant);
                }

                int dug = map.entrenchment(x, y);
                Faction built = map.builderOf(x, y);
                if (dug > 0) {
                    if (digs == null) {
                        digs = new byte[BLOCK_TILES * BLOCK_TILES];
                        builders = new byte[BLOCK_TILES * BLOCK_TILES];
                        java.util.Arrays.fill(builders, (byte) -1);
                    }
                    digs[dy * BLOCK_TILES + dx] = (byte) dug;
                    builders[dy * BLOCK_TILES + dx] =
                            built == null ? (byte) -1 : (byte) built.ordinal();
                    // Safe to cut into directly: both branches above hand back a canvas they
                    // just made. If either ever starts returning a shared atlas sprite this
                    // needs a copy first, or one trench would appear on every grass tile.
                    TerrainSprites.entrench(sprite, dug, variant, built);
                }
                canvas.blit(sprite.downscaled(shrink), dx * tile, dy * tile);
            }
        }

        bakes++;
        Block block = new Block();
        block.digLevels = digs;
        block.builders = builders;
        block.resolution = resolution;
        // Opaque by construction: every tile of a block is a full terrain sprite, so there is
        // no transparency to composite and the backend can copy instead.
        block.image = canvas.toOpaqueImage();
        block.oreLevels = levels;
        return block;
    }

    /** A block is stale once a seam in it has changed level, or somebody has moved earth. */
    private boolean isStale(TileMap map, Block block, int blockX, int blockY) {
        int originX = blockX * BLOCK_TILES;
        int originY = blockY * BLOCK_TILES;

        // Digging has to be checked even on a block that had no earthworks when it was baked —
        // unlike ore, which can only ever decrease from tiles that were already seams, ground
        // is dug where there was nothing before. So the null case here means "was flat", not
        // "cannot change", and a null array is compared against zero rather than skipped.
        for (int dy = 0; dy < BLOCK_TILES; dy++) {
            for (int dx = 0; dx < BLOCK_TILES; dx++) {
                int index = dy * BLOCK_TILES + dx;
                byte bakedDig = block.digLevels == null ? 0 : block.digLevels[index];
                if (map.entrenchment(originX + dx, originY + dy) != bakedDig) {
                    return true;
                }
                if (block.builders != null) {
                    Faction built = map.builderOf(originX + dx, originY + dy);
                    byte now = built == null ? (byte) -1 : (byte) built.ordinal();
                    if (now != block.builders[index]) {
                        return true;
                    }
                }
                if (block.oreLevels == null) {
                    continue;
                }
                byte baked = block.oreLevels[index];
                if (baked < 0) {
                    continue;
                }
                int ore = map.ore(originX + dx, originY + dy);
                int now = ore > 0 ? oreLevel(ore) : -1;
                if (now != baked) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Matches SpriteAtlas.ore's banding, so a baked seam looks like a drawn one. */
    private static int oreLevel(int ore) {
        return ore > 450 ? 2 : (ore > 150 ? 1 : 0);
    }

    private void evictIfCrowded() {
        while (blocks.size() > MAX_RESIDENT) {
            Long oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Long, Block> entry : blocks.entrySet()) {
                if (entry.getValue().lastUsed < oldest) {
                    oldest = entry.getValue().lastUsed;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            blocks.remove(oldestKey);
        }
    }
}
