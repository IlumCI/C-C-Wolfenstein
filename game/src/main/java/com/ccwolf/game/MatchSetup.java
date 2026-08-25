package com.ccwolf.game;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.game.art.WolfPalette;
import com.ccwolf.game.render.Palette;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.TextAlign;

/**
 * The screen before the match: pick a side, pick a way of fighting, and go.
 *
 * <p>Doctrines made this necessary. Before them a match needed nothing decided beyond which
 * frontend launched it, so both frontends hardcoded a faction and started cold — but a doctrine
 * is a choice with no sensible default, chosen once and never changed, and a choice like that
 * needs a place to be made. The screen also finally surfaces the two decisions that were always
 * there: which side, and how hard.
 *
 * <p>Platform-neutral on the same terms as {@link com.ccwolf.game.render.Hud}: it draws on a
 * {@code Surface}, takes taps as coordinates, and holds its own layout — so the desktop and the
 * app show the identical screen and there is exactly one of it to get right.
 *
 * <p>Picking a faction resets the doctrine to none rather than translating it, because doctrines
 * do not translate: each belongs to one side, and {@code GameWorld.addPlayer} would refuse a
 * mismatch anyway. The screen never offers what the world would refuse.
 */
public final class MatchSetup {

    /** One tappable row. */
    private static final class Option {
        float left;
        float top;
        float right;
        float bottom;

        boolean hit(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    private final Brush paint = new Brush();

    /** Which ground. Index into {@link #MAP_NAMES}. */
    private int mapIndex;

    private static final String[] MAP_NAMES = {MapCatalog.KREISAU_VALLEY,
        MapCatalog.ASCHEFELD, MapCatalog.SCHWARZBRUCK, MapCatalog.FRONTLINE,
        MapCatalog.GERMANIA};
    private static final String[] MAP_LABELS = {"Kreisau Valley  64x64", "Aschefeld  96x96",
        "Schwarzbruck  128x128", "Frontline  256x256", "Germania  256x256"};

    private Faction faction = Faction.RESISTANCE;
    /** Index into the faction's doctrines plus one: zero is "no doctrine". */
    private int doctrineIndex;
    private Difficulty difficulty = Difficulty.VETERAN;
    private boolean started;

    private float width;
    private float height;
    private float scale = 1f;

    /**
     * One cycling row rather than a grid: at five maps the grid pushed BEGIN off a
     * 720-pixel screen, and the roster will only grow. Tapping steps through the list.
     */
    private final Option mapRow = new Option();
    private final Option[] factions = {new Option(), new Option()};
    private final Option[] doctrines = {new Option(), new Option(), new Option(), new Option()};
    private final Option[] difficulties;
    private final Option start = new Option();

    public MatchSetup() {
        difficulties = new Option[Difficulty.values().length];
        for (int i = 0; i < difficulties.length; i++) {
            difficulties[i] = new Option();
        }
    }

    public void layout(float screenWidth, float screenHeight, float density) {
        this.width = screenWidth;
        this.height = screenHeight;
        this.scale = density;

        float panelWidth = Math.min(screenWidth - 40f * density, 420f * density);
        float left = (screenWidth - panelWidth) / 2f;
        float right = left + panelWidth;
        // Sized to fit a seven-hundred-pixel window at desktop density: the first layout used
        // taller rows and the Begin button landed below the bottom edge of the screen, where
        // the test that drives the screen like a finger could not press it - and neither could
        // a finger.
        float y = screenHeight * 0.10f;
        float rowHeight = 24f * density;
        float gap = 6f * density;

        float half = (panelWidth - gap) / 2f;
        mapRow.left = left;
        mapRow.right = right;
        mapRow.top = y;
        mapRow.bottom = y + rowHeight;
        y += rowHeight + 18f * density;

        for (int i = 0; i < 2; i++) {
            factions[i].left = left + i * (half + gap);
            factions[i].right = factions[i].left + half;
            factions[i].top = y;
            factions[i].bottom = y + rowHeight;
        }
        y += rowHeight + 18f * density;

        for (int i = 0; i < doctrines.length; i++) {
            doctrines[i].left = left;
            doctrines[i].right = right;
            doctrines[i].top = y;
            doctrines[i].bottom = y + rowHeight;
            y += rowHeight + gap;
        }
        y += 10f * density;

        float third = (panelWidth - 2 * gap) / difficulties.length;
        for (int i = 0; i < difficulties.length; i++) {
            difficulties[i].left = left + i * (third + gap);
            difficulties[i].right = difficulties[i].left + third;
            difficulties[i].top = y;
            difficulties[i].bottom = y + rowHeight;
        }
        y += rowHeight + 16f * density;

        start.left = left;
        start.right = right;
        start.top = y;
        start.bottom = y + rowHeight * 1.3f;
    }

    /** Handles a tap. Returns true once the player has hit Start. */
    public boolean tap(float x, float y) {
        if (mapRow.hit(x, y)) {
            mapIndex = (mapIndex + 1) % MAP_NAMES.length;
            return false;
        }
        for (int i = 0; i < 2; i++) {
            if (factions[i].hit(x, y)) {
                Faction picked = i == 0 ? Faction.RESISTANCE : Faction.REGIME;
                if (picked != faction) {
                    faction = picked;
                    doctrineIndex = 0;
                }
                return false;
            }
        }
        Doctrine[] mine = doctrinesFor(faction);
        for (int i = 0; i <= mine.length; i++) {
            if (doctrines[i].hit(x, y)) {
                doctrineIndex = i;
                return false;
            }
        }
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            if (difficulties[i].hit(x, y)) {
                difficulty = levels[i];
                return false;
            }
        }
        if (start.hit(x, y)) {
            started = true;
        }
        return started;
    }

