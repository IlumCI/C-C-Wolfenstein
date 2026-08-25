package com.ccwolf.game;

import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.game.render.GermaniaDecor;
import com.ccwolf.game.render.Palette;
import com.ccwolf.gfx.Brush;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The capital, whole: every tile of the hand-drawn plan at five pixels each, with the
 * monument sprites standing on their footprints — the drawing as its author sees it. And the
 * monuments alone on a sheet, at working scale, for the review the floor-tile version of this
 * city failed.
 */
public class GermaniaPosterTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int PX = 5;

    @Test
    public void theWholeCapitalOnOnePoster() throws IOException {
        TileMap map = MapCatalog.load(MapCatalog.GERMANIA);
        Frame frame = new Frame(map.width() * PX, map.height() * PX);
        Brush brush = new Brush();

        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                brush.setColor(Palette.terrain(map.terrain(x, y), x, y, map.ore(x, y)));
                frame.surface().fillRect(x * PX, y * PX, (x + 1) * PX, (y + 1) * PX, brush);
            }
        }
        brush.setColor(0xFFFFFFFF);
        for (boolean overhead : new boolean[] {false, true}) {
            for (GermaniaDecor.Placed p : GermaniaDecor.placed(overhead)) {
                int[] at = GermaniaDecor.frameOf(p);
                frame.surface().drawImage(GermaniaDecor.imageOf(p), at[0] * PX, at[1] * PX,
                        (at[0] + at[2]) * PX, (at[1] + at[3]) * PX, brush);
            }
        }
        frame.save("germania-poster.png");
    }

    @Test
    public void theMonumentsOnOneSheet() throws IOException {
        Frame frame = new Frame(1200, 1000);
        Brush brush = new Brush();
        brush.setColor(0xFF15161A);
        frame.surface().fillRect(0, 0, 1200, 1000, brush);
        brush.setColor(0xFFFFFFFF);

        // Hall at half scale, the rest at half beside and below it.
        com.ccwolf.gfx.Image hall = com.ccwolf.game.art.MonumentSprites.hall();
        com.ccwolf.gfx.Image arch = com.ccwolf.game.art.MonumentSprites.arch();
        com.ccwolf.gfx.Image station = com.ccwolf.game.art.MonumentSprites.station();
        com.ccwolf.gfx.Image west = com.ccwolf.game.art.MonumentSprites.palaceWest();
        com.ccwolf.gfx.Image east = com.ccwolf.game.art.MonumentSprites.palaceEast();

        frame.surface().drawImage(hall, 20, 20, 20 + 512, 20 + 512, brush);
        frame.surface().drawImage(west, 570, 20, 570 + 192, 20 + 288, brush);
        frame.surface().drawImage(east, 790, 20, 790 + 192, 20 + 288, brush);
        frame.surface().drawImage(arch, 570, 350, 570 + 288, 350 + 160, brush);
        frame.surface().drawImage(station, 20, 580, 20 + 560, 580 + 160, brush);

        assertTrue(hall.width() == 1024 && hall.height() == 1024);
        frame.save("germania-monuments.png");
    }
}
