package com.ccwolf.game.render;

import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.SpriteAtlas;
import com.ccwolf.game.art.TerrainSprites;
import com.ccwolf.gfx.Image;
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
 * <p>Terrain does not change, but uranium does: a seam that is worked out has to stop looking
 * like a seam. Each block remembers the ore levels it was baked with and rebakes when they
 * move — which is rare, because the level is a coarse three-step reading of the tile, not its
 * exact amount.
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
    public Image block(TileMap map, int blockX, int blockY) {
        if (map.width() != mapWidth || map.height() != mapHeight) {
            // A different map: nothing cached can be about this one.
            clear();
            mapWidth = map.width();
            mapHeight = map.height();
        }

        Long key = Long.valueOf(((long) blockX << 32) ^ (blockY & 0xFFFFFFFFL));
        Block block = blocks.get(key);
        if (block != null && !isStale(map, block, blockX, blockY)) {
            block.lastUsed = ++useCounter;
            return block.image;
        }

        Block baked = bake(map, blockX, blockY);
        baked.lastUsed = ++useCounter;
        blocks.put(key, baked);
        evictIfCrowded();
        return baked.image;
    }

    private Block bake(TileMap map, int blockX, int blockY) {
        int tile = TerrainSprites.TILE;
        int originX = blockX * BLOCK_TILES;
        int originY = blockY * BLOCK_TILES;

        PixelCanvas canvas = new PixelCanvas(BLOCK_TILES * tile, BLOCK_TILES * tile);
        byte[] levels = null;

        for (int dy = 0; dy < BLOCK_TILES; dy++) {
            for (int dx = 0; dx < BLOCK_TILES; dx++) {
                int x = originX + dx;
                int y = originY + dy;
                Terrain terrain = map.terrain(x, y);
                int variant = SpriteAtlas.variantFor(x, y);

                if (terrain == Terrain.ORE && map.ore(x, y) > 0) {
                    int level = oreLevel(map.ore(x, y));
                    if (levels == null) {
                        levels = new byte[BLOCK_TILES * BLOCK_TILES];
                        // -1 marks "not a seam", so a plain tile never looks stale.
                        java.util.Arrays.fill(levels, (byte) -1);
                    }
                    levels[dy * BLOCK_TILES + dx] = (byte) level;
                    canvas.blit(TerrainSprites.renderOre(variant, level), dx * tile, dy * tile);
                } else {
                    canvas.blit(TerrainSprites.render(terrain, variant), dx * tile, dy * tile);
                }
            }
        }

        bakes++;
        Block block = new Block();
        // Opaque by construction: every tile of a block is a full terrain sprite, so there is
        // no transparency to composite and the backend can copy instead.
        block.image = canvas.toOpaqueImage();
        block.oreLevels = levels;
        return block;
    }

    /** A block is stale once any seam in it has visibly changed level. */
    private boolean isStale(TileMap map, Block block, int blockX, int blockY) {
        if (block.oreLevels == null) {
            return false;
        }
        int originX = blockX * BLOCK_TILES;
        int originY = blockY * BLOCK_TILES;
        for (int dy = 0; dy < BLOCK_TILES; dy++) {
            for (int dx = 0; dx < BLOCK_TILES; dx++) {
                byte baked = block.oreLevels[dy * BLOCK_TILES + dx];
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
