package com.ccwolf.game.render;

import com.ccwolf.gfx.Colors;
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
    public static final int GRASS = Colors.rgb(64, 78, 48);
    public static final int GRASS_ALT = Colors.rgb(58, 71, 44);
    public static final int ROAD = Colors.rgb(104, 98, 82);
    public static final int RUBBLE = Colors.rgb(72, 68, 58);
    public static final int WATER = Colors.rgb(38, 56, 74);
    public static final int WALL = Colors.rgb(53, 55, 59);
    /** Germania's grounds: the monolith grey, the monument pale, the parade slab, the road. */
    public static final int SUPERCRETE = Colors.rgb(90, 92, 98);
    public static final int MARBLE = Colors.rgb(191, 181, 149);
    public static final int PAVEMENT = Colors.rgb(112, 114, 120);
    public static final int HIGHWAY = Colors.rgb(46, 46, 52);
    public static final int ORE = Colors.rgb(92, 190, 62);
    public static final int ORE_RICH = Colors.rgb(143, 232, 106);

    // --- factions -------------------------------------------------------------------------
    public static final int RESISTANCE = Colors.rgb(214, 178, 92);
    public static final int RESISTANCE_DARK = Colors.rgb(142, 112, 48);
    public static final int REGIME = Colors.rgb(178, 62, 56);
    public static final int REGIME_DARK = Colors.rgb(112, 36, 32);

    // --- interface ------------------------------------------------------------------------
    public static final int HUD_BG = Colors.rgb(28, 30, 26);
    public static final int HUD_PANEL = Colors.rgb(44, 47, 40);
    public static final int HUD_PANEL_LIT = Colors.rgb(66, 70, 58);
    public static final int HUD_BORDER = Colors.rgb(86, 90, 76);
    public static final int HUD_TEXT = Colors.rgb(226, 222, 206);
    public static final int HUD_TEXT_DIM = Colors.rgb(150, 148, 132);
    public static final int GOLD = Colors.rgb(220, 184, 96);
    public static final int POWER_OK = Colors.rgb(120, 190, 110);
    public static final int POWER_LOW = Colors.rgb(206, 122, 60);
    public static final int SELECTION = Colors.rgb(150, 230, 130);
    public static final int HEALTH_GOOD = Colors.rgb(110, 200, 100);
    public static final int HEALTH_FAIR = Colors.rgb(220, 190, 80);
    public static final int HEALTH_POOR = Colors.rgb(206, 78, 66);
    public static final int TRACER = Colors.rgb(255, 232, 150);
    public static final int EXPLOSION = Colors.rgb(255, 176, 72);
    public static final int PLACE_OK = Colors.argb(110, 120, 230, 120);
    public static final int PLACE_BAD = Colors.argb(110, 230, 90, 80);

    /** Sabotage arcs: a cold electric white-blue with a hotter core. */
    public static final int ARC = Colors.rgb(150, 214, 255);
    public static final int ARC_CORE = Colors.rgb(238, 250, 255);

    /** Unexplored fog: solid. Explored-but-unseen: a heavy veil over remembered terrain. */
    public static final int FOG_UNEXPLORED = Colors.rgb(14, 16, 13);
    public static final int FOG_EXPLORED = Colors.argb(140, 12, 13, 11);

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
            case SUPERCRETE:
                return SUPERCRETE;
            case MARBLE:
                return MARBLE;
            case PAVEMENT:
                return PAVEMENT;
            case HIGHWAY:
                return HIGHWAY;
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
