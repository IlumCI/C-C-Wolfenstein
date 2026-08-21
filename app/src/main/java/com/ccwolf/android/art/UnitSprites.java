package com.ccwolf.android.art;

import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;

/**
 * Pixel-art recipes for every unit, baked once at startup.
 *
 * <p>Two approaches, chosen per unit:
 *
 * <ul>
 *   <li><b>Infantry</b> are drawn upright per facing. A soldier seen from above-behind keeps
 *       his helmet on top whichever way he is looking, so the body is redrawn for each of the
 *       eight facings and only the weapon, face and pack move. Rotating a soldier sprite
 *       bodily would lay him on his side.</li>
 *   <li><b>Vehicles</b> are drawn once facing east and rotated. A hull genuinely does rotate
 *       in the world, and eight hand-drawn hulls would be eight times the work for a
 *       silhouette the player reads at forty pixels across.</li>
 * </ul>
 */
public final class UnitSprites {

    /** Source pixels per tile. Everything is authored at this scale. */
    public static final int TILE = 24;

    /** Infantry live in a one-tile box; vehicles get a bigger one so rotation cannot clip. */
    public static final int INFANTRY_SIZE = 24;
    public static final int VEHICLE_SIZE = 32;

    public static final int FACINGS = 8;
    public static final int WALK_FRAMES = 2;

    private static final int SHADOW = 0x55000000;
    private static final int OUTLINE = 0xFF13140F;

    private UnitSprites() {
    }

