package com.ccwolf.game.art;

import com.ccwolf.gfx.Gfx;
import com.ccwolf.gfx.Image;

/**
 * The monuments of Germania, each one sprite the size of a district.
 *
 * <p>The first cut of the capital drew its monuments as repeated marble floor tiles, and the
 * review said what a floor always says: nothing. A building the plan gave three hundred and
 * twenty metres of dome deserves to be drawn as a building — so these are authored top-down at
 * thirty-two pixels a tile, anchored on the map by {@code GermaniaDecor}, and drawn over their
 * terrain footprints. The terrain stays the truth the simulation walks against; these are what
 * that truth looks like from a gunship.
 *
 * <p>House rules apply at any size: lit from the north-west, one saturated colour (BLOOD) per
 * subject, the copper roofs gone to {@link WolfPalette#PATINA} because nothing here has been
 * polished since it was raised, and no insignia — the architecture is the statement.
 */
public final class MonumentSprites {

    /** Pixels per map tile. Monuments are seen mostly from altitude; 32 is plenty. */
    public static final int PPT = 32;

    public static final int HALL_TILES = 32;
    public static final int ARCH_TILES_W = 18;
    public static final int ARCH_TILES_H = 10;
    public static final int STATION_TILES_W = 35;
    public static final int STATION_TILES_H = 10;
    public static final int PALACE_TILES_W = 12;
    public static final int PALACE_TILES_H = 18;

    private static Image hall;
    private static Image arch;
    private static Image station;
    private static Image palaceWest;
    private static Image palaceEast;

    private MonumentSprites() {
    }

    public static synchronized Image hall() {
        if (hall == null) {
            hall = volkshalle().toImage();
        }
        return hall;
    }

    public static synchronized Image arch() {
        if (arch == null) {
            arch = triumphbogen().toImage();
        }
        return arch;
    }

    public static synchronized Image station() {
        if (station == null) {
            station = suedbahnhof().toImage();
        }
        return station;
    }

    public static synchronized Image palaceWest() {
        if (palaceWest == null) {
            palaceWest = palace(11).toImage();
        }
        return palaceWest;
    }

    public static synchronized Image palaceEast() {
        if (palaceEast == null) {
            palaceEast = palace(23).toImage();
        }
        return palaceEast;
    }

    // --- the Hall -------------------------------------------------------------------------

