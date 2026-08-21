package com.ccwolf.game;

import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.TerrainSprites;
import com.ccwolf.core.map.Terrain;
import java.io.IOException;
import org.junit.Test;

/**
 * A look at dug ground, at the four depths and on each surface it can be cut into.
 *
 * <p>Written to be looked at rather than asserted on. The thing being checked is that the four
 * steps read as four steps from across a table, and that a line of dug tiles joins up into a
 * trench instead of a row of separate holes.
 */
public class TrenchArtTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int TILE = TerrainSprites.TILE;
    private static final int ZOOM = 3;

    @Test
    public void trenchesAtEveryDepthAndOnEveryGround() throws IOException {
        Terrain[] grounds = {Terrain.GRASS, Terrain.ROAD, Terrain.RUBBLE};
        int cols = TerrainSprites.TRENCH_LEVELS + 1;
        int rows = grounds.length;

        PixelCanvas sheet = new PixelCanvas(cols * TILE, rows * TILE);
        for (int row = 0; row < rows; row++) {
            for (int level = 0; level <= TerrainSprites.TRENCH_LEVELS; level++) {
                PixelCanvas tile = TerrainSprites.render(grounds[row], level);
                TerrainSprites.entrench(tile, level, level);
                sheet.blit(tile, level * TILE, row * TILE);
            }
        }
        save(zoom(sheet), "trench-depths.png");
    }

    @Test
    public void aDugLineJoinsUp() throws IOException {
        int wide = 6;
        int tall = 3;
        PixelCanvas sheet = new PixelCanvas(wide * TILE, tall * TILE);
        for (int y = 0; y < tall; y++) {
            for (int x = 0; x < wide; x++) {
                PixelCanvas tile = TerrainSprites.render(Terrain.GRASS, (x * 7 + y * 3) % 4);
                // A line across the middle row, with the ends still being dug.
                int level = y == 1 ? (x == 0 || x == wide - 1 ? 2 : 4) : 0;
                TerrainSprites.entrench(tile, level, (x * 7 + y * 3) % 4);
                sheet.blit(tile, x * TILE, y * TILE);
            }
        }
        save(zoom(sheet), "trench-line.png");
    }

    private void save(PixelCanvas canvas, String name) throws IOException {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                canvas.width(), canvas.height(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, canvas.width(), canvas.height(), canvas.pixels(), 0, canvas.width());
        Frame.write(out, name);
    }

    /** Nearest-neighbour magnification, so the pixels can be seen. */
    private PixelCanvas zoom(PixelCanvas source) {
        PixelCanvas out = new PixelCanvas(source.width() * ZOOM, source.height() * ZOOM);
        for (int y = 0; y < out.height(); y++) {
            for (int x = 0; x < out.width(); x++) {
                out.set(x, y, source.get(x / ZOOM, y / ZOOM));
            }
        }
        return out;
    }
}
