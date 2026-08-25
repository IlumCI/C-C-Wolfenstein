package com.ccwolf.game;

import com.ccwolf.game.art.PixelCanvas;
import com.ccwolf.game.art.UnitSprites;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * The heavy roster, one cell per facing with air between them.
 *
 * <p>The main contact sheet packs cells edge to edge, which is fine for a rifleman and useless
 * for anything that fills its canvas: eight Ubersoldat frames touching read as one long armour
 * band, and nothing can be judged about a sprite whose neighbours are welded to it.
 */
public class HeavySheetTest {

    static {
        Frame.useAwtBackend();
    }

    @Test
    public void heaviesWithAirBetweenThem() throws IOException {
        UnitType[] types = {UnitType.UBERSOLDAT, UnitType.AUSMERZER, UnitType.GASWERFER,
                UnitType.PANZERHUND, UnitType.STURMPANZER, UnitType.CAPTURED_PANZER,
                UnitType.GYROCOPTER, UnitType.LUFTPANZER};
        Faction[] factions = {Faction.REGIME, Faction.REGIME, Faction.REGIME, Faction.REGIME,
                Faction.REGIME, Faction.RESISTANCE, Faction.RESISTANCE, Faction.REGIME};
        int cell = 160;
        int pad = 12;
        PixelCanvas sheet = new PixelCanvas(pad + 8 * (cell + pad),
                pad + types.length * (cell + pad));
        sheet.fill(0xFF3A3B2E);
        for (int t = 0; t < types.length; t++) {
            for (int facing = 0; facing < 8; facing++) {
                PixelCanvas sprite = UnitSprites.render(types[t], factions[t], facing, 0);
                // blit copies logical pixels, so centre with the logical width.
                int x = pad + facing * (cell + pad) + (cell - sprite.width()) / 2;
                int y = pad + t * (cell + pad) + (cell - sprite.height()) / 2;
                sheet.blit(sprite, x, y);
            }
        }
        save(sheet, "sheet-heavies.png");
        assertTrue(true);
    }

    private void save(PixelCanvas canvas, String name) throws IOException {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                canvas.pixelWidth(), canvas.pixelHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, canvas.pixelWidth(), canvas.pixelHeight(), canvas.pixels(), 0,
                canvas.pixelWidth());
        Frame.write(out, name);
    }
}
