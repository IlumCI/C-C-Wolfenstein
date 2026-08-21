package com.ccwolf.android.render;

import android.graphics.Color;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.map.Terrain;

/**
 * Every colour the game draws with, in one place.
 *
 * <p>The look is deliberately muddy and desaturated — occupied-Europe greens and greys — with
 * the two factions separated by warm sand (Resistance) against cold steel-red (Regime), so
 * ownership reads at a glance even at the smallest zoom level.
 */
public final class Palette {

    // --- terrain --------------------------------------------------------------------------
    public static final int GRASS = Color.rgb(64, 78, 48);
    public static final int GRASS_ALT = Color.rgb(58, 71, 44);
    public static final int ROAD = Color.rgb(104, 98, 82);
    public static final int RUBBLE = Color.rgb(72, 68, 58);
    public static final int WATER = Color.rgb(38, 56, 74);
    public static final int WALL = Color.rgb(53, 55, 59);
    public static final int ORE = Color.rgb(92, 190, 62);
    public static final int ORE_RICH = Color.rgb(143, 232, 106);

    // --- factions -------------------------------------------------------------------------
    public static final int RESISTANCE = Color.rgb(214, 178, 92);
    public static final int RESISTANCE_DARK = Color.rgb(142, 112, 48);
    public static final int REGIME = Color.rgb(178, 62, 56);
    public static final int REGIME_DARK = Color.rgb(112, 36, 32);

    // --- interface ------------------------------------------------------------------------
    public static final int HUD_BG = Color.rgb(28, 30, 26);
    public static final int HUD_PANEL = Color.rgb(44, 47, 40);
    public static final int HUD_PANEL_LIT = Color.rgb(66, 70, 58);
    public static final int HUD_BORDER = Color.rgb(86, 90, 76);
    public static final int HUD_TEXT = Color.rgb(226, 222, 206);
    public static final int HUD_TEXT_DIM = Color.rgb(150, 148, 132);
    public static final int GOLD = Color.rgb(220, 184, 96);
    public static final int POWER_OK = Color.rgb(120, 190, 110);
    public static final int POWER_LOW = Color.rgb(206, 122, 60);
    public static final int SELECTION = Color.rgb(150, 230, 130);
    public static final int HEALTH_GOOD = Color.rgb(110, 200, 100);
    public static final int HEALTH_FAIR = Color.rgb(220, 190, 80);
    public static final int HEALTH_POOR = Color.rgb(206, 78, 66);
    public static final int TRACER = Color.rgb(255, 232, 150);
    public static final int EXPLOSION = Color.rgb(255, 176, 72);
    public static final int PLACE_OK = Color.argb(110, 120, 230, 120);
    public static final int PLACE_BAD = Color.argb(110, 230, 90, 80);

    /** Unexplored fog: solid. Explored-but-unseen: a heavy veil over remembered terrain. */
    public static final int FOG_UNEXPLORED = Color.rgb(14, 16, 13);
    public static final int FOG_EXPLORED = Color.argb(140, 12, 13, 11);

    private Palette() {
    }

    public static int terrain(Terrain t, int x, int y, int ore) {
        switch (t) {
            case ROAD:
                return ROAD;
            case RUBBLE:
                return RUBBLE;
            case WATER:
                return WATER;
            case WALL:
                return WALL;
            case ORE:
                return ore > 350 ? ORE_RICH : (ore > 0 ? ORE : GRASS);
            case GRASS:
            default:
                // Two shades in a checker keeps a big field of grass from looking like a bug.
                return ((x + y) & 1) == 0 ? GRASS : GRASS_ALT;
        }
    }

    public static int faction(Faction f) {
        return f == Faction.REGIME ? REGIME : RESISTANCE;
    }

    public static int factionDark(Faction f) {
        return f == Faction.REGIME ? REGIME_DARK : RESISTANCE_DARK;
    }

    public static int health(float fraction) {
        if (fraction > 0.6f) {
            return HEALTH_GOOD;
        }
        return fraction > 0.3f ? HEALTH_FAIR : HEALTH_POOR;
    }
}
