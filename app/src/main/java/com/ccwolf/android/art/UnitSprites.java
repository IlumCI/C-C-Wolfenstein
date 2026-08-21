package com.ccwolf.android.art;

import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;

/**
 * Pixel-art recipes for every unit, baked once at startup.
 *
 * <p>Authored at 32 pixels to the tile, with vehicles in a 48-pixel box. That is deliberately
 * generous: at the previous 24 pixels there was no room for a Panzerhund to have haunches or a
 * jeep to have seats, and everything came out as a silhouette with a colour on it.
 *
 * <p>Two approaches, chosen per unit:
 *
 * <ul>
 *   <li><b>Infantry</b> are redrawn for each of the eight facings. A soldier seen from
 *       above-behind keeps his helmet on top whichever way he is looking; rotating him bodily
 *       would lay him on his side.</li>
 *   <li><b>Vehicles</b> are drawn once facing east and rotated, because a hull genuinely does
 *       rotate. The rotation is supersampled so the diagonals hold together.</li>
 * </ul>
 */
public final class UnitSprites {

    /** Source pixels per tile. Everything is authored at this scale. */
    public static final int TILE = 32;

    public static final int INFANTRY_SIZE = 32;
    public static final int VEHICLE_SIZE = 48;

    public static final int FACINGS = 8;
    public static final int WALK_FRAMES = 2;

    private static final int OUTLINE = 0xFF0E0F0B;

    private UnitSprites() {
    }

    /**
     * Bakes one unit sprite.
     *
     * @param facing 0 = east, then clockwise in eighths
     * @param frame walk or tread frame; cargo level for the harvester
     */
    public static PixelCanvas render(UnitType type, Faction faction, int facing, int frame) {
        switch (type) {
            case SCOUT_JEEP:
                return vehicle(jeep(faction, frame), facing, 15, 5);
            case CAPTURED_PANZER:
                return vehicle(panzer(faction, frame), facing, 18, 6);
            case PANZERHUND:
                return vehicle(hound(frame), facing, 15, 5);
            case HARVESTER:
                return vehicle(harvester(faction, frame), facing, 18, 6);
            case UBERSOLDAT:
                return ubersoldat(facing, frame);
            case ROCKETEER:
                return infantry(faction, facing, frame, Kit.ROCKET);
            case SOLDAT:
                return infantry(faction, facing, frame, Kit.SMG);
            case PARTISAN:
            default:
                return infantry(faction, facing, frame, Kit.RIFLE);
        }
    }

    /** What an infantryman is carrying, which is most of how you tell them apart. */
    private enum Kit { RIFLE, SMG, ROCKET }

    /**
     * Turns a hull drawn facing east into a finished sprite for one facing.
     *
     * <p>Order matters: rotate the bare hull, then outline it, then lay it over a shadow.
     * Outlining first tears the outline apart; rotating the shadow sends the sun spinning.
     */
    private static PixelCanvas vehicle(PixelCanvas east, int facing, int shadowRx, int shadowRy) {
        PixelCanvas hull = facing == 0 ? east
                : east.rotatedSmooth((float) (facing * Math.PI / 4.0), 3);
        hull.outline(OUTLINE);

        PixelCanvas out = new PixelCanvas(east.width(), east.height());
        out.groundShadow(east.width() / 2, east.height() / 2 + 7, shadowRx, shadowRy);
        out.blit(hull, 0, 0);
        return out;
    }

    // --- infantry -------------------------------------------------------------------------

    /**
     * A foot soldier at 32 pixels: boots, legs, coat, webbing, shoulders, head and weapon,
     * each drawn as its own small shape so the figure has parts rather than being one blob.
     */
    private static PixelCanvas infantry(Faction faction, int facing, int frame, Kit kit) {
        PixelCanvas c = new PixelCanvas(INFANTRY_SIZE, INFANTRY_SIZE);
        boolean regime = faction == Faction.REGIME;

        int[] coat = regime ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] webbing = regime ? WolfPalette.NIGHT : WolfPalette.LEATHER;
        int[] trousers = regime ? WolfPalette.NIGHT : WolfPalette.LEATHER;

        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        boolean toViewer = dy > 0.35f;
        boolean away = dy < -0.35f;
        int step = frame == 1 ? 1 : 0;

        c.groundShadow(16, 28, 7, 3);

        // --- legs and boots: the back leg is drawn first and a shade darker -----------------
        int bootLight = WolfPalette.shade(WolfPalette.LEATHER, regime ? 3 : 2);
        int bootDark = WolfPalette.shade(WolfPalette.LEATHER, 4);

        c.panel(12, 20 - step, 4, 5, trousers, 2);
        c.rect(12, 24 - step, 4, 3, bootDark);
        c.hLine(12, 15, 24 - step, bootLight);

        c.panel(17, 20 + step, 4, 5, trousers, 1);
        c.rect(17, 24 + step, 4, 3, bootDark);
        c.hLine(17, 20, 24 + step, bootLight);