    public boolean isStarted() {
        return started;
    }

    public String mapName() {
        return MAP_NAMES[mapIndex];
    }

    public Faction faction() {
        return faction;
    }

    public Doctrine doctrine() {
        return doctrineIndex == 0 ? null : doctrinesFor(faction)[doctrineIndex - 1];
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    private static Doctrine[] doctrinesFor(Faction f) {
        Doctrine[] all = Doctrine.values();
        int count = 0;
        for (Doctrine d : all) {
            if (d.availableTo(f)) {
                count++;
            }
        }
        Doctrine[] out = new Doctrine[count];
        int index = 0;
        for (Doctrine d : all) {
            if (d.availableTo(f)) {
                out[index++] = d;
            }
        }
        return out;
    }

    public void draw(Surface surface) {
        paint.setColor(0xFF0B0C0A);
        surface.fillRect(0, 0, width, height, paint);

        paint.setColor(Palette.HUD_TEXT);
        paint.setTextSize(20f * scale);
        paint.setBold(true);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText("SKIRMISH", width / 2f, height * 0.09f, paint);
        paint.setBold(false);

        label(surface, "GROUND", mapRow.top);
        drawOption(surface, mapRow, "‹  " + MAP_LABELS[mapIndex] + "  ›", true,
                Palette.HUD_TEXT_DIM);

        label(surface, "SIDE", factions[0].top);
        drawOption(surface, factions[0], "Kreisau Circle", faction == Faction.RESISTANCE,
                Palette.RESISTANCE);
        drawOption(surface, factions[1], "The Regime", faction == Faction.REGIME,
                Palette.REGIME);

        label(surface, "DOCTRINE", doctrines[0].top);
        Doctrine[] mine = doctrinesFor(faction);
        drawOption(surface, doctrines[0], "None - fight as before", doctrineIndex == 0,
                Palette.HUD_TEXT_DIM);
        for (int i = 0; i < mine.length; i++) {
            drawDoctrine(surface, doctrines[i + 1], mine[i], doctrineIndex == i + 1);
        }

        label(surface, "ENEMY", difficulties[0].top);
        Difficulty[] levels = Difficulty.values();
        for (int i = 0; i < levels.length; i++) {
            drawOption(surface, difficulties[i], levels[i].name(), difficulty == levels[i],
                    Palette.HUD_TEXT_DIM);
        }

        paint.setColor(WolfPalette.shade(WolfPalette.BLOOD, 2));
        surface.fillRect(start.left, start.top, start.right, start.bottom, paint);
        paint.setColor(WolfPalette.shade(WolfPalette.BLOOD, 0));
        paint.setStrokeWidth(2f * scale);
        surface.strokeRect(start.left, start.top, start.right, start.bottom, paint);
        paint.setColor(Palette.HUD_TEXT);
        paint.setTextSize(15f * scale);
        paint.setBold(true);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText("BEGIN THE MATCH", (start.left + start.right) / 2f,
                start.top + (start.bottom - start.top) * 0.62f, paint);
        paint.setBold(false);
    }

    private void label(Surface surface, String text, float rowTop) {
        paint.setColor(Palette.HUD_TEXT_DIM);
        paint.setTextSize(10f * scale);
        paint.setAlign(TextAlign.LEFT);
        surface.drawText(text, factions[0].left, rowTop - 6f * scale, paint);
    }

    private void drawOption(Surface surface, Option box, String text, boolean selected,
                            int accent) {
        fillOption(surface, box, selected);
        paint.setColor(selected ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM);
        paint.setTextSize(12f * scale);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText(text, (box.left + box.right) / 2f,
                box.top + (box.bottom - box.top) * 0.62f, paint);
        if (selected) {
            paint.setColor(accent);
            surface.fillRect(box.left, box.top, box.left + 3f * scale, box.bottom, paint);
        }
    }

    /** A doctrine row: the name on the left, its one-line description on the right. */
    private void drawDoctrine(Surface surface, Option box, Doctrine doctrine, boolean selected) {
        fillOption(surface, box, selected);
        paint.setColor(selected ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM);
        paint.setTextSize(12f * scale);
        paint.setAlign(TextAlign.LEFT);
        surface.drawText(doctrine.displayName(), box.left + 10f * scale,
                box.top + (box.bottom - box.top) * 0.62f, paint);
        paint.setColor(Palette.HUD_TEXT_DIM);
        paint.setTextSize(9f * scale);
        paint.setAlign(TextAlign.RIGHT);
        surface.drawText(doctrine.description(), box.right - 10f * scale,
                box.top + (box.bottom - box.top) * 0.62f, paint);
        if (selected) {
            paint.setColor(faction == Faction.RESISTANCE ? Palette.RESISTANCE : Palette.REGIME);
            surface.fillRect(box.left, box.top, box.left + 3f * scale, box.bottom, paint);
        }
    }

    private void fillOption(Surface surface, Option box, boolean selected) {
        paint.setColor(selected ? Palette.HUD_PANEL : Palette.HUD_BG);
        surface.fillRect(box.left, box.top, box.right, box.bottom, paint);
        paint.setStrokeWidth(1f * scale);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, selected ? 0 : 3));
        surface.strokeRect(box.left, box.top, box.right, box.bottom, paint);
    }
}
