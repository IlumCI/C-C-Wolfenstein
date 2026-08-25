package com.ccwolf.game.art;

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

    /**
     * How many real pixels each authored one becomes.
     *
     * <p>Men, vehicles and guns are composed in the sizes below — a rifleman on a
     * thirty-two-pixel square, a hull on forty-eight — and those numbers are the drawing. The
     * canvas underneath is what grew, so the roster matches the ground it stands on without a
     * single figure being redrawn. Same mechanism as the structures, and the same reason: the
     * composition is the art, and the resolution is not.
     */
    private static final int SCALE = Math.max(1, TerrainSprites.TILE / TILE);

    /** Every sprite starts here, so none of them can be made at the wrong scale by accident. */
    private static PixelCanvas canvas(int size) {
        return new PixelCanvas(size, size, SCALE);
    }

    /**
     * The finer grid the drawn infantry and the Ubersoldat are authored on: sixty-four art
     * pixels to a tile at two screen pixels each, which is the same hundred and twenty-eight on
     * screen that thirty-two at four gives. Nothing downstream can tell the difference - the
     * atlas, the renderer and the memory budget only ever see the finished buffer - which is why
     * the roster moved across one unit at a time.
     */
    public static final int FINE_TILE = 64;

    static final int FINE_SCALE = Math.max(1, TerrainSprites.TILE / FINE_TILE);

    public static final int INFANTRY_SIZE = 32;
    public static final int VEHICLE_SIZE = 48;

    /** A heavy piece: twice a man's frontage, and it should be obvious it does not fit a tile. */
    public static final int HEAVY_SIZE = 64;

    /**
     * A land cruiser. Three and a half tiles across.
     *
     * <p>The Regime's answer to every problem is a larger machine, and the wonder-weapon
     * programmes are where that stops being a joke. So the Resonanzkanone is drawn at the size
     * the fiction implies rather than the size that is convenient: it should dwarf the tank
     * parked beside it, and it should be obvious from across the map that something enormous is
     * coming.
     */
    public static final int SUPERWEAPON_SIZE = 112;

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
        return round(figure(type, faction, facing, frame));
    }

    /**
     * How much the rim is lifted and dropped to give a figure volume.
     *
     * <p>Restrained on purpose. Enough to say which way a shoulder turns, not enough to make
     * every man look like he is standing in a spotlight - and the same numbers for everyone, so
     * the roster is lit by one sun.
     */
    private static final float RIM_LIGHT = 0.16f;
    private static final float RIM_SHADOW = 0.22f;

    private static PixelCanvas round(PixelCanvas c) {
        return c.roundEdges(RIM_LIGHT, RIM_SHADOW);
    }

    private static PixelCanvas figure(UnitType type, Faction faction, int facing, int frame) {
        switch (type) {
            case SCOUT_JEEP:
                return vehicle(jeep(faction, frame), facing, 15, 5);
            case CAPTURED_PANZER:
                return vehicle(panzer(faction, frame), facing, 18, 6);
            case STURMPANZER:
                return vehicle(sturmpanzer(frame), facing, 20, 7);
            case PANZERHUND:
                return vehicle(hound(frame), facing, 15, 5);
            case HARVESTER:
                return vehicle(harvester(faction, frame), facing, 18, 6);
            case FELDKANONE:
                return vehicle(feldkanone(frame), facing, 15, 5);
            case NEBELWERFER:
                return vehicle(nebelwerfer(frame), facing, 22, 8);
            case GASWERFER:
                return vehicle(gaswerfer(frame), facing, 15, 5);
            case GYROCOPTER:
                return aircraft(gyrocopter(frame), facing);
            case LUFTPANZER:
                return aircraft(luftpanzer(frame), facing);
            case AUSMERZER:
                return FineUbersoldat.drawAusmerzer(facing, frame, FINE_SCALE, OUTLINE);
            case RESONANZKANONE:
                return vehicle(resonanzkanone(frame), facing, 40, 15);
            case UBERSOLDAT:
                return FineUbersoldat.draw(facing, frame, FINE_SCALE, OUTLINE);
            default:
                // The doll re-authored at sixty-four to a tile: same design, four times the
                // pixels, and for the first time the man has arms. The coarse recipe below
                // stays as the reference it was refined from.
                return FineInfantry.draw(faction == Faction.REGIME, facing, frame,
                        loadoutFor(type, faction), FINE_SCALE, OUTLINE);
        }
    }

    /**
     * Everything that makes one infantryman look different from another.
     *
     * <p>Six Resistance units sharing one body with a different gun would read as one unit
     * with six weapons. Headgear changes the silhouette, the pack changes the outline from
     * behind, and the crouch changes the height — so they are told apart at a glance even
     * before you notice what they are carrying.
     */
    static final class Loadout {
        final Kit kit;
        final Head head;
        final Pack pack;
        final boolean crouched;
        /** Dressed as a civilian rather than in fatigues — the point of an infiltrator. */
        boolean civilian;
        /** Shade offset into the faction's cloth ramp, so the squad is not uniformly dressed. */
        final int clothShift;

        Loadout(Kit kit, Head head, Pack pack, boolean crouched, int clothShift) {
            this.kit = kit;
            this.head = head;
            this.pack = pack;
            this.crouched = crouched;
            this.clothShift = clothShift;
        }
    }

    private static Loadout loadoutFor(UnitType type, Faction faction) {
        switch (type) {
            case ROCKETEER:
                // Wearing a helmet taken off a dead Soldat: free storytelling.
                return new Loadout(Kit.ROCKET, Head.STOLEN_HELMET, Pack.ROCKET_BAG, false, 0);
            case MARKSMAN:
                return new Loadout(Kit.SNIPER, Head.GHILLIE, Pack.NONE, true, 1);
            case GRENADIER:
                return new Loadout(Kit.GRENADE, Head.BANDANA, Pack.CHARGE_BAG, false, -1);
            case SABOTEUR:
                return new Loadout(Kit.NONE, Head.HOOD, Pack.WIRE_COIL, true, 1);
            case INFILTRATOR: {
                Loadout infiltrator =
                        new Loadout(Kit.NONE, Head.CAP, Pack.SATCHEL_ONLY, true, 1);
                infiltrator.civilian = true;
                return infiltrator;
            }
            case SCHARFSCHUTZE:
                return new Loadout(Kit.SNIPER, Head.COVERED_HELMET, Pack.DRAPE, true, 0);
            case STURMPIONIER:
                return new Loadout(Kit.FLAMER, Head.REGIME_HELMET, Pack.FUEL_TANKS, false, -1);
            case SOLDAT:
                return new Loadout(Kit.SMG, Head.REGIME_HELMET, Pack.NONE, false, 0);
            case FLAMMTRUPP:
                // Firestorm's wave: the Sturmpionier's kit on a lighter uniform, and four of
                // them to a team. The pale grey is the read - a wall of ash-coloured men with
                // tanks on their backs coming out of the smoke.
                return new Loadout(Kit.FLAMER, Head.REGIME_HELMET, Pack.FUEL_TANKS, false, 2);
            case PARTISAN:
            default:
                return new Loadout(Kit.RIFLE, Head.CAP, Pack.BANDOLIER, false, 0);
        }
    }

    /** What an infantryman is carrying. */
    enum Kit { RIFLE, SMG, ROCKET, SNIPER, GRENADE, FLAMER, NONE }

    /** What is on their head — the fastest way to tell two sprites apart from above. */
    enum Head { CAP, BANDANA, STOLEN_HELMET, HOOD, GHILLIE, REGIME_HELMET,
        COVERED_HELMET }

    /** What is on their back, which is what you see when they are walking away. */
    enum Pack { NONE, BANDOLIER, ROCKET_BAG, CHARGE_BAG, WIRE_COIL, FUEL_TANKS, DRAPE,
        SATCHEL_ONLY }

    /**
     * Turns a hull drawn facing east into a finished sprite for one facing.
     *
     * <p>Order matters: rotate the bare hull, then outline it, then lay it over a shadow.
     * Outlining first tears the outline apart; rotating the shadow sends the sun spinning.
     */
    private static PixelCanvas vehicle(PixelCanvas east, int facing, int shadowRx, int shadowRy) {
        // Supersampling the rotation matters much less than it did: the hull is drawn at four
        // times the resolution now, so the stair-stepping the factor exists to hide is already
        // a quarter the size. Two instead of three keeps the bake from spending seconds on
        // temporary buffers of two million pixels a facing.
        int smoothing = east.scale() > 1 ? 1 : 3;
        PixelCanvas hull = facing == 0 ? east
                : east.rotatedSmooth((float) (facing * Math.PI / 4.0), smoothing);
        hull.outline(OUTLINE);

        // Composed at the hull's own scale so the shadow keeps the coordinates it was authored
        // in, then the finished hull is laid over it one real pixel at a time - it has already
        // been rotated at full resolution and must not be planted in blocks a second time.
        PixelCanvas out = new PixelCanvas(east.width(), east.height(), east.scale());
        out.groundShadow(east.width() / 2, east.height() / 2 + 7, shadowRx, shadowRy);
        // Both sides of the copy must speak real pixels. The rotated hulls already do - they
        // come back at scale one - but the facing-east hull skips rotation and arrives still at
        // authoring scale, and blitting its logical grid into a fine view drew every east-facing
        // vehicle at a quarter size in the corner of its cell. In the shipped game, not a sheet.
        out.fine().blit(hull.fine(), 0, 0);
        return out;
    }

    /**
     * The rotate-then-outline path for things that fly.
     *
     * <p>The vehicle composite bakes a ground shadow under the hull, and for an aircraft that
     * is exactly wrong: the shadow belongs on the ground while the airframe belongs above it,
     * and the renderer separates the two with a lift. So this is {@code vehicle()} without the
     * shadow - the renderer draws its own, displaced.
     */
    private static PixelCanvas aircraft(PixelCanvas east, int facing) {
        int smoothing = east.scale() > 1 ? 1 : 3;
        PixelCanvas hull = facing == 0 ? east
                : east.rotatedSmooth((float) (facing * Math.PI / 4.0), smoothing);
        hull.outline(OUTLINE);
        if (facing == 0) {
            return hull;
        }
        PixelCanvas out = new PixelCanvas(east.width(), east.height(), east.scale());
        out.fine().blit(hull.fine(), 0, 0);
        return out;
    }

    /**
     * The salvaged autogyro, facing east: a flying jeep, and it should look like one.
     *
     * <p>Lattice boom, canvas over the engine, a pilot in the open, and the rotor as the
     * animation: two blade angles alternating, over a faint blur ring that sells the spin at
     * one frame a tick.
     */
    private static PixelCanvas gyrocopter(int frame) {
        PixelCanvas c = canvas(VEHICLE_SIZE);
        int cx = 24;
        int cy = 24;
        int[] cloth = WolfPalette.OLIVE;
        int[] frameMetal = WolfPalette.LEATHER;

        // Tail boom, back to the west, with a small vertical fin.
        c.thickLine(cx - 16, cy, cx - 2, cy, 1, WolfPalette.shade(frameMetal, 2));
        c.line(cx - 16, cy - 1, cx - 2, cy - 1, WolfPalette.shade(frameMetal, 0));
        c.rect(cx - 18, cy - 4, 3, 8, WolfPalette.shade(cloth, 2));
        c.vLine(cx - 18, cy - 4, cy + 3, WolfPalette.shade(cloth, 0));
        // Tail rotor disc.
        c.ellipse(cx - 17, cy, 2, 5, WolfPalette.shade(WolfPalette.GUNMETAL, 2));

        // Fuselage pod: stubby, canvas-skinned, engine cowl forward.
        c.panel(cx - 4, cy - 5, 14, 11, cloth, 1);
        c.hLine(cx - 4, cy + 9, cy - 5, WolfPalette.shade(cloth, 0));
        c.hLine(cx - 4, cy + 9, cy + 5, WolfPalette.shade(cloth, 3));
        c.panel(cx + 8, cy - 4, 5, 9, frameMetal, 2);
        c.px(cx + 12, cy - 2, WolfPalette.shade(WolfPalette.GUNMETAL, 0));
        c.px(cx + 12, cy + 2, WolfPalette.shade(WolfPalette.GUNMETAL, 0));
        // The pilot, in the open, and his gun on the rail.
        c.ellipse(cx + 1, cy, 2, 2, WolfPalette.shade(WolfPalette.FLESH, 2));
        c.px(cx + 1, cy - 1, WolfPalette.shade(WolfPalette.LEATHER, 3));
        c.line(cx + 4, cy - 4, cx + 10, cy - 5, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        // The red rag, on the tail where their own AA can read it.
        c.rect(cx - 15, cy - 2, 3, 2, WolfPalette.shade(WolfPalette.BLOOD, 2));

        rotor(c, cx + 2, cy, 15, frame);
        return c;
    }

    /**
     * The Luftpanzer, facing east: a tank given rotors, which is the Regime design bureau in
     * one sentence. Tandem rotors, stub wings with rocket pods, the one red band.
     */
    private static PixelCanvas luftpanzer(int frame) {
        PixelCanvas c = canvas(HEAVY_SIZE);
        int cx = 32;
        int cy = 32;
        int[] plate = WolfPalette.NIGHT;

        // Stub wings and their pods first, under the hull.
        for (int side = -1; side <= 1; side += 2) {
            int wy = cy + side * 10;
            c.panel(cx - 4, wy - 2, 12, 4, plate, 1);
            c.panel(cx + 6, wy - 3, 8, 6, WolfPalette.GUNMETAL, 2);
            for (int t = 0; t < 3; t++) {
                c.px(cx + 13, wy - 2 + t * 2, WolfPalette.shade(WolfPalette.NIGHT, 4));
            }
        }

        // Hull: long, slab-sided, armoured.
        c.panel(cx - 18, cy - 6, 38, 13, plate, 2);
        c.hLine(cx - 18, cx + 19, cy - 6, WolfPalette.shade(plate, 0));
        c.hLine(cx - 18, cx + 19, cy + 6, WolfPalette.shade(plate, 4));
        // Cockpit glass, forward.
        c.panel(cx + 12, cy - 3, 7, 7, WolfPalette.STEEL, 2);
        c.hLine(cx + 12, cx + 18, cy - 3, WolfPalette.shade(WolfPalette.STEEL, 0));
        // Engine spine and exhausts.
        c.hLine(cx - 14, cx + 8, cy, WolfPalette.shade(plate, 3));
        c.px(cx - 10, cy - 5, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        c.px(cx - 4, cy - 5, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        // The band.
        c.vLine(cx - 8, cy - 6, cy + 6, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.vLine(cx - 7, cy - 6, cy + 6, WolfPalette.shade(WolfPalette.BLOOD, 2));

        rotor(c, cx - 10, cy, 13, frame);
        rotor(c, cx + 8, cy, 13, frame + 1);
        return c;
    }

    /**
     * A spinning rotor: a thin tip-path rim, two blades whose angle alternates per frame, and
     * a hub. Nothing filled - the first version laid a blur disc over the whole airframe and
     * both aircraft rendered as dark coins with nothing visibly flying underneath.
     */
    private static void rotor(PixelCanvas c, int cx, int cy, int radius, int frame) {
        boolean diagonal = (frame & 1) == 1;
        int reach = radius - 1;
        int d = (int) (reach * 0.7071f);
        if (diagonal) {
            c.line(cx - d, cy - d, cx + d, cy + d, WolfPalette.shade(WolfPalette.NIGHT, 2));
            c.line(cx - d, cy + d, cx + d, cy - d, WolfPalette.shade(WolfPalette.NIGHT, 2));
        } else {
            c.line(cx - reach, cy, cx + reach, cy, WolfPalette.shade(WolfPalette.NIGHT, 2));
            c.line(cx, cy - reach, cx, cy + reach, WolfPalette.shade(WolfPalette.NIGHT, 2));
        }
        // Blade tips catch the light: four pale pixels are the whole "spinning" read, because
        // they are the only part of the frame pair that visibly trades places.
        int reachTip = radius;
        int dTip = (int) (reachTip * 0.7071f);
        int glint = WolfPalette.shade(WolfPalette.SMOKE, 1);
        if (diagonal) {
            c.px(cx - dTip, cy - dTip, glint);
            c.px(cx + dTip, cy + dTip, glint);
            c.px(cx - dTip, cy + dTip, glint);
            c.px(cx + dTip, cy - dTip, glint);
        } else {
            c.px(cx - reachTip, cy, glint);
            c.px(cx + reachTip, cy, glint);
            c.px(cx, cy - reachTip, glint);
            c.px(cx, cy + reachTip, glint);
        }
        c.ellipse(cx, cy, 2, 2, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        c.px(cx - 1, cy - 1, WolfPalette.shade(WolfPalette.STEEL, 0));
    }

    // --- infantry -------------------------------------------------------------------------

    /**
     * A foot soldier at 32 pixels: boots, legs, coat, webbing, shoulders, head and weapon,
     * each drawn as its own small shape so the figure has parts rather than being one blob.
     */
    private static PixelCanvas infantry(Faction faction, int facing, int frame,
                                        Loadout loadout) {
        PixelCanvas c = canvas(INFANTRY_SIZE);
        boolean regime = faction == Faction.REGIME;

        int[] coat = loadout.civilian ? WolfPalette.LEATHER
                : (regime ? WolfPalette.NIGHT : WolfPalette.OLIVE);
        int[] webbing = regime ? WolfPalette.NIGHT : WolfPalette.LEATHER;
        int[] trousers = regime ? WolfPalette.NIGHT : WolfPalette.LEATHER;
        int cloth = clamp(1 + loadout.clothShift);

        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        boolean toViewer = dy > 0.35f;
        boolean away = dy < -0.35f;
        int step = frame == 1 ? 1 : 0;
        // A crouching figure sits lower and reads shorter, which is most of the silhouette
        // difference between a marksman lying up and a rifleman standing about.
        int drop = loadout.crouched ? 3 : 0;

        c.groundShadow(16, 28, loadout.crouched ? 8 : 7, 3);

        // --- legs and boots ----------------------------------------------------------------
        int bootLight = WolfPalette.shade(WolfPalette.LEATHER, regime ? 3 : 2);
        int bootDark = WolfPalette.shade(WolfPalette.LEATHER, 4);

        c.panel(12, 20 - step + drop, 4, 5 - drop, trousers, 2);
        c.rect(12, 24 - step, 4, 3, bootDark);
        c.hLine(12, 15, 24 - step, bootLight);

        c.panel(17, 20 + step + drop, 4, 5 - drop, trousers, 1);
        c.rect(17, 24 + step, 4, 3, bootDark);
        c.hLine(17, 20, 24 + step, bootLight);

        // --- coat --------------------------------------------------------------------------
        int top = 10 + drop;
        c.panel(11, top, 11, 12 - drop, coat, cloth);
        c.rect(10, top + 8 - drop, 13, 4, WolfPalette.shade(coat, cloth + 1));
        c.hLine(10, 22, top + 8 - drop, WolfPalette.shade(coat, cloth - 1));
        c.hLine(10, 22, top + 11 - drop, WolfPalette.shade(coat, 4));
        c.vLine(16, top + 1, top + 10 - drop, WolfPalette.shade(coat, cloth + 2));
        c.px(16, top + 3, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.px(16, top + 6, WolfPalette.shade(WolfPalette.BRASS, 2));

        // --- shoulders ---------------------------------------------------------------------
        int shoulderWidth = loadout.crouched ? 13 : 15;
        int shoulderX = 16 - shoulderWidth / 2;
        c.panel(shoulderX, top - 1, shoulderWidth, 4, coat, cloth);
        c.hLine(shoulderX, shoulderX + shoulderWidth - 1, top - 1,
                WolfPalette.shade(coat, cloth - 1));

        // --- belt and pouches ---------------------------------------------------------------
        c.hLine(11, 21, top + 8 - drop, WolfPalette.shade(webbing, 3));
        c.rect(11, top + 8 - drop, 3, 3, WolfPalette.shade(webbing, 2));
        c.rect(19, top + 8 - drop, 3, 3, WolfPalette.shade(webbing, 2));
        c.px(16, top + 8 - drop, WolfPalette.shade(WolfPalette.BRASS, 1));

        drawPack(c, loadout.pack, top, away, webbing);

        if (regime) {
            regimeKit(c, top, coat);
        } else if (loadout.pack == Pack.BANDOLIER) {
            resistanceKit(c, top, toViewer);
        } else {
            // Everyone in the cell wears the rag; only the riflemen wear the bandolier too.
            c.rect(9, top + 3, 3, 3, WolfPalette.shade(WolfPalette.BLOOD, 2));
            c.px(9, top + 3, WolfPalette.shade(WolfPalette.BLOOD, 1));
        }

        drawHead(c, faction, loadout.head, top, toViewer, away);
        drawWeapon(c, loadout.kit, dx, dy, top, regime);

        c.outline(OUTLINE);
        return c;
    }

    private static int clamp(int shade) {
        return Math.max(0, Math.min(3, shade));
    }

    /** What is slung on the back: the outline you see when a unit walks away from you. */
    private static void drawPack(PixelCanvas c, Pack pack, int top, boolean away, int[] webbing) {
        switch (pack) {
            case ROCKET_BAG:
                // Spare rockets in a rack, nose-up.
                c.panel(19, top + 1, 6, 8, webbing, 2);
                for (int i = 0; i < 2; i++) {
                    c.vLine(20 + i * 2, top, top + 3, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    c.px(20 + i * 2, top - 1, WolfPalette.shade(WolfPalette.BLOOD, 1));
                }
                break;
            case CHARGE_BAG:
                // A satchel of bundled charges, fuses showing.
                c.panel(19, top + 5, 7, 7, webbing, 1);
                c.hLine(19, 25, top + 8, WolfPalette.shade(webbing, 4));
                c.px(21, top + 4, WolfPalette.shade(WolfPalette.BONE, 2));
                c.px(23, top + 4, WolfPalette.shade(WolfPalette.BONE, 2));
                break;
            case WIRE_COIL:
                // A coil of det cord over one shoulder.
                c.ellipse(21, top + 4, 4, 4, WolfPalette.shade(WolfPalette.LEATHER, 3));
                c.ellipse(21, top + 4, 3, 3, WolfPalette.shade(WolfPalette.LEATHER, 1));
                c.ellipse(21, top + 4, 1, 1, WolfPalette.shade(WolfPalette.LEATHER, 4));
                break;
            case FUEL_TANKS:
                // Twin pressure bottles, the reason nobody stands behind a Sturmpionier.
                c.panel(18, top, 4, 10, WolfPalette.GUNMETAL, 2);
                c.panel(22, top + 1, 4, 9, WolfPalette.GUNMETAL, 3);
                c.hLine(18, 21, top, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.hLine(22, 25, top + 1, WolfPalette.shade(WolfPalette.BLOOD, 1));
                break;
            case DRAPE:
                // A shooter's cloth drape hanging off the shoulders.
                c.rect(9, top + 2, 14, 9, WolfPalette.shade(WolfPalette.NIGHT, 1));
                c.hLine(9, 22, top + 2, WolfPalette.shade(WolfPalette.NIGHT, 0));
                for (int x = 10; x < 23; x += 3) {
                    c.px(x, top + 11, WolfPalette.shade(WolfPalette.NIGHT, 3));
                }
                break;
            case SATCHEL_ONLY:
                c.panel(20, top + 6, 5, 6, webbing, 2);
                break;
            case BANDOLIER:
            case NONE:
            default:
                break;
        }

        if (away && pack != Pack.DRAPE && pack != Pack.FUEL_TANKS) {
            // Seen from behind, everyone carries a rolled blanket.
            c.rect(11, top, 11, 3, WolfPalette.shade(webbing, 1));
            c.hLine(11, 21, top, WolfPalette.shade(webbing, 0));
        }
    }

    /** Black plate, a red armband and a stencilled number    /** Black plate, a red armband and a stencilled number: the Regime's whole visual identity. */
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
     * Heads. This is where most of the variety lives: from directly above, headgear is the
     * clearest difference between two figures of the same build.
     */
    private static void drawHead(PixelCanvas c, Faction faction, Head head, int top,
                                 boolean toViewer, boolean away) {
        int headY = top - 8;
        boolean regimeHelmet = head == Head.REGIME_HELMET || head == Head.COVERED_HELMET
                || head == Head.STOLEN_HELMET;

        // Neck.
        c.rect(15, headY + 6, 3, 3, WolfPalette.shade(
                regimeHelmet && faction == Faction.REGIME ? WolfPalette.NIGHT
                        : WolfPalette.FLESH, 3));

        // Face, for anyone not behind a mask.
        boolean masked = head == Head.REGIME_HELMET && faction == Faction.REGIME;
        if (!away && !masked) {
            c.ellipse(16, headY + 5, 4, 4, WolfPalette.shade(WolfPalette.FLESH, 2));
            c.hLine(13, 19, headY + 7, WolfPalette.shade(WolfPalette.FLESH, 3));
            if (toViewer) {
                c.px(14, headY + 5, WolfPalette.shade(WolfPalette.NIGHT, 1));
                c.px(18, headY + 5, WolfPalette.shade(WolfPalette.NIGHT, 1));
            }
        }

        switch (head) {
            case REGIME_HELMET: {
                int[] lacquer = WolfPalette.NIGHT;
                c.ellipse(16, headY + 4, 5, 5, WolfPalette.shade(lacquer, 3));
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
                }
                break;
            }
            case STOLEN_HELMET: {
                // Regime steel on a Resistance head, with the insignia scratched off and a
                // strip of rag tied round it so their own side does not shoot them.
                int[] steel = WolfPalette.GUNMETAL;
                c.ellipse(16, headY + 3, 6, 5, WolfPalette.shade(steel, 3));
                c.ellipse(15, headY + 2, 5, 4, WolfPalette.shade(steel, 2));
                c.ellipse(15, headY + 1, 3, 2, WolfPalette.shade(steel, 1));
                c.hLine(9, 23, headY + 7, WolfPalette.shade(steel, 4));
                c.hLine(10, 22, headY + 2, WolfPalette.shade(WolfPalette.BLOOD, 2));
                c.px(10, headY + 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
                break;
            }
            case COVERED_HELMET: {
                // Helmet under a cloth cover, edges broken up with scrim.
                int[] cover = WolfPalette.NIGHT;
                c.ellipse(16, headY + 3, 6, 5, WolfPalette.shade(cover, 2));
                c.ellipse(15, headY + 2, 5, 4, WolfPalette.shade(cover, 1));
                c.hLine(9, 23, headY + 7, WolfPalette.shade(cover, 3));
                for (int i = 0; i < 4; i++) {
                    c.px(11 + i * 3, headY + 8, WolfPalette.shade(WolfPalette.OLIVE, 2));
                }
                break;
            }
            case GHILLIE: {
                // A ragged wrap: irregular fringe rather than a clean brim.
                int[] rag = WolfPalette.OLIVE;
                c.ellipse(16, headY + 4, 7, 5, WolfPalette.shade(rag, 3));
                c.ellipse(15, headY + 3, 6, 4, WolfPalette.shade(rag, 2));
                for (int i = 0; i < 7; i++) {
                    int x = 10 + i * 2;
                    c.vLine(x, headY + 7, headY + 8 + (i % 3), WolfPalette.shade(rag, 4));
                    c.px(x, headY + 6, WolfPalette.shade(rag, 1));
                }
                break;
            }
            case HOOD: {
                // A hood pulled up: tall at the back, shadowed where the face should be.
                int[] cloth = WolfPalette.LEATHER;
                c.ellipse(16, headY + 3, 6, 6, WolfPalette.shade(cloth, 3));
                c.ellipse(16, headY + 2, 5, 5, WolfPalette.shade(cloth, 2));
                c.rect(13, headY + 4, 7, 4, WolfPalette.shade(cloth, 4));
                c.hLine(13, 19, headY + 4, WolfPalette.shade(cloth, 1));
                if (toViewer) {
                    c.px(14, headY + 6, WolfPalette.shade(WolfPalette.FLESH, 3));
                    c.px(18, headY + 6, WolfPalette.shade(WolfPalette.FLESH, 3));
                }
                break;
            }
            case BANDANA: {
                // Bare head, dark hair, a rag round the brow and goggles pushed up.
                int[] hair = WolfPalette.LEATHER;
                c.ellipse(16, headY + 3, 5, 4, WolfPalette.shade(hair, 4));
                c.hLine(11, 21, headY + 3, WolfPalette.shade(WolfPalette.BLOOD, 2));
                c.hLine(11, 21, headY + 4, WolfPalette.shade(WolfPalette.BLOOD, 3));
                c.px(11, headY + 5, WolfPalette.shade(WolfPalette.BLOOD, 3));
                c.hLine(12, 20, headY + 1, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                c.px(13, headY + 1, WolfPalette.shade(WolfPalette.STEEL, 1));
                c.px(19, headY + 1, WolfPalette.shade(WolfPalette.STEEL, 1));
                break;
            }
            case CAP:
            default: {
                int[] wool = WolfPalette.LEATHER;
                c.ellipse(16, headY + 2, 6, 4, WolfPalette.shade(wool, 2));
                c.ellipse(15, headY + 1, 5, 3, WolfPalette.shade(wool, 1));
                c.hLine(11, 21, headY + 4, WolfPalette.shade(wool, 3));
                c.hLine(12, 20, headY + 5, WolfPalette.shade(wool, 4));
                c.px(11, headY + 2, WolfPalette.shade(wool, 0));
                break;
            }
        }
    }

    /** Weapons are drawn from the hand outwards    /** Weapons are drawn from the hand outwards, so they always point where the unit looks. */
    private static void drawWeapon(PixelCanvas c, Kit kit, float dx, float dy, int top,
                                   boolean regime) {
        int handX = 16;
        int handY = top + 5;
        int[] metal = WolfPalette.GUNMETAL;
        int[] stock = WolfPalette.LEATHER;

        switch (kit) {
            case ROCKET: {
                // A Panzerschreck: a thin grey tube on the shoulder, a flared venturi at the
                // back, a dark bulged warhead at the front and a small square blast shield.
                // Two earlier passes failed here — first a fat even-width bar that read as a
                // toy blaster, then a scarlet warhead that read as a red stripe across the
                // whole figure. The tube is two pixels wide and the only red is one band.
                float perpX = -dy;
                float perpY = dx;
                int shoulderX = Math.round(handX - dx * 3f);
                int shoulderY = Math.round(handY - dy * 3f) - 2;
                int tipX = Math.round(shoulderX + dx * 17f);
                int tipY = Math.round(shoulderY + dy * 17f);
                int backX = Math.round(shoulderX - dx * 6f);
                int backY = Math.round(shoulderY - dy * 6f);

                // Tube: exactly two pixels across, lit on one side.
                c.line(backX, backY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(Math.round(backX + perpX), Math.round(backY + perpY),
                        Math.round(tipX + perpX), Math.round(tipY + perpY),
                        WolfPalette.shade(metal, 3));

                // Flared venturi at the tail: a short wedge, wider than the tube.
                c.thickLine(backX, backY, Math.round(backX - dx * 2f),
                        Math.round(backY - dy * 2f), 2, WolfPalette.shade(metal, 3));
                c.px(Math.round(backX - dx * 2f), Math.round(backY - dy * 2f),
                        WolfPalette.shade(metal, 4));

                // Warhead: a dark bulge with one red band round it.
                int warheadX = Math.round(shoulderX + dx * 14f);
                int warheadY = Math.round(shoulderY + dy * 14f);
                c.thickLine(warheadX, warheadY, tipX, tipY, 1, WolfPalette.shade(metal, 3));
                c.px(warheadX, warheadY, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.px(Math.round(warheadX + perpX), Math.round(warheadY + perpY),
                        WolfPalette.shade(WolfPalette.BLOOD, 2));
                c.px(tipX, tipY, WolfPalette.shade(WolfPalette.NIGHT, 0));

                // Blast shield, square and small, mid-tube.
                int shieldX = Math.round(shoulderX + dx * 5f);
                int shieldY = Math.round(shoulderY + dy * 5f);
                c.rect(shieldX - 2, shieldY - 4, 4, 4, WolfPalette.shade(metal, 2));
                c.hLine(shieldX - 2, shieldX + 1, shieldY - 4, WolfPalette.shade(metal, 0));
                c.px(shieldX, shieldY - 2, WolfPalette.shade(metal, 4));
                break;
            }
            case SNIPER: {
                // Long barrel, wooden furniture, and a scope standing proud of the receiver.
                int tipX = Math.round(handX + dx * 16f);
                int tipY = Math.round(handY + dy * 16f);
                int buttX = Math.round(handX - dx * 6f);
                int buttY = Math.round(handY - dy * 6f);
                c.line(buttX, buttY, handX, handY, WolfPalette.shade(stock, 1));
                c.line(buttX, buttY + 1, handX, handY + 1, WolfPalette.shade(stock, 3));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                int scopeX = Math.round(handX + dx * 3f);
                int scopeY = Math.round(handY + dy * 3f) - 2;
                c.thickLine(scopeX, scopeY, Math.round(scopeX + dx * 5f),
                        Math.round(scopeY + dy * 5f), 1, WolfPalette.shade(metal, 0));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                break;
            }
            case GRENADE: {
                // A bundled charge, held back ready to throw: a stick with a head on it.
                int throwX = Math.round(handX + dx * 6f);
                int throwY = Math.round(handY + dy * 6f) - 3;
                c.line(handX, handY - 1, throwX, throwY, WolfPalette.shade(stock, 2));
                c.ellipse(throwX, throwY, 2, 2, WolfPalette.shade(metal, 2));
                c.px(throwX, throwY - 2, WolfPalette.shade(WolfPalette.BONE, 2));
                // Spare charges on the belt.
                c.px(13, top + 9, WolfPalette.shade(metal, 2));
                c.px(19, top + 9, WolfPalette.shade(metal, 2));
                break;
            }
            case FLAMER: {
                // A wand on a hose, with a pilot light burning at the tip.
                int tipX = Math.round(handX + dx * 11f);
                int tipY = Math.round(handY + dy * 11f);
                c.thickLine(handX, handY, tipX, tipY, 1, WolfPalette.shade(metal, 2));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.rect(tipX - 1, tipY - 1, 3, 3, WolfPalette.shade(metal, 3));
                c.px(tipX + 1, tipY, WolfPalette.shade(WolfPalette.FIRE, 0));
                c.px(tipX + 1, tipY - 1, WolfPalette.shade(WolfPalette.FIRE, 2));
                // Hose looping back to the tanks.
                c.line(handX - 2, handY + 2, 20, top + 4, WolfPalette.shade(metal, 3));
                break;
            }
            case NONE: {
                // No weapon: wire cutters in one hand, held low.
                int toolX = Math.round(handX + dx * 5f);
                int toolY = Math.round(handY + dy * 5f);
                c.line(handX, handY, toolX, toolY, WolfPalette.shade(metal, 2));
                c.px(toolX, toolY, WolfPalette.shade(metal, 0));
                c.px(toolX, toolY + 1, WolfPalette.shade(metal, 0));
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
     * The Ubersoldat: a hunched steel golem, top-heavy and hulking.
     *
     * <p>Three things carry the design, and the first pass at it had none of them. The
     * silhouette is widest and tallest at the pauldrons, with a small head sunk between them —
     * not a head-sized box perched on a torso-sized box. Every plate group is separated by a
     * near-black recess, so it reads as armour bolted over a machine rather than one grey
     * mass. And the plates sit in the middle of the steel ramp with highlights only on their
     * top edges; lit from the pale end of the ramp it glowed like white plastic.
     */
    private static PixelCanvas ubersoldat(int facing, int frame) {
        PixelCanvas c = canvas(INFANTRY_SIZE);
        int[] plate = WolfPalette.STEEL;
        int[] shade = WolfPalette.NIGHT;
        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        float perpX = -dy;
        float perpY = dx;
        boolean toViewer = dy > 0.3f;
        int step = frame == 1 ? 1 : 0;

        c.groundShadow(16, 30, 11, 3);

        // --- legs: dark hip joint, thick thigh, armoured shin, splayed foot ---------------
        drawGolemLeg(c, 11, 17, -step, plate, shade);
        drawGolemLeg(c, 21, 17, step, plate, shade);

        // --- waist: a narrow band of exposed machinery ------------------------------------
        c.rect(12, 15, 9, 4, WolfPalette.shade(shade, 2));
        for (int x = 12; x < 21; x += 2) {
            c.vLine(x, 15, 18, WolfPalette.shade(shade, 1));
        }

        // --- torso: a barrel, built row by row so it has a chest and a waist ---------------
        int[] rowLeft  = {11, 10,  9,  9,  9, 10, 10, 11, 12};
        int[] rowRight = {21, 22, 23, 23, 23, 22, 22, 21, 20};
        for (int i = 0; i < rowLeft.length; i++) {
            int y = 6 + i;
            int fill = i < 3 ? 1 : (i < 6 ? 2 : 3);
            c.hLine(rowLeft[i], rowRight[i], y, WolfPalette.shade(plate, fill));
            c.px(rowLeft[i], y, WolfPalette.shade(plate, 0));
            c.px(rowRight[i], y, WolfPalette.shade(plate, 4));
        }
        // Sternum ridge, raised and catching the light.
        c.vLine(16, 7, 13, WolfPalette.shade(plate, 0));
        c.vLine(17, 7, 13, WolfPalette.shade(plate, 3));
        // Chest plate seams, and four bolt heads at the corners of the breastplate.
        c.hLine(11, 21, 11, WolfPalette.shade(shade, 2));
        c.hLine(10, 22, 14, WolfPalette.shade(shade, 2));
        c.px(12, 8, WolfPalette.shade(plate, 0));
        c.px(20, 8, WolfPalette.shade(plate, 0));
        c.px(12, 13, WolfPalette.shade(plate, 0));
        c.px(20, 13, WolfPalette.shade(plate, 0));
        // A wash of grime along the bottom edge only, where it would actually collect.
        c.hLine(10, 22, 15, WolfPalette.shade(shade, 1));

        // --- pauldrons: the widest, tallest thing on the model ----------------------------
        drawPauldron(c, 6, 11, plate, shade, false);
        drawPauldron(c, 26, 11, plate, shade, true);

        // --- head: small, sunk between the pauldrons --------------------------------------
        // Neck recess first, so the head reads as set into the shoulders.
        c.rect(13, 7, 7, 3, WolfPalette.shade(shade, 3));
        c.rect(12, 1, 9, 8, WolfPalette.shade(plate, 3));
        c.rect(13, 2, 7, 6, WolfPalette.shade(plate, 2));
        c.hLine(12, 20, 1, WolfPalette.shade(plate, 1));
        c.vLine(12, 1, 8, WolfPalette.shade(plate, 2));
        c.vLine(20, 1, 8, WolfPalette.shade(plate, 4));
        // Bolts holding the face plate on.
        c.px(13, 2, WolfPalette.shade(plate, 0));
        c.px(19, 2, WolfPalette.shade(plate, 0));
        c.px(13, 7, WolfPalette.shade(plate, 0));
        c.px(19, 7, WolfPalette.shade(plate, 0));
        // Centre seam down the mask.
        c.vLine(16, 3, 7, WolfPalette.shade(shade, 2));

        if (toViewer) {
            // Thin optic slits rather than red squares, and a jaw grille under them.
            c.hLine(14, 15, 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.hLine(17, 18, 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.px(14, 4, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.px(18, 4, WolfPalette.shade(WolfPalette.BLOOD, 0));
            c.hLine(14, 18, 6, WolfPalette.shade(shade, 4));
            c.px(15, 6, WolfPalette.shade(shade, 1));
            c.px(17, 6, WolfPalette.shade(shade, 1));
        } else {
            // From behind: the back of the skull cap and its cable loom.
            c.hLine(14, 18, 5, WolfPalette.shade(plate, 3));
            c.px(16, 8, WolfPalette.shade(shade, 1));
        }

        // --- arms: a heavy fist on one side, the cannon on the other ----------------------
        int shoulderX = Math.round(16 + perpX * 7f);
        int shoulderY = Math.round(12 + perpY * 7f);
        int fistX = Math.round(shoulderX + dx * 5f);
        int fistY = Math.round(shoulderY + dy * 5f);
        c.thickLine(shoulderX, shoulderY, fistX, fistY, 2, WolfPalette.shade(plate, 2));
        c.thickLine(shoulderX, shoulderY, fistX, fistY, 1, WolfPalette.shade(plate, 1));
        c.ellipse(fistX, fistY, 3, 3, WolfPalette.shade(plate, 2));
        c.ellipse(fistX, fistY, 2, 2, WolfPalette.shade(shade, 1));

        drawArmCannon(c, dx, dy, perpX, perpY, plate, shade);

        c.outline(OUTLINE);
        return c;
    }

    /** A pauldron: a rounded mass sitting above the shoulder with a dark recess beneath it. */
    private static void drawPauldron(PixelCanvas c, int cx, int cy, int[] plate, int[] shade,
                                     boolean red) {
        // Recess under the plate, so it does not merge into the torso.
        c.ellipse(cx, cy + 3, 6, 4, WolfPalette.shade(shade, 2));
        c.ellipse(cx, cy, 6, 5, WolfPalette.shade(plate, 3));
        c.ellipse(cx, cy - 1, 6, 4, WolfPalette.shade(plate, 2));
        c.ellipse(cx - 1, cy - 2, 4, 2, WolfPalette.shade(plate, 1));
        // Lip along the top edge, the only bright line on the plate.
        c.hLine(cx - 4, cx + 3, cy - 5, WolfPalette.shade(plate, 0));
        // Ribs.
        c.line(cx - 5, cy, cx + 5, cy, WolfPalette.shade(shade, 1));
        c.line(cx - 4, cy + 3, cx + 4, cy + 3, WolfPalette.shade(shade, 1));
        c.px(cx - 3, cy - 2, WolfPalette.shade(plate, 0));
        c.px(cx + 3, cy - 2, WolfPalette.shade(plate, 0));
        c.px(cx, cy + 2, WolfPalette.shade(plate, 0));

        if (red) {
            // A painted band along the pauldron edge — the only colour on the machine.
            c.hLine(cx - 4, cx + 2, cy - 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
            c.px(cx - 4, cy - 4, WolfPalette.shade(WolfPalette.BLOOD, 0));
        }
    }

    /** Thigh, knee, shin and a splayed foot with a toe cap. */
    private static void drawGolemLeg(PixelCanvas c, int hipX, int hipY, int step, int[] plate,
                                     int[] shade) {
        // Hip joint: a dark gap between torso and leg.
        c.ellipse(hipX, hipY + step, 3, 3, WolfPalette.shade(shade, 2));
        // Thigh.
        c.rect(hipX - 3, hipY + 1 + step, 7, 5, WolfPalette.shade(plate, 3));
        c.hLine(hipX - 3, hipX + 3, hipY + 1 + step, WolfPalette.shade(plate, 1));
        // Knee.
        c.rect(hipX - 3, hipY + 6 + step, 7, 2, WolfPalette.shade(shade, 2));
        // Shin plate.
        c.rect(hipX - 4, hipY + 8 + step, 8, 6, WolfPalette.shade(plate, 2));
        c.hLine(hipX - 4, hipX + 3, hipY + 8 + step, WolfPalette.shade(plate, 0));
        c.vLine(hipX + 3, hipY + 8 + step, hipY + 13 + step, WolfPalette.shade(plate, 4));
        c.px(hipX - 2, hipY + 10 + step, WolfPalette.shade(plate, 0));
        c.px(hipX + 1, hipY + 10 + step, WolfPalette.shade(plate, 0));
        // Foot: wider than the shin, with a lit toe cap.
        c.rect(hipX - 5, hipY + 12 + step, 10, 3, WolfPalette.shade(plate, 3));
        c.hLine(hipX - 5, hipX + 4, hipY + 12 + step, WolfPalette.shade(plate, 1));
        c.hLine(hipX - 5, hipX + 4, hipY + 14 + step, WolfPalette.shade(shade, 3));
    }

    /** A multi-segment arm cannon: upper arm, housing, drum magazine, barrel and muzzle. */
    private static void drawArmCannon(PixelCanvas c, float dx, float dy, float perpX,
                                      float perpY, int[] plate, int[] shade) {
        int rootX = Math.round(16 - perpX * 7f);
        int rootY = Math.round(12 - perpY * 7f);

        int elbowX = Math.round(rootX + dx * 4f);
        int elbowY = Math.round(rootY + dy * 4f);
        int housingX = Math.round(rootX + dx * 9f);
        int housingY = Math.round(rootY + dy * 9f);
        int muzzleX = Math.round(rootX + dx * 16f);
        int muzzleY = Math.round(rootY + dy * 16f);

        // Upper arm.
        c.thickLine(rootX, rootY, elbowX, elbowY, 3, WolfPalette.shade(shade, 2));
        c.thickLine(rootX, rootY, elbowX, elbowY, 2, WolfPalette.shade(plate, 2));
        // Housing: the fattest part, where the mechanism lives.
        c.thickLine(elbowX, elbowY, housingX, housingY, 4, WolfPalette.shade(plate, 3));
        c.thickLine(elbowX, elbowY, housingX, housingY, 3, WolfPalette.shade(plate, 2));
        c.thickLine(elbowX, elbowY, housingX, housingY, 1, WolfPalette.shade(plate, 1));
        // Drum magazine hanging off the housing.
        int drumX = Math.round(housingX - perpX * 4f);
        int drumY = Math.round(housingY - perpY * 4f);
        c.ellipse(drumX, drumY, 3, 3, WolfPalette.shade(shade, 2));
        c.ellipse(drumX, drumY, 2, 2, WolfPalette.shade(plate, 3));
        c.px(drumX, drumY, WolfPalette.shade(WolfPalette.BLOOD, 1));
        // Barrel and a flared muzzle.
        c.thickLine(housingX, housingY, muzzleX, muzzleY, 2, WolfPalette.shade(shade, 1));
        c.thickLine(housingX, housingY, muzzleX, muzzleY, 1, WolfPalette.shade(plate, 3));
        c.ellipse(muzzleX, muzzleY, 2, 2, WolfPalette.shade(shade, 0));
        c.px(muzzleX, muzzleY, WolfPalette.shade(shade, 3));
    }

    // --- vehicles (drawn facing east, rotated at bake time) --------------------------------

    /**
     * Scout jeep: open-topped, four treaded wheels, a windscreen frame, two seats, a pintle
     * gun on a ring mount and a spare wheel on the back.
     */
    /**
     * How wide a type's sprite is authored, in tiles.
     *
     * <p>The renderer used to ask {@code isVehicle()} for this, which happened to be right
     * while the only oversized sprites belonged to vehicles. The guns broke that: they are
     * crew-served infantry as far as the simulation is concerned — suppressible, able to take
     * cover, able to dig in — but they are drawn through the rotate path at vehicle size, and a
     * renderer keyed on {@code isVehicle()} would have shrunk them by a third. Ask the art how
     * big the art is.
     */
    public static float boxTiles(UnitType type) {
        switch (type) {
            case RESONANZKANONE:
                return SUPERWEAPON_SIZE / (float) TILE;
            case NEBELWERFER:
                return HEAVY_SIZE / (float) TILE;
            case AUSMERZER:
                return HEAVY_SIZE / (float) TILE;
            case FELDKANONE:
            case GASWERFER:
            case GYROCOPTER:
                return VEHICLE_SIZE / (float) TILE;
            case LUFTPANZER:
                return HEAVY_SIZE / (float) TILE;
            default:
                return type.isVehicle() ? VEHICLE_SIZE / (float) TILE : 1f;
        }
    }

    /**
     * A Regime field gun on a Resistance farm cart, and the mismatch is the design.
     *
     * <p>Grey barrel, grey shield, and everything holding them up is timber and canvas with a
     * hand-painted mark over whatever was stencilled there before. Nothing about the two halves
     * agrees, which is the whole story of how the Kreisau Circle came to own artillery.
     */
    /**
     * The Gaswerfer-40: a pressure cylinder on a carriage, and a stubby projector.
     *
     * <p>Everything about the silhouette says "tank of something you do not want": the cylinder
     * is the biggest single shape, it carries the warning band, and the projector is almost an
     * afterthought - which is the truth of the weapon, since the shell is nothing and the cloud
     * is everything.
     */
    private static PixelCanvas gaswerfer(int frame) {
        PixelCanvas c = canvas(VEHICLE_SIZE);
        int[] metal = WolfPalette.GUNMETAL;
        int[] night = WolfPalette.NIGHT;
        int cx = 24;
        int cy = 24;
        int recoil = frame == 1 ? 1 : 0;

        // Carriage: a steel bed on two road wheels.
        for (int side = -1; side <= 1; side += 2) {
            int wy = cy + side * 9;
            c.ellipse(cx - 2, wy, 6, 6, WolfPalette.shade(night, 3));
            c.ellipse(cx - 2, wy, 4, 4, WolfPalette.shade(night, 1));
            c.ellipse(cx - 2, wy, 1, 1, WolfPalette.shade(metal, 0));
        }
        c.panel(cx - 12, cy - 7, 20, 15, night, 2);
        c.hLine(cx - 12, cx + 7, cy - 7, WolfPalette.shade(night, 0));

        // The cylinder, lying across the bed: the unit's whole identity.
        c.panel(cx - 10, cy - 5, 16, 11, metal, 2);
        c.hLine(cx - 10, cx + 5, cy - 5, WolfPalette.shade(metal, 0));
        c.hLine(cx - 10, cx + 5, cy + 5, WolfPalette.shade(metal, 4));
        c.ellipse(cx - 10, cy, 3, 5, WolfPalette.shade(metal, 3));
        c.ellipse(cx + 5, cy, 3, 5, WolfPalette.shade(metal, 1));
        // The warning band, and a valve wheel.
        c.vLine(cx - 3, cy - 5, cy + 5, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.vLine(cx - 2, cy - 5, cy + 5, WolfPalette.shade(WolfPalette.BLOOD, 2));
        c.ellipse(cx - 8, cy, 2, 2, WolfPalette.shade(WolfPalette.BRASS, 1));

        // The projector: short, fat, angled up off the front of the bed.
        c.thickLine(cx + 4, cy - recoil, cx + 16 - recoil, cy - recoil, 2,
                WolfPalette.shade(metal, 2));
        c.line(cx + 4, cy - 1 - recoil, cx + 16 - recoil, cy - 1 - recoil,
                WolfPalette.shade(metal, 0));
        c.ellipse(cx + 16 - recoil, cy - recoil, 3, 3, WolfPalette.shade(metal, 3));
        c.ellipse(cx + 16 - recoil, cy - recoil, 1, 1, WolfPalette.shade(night, 4));
        // Hose from cylinder to breech.
        c.line(cx + 3, cy + 3, cx + 8, cy + 1, WolfPalette.shade(night, 1));

        return c;
    }

    private static PixelCanvas feldkanone(int frame) {
        PixelCanvas c = canvas(VEHICLE_SIZE);
        int[] metal = WolfPalette.GUNMETAL;
        int[] steel = WolfPalette.STEEL;
        int[] timber = WolfPalette.LEATHER;
        int[] cloth = WolfPalette.OLIVE;
        int cx = 24;
        int cy = 24;
        int recoil = frame == 1 ? 2 : 0;

        // --- cart wheels: spoked timber, far too agricultural for the gun on top ----------
        for (int side = -1; side <= 1; side += 2) {
            int wy = cy + side * 9;
            c.ellipse(cx - 4, wy, 7, 7, WolfPalette.shade(timber, 3));
            c.ellipse(cx - 4, wy, 5, 5, WolfPalette.shade(timber, 2));
            c.ellipse(cx - 4, wy, 2, 2, WolfPalette.shade(metal, 3));
            for (int spoke = 0; spoke < 4; spoke++) {
                int dx = spoke < 2 ? (spoke == 0 ? -5 : 5) : 0;
                int dy = spoke < 2 ? 0 : (spoke == 2 ? -5 : 5);
                c.line(cx - 4, wy, cx - 4 + dx, wy + dy, WolfPalette.shade(timber, 1));
            }
        }

        // --- trail: two split legs dragging back off the cart bed -------------------------
        c.line(cx - 6, cy - 3, cx - 18, cy - 8, WolfPalette.shade(timber, 2));
        c.line(cx - 6, cy + 3, cx - 18, cy + 8, WolfPalette.shade(timber, 2));
        c.rect(cx - 19, cy - 9, 3, 3, WolfPalette.shade(metal, 3));
        c.rect(cx - 19, cy + 7, 3, 3, WolfPalette.shade(metal, 3));
        // Lashed-down canvas over the ready rounds.
        deck(c, cx - 14, cy - 4, 8, 9, cloth, 2);
        c.hLine(cx - 14, cx - 7, cy, WolfPalette.shade(timber, 1));

        // --- shield: sheet steel, chipped, with a hand-painted mark -----------------------
        c.rect(cx - 3, cy - 12, 6, 24, WolfPalette.shade(steel, 2));
        c.rampVertical(cx - 3, cy - 12, 6, 24, steel, 1, 3);
        c.vLine(cx - 3, cy - 12, cy + 11, WolfPalette.shade(steel, 0));
        c.vLine(cx + 2, cy - 12, cy + 11, WolfPalette.shade(steel, 4));
        // Rolled top and bottom edges, so the plate reads as a plate and not as the barrel.
        c.hLine(cx - 3, cx + 2, cy - 12, WolfPalette.shade(steel, 0));
        c.hLine(cx - 3, cx + 2, cy + 11, WolfPalette.shade(steel, 4));
        c.speckle(cx - 2, cy - 11, 4, 22, WolfPalette.shade(steel, 4), 71, 9);
        // The mark: a rough painted ring, put on over somebody else's stencil.
        c.ellipse(cx - 1, cy - 6, 3, 3, WolfPalette.shade(cloth, 0));
        c.ellipse(cx - 1, cy - 6, 2, 2, WolfPalette.shade(steel, 2));

        // --- breech and barrel, recoiling between frames ----------------------------------
        // The shield stands proud of the cart, so it throws onto it before the gun goes on.
        contactShadow(c, cx - 3, cy - 12, 6, 24, timber);
        c.panel(cx + 1, cy - 4, 7, 8, metal, 2);
        tube(c, cx + 7 - recoil, cx + 22 - recoil, cy, 2, metal);
        c.rect(cx + 20 - recoil, cy - 3, 3, 6, WolfPalette.shade(metal, 3));
        c.hLine(cx + 20 - recoil, cx + 22 - recoil, cy - 3, WolfPalette.shade(metal, 1));

        // --- crew: two men, so that a hit on this reads as men being hit ------------------
        crewman(c, cx - 8, cy - 11, cloth);
        crewman(c, cx - 9, cy + 8, cloth);
        return c;
    }

    /**
     * A launcher truck: a cab, a flatbed, and four tubes pointing off the end of it.
     *
     * <p>1970s factory work, and squared off everywhere the Feldkanone is not. Drawn at
     * {@link #HEAVY_SIZE} — twice a man's frontage, so it reads as something that needs a road.
     *
     * <p>The first version of this looked like a robot vacuum cleaner, for three reasons worth
     * writing down because they are all easy to repeat. Its eight tubes were graduated in
     * length, which rounded the front edge into a lozenge. The hazard striping ran all the way
     * round the hull, which turned that lozenge into a bumper. And the tubes were three pixels
     * thick with one pixel between them, which at any distance is not a rack of tubes, it is a
     * brush roller.
     *
     * <p>So: four tubes, all the same length, thick enough to be told apart, protruding well
     * clear of the bed so their mouths line up in open air. Square corners, wheels rather than
     * full-width tracks — the Resonanzkanone is the tracked one — and striping only on the cab.
     * Four is also what the weapon's salvo actually is, so the sprite says what the gun does.
     */
    private static PixelCanvas nebelwerfer(int frame) {
        PixelCanvas c = canvas(HEAVY_SIZE);
        int[] body = WolfPalette.NIGHT;
        int[] metal = WolfPalette.GUNMETAL;
        int[] brass = WolfPalette.BRASS;
        int cy = 32;
        int lit = frame == 1 ? 1 : 0;

        int cabLeft = 3;
        int cabRight = 18;
        int bedRight = 44;
        int tubeLeft = 20;
        int tubeRight = 57;

        // --- road wheels, three pairs under the bed ---------------------------------------
        for (int side = -1; side <= 1; side += 2) {
            int wy = cy + side * 17;
            for (int pair = 0; pair < 3; pair++) {
                int wx = cabLeft + 4 + pair * 13;
                c.rect(wx, wy - 3, 10, 6, WolfPalette.shade(metal, 4));
                c.hLine(wx, wx + 9, wy - 3, WolfPalette.shade(metal, 2));
                c.ellipse(wx + 4, wy, 2, 2, WolfPalette.shade(metal, 3));
            }
        }

        // --- flatbed: square corners, nothing rounded anywhere ----------------------------
        deck(c, cabLeft, cy - 14, bedRight - cabLeft, 29, body, 1);
        c.rivets(cabLeft + 1, cy - 13, bedRight - cabLeft - 2, 27, 6,
                WolfPalette.shade(metal, 1), WolfPalette.shade(metal, 4));

        // --- cab: an armoured box at the rear, with a vision slit and the only striping ----
        contactShadow(c, cabLeft, cy - 11, cabRight - cabLeft, 23, body);
        deck(c, cabLeft, cy - 11, cabRight - cabLeft, 23, body, 0);
        c.rect(cabLeft, cy - 4, 2, 8, WolfPalette.shade(metal, 4));
        c.hazard(cabLeft, cy - 14, cabRight - cabLeft, 3,
                WolfPalette.shade(brass, 1), WolfPalette.shade(body, 3));

        // --- elevation frame: two uprights holding the rack off the bed --------------------
        c.rect(tubeLeft, cy - 16, 3, 33, WolfPalette.shade(metal, 2));
        c.vLine(tubeLeft, cy - 16, cy + 16, WolfPalette.shade(metal, 1));
        c.rect(bedRight - 6, cy - 16, 3, 33, WolfPalette.shade(metal, 2));
        c.vLine(bedRight - 6, cy - 16, cy + 16, WolfPalette.shade(metal, 1));

        // --- the rack: four tubes, equal length, mouths lined up in open air ---------------
        // The whole rack stands off the bed, so it throws onto it first - over the bed only,
        // since past the tailgate there is nothing underneath for a shadow to land on.
        for (int t = 0; t < 4; t++) {
            contactShadow(c, tubeLeft, cy - 14 + t * 8, bedRight - tubeLeft, 5, body);
        }
        for (int t = 0; t < 4; t++) {
            int ty = cy - 14 + t * 8;
            tube(c, tubeLeft, tubeRight, ty + 2, 2, metal);
            // Mouth: a brass ring, hot on the frame where that tube is the one firing.
            c.rect(tubeRight - 3, ty, 3, 5,
                    WolfPalette.shade(brass, lit == 1 && t % 2 == 0 ? 0 : 2));
            c.rect(tubeRight - 2, ty + 1, 2, 3, WolfPalette.shade(body, 4));
        }

        skullStencil(c, cabLeft + 8, cy - 8);
        crewman(c, cabLeft + 2, cy - 18, body);
        crewman(c, cabLeft + 2, cy + 18, body);
        return c;
    }

    /**
     * Not an artifact. A Regime gun built around one, at the size the Regime builds things.
     *
     * <p>The Totenkopf Division does not field alien weaponry. It fields <em>its</em> weaponry
     * with alien contents, reverse-engineered into the same riveted plate, blackout paint and
     * brutal geometry as everything else on its inventory — so the vocabulary is drawn from the
     * real wonder-weapon programmes rather than from anything organic.
     *
     * <p>Three references, all load-bearing:
     *
     * <ul>
     *   <li><b>The thousand-tonne land cruiser</b> — the reason this is drawn at
     *       {@link #SUPERWEAPON_SIZE} rather than at vehicle scale. Three track assemblies a
     *       side, a hull built like a warship's, secondary mounts on the corners. It should
     *       dwarf a tank, and it should look like it ruins the ground it is parked on.</li>
     *   <li><b>The high-pressure pump</b> — a barrel with a row of angled pressure chambers
     *       branching off it, each adding to the round on its way down the bore. This is what
     *       makes it a gun with an appalling internal process rather than merely a gun.</li>
     *   <li><b>Naval practice</b> — a barbette, a conning position with vision slits, and a
     *       gun far too large for the carriage under it.</li>
     * </ul>
     *
     * <p>An earlier version built the front end from stacked parabolic reflectors, after the
     * acoustic cannon. It was accurate and it was wrong: nested dishes read as an antenna, and
     * what this has to read as first, before anything clever, is artillery.
     *
     * <p>{@link WolfPalette#RESONANCE} appears in the chamber throats, the breech seam and down
     * the bore, and nowhere else. Caged, panelled over, and let out in one direction on
     * purpose: what you see is the containment, not the thing contained.
     */
    private static PixelCanvas resonanzkanone(int frame) {
        PixelCanvas c = canvas(SUPERWEAPON_SIZE);
        int[] glow = WolfPalette.RESONANCE;
        int[] steel = WolfPalette.STEEL;
        int[] metal = WolfPalette.GUNMETAL;
        int[] night = WolfPalette.NIGHT;
        int cy = 56;
        int charge = frame == 1 ? 0 : 2;

        // Absolute pixels rather than offsets from a centre: the assembly has to fit the canvas
        // and stay balanced about its middle, because the sprite is rotated about (56, 56) for
        // the other seven facings.
        //
        // The proportion is the thing to get right and took two attempts. A hull as tall as it
        // is long reads as a bunker however it is detailed, and a barbette drawn twenty pixels
        // across swallowed the whole deck and turned the gun into a dome. A land cruiser is
        // long, and its gun sticks well out past the end of it.
        int hullLeft = 2;
        int hullRight = 74;
        int hullHalf = 22;
        int barbetteX = 58;
        int breechRight = 78;
        int barrelRight = 98;
        int muzzleRight = 108;

        // --- three track assemblies a side, in the land-cruiser manner --------------------
        for (int side = -1; side <= 1; side += 2) {
            int ty = cy + side * 28 - 5;
            for (int bogie = 0; bogie < 3; bogie++) {
                int bx = hullLeft + 3 + bogie * 23;
                c.rect(bx, ty, 22, 11, WolfPalette.shade(metal, 4));
                c.hLine(bx, bx + 21, ty, WolfPalette.shade(metal, 2));
                c.hLine(bx, bx + 21, ty + 10, WolfPalette.shade(night, 4));
                for (int link = 0; link < 11; link++) {
                    c.vLine(bx + 1 + link * 2, ty + 1, ty + 9, WolfPalette.shade(metal, 3));
                }
                c.ellipse(bx + 6, ty + 5, 3, 3, WolfPalette.shade(metal, 2));
                c.ellipse(bx + 15, ty + 5, 3, 3, WolfPalette.shade(metal, 2));
            }
        }

        // --- hull: long, riveted, and with nothing decorative on it -----------------------
        contactShadow(c, hullLeft, cy - hullHalf, hullRight - hullLeft, hullHalf * 2 + 1, metal);
        deck(c, hullLeft, cy - hullHalf, hullRight - hullLeft, hullHalf * 2 + 1, night, 1);
        c.rivets(hullLeft + 2, cy - hullHalf + 2, hullRight - hullLeft - 4, hullHalf * 2 - 3,
                8, WolfPalette.shade(steel, 2), WolfPalette.shade(night, 4));
        c.hazard(hullLeft, cy - hullHalf - 3, hullRight - hullLeft, 3,
                WolfPalette.shade(WolfPalette.BRASS, 1), WolfPalette.shade(night, 3));
        c.hazard(hullLeft, cy + hullHalf + 1, hullRight - hullLeft, 3,
                WolfPalette.shade(WolfPalette.BRASS, 1), WolfPalette.shade(night, 3));

        // --- deckhouse: a raised block forward of the gun, with a conning slit -------------
        contactShadow(c, hullLeft + 6, cy - 13, 26, 27, night);
        deck(c, hullLeft + 6, cy - 13, 26, 27, night, 0);
        c.rivets(hullLeft + 7, cy - 12, 24, 25, 7, WolfPalette.shade(steel, 1),
                WolfPalette.shade(night, 4));
        c.rect(hullLeft + 6, cy - 3, 3, 7, WolfPalette.shade(metal, 4));
        c.vLine(hullLeft + 7, cy - 2, cy + 2, WolfPalette.shade(glow, charge + 3));

        // --- two secondary mounts, small and dark, on the forward corners ------------------
        int[][] mounts = {{hullLeft + 38, cy - 16}, {hullLeft + 38, cy + 16}};
        for (int i = 0; i < mounts.length; i++) {
            c.ellipse(mounts[i][0], mounts[i][1], 4, 4, WolfPalette.shade(steel, 4));
            c.ellipse(mounts[i][0], mounts[i][1], 2, 2, WolfPalette.shade(steel, 2));
            c.rect(mounts[i][0], mounts[i][1] - 1, 8, 2, WolfPalette.shade(metal, 3));
        }

        // --- barbette: a ring, not a dome. Dark, so the deck reads through it --------------
        c.ellipse(barbetteX + 2, cy + 2, 14, 15, WolfPalette.shade(night, 4));
        c.ellipse(barbetteX, cy, 14, 15, WolfPalette.shade(steel, 4));
        c.ellipse(barbetteX, cy, 12, 13, WolfPalette.shade(steel, 3));
        c.ellipse(barbetteX, cy, 9, 10, WolfPalette.shade(night, 2));
        c.rivets(barbetteX - 11, cy - 12, 22, 24, 7, WolfPalette.shade(steel, 1),
                WolfPalette.shade(steel, 4));

        // --- breech: a mass of steel with the sliding block showing ------------------------
        deck(c, barbetteX + 2, cy - 12, breechRight - barbetteX - 2, 25, steel, 2);
        c.hLine(barbetteX + 2, breechRight - 1, cy - 2, WolfPalette.shade(steel, 4));
        c.hLine(barbetteX + 2, breechRight - 1, cy + 2, WolfPalette.shade(steel, 1));
        c.vLine(breechRight - 1, cy - 6, cy + 5, WolfPalette.shade(glow, charge + 2));

        // --- the barrel: long and thin, sticking well clear of the hull -------------------
        tube(c, breechRight, barrelRight, cy, 5, metal);
        for (int rib = 0; rib < 4; rib++) {
            c.vLine(breechRight + 2 + rib * 2, cy - 3, cy + 3, WolfPalette.shade(metal, 2));
        }

        // --- pressure chambers, clustered at the breech end --------------------------------
        // They must leave a clean run of bare tube in front of them: spread down the whole
        // barrel they read as a centipede and bury the gun in its own plumbing.
        for (int i = 0; i < 2; i++) {
            int bx = breechRight + 4 + i * 5;
            for (int side = -1; side <= 1; side += 2) {
                int tipY = cy + side * 13;
                c.thickLine(bx, cy + side * 4, bx - 4, tipY, 1, WolfPalette.shade(metal, 2));
                c.rect(bx - 7, tipY + (side < 0 ? -3 : 0), 7, 4, WolfPalette.shade(steel, 3));
                c.rectOutline(bx - 7, tipY + (side < 0 ? -3 : 0), 7, 4,
                        WolfPalette.shade(night, 4));
                c.rect(bx - 5, tipY + (side < 0 ? -2 : 1), 3, 2,
                        WolfPalette.shade(glow, charge));
            }
        }

        // --- muzzle: a heavy brake, and the one honest look down the bore ------------------
        c.rect(barrelRight, cy - 10, muzzleRight - barrelRight, 21, WolfPalette.shade(steel, 2));
        c.hLine(barrelRight, muzzleRight - 1, cy - 10, WolfPalette.shade(steel, 0));
        c.hLine(barrelRight, muzzleRight - 1, cy + 10, WolfPalette.shade(steel, 4));
        c.rect(barrelRight + 2, cy - 8, 5, 4, WolfPalette.shade(night, 4));
        c.rect(barrelRight + 2, cy + 5, 5, 4, WolfPalette.shade(night, 4));
        c.rect(barrelRight, cy - 4, muzzleRight - barrelRight, 9, WolfPalette.shade(night, 4));
        c.rect(barrelRight + 1, cy - 3, muzzleRight - barrelRight - 1, 7,
                WolfPalette.shade(glow, charge + 2));
        c.rect(barrelRight + 2, cy - 1, muzzleRight - barrelRight - 2, 3,
                WolfPalette.shade(glow, charge));

        // --- skull plate on the deckhouse. The only marking anything in this game carries --
        skullStencil(c, hullLeft + 19, cy - 16);

        crewman(c, hullLeft + 36, cy - 6, night);
        crewman(c, hullLeft + 36, cy + 6, night);
        return c;
    }

    /**
     * A horizontal cylinder seen from above.
     *
     * <p>Flat fill with one bright line on top is what a plank looks like. A barrel is round,
     * and the thing that says so is a gradient across it — bright where it faces the light,
     * black where it turns away — with a hard specular line along the top edge.
     *
     * <p>Everything in this file is lit from the <b>north-west</b>. That is not a preference,
     * it is a contract: a sprite lit from anywhere else sits in the same field as the rest and
     * makes the whole scene look lit from nowhere.
     */
    private static void tube(PixelCanvas c, int left, int right, int cy, int half, int[] ramp) {
        c.rampVertical(left, cy - half, right - left, half * 2 + 1, ramp, 1, 4);
        c.hLine(left, right - 1, cy - half, WolfPalette.shade(ramp, 0));
        c.hLine(left, right - 1, cy + half, WolfPalette.shade(ramp, 4));
    }

    /**
     * A large flat plate, with the slight curvature a real one has.
     *
     * <p>{@link PixelCanvas#panel} gives a plate crisp edges and a dead-flat middle, which is
     * right for a small fitting and wrong for a deck the size of a hull: a big uniform fill is
     * the single thing that most makes a top-down sprite look like a sticker. Ramping the
     * interior from the same north-west light gives it a top that catches and a bottom that
     * falls away, and costs nothing.
     */
    private static void deck(PixelCanvas c, int x, int y, int w, int h, int[] ramp, int base) {
        c.rampVertical(x, y, w, h, ramp, base, base + 2);
        c.bevel(x, y, w, h, WolfPalette.shade(ramp, base - 1), WolfPalette.shade(ramp, base + 3));
    }

    /**
     * The shadow a raised part throws onto the surface underneath it.
     *
     * <p>Down and to the right, following the same north-west light. This is most of what makes
     * a top-down sprite read as having height at all: without it a gun barrel and a stripe
     * painted on the deck are the same picture.
     *
     * <p>Opaque, drawn from the surface's own ramp rather than as translucent black, because
     * {@link PixelCanvas#px} writes colours straight in without blending — a translucent shadow
     * here would punch a half-transparent hole through the hull rather than darken it.
     */
    private static void contactShadow(PixelCanvas c, int x, int y, int w, int h, int[] ramp) {
        c.rect(x + 2, y + 2, w, h, WolfPalette.shade(ramp, 4));
    }

    /**
     * A small stencilled skull, the only marking anything in this game carries.
     *
     * <p>Drawn rather than suggested. A bone-coloured ellipse of about this size reads as a
     * splash of spilled paint, not as a mark somebody put there on purpose — the sockets and
     * the jaw are what make it a stencil, and they have to be cut into it even at six pixels
     * across.
     */
    private static void skullStencil(PixelCanvas c, int cx, int cy) {
        int[] bone = WolfPalette.BONE;
        int dark = WolfPalette.shade(WolfPalette.NIGHT, 4);
        // Cranium.
        c.ellipse(cx, cy, 3, 2, WolfPalette.shade(bone, 3));
        c.hLine(cx - 2, cx + 2, cy - 2, WolfPalette.shade(bone, 2));
        // Sockets, which are the whole reason it reads.
        c.px(cx - 1, cy, dark);
        c.px(cx + 1, cy, dark);
        // Jaw.
        c.hLine(cx - 1, cx + 1, cy + 2, WolfPalette.shade(bone, 3));
        c.px(cx, cy + 3, WolfPalette.shade(bone, 4));
    }

    /**
     * A man beside a gun, seen from above.
     *
     * <p>Small and crude on purpose — this is background to the weapon, not a figure in its own
     * right. It exists because the simulation reports a gun as infantry when it is hit, so a
     * hit on one sprays blood; without visible crew that would read as a bug rather than as the
     * gun's crew being killed, which is exactly what it is.
     */
    private static void crewman(PixelCanvas c, int x, int y, int[] cloth) {
        // A helmet from above, not a face. Two earlier versions put flesh at the centre and
        // both read as a scrap of pale debris beside the gun rather than as a man: at three
        // pixels across, the lightest colour wins and skin is the lightest thing on a soldier.
        c.ellipse(x, y, 2, 2, WolfPalette.shade(cloth, 4));
        c.ellipse(x, y, 1, 1, WolfPalette.shade(cloth, 2));
        c.px(x, y - 1, WolfPalette.shade(cloth, 1));
    }

    private static PixelCanvas jeep(Faction faction, int frame) {
        PixelCanvas c = canvas(VEHICLE_SIZE);
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
        PixelCanvas c = canvas(VEHICLE_SIZE);
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
     * Sturmpanzer: the heaviest thing in the game. Wider tracks with side skirts, a boxier
     * turret set further back, a longer gun, and a red band across the engine deck. It has to
     * read as bigger than the Captured Panzer at a glance, or stealing one means nothing.
     */
    private static PixelCanvas sturmpanzer(int frame) {
        PixelCanvas c = canvas(VEHICLE_SIZE);
        int[] hull = WolfPalette.NIGHT;
        int[] metal = WolfPalette.GUNMETAL;
        int cx = 24;
        int cy = 24;
        int roll = frame == 1 ? 1 : 0;

        // Wide tracks with skirt plates hanging over them.
        for (int side = 0; side < 2; side++) {
            int ty = side == 0 ? cy - 18 : cy + 11;
            c.rect(cx - 19, ty, 38, 7, WolfPalette.shade(metal, 4));
            for (int i = 0; i < 6; i++) {
                c.ellipse(cx - 15 + i * 6, ty + 3, 3, 3, WolfPalette.shade(metal, 3));
                c.px(cx - 15 + i * 6, ty + 3, WolfPalette.shade(metal, 2));
            }
            for (int x = cx - 19 + roll; x < cx + 19; x += 3) {
                c.vLine(x, ty, ty + 1, WolfPalette.shade(metal, 1));
                c.vLine(x, ty + 5, ty + 6, WolfPalette.shade(metal, 1));
            }
            // Skirt: a plate bolted along the track run.
            int sy = side == 0 ? ty + 5 : ty - 2;
            c.rect(cx - 17, sy, 34, 2, WolfPalette.shade(hull, 1));
            c.rivets(cx - 16, sy, 32, 2, 6, WolfPalette.shade(hull, 0),
                    WolfPalette.shade(hull, 4));
        }

        // Hull: longer and squarer than the medium tank, with a heavy bow plate.
        c.panel(cx - 18, cy - 12, 36, 24, hull, 1);
        c.rivets(cx - 16, cy - 10, 32, 20, 7, WolfPalette.shade(hull, 0),
                WolfPalette.shade(hull, 4));
        for (int i = 0; i < 7; i++) {
            c.vLine(cx + 12 + i, cy - 11 + i, cy + 11 - i, WolfPalette.shade(hull, 0));
        }
        // Engine deck: louvres and twin exhausts at the tail.
        c.rect(cx - 17, cy - 8, 7, 16, WolfPalette.shade(hull, 3));
        for (int i = 0; i < 6; i++) {
            c.hLine(cx - 17, cx - 11, cy - 7 + i * 3, WolfPalette.shade(metal, 4));
        }
        c.rect(cx - 20, cy - 5, 3, 3, WolfPalette.shade(metal, 2));
        c.rect(cx - 20, cy + 3, 3, 3, WolfPalette.shade(metal, 2));

        // Turret: a slab, set back, with a commander's cupola and a stowage bin.
        c.rect(cx - 8, cy - 10, 18, 20, WolfPalette.shade(hull, 2));
        c.bevel(cx - 8, cy - 10, 18, 20, WolfPalette.shade(hull, 0),
                WolfPalette.shade(hull, 4));
        c.rivets(cx - 6, cy - 8, 14, 16, 6, WolfPalette.shade(hull, 0),
                WolfPalette.shade(hull, 4));
        c.ellipse(cx - 4, cy - 5, 4, 4, WolfPalette.shade(metal, 2));
        c.ellipse(cx - 4, cy - 5, 3, 3, WolfPalette.shade(metal, 4));
        c.panel(cx - 7, cy + 5, 8, 5, WolfPalette.LEATHER, 2);

        // Main gun: longer and fatter than anything else on the field.
        c.rect(cx + 9, cy - 4, 6, 8, WolfPalette.shade(hull, 3));
        c.rect(cx + 13, cy - 3, 14, 6, WolfPalette.shade(metal, 2));
        c.hLine(cx + 13, cx + 26, cy - 3, WolfPalette.shade(metal, 1));
        c.hLine(cx + 13, cx + 26, cy + 2, WolfPalette.shade(metal, 4));
        c.rect(cx + 24, cy - 4, 5, 8, WolfPalette.shade(metal, 3));
        c.px(cx + 28, cy - 1, WolfPalette.shade(metal, 0));

        // The band.
        c.hLine(cx - 17, cx - 11, cy - 11, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(cx - 17, cx - 11, cy - 10, WolfPalette.shade(WolfPalette.BLOOD, 2));

        return c;
    }

    /**
     * Panzerhund: an armoured quadruped with a heavy head, a plated spine, exhaust stacks and
     * four piston legs. The head is the point of the design — it is nearly a third of the
     * animal, with a hinged jaw, a flame nozzle behind the teeth and one red optic.
     */
    private static PixelCanvas hound(int frame) {
        PixelCanvas c = canvas(VEHICLE_SIZE);
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
        PixelCanvas c = canvas(VEHICLE_SIZE);
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