        // --- greatcoat ---------------------------------------------------------------------
        int top = 10;
        c.panel(11, top, 11, 12, coat, 1);
        // Coat skirt flares below the belt.
        c.rect(10, top + 8, 13, 4, WolfPalette.shade(coat, 2));
        c.hLine(10, 22, top + 8, WolfPalette.shade(coat, 0));
        c.hLine(10, 22, top + 11, WolfPalette.shade(coat, 4));
        // Centre seam and a couple of buttons.
        c.vLine(16, top + 1, top + 10, WolfPalette.shade(coat, 3));
        c.px(16, top + 3, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.px(16, top + 6, WolfPalette.shade(WolfPalette.BRASS, 2));

        // --- shoulders ---------------------------------------------------------------------
        c.panel(9, top - 1, 15, 4, coat, 1);
        c.hLine(9, 23, top - 1, WolfPalette.shade(coat, 0));

        // --- webbing, belt and pouches -----------------------------------------------------
        c.hLine(11, 21, top + 8, WolfPalette.shade(webbing, 3));
        c.hLine(11, 21, top + 9, WolfPalette.shade(webbing, 4));
        c.rect(11, top + 8, 3, 3, WolfPalette.shade(webbing, 2));
        c.rect(19, top + 8, 3, 3, WolfPalette.shade(webbing, 2));
        c.px(16, top + 8, WolfPalette.shade(WolfPalette.BRASS, 1));

        if (away) {
            // Seen from behind: pack, rolled blanket, entrenching tool.
            c.panel(12, top + 1, 9, 8, webbing, 2);
            c.hLine(12, 20, top + 4, WolfPalette.shade(webbing, 4));
            c.rect(11, top, 11, 2, WolfPalette.shade(webbing, 1));
            c.rect(20, top + 5, 2, 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        }

        if (regime) {
            regimeKit(c, top, coat);
        } else {
            resistanceKit(c, top, toViewer);
        }

        drawHead(c, faction, top, toViewer, away);
        drawWeapon(c, kit, dx, dy, top, regime);

        c.outline(OUTLINE);
        return c;
    }

    /** Black plate, a red armband and a stencilled number: the Regime's whole visual identity. */
    private static void regimeKit(PixelCanvas c, int top, int[] coat) {
        // Chest plate.
        c.panel(12, top + 1, 9, 6, coat, 0);
        c.rivets(13, top + 2, 8, 5, 4, WolfPalette.shade(WolfPalette.STEEL, 1),
                WolfPalette.shade(coat, 4));

        // Shoulder plates, one of them stencilled.
        c.panel(9, top - 1, 5, 4, coat, 0);
        c.panel(19, top - 1, 5, 4, coat, 0);
        c.px(20, top, WolfPalette.shade(WolfPalette.BONE, 1));
        c.px(21, top, WolfPalette.shade(WolfPalette.BONE, 1));
        c.px(21, top + 1, WolfPalette.shade(WolfPalette.BONE, 2));

        // The armband. One saturated colour on an otherwise black figure.
        c.rect(9, top + 3, 3, 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(9, 11, top + 3, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.hLine(9, 11, top + 6, WolfPalette.shade(WolfPalette.BLOOD, 3));
    }

    /** Scavenged kit: a bandolier, a satchel, a scarf, and a red rag on the arm. */
    private static void resistanceKit(PixelCanvas c, int top, boolean toViewer) {
        // Bandolier across the chest, with rounds in it.
        for (int i = 0; i < 6; i++) {
            int bx = 11 + i;
            int by = top + 1 + i;
            c.px(bx, by, WolfPalette.shade(WolfPalette.LEATHER, 1));
            c.px(bx + 1, by, WolfPalette.shade(WolfPalette.LEATHER, 3));
            if ((i & 1) == 0) {
                c.px(bx + 1, by + 1, WolfPalette.shade(WolfPalette.BRASS, 1));
            }
        }
        // Satchel on the hip.
        c.panel(20, top + 7, 5, 5, WolfPalette.LEATHER, 2);
        c.hLine(20, 24, top + 9, WolfPalette.shade(WolfPalette.LEATHER, 4));

        // Red rag tied round the upper arm.
        c.rect(9, top + 3, 3, 3, WolfPalette.shade(WolfPalette.BLOOD, 2));
        c.px(9, top + 3, WolfPalette.shade(WolfPalette.BLOOD, 1));

        if (toViewer) {
            // Scarf pulled up over the chin.
            c.rect(13, top - 2, 7, 3, WolfPalette.shade(WolfPalette.BONE, 3));
            c.hLine(13, 19, top - 2, WolfPalette.shade(WolfPalette.BONE, 2));
        }
    }

    /**
     * Heads. The Regime wears a lacquered helmet over a gas mask and never shows skin; the
     * Resistance wears a flat cap, and you can see their face.
     */
    private static void drawHead(PixelCanvas c, Faction faction, int top, boolean toViewer,
                                 boolean away) {
        boolean regime = faction == Faction.REGIME;
        int headY = top - 8;

        // Neck.
        c.rect(15, headY + 6, 3, 3, WolfPalette.shade(
                regime ? WolfPalette.NIGHT : WolfPalette.FLESH, 3));

        if (regime) {
            int[] lacquer = WolfPalette.NIGHT;
            // Skull under the helmet.
            c.ellipse(16, headY + 4, 5, 5, WolfPalette.shade(lacquer, 3));
            // Helmet dome with a flared rim, lit from the north-west.
            c.ellipse(16, headY + 3, 6, 5, WolfPalette.shade(lacquer, 2));
            c.ellipse(15, headY + 2, 5, 4, WolfPalette.shade(lacquer, 1));
            c.ellipse(15, headY + 1, 3, 2, WolfPalette.shade(lacquer, 0));
            c.hLine(9, 23, headY + 7, WolfPalette.shade(lacquer, 3));
            c.hLine(9, 23, headY + 8, WolfPalette.shade(lacquer, 4));

            if (toViewer) {
                // Gas mask: two burning lenses and a filter canister.
                c.rect(12, headY + 4, 9, 4, WolfPalette.shade(lacquer, 4));
                c.rect(12, headY + 4, 3, 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.rect(18, headY + 4, 3, 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.px(13, headY + 4, WolfPalette.shade(WolfPalette.BLOOD, 0));
                c.px(19, headY + 4, WolfPalette.shade(WolfPalette.BLOOD, 0));
                c.rect(15, headY + 7, 3, 3, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                c.hLine(15, 17, headY + 7, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
            }
        } else {
            int[] wool = WolfPalette.LEATHER;
            if (!away) {
                // Face and dark hair under the cap.
                c.ellipse(16, headY + 5, 4, 4, WolfPalette.shade(WolfPalette.FLESH, 2));
                c.hLine(13, 19, headY + 7, WolfPalette.shade(WolfPalette.FLESH, 3));
                if (toViewer) {
                    c.px(14, headY + 5, WolfPalette.shade(WolfPalette.NIGHT, 1));
                    c.px(18, headY + 5, WolfPalette.shade(WolfPalette.NIGHT, 1));
                }
            }
            // Flat cap: crown plus a peak jutting the way he is looking.
            c.ellipse(16, headY + 2, 6, 4, WolfPalette.shade(wool, 2));
            c.ellipse(15, headY + 1, 5, 3, WolfPalette.shade(wool, 1));
            c.hLine(11, 21, headY + 4, WolfPalette.shade(wool, 3));
            c.hLine(12, 20, headY + 5, WolfPalette.shade(wool, 4));
            c.px(11, headY + 2, WolfPalette.shade(wool, 0));
        }
    }

    /** Weapons are drawn from the hand outwards, so they always point where the unit looks. */
    private static void drawWeapon(PixelCanvas c, Kit kit, float dx, float dy, int top,
                                   boolean regime) {
        int handX = 16;
        int handY = top + 5;
        int[] metal = WolfPalette.GUNMETAL;
        int[] stock = WolfPalette.LEATHER;

        switch (kit) {
            case ROCKET: {
                // A launch tube: thick, long, with a blast shield and a loaded rocket.
                int tipX = Math.round(handX + dx * 15f);
                int tipY = Math.round(handY + dy * 15f);
                int backX = Math.round(handX - dx * 6f);
                int backY = Math.round(handY - dy * 6f);

                c.line(backX, backY - 1, tipX, tipY - 1, WolfPalette.shade(metal, 1));
                c.line(backX, backY, tipX, tipY, WolfPalette.shade(metal, 2));
                c.line(backX, backY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                c.line(backX, backY + 2, tipX, tipY + 2, WolfPalette.shade(metal, 4));
                // Blast shield over the firer's head.
                int shieldX = Math.round(handX + dx * 5f);
                int shieldY = Math.round(handY + dy * 5f);
                c.rect(shieldX - 2, shieldY - 4, 4, 4, WolfPalette.shade(metal, 2));
                c.hLine(shieldX - 2, shieldX + 1, shieldY - 4, WolfPalette.shade(metal, 1));
                // Warhead.
                c.px(tipX, tipY, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.px(tipX, tipY + 1, WolfPalette.shade(WolfPalette.BLOOD, 2));
                break;
            }
            case SMG: {
                int tipX = Math.round(handX + dx * 10f);
                int tipY = Math.round(handY + dy * 10f);
                c.line(handX - 1, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(handX - 1, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                // Stick magazine and folding stock.
                int magX = Math.round(handX + dx * 3f);
                int magY = Math.round(handY + dy * 3f);
                c.rect(magX - 1, magY + 2, 2, 4, WolfPalette.shade(metal, 2));
                c.line(handX - 1, handY, Math.round(handX - dx * 5f),
                        Math.round(handY - dy * 5f), WolfPalette.shade(metal, 3));
                break;
            }
            case RIFLE:
            default: {
                int tipX = Math.round(handX + dx * 13f);
                int tipY = Math.round(handY + dy * 13f);
                int buttX = Math.round(handX - dx * 5f);
                int buttY = Math.round(handY - dy * 5f);
                // Wooden furniture at the back, blued steel at the front.
                c.line(buttX, buttY, handX, handY, WolfPalette.shade(stock, 1));
                c.line(buttX, buttY + 1, handX, handY + 1, WolfPalette.shade(stock, 3));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                // Bolt handle.
                int boltX = Math.round(handX + dx * 2f);
                int boltY = Math.round(handY + dy * 2f);
                c.px(boltX, boltY - 1, WolfPalette.shade(metal, 0));
                break;
            }
        }
    }

    /**
     * The Ubersoldat: a steel golem, not a man in a coat. Pale plate, hydraulics, a bolted
     * face with red optics, and one red shoulder panel.
     */
    private static PixelCanvas ubersoldat(int facing, int frame) {
        PixelCanvas c = new PixelCanvas(INFANTRY_SIZE, INFANTRY_SIZE);
        int[] plate = WolfPalette.STEEL;
        int[] joint = WolfPalette.NIGHT;
        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        boolean toViewer = dy > 0.35f;
        int step = frame == 1 ? 1 : 0;

        c.groundShadow(16, 29, 10, 3);

        // --- legs: armoured shin plates over black hydraulics ------------------------------
        c.rect(10, 20 - step, 5, 4, WolfPalette.shade(joint, 2));
        c.panel(9, 23 - step, 7, 5, plate, 2);
        c.rect(9, 27 - step, 7, 2, WolfPalette.shade(plate, 4));

        c.rect(18, 20 + step, 5, 4, WolfPalette.shade(joint, 2));
        c.panel(17, 23 + step, 7, 5, plate, 2);
        c.rect(17, 27 + step, 7, 2, WolfPalette.shade(plate, 4));

        // --- torso: a riveted slab with a black waist section ------------------------------
        int top = 8;
        c.rect(11, top + 11, 11, 4, WolfPalette.shade(joint, 2));
        c.panel(8, top, 17, 12, plate, 1);
        c.rivets(10, top + 2, 14, 9, 4, WolfPalette.shade(plate, 0),
                WolfPalette.shade(plate, 4));
        // Chest ridge.
        c.vLine(16, top + 1, top + 10, WolfPalette.shade(plate, 0));
        c.vLine(17, top + 1, top + 10, WolfPalette.shade(plate, 3));

        // --- shoulders: big pauldrons, the right one red -----------------------------------
        c.panel(4, top, 6, 7, plate, 1);
        c.panel(23, top, 6, 7, plate, 1);
        c.rect(24, top + 1, 4, 3, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(24, 27, top + 1, WolfPalette.shade(WolfPalette.BLOOD, 0));

        // --- head: a bolted face plate, blank as a statue ----------------------------------
        int headY = top - 7;
        c.rect(14, headY + 5, 5, 3, WolfPalette.shade(joint, 2));
        c.panel(11, headY, 11, 7, plate, 1);
        c.px(12, headY + 1, WolfPalette.shade(plate, 3));
        c.px(21, headY + 1, WolfPalette.shade(plate, 3));
        c.hLine(11, 21, headY, WolfPalette.shade(plate, 0));
        if (toViewer) {
            c.rect(13, headY + 2, 3, 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.rect(18, headY + 2, 3, 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.px(13, headY + 2, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.px(18, headY + 2, WolfPalette.shade(WolfPalette.BLOOD, 0));
            // Grille where a mouth would be.
            for (int i = 0; i < 3; i++) {
                c.px(15 + i * 2, headY + 5, WolfPalette.shade(joint, 0));
            }
        }

        // --- arm cannon --------------------------------------------------------------------
        int handX = 16;
        int handY = top + 6;
        int tipX = Math.round(handX + dx * 17f);
        int tipY = Math.round(handY + dy * 17f);
        c.line(handX, handY - 2, tipX, tipY - 2, WolfPalette.shade(plate, 1));
        c.line(handX, handY - 1, tipX, tipY - 1, WolfPalette.shade(plate, 2));
        c.line(handX, handY, tipX, tipY, WolfPalette.shade(plate, 3));
        c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(joint, 2));
        // Muzzle and feed drum.
        c.ellipse(tipX, tipY - 1, 2, 2, WolfPalette.shade(joint, 1));
        int drumX = Math.round(handX + dx * 7f);
        int drumY = Math.round(handY + dy * 7f);
        c.ellipse(drumX, drumY - 3, 3, 3, WolfPalette.shade(joint, 2));
        c.px(drumX, drumY - 3, WolfPalette.shade(WolfPalette.BLOOD, 1));

        c.outline(OUTLINE);
        return c;
    }

    // --- vehicles (drawn facing east, rotated at bake time) --------------------------------

    /**
     * Scout jeep: open-topped, four treaded wheels, a windscreen frame, two seats, a pintle
     * gun on a ring mount and a spare wheel on the back.
     */
    private static PixelCanvas jeep(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] body = faction == Faction.REGIME ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = 24;
        int cy = 24;
        int roll = frame == 1 ? 1 : 0;

        // --- wheels, with tread blocks that step round between frames ----------------------
        int[] wheelX = {cx - 11, cx - 11, cx + 5, cx + 5};
        int[] wheelY = {cy - 12, cy + 6, cy - 12, cy + 6};
        for (int i = 0; i < 4; i++) {
            int wx = wheelX[i];
            int wy = wheelY[i];
            c.rect(wx, wy, 7, 6, WolfPalette.shade(metal, 4));
            c.hLine(wx, wx + 6, wy, WolfPalette.shade(metal, 3));
            for (int t = 0; t < 4; t++) {
                c.px(wx + 1 + ((t * 2 + roll) % 6), wy + 2, WolfPalette.shade(metal, 2));
                c.px(wx + 1 + ((t * 2 + roll) % 6), wy + 3, WolfPalette.shade(metal, 2));
            }
            c.ellipse(wx + 3, wy + 3, 1, 1, WolfPalette.shade(metal, 1)); // hub
        }

        // --- hull --------------------------------------------------------------------------
        c.panel(cx - 14, cy - 9, 28, 18, body, 1);
        // Wheel arches.
        c.rect(cx - 12, cy - 10, 7, 2, WolfPalette.shade(body, 3));
        c.rect(cx - 12, cy + 8, 7, 2, WolfPalette.shade(body, 3));
        c.rect(cx + 4, cy - 10, 7, 2, WolfPalette.shade(body, 3));
        c.rect(cx + 4, cy + 8, 7, 2, WolfPalette.shade(body, 3));

        // --- bonnet with louvres and headlights -------------------------------------------
        c.panel(cx + 2, cy - 7, 12, 14, body, 0);
        for (int i = 0; i < 4; i++) {
            c.vLine(cx + 5 + i * 2, cy - 5, cy + 5, WolfPalette.shade(body, 3));
        }
        c.ellipse(cx + 14, cy - 5, 2, 2, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.ellipse(cx + 14, cy + 5, 2, 2, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
        c.px(cx + 14, cy - 5, WolfPalette.shade(WolfPalette.BRASS, 1));
        c.px(cx + 14, cy + 5, WolfPalette.shade(WolfPalette.BRASS, 1));

        // --- windscreen frame, folded flat over the bonnet --------------------------------
        c.rect(cx + 1, cy - 8, 2, 16, WolfPalette.shade(metal, 2));
        c.rect(cx + 1, cy - 8, 2, 2, WolfPalette.shade(metal, 1));

        // --- crew compartment: two seats and a floor --------------------------------------
        c.rect(cx - 12, cy - 7, 13, 14, WolfPalette.shade(metal, 4));
        c.panel(cx - 11, cy - 6, 5, 5, WolfPalette.LEATHER, 2);
        c.panel(cx - 11, cy + 1, 5, 5, WolfPalette.LEATHER, 2);
        c.rect(cx - 5, cy - 6, 4, 12, WolfPalette.shade(body, 2));

        // --- pintle gun on a ring mount ---------------------------------------------------
        c.ellipse(cx - 3, cy, 4, 4, WolfPalette.shade(metal, 3));
        c.ellipse(cx - 3, cy, 2, 2, WolfPalette.shade(metal, 1));
        c.rect(cx - 2, cy - 1, 16, 2, WolfPalette.shade(metal, 1));
        c.hLine(cx - 2, cx + 13, cy + 1, WolfPalette.shade(metal, 3));
        c.rect(cx + 12, cy - 2, 3, 4, WolfPalette.shade(metal, 2)); // flash hider

        // --- spare wheel and jerrycan on the tail -----------------------------------------
        c.ellipse(cx - 15, cy, 4, 4, WolfPalette.shade(metal, 3));
        c.ellipse(cx - 15, cy, 2, 2, WolfPalette.shade(metal, 4));
        c.panel(cx - 15, cy - 8, 4, 6, WolfPalette.LEATHER, 2);

        if (faction == Faction.REGIME) {
            c.hLine(cx - 12, cx - 7, cy - 9, WolfPalette.shade(WolfPalette.BLOOD, 1));
        }
        return c;
    }

    /**
     * Captured Panzer: road wheels visible under the track runs, a riveted hull with a sloped
     * glacis, a turret with a cupola hatch, stowage, and a long gun with a muzzle brake.
     */
    private static PixelCanvas panzer(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] hull = faction == Faction.REGIME ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = 24;
        int cy = 24;
        int roll = frame == 1 ? 1 : 0;

        // --- tracks: run, road wheels, drive sprocket, then the links on top ---------------
        for (int side = 0; side < 2; side++) {
            int ty = side == 0 ? cy - 15 : cy + 9;
            c.rect(cx - 17, ty, 34, 6, WolfPalette.shade(metal, 4));
            for (int i = 0; i < 5; i++) {
                c.ellipse(cx - 13 + i * 7, ty + 3, 3, 2, WolfPalette.shade(metal, 3));
                c.px(cx - 13 + i * 7, ty + 3, WolfPalette.shade(metal, 2));
            }
            c.ellipse(cx + 16, ty + 3, 2, 3, WolfPalette.shade(metal, 2)); // sprocket
            for (int x = cx - 17 + roll; x < cx + 17; x += 3) {
                c.vLine(x, ty, ty + 1, WolfPalette.shade(metal, 1));
                c.vLine(x, ty + 4, ty + 5, WolfPalette.shade(metal, 1));
            }
        }

        // --- hull, sloped at the bow ------------------------------------------------------
        c.panel(cx - 16, cy - 10, 32, 20, hull, 1);
        c.rivets(cx - 14, cy - 8, 28, 16, 6, WolfPalette.shade(hull, 0),
                WolfPalette.shade(hull, 4));
        // Glacis plate: a lighter wedge at the front.
        for (int i = 0; i < 6; i++) {
            c.vLine(cx + 11 + i, cy - 9 + i, cy + 9 - i, WolfPalette.shade(hull, 0));
        }
        // Engine deck grille at the rear.
        c.rect(cx - 15, cy - 6, 6, 12, WolfPalette.shade(hull, 3));
        for (int i = 0; i < 5; i++) {
            c.hLine(cx - 15, cx - 10, cy - 5 + i * 3, WolfPalette.shade(metal, 4));
        }
        // Stowage box and a spare track link on the flank.
        c.panel(cx - 8, cy - 13, 8, 4, WolfPalette.LEATHER, 2);
        c.rect(cx + 2, cy + 10, 6, 3, WolfPalette.shade(metal, 3));

        // --- turret -----------------------------------------------------------------------
        c.ellipse(cx - 1, cy, 10, 9, WolfPalette.shade(hull, 3));
        c.ellipse(cx - 1, cy - 1, 9, 8, WolfPalette.shade(hull, 2));
        c.ellipse(cx - 2, cy - 2, 7, 6, WolfPalette.shade(hull, 1));
        c.rivets(cx - 7, cy - 6, 12, 11, 5, WolfPalette.shade(hull, 0),
                WolfPalette.shade(hull, 4));
        // Commander's cupola with a hatch ring.
        c.ellipse(cx - 5, cy - 4, 3, 3, WolfPalette.shade(metal, 2));
        c.ellipse(cx - 5, cy - 4, 2, 2, WolfPalette.shade(metal, 4));
        // Mantlet and gun.
        c.rect(cx + 7, cy - 3, 4, 6, WolfPalette.shade(hull, 2));
        c.rect(cx + 10, cy - 2, 12, 4, WolfPalette.shade(metal, 2));
        c.hLine(cx + 10, cx + 21, cy - 2, WolfPalette.shade(metal, 1));
        c.hLine(cx + 10, cx + 21, cy + 1, WolfPalette.shade(metal, 4));
        c.rect(cx + 20, cy - 3, 4, 6, WolfPalette.shade(metal, 3)); // muzzle brake
        c.px(cx + 23, cy - 1, WolfPalette.shade(metal, 0));

        if (faction == Faction.REGIME) {
            // Red band across the engine deck.
            c.hLine(cx - 15, cx - 10, cy - 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.hLine(cx - 15, cx - 10, cy - 7, WolfPalette.shade(WolfPalette.BLOOD, 2));
        } else {
            // Captured: black Regime paint showing through a hasty olive repaint, and a
            // whitewashed stripe so their own gunners do not shoot it.
            c.rect(cx - 12, cy - 9, 5, 4, WolfPalette.shade(WolfPalette.NIGHT, 2));
            c.rect(cx + 3, cy + 6, 4, 3, WolfPalette.shade(WolfPalette.NIGHT, 2));
            c.hLine(cx - 6, cx + 2, cy + 8, WolfPalette.shade(WolfPalette.BONE, 1));
            c.hLine(cx - 6, cx + 2, cy + 9, WolfPalette.shade(WolfPalette.BONE, 3));
        }
        return c;
    }

    /**
     * Panzerhund: an armoured quadruped with a heavy head, a plated spine, exhaust stacks and
     * four piston legs. The head is the point of the design — it is nearly a third of the
     * animal, with a hinged jaw, a flame nozzle behind the teeth and one red optic.
     */
    private static PixelCanvas hound(int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] armour = WolfPalette.NIGHT;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = 20;
        int cy = 24;
        int gait = frame == 1 ? 2 : -2;

        // --- torso: a rounded armoured barrel, wider at the shoulders than the hips --------
        c.ellipse(cx - 2, cy, 14, 8, WolfPalette.shade(armour, 2));
        c.ellipse(cx + 2, cy, 10, 8, WolfPalette.shade(armour, 1));
        c.ellipse(cx - 1, cy - 2, 12, 5, WolfPalette.shade(armour, 0));
        c.ellipse(cx - 3, cy + 3, 11, 4, WolfPalette.shade(armour, 3));

        // Two shoulder plates only. Four evenly spaced segments read as a beetle's back.
        c.vLine(cx - 6, cy - 6, cy + 5, WolfPalette.shade(armour, 4));
        c.vLine(cx - 5, cy - 6, cy + 5, WolfPalette.shade(armour, 0));
        c.vLine(cx + 4, cy - 5, cy + 4, WolfPalette.shade(armour, 4));
        c.rivets(cx - 9, cy - 5, 18, 10, 6, WolfPalette.shade(metal, 1),
                WolfPalette.shade(armour, 4));

        // --- limbs: attached at the torso edge and short, so they read as legs, not sticks -
        // Hips and shoulders sit just inside the body outline; each leg goes out and down to
        // a paw. Drawing them after the torso is what keeps them visibly connected to it.
        leg(c, cx + 5, cy - 6, 4, -7 - gait, metal, armour);
        leg(c, cx + 5, cy + 6, 4, 7 + gait, metal, armour);
        leg(c, cx - 8, cy - 6, -3, -7 + gait, metal, armour);
        leg(c, cx - 8, cy + 6, -3, 7 - gait, metal, armour);

        // Haunch plates over the rear leg joints.
        c.ellipse(cx - 8, cy - 5, 4, 3, WolfPalette.shade(armour, 1));
        c.ellipse(cx - 8, cy + 5, 4, 3, WolfPalette.shade(armour, 1));

        // Exhaust stacks along the spine, sooty at the lips.
        for (int i = 0; i < 3; i++) {
            int sx = cx - 7 + i * 5;
            c.rect(sx, cy - 10, 3, 5, WolfPalette.shade(metal, 2));
            c.hLine(sx, sx + 2, cy - 10, WolfPalette.shade(metal, 0));
            c.px(sx + 1, cy - 11, WolfPalette.shade(WolfPalette.SMOKE, 1));
        }

        // Stubby armoured tail.
        c.rect(cx - 16, cy - 2, 5, 5, WolfPalette.shade(armour, 2));
        c.rect(cx - 19, cy - 1, 4, 3, WolfPalette.shade(metal, 3));

        // --- neck: a short segmented collar, narrower than both body and head --------------
        c.rect(cx + 10, cy - 4, 4, 9, WolfPalette.shade(metal, 3));
        c.vLine(cx + 11, cy - 4, cy + 4, WolfPalette.shade(metal, 1));
        c.vLine(cx + 13, cy - 4, cy + 4, WolfPalette.shade(metal, 2));

        // --- head: broad skull, brow, single optic, then a snout with jaws -----------------
        int hx = cx + 13;
        c.ellipse(hx + 4, cy, 5, 6, WolfPalette.shade(armour, 2));
        c.panel(hx, cy - 5, 9, 10, armour, 1);
        c.rect(hx + 1, cy - 4, 7, 3, WolfPalette.shade(armour, 0)); // brow
        c.hLine(hx, hx + 8, cy - 6, WolfPalette.shade(metal, 1));
        c.rect(hx + 1, cy + 3, 7, 2, WolfPalette.shade(armour, 3)); // jowl

        // The optic, housed and burning.
        c.ellipse(hx + 3, cy - 2, 3, 2, WolfPalette.shade(metal, 3));
        c.ellipse(hx + 3, cy - 2, 2, 1, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.px(hx + 3, cy - 2, WolfPalette.shade(WolfPalette.BLOOD, 0));

        // Snout: narrower than the skull, with an upper and lower jaw plate.
        c.rect(hx + 8, cy - 4, 7, 3, WolfPalette.shade(metal, 2));
        c.rect(hx + 8, cy + 1, 7, 3, WolfPalette.shade(metal, 2));
        c.hLine(hx + 8, hx + 14, cy - 4, WolfPalette.shade(metal, 1));
        c.hLine(hx + 8, hx + 14, cy + 3, WolfPalette.shade(metal, 4));
        // Teeth between the jaws, and the flame nozzle behind them.
        for (int i = 0; i < 5; i++) {
            c.px(hx + 9 + i, cy - 1, WolfPalette.shade(WolfPalette.BONE, 1));
            c.px(hx + 9 + i, cy + 1, WolfPalette.shade(WolfPalette.BONE, 2));
        }
        c.px(hx + 8, cy, WolfPalette.shade(WolfPalette.FIRE, 2));
        c.px(hx + 14, cy, WolfPalette.shade(WolfPalette.FIRE, 1));

        // Cheek vents, tight against the skull. Vanes standing off the head read as two more
        // legs once the sprite is rotated.
        c.px(hx + 1, cy - 6, WolfPalette.shade(metal, 1));
        c.px(hx + 1, cy + 5, WolfPalette.shade(metal, 1));

        return c;
    }

    /**
     * One leg: a thick thigh from the body edge, a knee, a shin, and a splayed paw plate.
     * Kept short on purpose — long thin limbs read as insect legs, not as an armoured hound.
     */
    private static void leg(PixelCanvas c, int hipX, int hipY, int outX, int outY,
                            int[] metal, int[] armour) {
        int kneeX = hipX + outX;
        int kneeY = hipY + outY / 2;
        int footX = hipX + outX / 2;
        int footY = hipY + outY;

        // Thigh: three pixels thick so it reads as a limb with mass.
        c.line(hipX, hipY - 1, kneeX, kneeY - 1, WolfPalette.shade(armour, 1));
        c.line(hipX, hipY, kneeX, kneeY, WolfPalette.shade(metal, 1));
        c.line(hipX, hipY + 1, kneeX, kneeY + 1, WolfPalette.shade(metal, 3));

        c.ellipse(kneeX, kneeY, 2, 2, WolfPalette.shade(metal, 2));
        c.px(kneeX, kneeY - 1, WolfPalette.shade(metal, 0));

        c.line(kneeX, kneeY, footX, footY, WolfPalette.shade(metal, 1));
        c.line(kneeX + 1, kneeY, footX + 1, footY, WolfPalette.shade(metal, 3));

        // Paw: a plate with two claws biting into the ground.
        c.rect(footX - 3, footY - 1, 6, 3, WolfPalette.shade(metal, 2));
        c.hLine(footX - 3, footX + 2, footY - 1, WolfPalette.shade(metal, 1));
        c.hLine(footX - 3, footX + 2, footY + 1, WolfPalette.shade(metal, 4));
        c.px(footX - 3, footY + 2, WolfPalette.shade(WolfPalette.BONE, 2));
        c.px(footX + 2, footY + 2, WolfPalette.shade(WolfPalette.BONE, 2));
    }

    /**
     * Harvester: a tracked machine with an engine deck, a ribbed hopper, a glazed cab,
     * hydraulic arms and a toothed cutting drum.
     *
     * <p>The hopper is kept well inside the deck. An earlier pass had it filling the chassis,
     * and the whole vehicle read as an empty picture frame with a hole in the middle.
     */
    private static PixelCanvas harvester(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] body = faction == Faction.REGIME ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = 24;
        int cy = 24;

        // --- tracks ------------------------------------------------------------------------
        for (int side = 0; side < 2; side++) {
            int ty = side == 0 ? cy - 16 : cy + 10;
            c.rect(cx - 16, ty, 30, 6, WolfPalette.shade(metal, 4));
            for (int i = 0; i < 4; i++) {
                c.ellipse(cx - 11 + i * 8, ty + 3, 3, 2, WolfPalette.shade(metal, 3));
            }
            for (int x = cx - 16; x < cx + 14; x += 3) {
                c.vLine(x, ty, ty + 1, WolfPalette.shade(metal, 1));
                c.vLine(x, ty + 4, ty + 5, WolfPalette.shade(metal, 1));
            }
        }

        // --- chassis deck ------------------------------------------------------------------
        c.panel(cx - 16, cy - 11, 30, 22, body, 1);
        c.rivets(cx - 14, cy - 9, 27, 18, 7, WolfPalette.shade(body, 0),
                WolfPalette.shade(body, 4));

        // Engine deck at the tail: a louvred grille and an exhaust.
        c.panel(cx - 15, cy - 8, 6, 16, body, 2);
        for (int i = 0; i < 5; i++) {
            c.hLine(cx - 14, cx - 10, cy - 7 + i * 3, WolfPalette.shade(metal, 4));
        }
        c.rect(cx - 17, cy - 3, 2, 3, WolfPalette.shade(metal, 2));

        // --- hopper: an open bin set into the deck, rimmed and ribbed ----------------------
        int hx = cx - 8;
        int hy = cy - 8;
        int hw = 12;
        int hh = 16;
        c.panel(hx, hy, hw, hh, metal, 2);
        c.rect(hx + 2, hy + 2, hw - 4, hh - 4, WolfPalette.shade(metal, 4));
        c.rectOutline(hx + 2, hy + 2, hw - 4, hh - 4, WolfPalette.shade(metal, 0));
        // Ribs up the outside of the bin.
        for (int i = 1; i < 4; i++) {
            c.vLine(hx + i * 3, hy, hy + 1, WolfPalette.shade(metal, 1));
            c.vLine(hx + i * 3, hy + hh - 2, hy + hh - 1, WolfPalette.shade(metal, 1));
        }

        if (frame > 0) {
            // Ore heaped inside, glowing, with a lit crust on top.
            int fill = frame == 1 ? 5 : 11;
            int top = hy + hh - 3 - fill;
            c.rect(hx + 3, top, hw - 6, fill, WolfPalette.shade(WolfPalette.OCCULT, 2));
            c.hLine(hx + 3, hx + hw - 4, top, WolfPalette.shade(WolfPalette.OCCULT, 0));
            c.speckle(hx + 3, top, hw - 6, fill, WolfPalette.shade(WolfPalette.OCCULT, 0),
                    frame * 13, 5);
            c.speckle(hx + 3, top, hw - 6, fill, WolfPalette.shade(WolfPalette.OCCULT, 3),
                    frame * 7, 6);
        }

        // --- cab: glazed on two sides, with a roof light ----------------------------------
        c.panel(cx + 5, cy - 9, 9, 18, body, 0);
        c.rect(cx + 7, cy - 7, 6, 6, WolfPalette.shade(metal, 4));
        c.hLine(cx + 7, cx + 12, cy - 7, WolfPalette.shade(WolfPalette.STEEL, 1));
        c.vLine(cx + 7, cy - 7, cy - 2, WolfPalette.shade(WolfPalette.STEEL, 2));
        c.rect(cx + 7, cy + 2, 6, 6, WolfPalette.shade(metal, 4));
        c.hLine(cx + 7, cx + 12, cy + 2, WolfPalette.shade(WolfPalette.STEEL, 2));
        c.px(cx + 13, cy - 9, WolfPalette.shade(WolfPalette.FIRE, 1));

        // --- hydraulic arms reaching forward to the drum ----------------------------------
        c.rect(cx + 13, cy - 10, 6, 3, WolfPalette.shade(metal, 2));
        c.rect(cx + 13, cy + 7, 6, 3, WolfPalette.shade(metal, 2));
        c.hLine(cx + 13, cx + 18, cy - 10, WolfPalette.shade(metal, 0));
        c.hLine(cx + 13, cx + 18, cy + 7, WolfPalette.shade(metal, 0));

        // --- cutting drum with teeth, in a hazard-striped housing -------------------------
        c.panel(cx + 17, cy - 12, 5, 24, metal, 2);
        c.hazard(cx + 17, cy - 12, 5, 5, WolfPalette.shade(WolfPalette.BRASS, 1),
                WolfPalette.shade(metal, 4));
        c.hazard(cx + 17, cy + 7, 5, 5, WolfPalette.shade(WolfPalette.BRASS, 1),
                WolfPalette.shade(metal, 4));
        for (int y = cy - 10; y < cy + 11; y += 3) {
            c.px(cx + 22, y, WolfPalette.shade(WolfPalette.BONE, 1));
            c.px(cx + 22, y + 1, WolfPalette.shade(WolfPalette.BONE, 3));
        }

        if (faction == Faction.REGIME) {
            c.hLine(cx - 15, cx - 10, cy - 10, WolfPalette.shade(WolfPalette.BLOOD, 1));
        }
        return c;
    }
}