    /**
     * The Great Hall from directly above: a square marble podium, the drum, and the copper
     * dome that owns the skyline — here, the whole sprite. Shaded as a sphere the house way:
     * stacked discs walking toward the north-west light, ribs radiating to the crown, and the
     * oculus lantern at the top with the only red the building is allowed.
     */
    private static PixelCanvas volkshalle() {
        int size = HALL_TILES * PPT;
        PixelCanvas c = new PixelCanvas(size, size);
        int[] bone = WolfPalette.BONE;
        int[] patina = WolfPalette.PATINA;

        // Podium: the full footprint, cornice-edged, colonnade ticks all round.
        c.fill(WolfPalette.shade(bone, 2));
        edge(c, 0, 0, size, size, bone);
        for (int i = 24; i < size - 24; i += 32) {
            c.vLine(i, 6, 20, WolfPalette.shade(bone, 4));
            c.vLine(i, size - 21, size - 7, WolfPalette.shade(bone, 4));
            c.hLine(6, 20, i, WolfPalette.shade(bone, 4));
            c.hLine(size - 21, size - 7, i, WolfPalette.shade(bone, 4));
        }

        // Corner pavilions, each with its own small patina cap.
        int pav = 84;
        int[][] corners = {{10, 10}, {size - pav - 10, 10}, {10, size - pav - 10},
            {size - pav - 10, size - pav - 10}};
        for (int[] at : corners) {
            c.rect(at[0], at[1], pav, pav, WolfPalette.shade(bone, 1));
            c.hLine(at[0], at[0] + pav - 1, at[1] + pav - 1, WolfPalette.shade(bone, 4));
            c.vLine(at[0] + pav - 1, at[1], at[1] + pav - 1, WolfPalette.shade(bone, 4));
            c.ellipse(at[0] + pav / 2, at[1] + pav / 2, 26, 26, WolfPalette.shade(patina, 2));
            c.ellipse(at[0] + pav / 2 - 6, at[1] + pav / 2 - 6, 16, 16,
                    WolfPalette.shade(patina, 1));
        }

        // The attic block the drum stands on.
        int inset = 96;
        c.rect(inset, inset, size - 2 * inset, size - 2 * inset, WolfPalette.shade(bone, 1));
        c.hLine(inset, size - inset - 1, size - inset - 1, WolfPalette.shade(bone, 4));
        c.vLine(size - inset - 1, inset, size - inset - 1, WolfPalette.shade(bone, 4));

        // South portico, facing the plaza: steps, then the column roof with its shadow teeth.
        int porticoW = 448;
        int px0 = (size - porticoW) / 2;
        for (int s = 0; s < 5; s++) {
            c.rect(px0 - 20 + s * 4, size - 26 + s * 5, porticoW + 40 - s * 8, 5,
                    WolfPalette.shade(bone, 1 + (s & 1)));
        }
        c.rect(px0, size - 116, porticoW, 88, WolfPalette.shade(bone, 0));
        for (int i = 0; i < 14; i++) {
            c.rect(px0 + 10 + i * 32, size - 34, 8, 8, WolfPalette.shade(bone, 4));
        }
        // The two banners, the only red on the building.
        c.rect(px0 - 30, size - 110, 14, 84, WolfPalette.shade(WolfPalette.BLOOD, 2));
        c.rect(px0 + porticoW + 16, size - 110, 14, 84,
                WolfPalette.shade(WolfPalette.BLOOD, 2));

        // The drum cornice, then the dome as a lit sphere.
        int cx = size / 2;
        int cy = size / 2 - 32;
        c.ellipse(cx, cy, 412, 412, WolfPalette.shade(bone, 0));
        c.ellipse(cx, cy, 404, 404, WolfPalette.shade(bone, 3));
        c.ellipse(cx, cy, 396, 396, WolfPalette.shade(patina, 3));
        c.ellipse(cx - 26, cy - 26, 352, 352, WolfPalette.shade(patina, 2));
        c.ellipse(cx - 54, cy - 54, 292, 292, WolfPalette.shade(patina, 1));
        c.ellipse(cx - 84, cy - 84, 212, 212, WolfPalette.shade(patina, 0));

        // Ribs, radiating from the crown; drawn dark so the sphere reads as panelled copper.
        int crownX = cx - 60;
        int crownY = cy - 60;
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            int rx = cx + (int) Math.round(Math.cos(a) * 392);
            int ry = cy + (int) Math.round(Math.sin(a) * 392);
            c.line(crownX + (int) (Math.cos(a) * 40), crownY + (int) (Math.sin(a) * 40),
                    rx, ry, WolfPalette.shade(patina, 4));
        }
        // Streaks: the rain runs down the panels, so the panels say which way is down.
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * (i + 0.5) / 28;
            int r0 = 180 + (i * 37) % 120;
            c.line(cx + (int) (Math.cos(a) * r0), cy + (int) (Math.sin(a) * r0),
                    cx + (int) (Math.cos(a) * (r0 + 90)), cy + (int) (Math.sin(a) * (r0 + 90)),
                    WolfPalette.shade(patina, 3));
        }

        // The lantern: marble ring, dark oculus, four red pennants at the cardinal points.
        c.ellipse(crownX, crownY, 52, 52, WolfPalette.shade(bone, 1));
        c.ellipse(crownX + 6, crownY + 6, 44, 44, WolfPalette.shade(bone, 3));
        c.ellipse(crownX, crownY, 24, 24, WolfPalette.shade(WolfPalette.NIGHT, 2));
        c.rect(crownX - 4, crownY - 66, 8, 18, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.rect(crownX - 4, crownY + 48, 8, 18, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.rect(crownX - 66, crownY - 4, 18, 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.rect(crownX + 48, crownY - 4, 18, 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
        return c;
    }

    // --- the Arch -------------------------------------------------------------------------

    /**
     * The Arch from above is a roof you drive beneath: a coffered marble slab spanning the
     * avenue, its two vault mouths in deep shadow at the north and south faces. Drawn as the
     * overhead layer, so a convoy on the axis genuinely disappears under it.
     */
    private static PixelCanvas triumphbogen() {
        int w = ARCH_TILES_W * PPT;
        int h = ARCH_TILES_H * PPT;
        PixelCanvas c = new PixelCanvas(w, h);
        int[] bone = WolfPalette.BONE;

        c.fill(WolfPalette.shade(bone, 2));
        edge(c, 0, 0, w, h, bone);

        // The attic slab, coffered: sunken squares in ranks across the whole roof.
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 8; col++) {
                int x = 32 + col * 66;
                int y = 40 + row * 64;
                c.rect(x, y, 46, 44, WolfPalette.shade(bone, 3));
                c.rect(x + 5, y + 5, 36, 34, WolfPalette.shade(bone, 4));
                c.hLine(x, x + 45, y, WolfPalette.shade(bone, 1));
                c.vLine(x, y, y + 43, WolfPalette.shade(bone, 1));
            }
        }

        // The vault mouths: the passage's shadow eating the north and south faces.
        int mx0 = 5 * PPT;
        int mx1 = 13 * PPT;
        for (int d = 0; d < 22; d++) {
            int shade = d < 8 ? 4 : 3;
            c.hLine(mx0 + d / 2, mx1 - d / 2, d, WolfPalette.shade(WolfPalette.NIGHT, shade));
            c.hLine(mx0 + d / 2, mx1 - d / 2, h - 1 - d,
                    WolfPalette.shade(WolfPalette.NIGHT, shade));
        }

        // Pier caps at the four corners of each leg.
        for (int lx : new int[] {0, 13 * PPT}) {
            c.rect(lx + 8, 8, 5 * PPT - 16, 24, WolfPalette.shade(bone, 1));
            c.rect(lx + 8, h - 32, 5 * PPT - 16, 24, WolfPalette.shade(bone, 1));
        }
        return c;
    }

    // --- the Station ----------------------------------------------------------------------

    /** The South Station: a headhouse in marble and a glazed vault as long as a district. */
    private static PixelCanvas suedbahnhof() {
        int w = STATION_TILES_W * PPT;
        int h = STATION_TILES_H * PPT;
        PixelCanvas c = new PixelCanvas(w, h);
        int[] bone = WolfPalette.BONE;
        int[] steel = WolfPalette.STEEL;
        int[] night = WolfPalette.NIGHT;

        // Headhouse strip along the north face.
        c.rect(0, 0, w, 64, WolfPalette.shade(bone, 1));
        c.hLine(0, w - 1, 63, WolfPalette.shade(bone, 4));
        for (int i = 20; i < w - 20; i += 40) {
            c.vLine(i, 8, 52, WolfPalette.shade(bone, 4));
        }
        // One banner at the centre door.
        c.rect(w / 2 - 8, 8, 16, 48, WolfPalette.shade(WolfPalette.BLOOD, 2));

        // The great vault: steel ribs over smoke-dark glass. The ribs are drawn bright -
        // the first bake put dark steel on dark glass and the vault read as a tar slab.
        c.rect(0, 64, w, h - 128, WolfPalette.shade(night, 1));
        // The vault curves: brighter along the north third, darkest at the south springing.
        c.rect(0, 64, w, 26, WolfPalette.shade(steel, 1));
        c.rect(0, 90, w, 18, WolfPalette.shade(steel, 3));
        c.rect(0, h - 82, w, 18, WolfPalette.shade(night, 4));
        for (int x = 0; x < w; x += 18) {
            c.vLine(x, 64, h - 65, WolfPalette.shade(steel, 2));
            c.vLine(x + 1, 64, h - 65, WolfPalette.shade(steel, 4));
        }
        // Glazing bars across the glass, so the roof reads as panes rather than as water.
        for (int y = 118; y < h - 82; y += 22) {
            c.hLine(0, w - 1, y, WolfPalette.shade(steel, 4));
        }
        // Soot down the glass over the platforms.
        for (int i = 0; i < 30; i++) {
            int x = (i * 149) % w;
            c.vLine(x, 120 + (i * 53) % 60, 190 + (i * 31) % 60,
                    WolfPalette.shade(WolfPalette.SMOKE, 4));
        }

        // Platform canopies marching out of the south face, a train gap between each.
        for (int i = 0; i < 7; i++) {
            int x = 30 + i * 156;
            c.rect(x, h - 64, 96, 60, WolfPalette.shade(steel, 2));
            c.hLine(x, x + 95, h - 64, WolfPalette.shade(steel, 0));
            c.vLine(x + 95, h - 64, h - 5, WolfPalette.shade(night, 3));
        }
        return c;
    }

    // --- the palaces ----------------------------------------------------------------------

    /**
     * A government palace: a patina roof around a paved court, a portico on the plaza side.
     * Two are baked with different seeds so the Palace and the Chancellery are brothers, not
     * twins.
     */
    private static PixelCanvas palace(int seed) {
        int w = PALACE_TILES_W * PPT;
        int h = PALACE_TILES_H * PPT;
        PixelCanvas c = new PixelCanvas(w, h);
        int[] bone = WolfPalette.BONE;
        int[] patina = WolfPalette.PATINA;
        int[] concrete = WolfPalette.CONCRETE;

        // The roof ring: copper gone green, ridged along each wing.
        c.fill(WolfPalette.shade(patina, 2));
        edge(c, 0, 0, w, h, bone);
        int band = 72;
        for (int i = 12; i < w - 12; i += 14) {
            c.vLine(i, 6, band - 6, WolfPalette.shade(patina, (i / 14 + seed) % 2 == 0 ? 1 : 3));
            c.vLine(i, h - band + 6, h - 7, WolfPalette.shade(patina,
                    (i / 14 + seed) % 2 == 0 ? 3 : 1));
        }
        for (int i = 12; i < h - 12; i += 14) {
            c.hLine(6, band - 6, i, WolfPalette.shade(patina, (i / 14 + seed) % 2 == 0 ? 1 : 3));
            c.hLine(w - band + 6, w - 7, i, WolfPalette.shade(patina,
                    (i / 14 + seed) % 2 == 0 ? 3 : 1));
        }

        // The court: slabs, and the dry fountain nobody turned back on. The first bake
        // filled it two shades too dark and it read as a shaft, not a courtyard.
        c.rect(band, band, w - 2 * band, h - 2 * band, WolfPalette.shade(concrete, 1));
        for (int x = band; x < w - band; x += 24) {
            c.vLine(x, band, h - band - 1, WolfPalette.shade(concrete, 3));
        }
        for (int y = band; y < h - band; y += 24) {
            c.hLine(band, w - band - 1, y, WolfPalette.shade(concrete, 3));
        }
        // The roof's shadow falls into the court along its north and west inner walls.
        c.rect(band, band, w - 2 * band, 10, WolfPalette.shade(concrete, 4));
        c.rect(band, band, 10, h - 2 * band, WolfPalette.shade(concrete, 4));
        c.ellipse(w / 2, h / 2, 30, 30, WolfPalette.shade(bone, 2));
        c.ellipse(w / 2, h / 2, 22, 22, WolfPalette.shade(concrete, 4));
        c.ellipse(w / 2 - 4, h / 2 - 4, 8, 8, WolfPalette.shade(bone, 0));

        // Portico and banners on the court's south approach.
        c.rect(w / 2 - 60, h - 30, 120, 24, WolfPalette.shade(bone, 0));
        for (int i = 0; i < 5; i++) {
            c.rect(w / 2 - 50 + i * 24, h - 12, 6, 6, WolfPalette.shade(bone, 4));
        }
        c.rect(w / 2 - 80, h - 28, 10, 22, WolfPalette.shade(WolfPalette.BLOOD, 2));
        c.rect(w / 2 + 70, h - 28, 10, 22, WolfPalette.shade(WolfPalette.BLOOD, 2));
        return c;
    }

    /** Cornice edging: a light north-west lip and a dark south-east one, the house light. */
    private static void edge(PixelCanvas c, int x, int y, int w, int h, int[] ramp) {
        for (int d = 0; d < 4; d++) {
            c.hLine(x + d, x + w - 1 - d, y + d, WolfPalette.shade(ramp, d < 2 ? 0 : 1));
            c.vLine(x + d, y + d, y + h - 1 - d, WolfPalette.shade(ramp, d < 2 ? 0 : 1));
            c.hLine(x + d, x + w - 1 - d, y + h - 1 - d, WolfPalette.shade(ramp, 4));
            c.vLine(x + w - 1 - d, y + d, y + h - 1 - d, WolfPalette.shade(ramp, 4));
        }
    }
}