    /**
     * Bakes one unit sprite.
     *
     * @param facing 0 = east, then clockwise in eighths
     * @param frame walk/tread animation frame
     */
    public static PixelCanvas render(UnitType type, Faction faction, int facing, int frame) {
        switch (type) {
            case SCOUT_JEEP:
                return vehicle(jeep(faction, frame), facing, 11);
            case CAPTURED_PANZER:
                return vehicle(panzer(faction, frame), facing, 13);
            case PANZERHUND:
                return vehicle(hound(faction, frame), facing, 9);
            case HARVESTER:
                return vehicle(harvester(faction, frame), facing, 13);
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
     * <p>Order matters here and was wrong the first time round: rotate the bare hull, then
     * outline it, then lay it over a shadow. Outlining before the rotation tears the outline
     * apart, and rotating the shadow with the hull sends the sun spinning round the map.
     */
    private static PixelCanvas vehicle(PixelCanvas east, int facing, int shadowRadius) {
        PixelCanvas hull = facing == 0 ? east
                : east.rotatedSmooth((float) (facing * Math.PI / 4.0), 3);
        hull.outline(OUTLINE);

        PixelCanvas out = new PixelCanvas(east.width(), east.height());
        out.ellipse(east.width() / 2, east.height() / 2 + 5, shadowRadius, shadowRadius / 3,
                SHADOW);
        out.blit(hull, 0, 0);
        return out;
    }

    // --- infantry -------------------------------------------------------------------------

    private static PixelCanvas infantry(Faction faction, int facing, int frame, Kit kit) {
        PixelCanvas c = new PixelCanvas(INFANTRY_SIZE, INFANTRY_SIZE);
        boolean regime = faction == Faction.REGIME;
        int[] cloth = regime ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] gear = regime ? WolfPalette.NIGHT : WolfPalette.LEATHER;

        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        boolean facingViewer = dy > 0.35f;
        boolean facingAway = dy < -0.35f;
        int bob = frame == 1 ? 1 : 0;

        c.ellipse(12, 20, 6, 2, SHADOW);

        // Boots. They swap on the walk frame, which is the whole animation.
        int bootDark = WolfPalette.shade(gear, 3);
        c.rect(9, 16 - bob, 3, 4, bootDark);
        c.rect(13, 16 + bob, 3, 4, bootDark);

        // Greatcoat: lit from the top-left like everything else in the game.
        int top = 7 + bob;
        c.rampVertical(8, top, 9, 10, cloth, 1, 3);
        c.bevel(8, top, 9, 10, WolfPalette.shade(cloth, 0), WolfPalette.shade(cloth, 4));

        // Webbing and belt.
        c.hLine(8, 16, top + 6, WolfPalette.shade(gear, regime ? 4 : 2));
        c.px(12, top + 6, WolfPalette.shade(regime ? WolfPalette.STEEL : WolfPalette.BRASS, 1));

        if (regime) {
            // The single hot accent on an otherwise black figure: a red band on the left arm,
            // and a stencilled number on the shoulder plate.
            c.rect(7, top + 2, 2, 3, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.px(7, top + 2, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.px(16, top + 1, WolfPalette.shade(WolfPalette.BONE, 1));
            c.px(17, top + 1, WolfPalette.shade(WolfPalette.BONE, 2));
            // Chest plate: a slab with a lit top edge, riveted.
            c.rect(9, top + 2, 7, 4, WolfPalette.shade(cloth, 1));
            c.hLine(9, 15, top + 2, WolfPalette.shade(cloth, 0));
            c.px(9, top + 5, WolfPalette.shade(WolfPalette.STEEL, 2));
            c.px(15, top + 5, WolfPalette.shade(WolfPalette.STEEL, 2));
        }

        // Shoulders, wider than the coat so the figure reads as a person from above.
        c.rect(7, top, 11, 3, WolfPalette.shade(cloth, 1));
        c.hLine(7, 17, top, WolfPalette.shade(cloth, 0));

        if (facingAway) {
            // Seen from behind: pack and rolled blanket.
            c.rect(9, top + 1, 7, 6, WolfPalette.shade(gear, 2));
            c.rect(9, top + 1, 7, 2, WolfPalette.shade(gear, 1));
            c.hLine(9, 15, top + 4, WolfPalette.shade(gear, 3));
        }

        drawHead(c, faction, top, facingViewer);
        drawWeapon(c, kit, gear, dx, dy, top);

        c.outline(OUTLINE);
        return c;
    }

    /** Cloth cap for the Resistance, flared steel helmet for the Regime. */
    private static void drawHead(PixelCanvas c, Faction faction, int top, boolean facingViewer) {
        boolean regime = faction == Faction.REGIME;
        int headY = top - 4;

        if (facingViewer && !regime) {
            c.rect(10, headY + 3, 5, 3, WolfPalette.shade(WolfPalette.FLESH, 2));
            c.px(11, headY + 4, WolfPalette.shade(WolfPalette.FLESH, 4));
            c.px(13, headY + 4, WolfPalette.shade(WolfPalette.FLESH, 4));
        }

        if (regime) {
            // Lacquered helmet over a gas mask: no skin shows, and the lenses burn red.
            int[] lacquer = WolfPalette.NIGHT;
            c.ellipse(12, headY + 2, 5, 4, WolfPalette.shade(lacquer, 2));
            c.ellipse(12, headY + 1, 4, 3, WolfPalette.shade(lacquer, 1));
            c.hLine(7, 17, headY + 4, WolfPalette.shade(lacquer, 3)); // flared rim
            c.hLine(9, 14, headY - 1, WolfPalette.shade(lacquer, 0)); // highlight
            if (facingViewer) {
                c.rect(10, headY + 3, 5, 3, WolfPalette.shade(lacquer, 4)); // mask
                c.px(10, headY + 3, WolfPalette.shade(WolfPalette.BLOOD, 0));
                c.px(14, headY + 3, WolfPalette.shade(WolfPalette.BLOOD, 0));
                c.px(12, headY + 5, WolfPalette.shade(lacquer, 3)); // filter
            }
        } else {
            int[] wool = WolfPalette.LEATHER;
            c.ellipse(12, headY + 2, 4, 3, WolfPalette.shade(wool, 2));
            c.hLine(8, 16, headY + 4, WolfPalette.shade(wool, 3)); // brim
            c.hLine(10, 14, headY, WolfPalette.shade(wool, 1));
            // Armband: the Resistance's only insignia.
            c.px(8, headY + 7, WolfPalette.shade(WolfPalette.BONE, 1));
            c.px(8, headY + 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
        }
    }

    private static void drawWeapon(PixelCanvas c, Kit kit, int[] gear, float dx, float dy,
                                   int top) {
        int handX = 12;
        int handY = top + 4;
        int[] metal = WolfPalette.GUNMETAL;

        switch (kit) {
            case ROCKET: {
                // A launch tube is thick, long, and sits on the shoulder.
                int tipX = Math.round(handX + dx * 11f);
                int tipY = Math.round(handY + dy * 11f);
                c.line(handX, handY - 1, tipX, tipY - 1, WolfPalette.shade(metal, 1));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 2));
                c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                c.px(tipX, tipY, WolfPalette.shade(WolfPalette.FIRE, 3));
                c.rect(handX - 2, handY - 3, 4, 3, WolfPalette.shade(gear, 2));
                break;
            }
            case SMG: {
                int tipX = Math.round(handX + dx * 7f);
                int tipY = Math.round(handY + dy * 7f);
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                // Stick magazine hanging under the receiver.
                c.rect(handX - 1, handY + 2, 2, 3, WolfPalette.shade(metal, 2));
                break;
            }
            case RIFLE:
            default: {
                int tipX = Math.round(handX + dx * 10f);
                int tipY = Math.round(handY + dy * 10f);
                c.line(handX - 1, handY, tipX, tipY, WolfPalette.shade(WolfPalette.LEATHER, 2));
                c.line(handX + 1, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                break;
            }
        }
    }

    /**
     * The Ubersoldat: not a man in a coat but a slab of plate with a man somewhere inside.
     * Broader than everything else on the field, and the only unit with a lit visor.
     */
    private static PixelCanvas ubersoldat(int facing, int frame) {
        PixelCanvas c = new PixelCanvas(INFANTRY_SIZE, INFANTRY_SIZE);
        int[] plate = WolfPalette.STEEL;
        int[] metal = WolfPalette.NIGHT;
        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        boolean facingViewer = dy > 0.35f;
        int bob = frame == 1 ? 1 : 0;

        c.ellipse(12, 21, 8, 2, SHADOW);

        // Piston legs.
        c.rect(8, 16 - bob, 4, 5, WolfPalette.shade(metal, 3));
        c.rect(13, 16 + bob, 4, 5, WolfPalette.shade(metal, 3));
        c.hLine(8, 11, 18 - bob, WolfPalette.shade(metal, 1));
        c.hLine(13, 16, 18 + bob, WolfPalette.shade(metal, 1));

        int top = 6;
        // Torso: a riveted box, not a coat.
        c.rampVertical(6, top, 13, 11, plate, 1, 3);
        c.bevel(6, top, 13, 11, WolfPalette.shade(plate, 0), WolfPalette.shade(plate, 4));
        c.rect(5, top + 1, 3, 5, WolfPalette.shade(metal, 2));
        c.rect(17, top + 1, 3, 5, WolfPalette.shade(metal, 2));
        // One red shoulder panel, the only colour anywhere on the machine.
        c.rect(17, top + 1, 3, 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.px(17, top + 1, WolfPalette.shade(WolfPalette.BLOOD, 0));

        // Rivets down the chest plate.
        for (int y = top + 2; y < top + 10; y += 3) {
            c.px(9, y, WolfPalette.shade(plate, 0));
            c.px(15, y, WolfPalette.shade(plate, 0));
        }

        // Head: a bolted face plate, blank as a statue, with two red optics.
        c.rect(9, top - 4, 7, 5, WolfPalette.shade(plate, 1));
        c.hLine(9, 15, top - 4, WolfPalette.shade(plate, 0));
        c.hLine(8, 16, top + 1, WolfPalette.shade(plate, 3));
        c.px(9, top - 3, WolfPalette.shade(plate, 3));
        c.px(15, top - 3, WolfPalette.shade(plate, 3));
        if (facingViewer) {
            c.px(10, top - 2, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.px(14, top - 2, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.px(12, top - 1, WolfPalette.shade(plate, 4));
        }

        // Arm cannon.
        int handX = 12;
        int handY = top + 5;
        int tipX = Math.round(handX + dx * 12f);
        int tipY = Math.round(handY + dy * 12f);
        c.line(handX, handY - 1, tipX, tipY - 1, WolfPalette.shade(metal, 1));
        c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 2));
        c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
        c.px(tipX, tipY, WolfPalette.shade(WolfPalette.OCCULT, 1));

        c.outline(OUTLINE);
        return c;
    }

    // --- vehicles (drawn facing east, rotated at bake time) --------------------------------

    private static PixelCanvas jeep(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] body = faction == Faction.REGIME ? WolfPalette.STEEL : WolfPalette.OLIVE;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = VEHICLE_SIZE / 2;
        int cy = VEHICLE_SIZE / 2;


        // Wheels, which shimmer between frames to suggest motion.
        int tyre = WolfPalette.shade(metal, frame == 1 ? 4 : 3);
        c.rect(cx - 8, cy - 7, 5, 3, tyre);
        c.rect(cx - 8, cy + 4, 5, 3, tyre);
        c.rect(cx + 4, cy - 7, 5, 3, tyre);
        c.rect(cx + 4, cy + 4, 5, 3, tyre);

        // Hull.
        c.rampVertical(cx - 10, cy - 6, 20, 12, body, 0, 2);
        c.bevel(cx - 10, cy - 6, 20, 12, WolfPalette.shade(body, 0), WolfPalette.shade(body, 4));

        // Bonnet louvres at the front, crew compartment behind.
        c.rect(cx + 2, cy - 4, 7, 8, WolfPalette.shade(body, 2));
        for (int i = 0; i < 3; i++) {
            c.vLine(cx + 4 + i * 2, cy - 3, cy + 3, WolfPalette.shade(body, 3));
        }
        c.rect(cx - 8, cy - 4, 8, 8, WolfPalette.shade(metal, 3));
        c.rect(cx - 7, cy - 3, 6, 6, WolfPalette.shade(metal, 2));

        // Pintle gun over the bonnet, plus a jerrycan on the back.
        c.line(cx, cy, cx + 13, cy, WolfPalette.shade(metal, 1));
        c.px(cx + 13, cy, WolfPalette.shade(metal, 0));
        c.rect(cx - 11, cy - 3, 3, 6, WolfPalette.shade(WolfPalette.LEATHER, 2));

        return c;
    }

    private static PixelCanvas panzer(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] hull = faction == Faction.REGIME ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = VEHICLE_SIZE / 2;
        int cy = VEHICLE_SIZE / 2;


        // Tracks: rungs step along by one pixel between frames, which reads as movement.
        int offset = frame == 1 ? 1 : 0;
        c.rect(cx - 12, cy - 9, 24, 4, WolfPalette.shade(metal, 3));
        c.rect(cx - 12, cy + 5, 24, 4, WolfPalette.shade(metal, 3));
        for (int x = cx - 11 + offset; x < cx + 12; x += 3) {
            c.vLine(x, cy - 9, cy - 6, WolfPalette.shade(metal, 1));
            c.vLine(x, cy + 5, cy + 8, WolfPalette.shade(metal, 1));
        }

        // Hull, with sloped glacis at the front.
        c.rampVertical(cx - 11, cy - 6, 22, 12, hull, 0, 2);
        c.bevel(cx - 11, cy - 6, 22, 12, WolfPalette.shade(hull, 0), WolfPalette.shade(hull, 4));
        c.line(cx + 8, cy - 6, cx + 12, cy - 2, WolfPalette.shade(hull, 0));
        c.line(cx + 8, cy + 5, cx + 12, cy + 1, WolfPalette.shade(hull, 4));

        // Turret and gun.
        c.ellipse(cx - 1, cy, 6, 5, WolfPalette.shade(hull, 2));
        c.ellipse(cx - 1, cy - 1, 5, 4, WolfPalette.shade(hull, 1));
        c.rect(cx + 4, cy - 1, 12, 3, WolfPalette.shade(metal, 2));
        c.hLine(cx + 4, cx + 15, cy - 1, WolfPalette.shade(metal, 1));
        c.rect(cx + 14, cy - 2, 3, 5, WolfPalette.shade(metal, 3)); // muzzle brake

        if (faction == Faction.REGIME) {
            // Regime armour carries a red band across the engine deck.
            c.hLine(cx - 9, cx - 5, cy - 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.hLine(cx - 9, cx - 5, cy - 3, WolfPalette.shade(WolfPalette.BLOOD, 2));
        } else {
            // Captured: black paint showing through a hasty olive repaint, plus a daubed stripe.
            c.rect(cx - 9, cy - 5, 4, 3, WolfPalette.shade(WolfPalette.NIGHT, 2));
            c.hLine(cx - 10, cx - 4, cy + 3, WolfPalette.shade(WolfPalette.BONE, 2));
        }

        return c;
    }

    private static PixelCanvas hound(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] body = WolfPalette.NIGHT;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = VEHICLE_SIZE / 2;
        int cy = VEHICLE_SIZE / 2;
        int gait = frame == 1 ? 2 : -2;

        // Legs first, so the hull sits on top of them: splayed, jointed, and out of phase so
        // the thing looks like it is running rather than sliding.
        int legLit = WolfPalette.shade(metal, 1);
        int legDark = WolfPalette.shade(metal, 3);
        drawLeg(c, cx - 4, cy - 4, -4, -7 + gait, legLit, legDark);
        drawLeg(c, cx - 4, cy + 4, -4, 7 - gait, legLit, legDark);
        drawLeg(c, cx + 4, cy - 4, 4, -7 - gait, legLit, legDark);
        drawLeg(c, cx + 4, cy + 4, 4, 7 + gait, legLit, legDark);

        // Armoured barrel of a body.
        c.rampVertical(cx - 9, cy - 5, 16, 10, body, 0, 2);
        c.bevel(cx - 9, cy - 5, 16, 10, WolfPalette.shade(body, 0), WolfPalette.shade(body, 4));

        // Spine plates and exhaust stubs.
        c.hLine(cx - 7, cx + 5, cy - 3, WolfPalette.shade(body, 0));
        for (int x = cx - 6; x < cx + 5; x += 4) {
            c.px(x, cy - 6, WolfPalette.shade(metal, 1));
            c.px(x, cy - 7, WolfPalette.shade(SMOKE_STACK, 0));
        }

        // Neck and head: a blunt wedge, jaws open, one lamp burning.
        c.rect(cx + 6, cy - 3, 4, 6, WolfPalette.shade(metal, 2));
        c.rect(cx + 9, cy - 4, 5, 8, WolfPalette.shade(metal, 1));
        c.bevel(cx + 9, cy - 4, 5, 8, WolfPalette.shade(metal, 0), WolfPalette.shade(metal, 4));

        // Jaws: an upper and lower plate with teeth between them.
        c.line(cx + 13, cy - 3, cx + 16, cy - 2, WolfPalette.shade(metal, 1));
        c.line(cx + 13, cy + 3, cx + 16, cy + 2, WolfPalette.shade(metal, 1));
        for (int i = 0; i < 3; i++) {
            c.px(cx + 14 + i, cy - 1, WolfPalette.shade(WolfPalette.BONE, 1));
            c.px(cx + 14 + i, cy + 1, WolfPalette.shade(WolfPalette.BONE, 2));
        }
        c.px(cx + 11, cy - 2, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.px(cx + 11, cy + 2, WolfPalette.shade(WolfPalette.BLOOD, 1));

        return c;
    }

    /** Smoke discolouring the exhaust stubs, kept out of the ramp list on purpose. */
    private static final int[] SMOKE_STACK = WolfPalette.SMOKE;

    /** A two-segment leg: thigh out from the hull, shin down to the ground. */
    private static void drawLeg(PixelCanvas c, int hipX, int hipY, int outX, int outY,
                                int lit, int dark) {
        int kneeX = hipX + outX;
        int kneeY = hipY + outY / 2;
        int footX = hipX + outX / 2;
        int footY = hipY + outY;
        c.line(hipX, hipY, kneeX, kneeY, lit);
        c.line(hipX, hipY + 1, kneeX, kneeY + 1, dark);
        c.line(kneeX, kneeY, footX, footY, lit);
        c.line(kneeX + 1, kneeY, footX + 1, footY, dark);
        c.px(footX, footY, WolfPalette.shade(WolfPalette.BONE, 3));
    }

    /**
     * The harvester. Its ore load shows: {@code frame} doubles as the fill level, which is
     * how a player can tell at a glance whether one is on its way out or on its way home.
     */
    private static PixelCanvas harvester(Faction faction, int frame) {
        PixelCanvas c = new PixelCanvas(VEHICLE_SIZE, VEHICLE_SIZE);
        int[] body = faction == Faction.REGIME ? WolfPalette.NIGHT : WolfPalette.OLIVE;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = VEHICLE_SIZE / 2;
        int cy = VEHICLE_SIZE / 2;

        // Tracks.
        c.rect(cx - 12, cy - 10, 21, 4, WolfPalette.shade(metal, 3));
        c.rect(cx - 12, cy + 6, 21, 4, WolfPalette.shade(metal, 3));
        for (int x = cx - 11; x < cx + 9; x += 3) {
            c.vLine(x, cy - 10, cy - 7, WolfPalette.shade(metal, 1));
            c.vLine(x, cy + 6, cy + 9, WolfPalette.shade(metal, 1));
        }

        // Chassis: solid, lit from the top, not an empty box outline.
        c.rampVertical(cx - 11, cy - 7, 20, 14, body, 0, 2);
        c.bevel(cx - 11, cy - 7, 20, 14, WolfPalette.shade(body, 0), WolfPalette.shade(body, 4));

        // Hopper: a bin set into the deck. Filled with the darkest shade it read as a hole
        // punched through the machine, so the interior is mid-tone with a lit rim and the bin
        // is kept well inside the deck.
        int hopperX = cx - 8;
        int hopperY = cy - 3;
        int hopperW = 9;
        int hopperH = 7;
        c.rect(hopperX, hopperY, hopperW, hopperH, WolfPalette.shade(metal, 2));
        c.rectOutline(hopperX, hopperY, hopperW, hopperH, WolfPalette.shade(metal, 0));
        c.hLine(hopperX + 1, hopperX + hopperW - 2, hopperY + 1,
                WolfPalette.shade(metal, 3));
        // Cross-braces over the bin.
        c.vLine(hopperX + 3, hopperY, hopperY + hopperH - 1, WolfPalette.shade(metal, 1));
        c.vLine(hopperX + 6, hopperY, hopperY + hopperH - 1, WolfPalette.shade(metal, 1));

        // The load, glowing, filling from the bottom.
        if (frame > 0) {
            int fill = frame == 1 ? 3 : 6;
            int top = hopperY + hopperH - 1 - fill;
            c.rect(hopperX + 1, top, hopperW - 2, fill,
                    WolfPalette.shade(WolfPalette.OCCULT, 2));
            c.hLine(hopperX + 1, hopperX + hopperW - 2, top,
                    WolfPalette.shade(WolfPalette.OCCULT, 0));
            c.speckle(hopperX + 1, top, hopperW - 2, fill,
                    WolfPalette.shade(WolfPalette.OCCULT, 1), 7, 4);
        }

        // Cab over the driver, then the cutting drum on the nose.
        c.rect(cx + 2, cy - 5, 6, 10, WolfPalette.shade(body, 1));
        c.bevel(cx + 2, cy - 5, 6, 10, WolfPalette.shade(body, 0), WolfPalette.shade(body, 4));
        c.rect(cx + 4, cy - 3, 3, 6, WolfPalette.shade(metal, 4)); // windscreen

        c.rect(cx + 8, cy - 7, 4, 14, WolfPalette.shade(metal, 2));
        c.bevel(cx + 8, cy - 7, 4, 14, WolfPalette.shade(metal, 1),
                WolfPalette.shade(metal, 4));
        for (int y = cy - 6; y < cy + 7; y += 2) {
            c.px(cx + 12, y, WolfPalette.shade(WolfPalette.BONE, 1)); // cutter teeth
        }

        return c;
    }
}
