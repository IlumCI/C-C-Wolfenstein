package com.ccwolf.game;

import com.ccwolf.core.map.Terrain;
import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.TerrainSprites;
import com.ccwolf.gfx.Brush;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The contact sheet for the Germania ground vocabulary — the instrument the tiles are judged
 * on, at bake resolution, all variants side by side. The assertions keep the recipes honest
 * (drawn, opaque, and distinct from each other); the saved sheet is where the taste happens.
 */
public class GermaniaTerrainSheetTest {

    static {
        Frame.useAwtBackend();
    }

    private static final Terrain[] NEW_GROUND = {
        Terrain.SUPERCRETE, Terrain.MARBLE, Terrain.PAVEMENT, Terrain.HIGHWAY,
    };

    @Test
    public void theNewGroundsDrawAndDiffer() throws IOException {
        int tile = TerrainSprites.TILE;
        Frame frame = new Frame(tile * TerrainSprites.VARIANTS, tile * NEW_GROUND.length);
        Brush brush = new Brush();

        long[] signatures = new long[NEW_GROUND.length];
        for (int t = 0; t < NEW_GROUND.length; t++) {
            for (int v = 0; v < TerrainSprites.VARIANTS; v++) {
                PixelCanvas canvas = TerrainSprites.render(NEW_GROUND[t], v);
                int opaque = 0;
                long sum = 0;
                for (int p : canvas.pixels()) {
                    if ((p >>> 24) != 0) {
                        opaque++;
                        sum += p & 0xFFFFFF;
                    }
                }
                assertTrue(NEW_GROUND[t] + " variant " + v + " must be fully painted",
                        opaque == tile * tile);
                if (v == 0) {
                    signatures[t] = sum / opaque;
                }
                frame.surface().drawImage(canvas.toImage(), v * tile, t * tile,
                        (v + 1) * tile, (t + 1) * tile, brush);
            }
        }
        // Average colours must be tellable apart: four grounds that bake to the same grey
        // would be one ground with four names.
        for (int a = 0; a < signatures.length; a++) {
            for (int b = a + 1; b < signatures.length; b++) {
                long da = Math.abs((signatures[a] & 0xFF) - (signatures[b] & 0xFF));
                assertTrue(NEW_GROUND[a] + " and " + NEW_GROUND[b] + " bake too alike",
                        da > 8 || Math.abs(signatures[a] - signatures[b]) > 0x080808);
            }
        }
        frame.save("germania-terrain.png");
    }
}
