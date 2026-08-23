package com.ccwolf.game.art;

/**
 * The infantry doll, re-authored on the sixty-four grid.
 *
 * <p>This is not a new design. The game's infantry language was settled two art passes ago and it
 * works: a front-facing figure whatever the facing — big helmet, a face band, a chunky coat,
 * short legs — with the weapon drawn from the hand outward so it points where the unit looks,
 * exactly one saturated red mark per man, and a thin outline. What the coarse grid could not
 * afford was curvature and hands: helmets were stepped ovals, and every man's arms were implied.
 * The fine grid buys those two things, and this class spends it on nothing else.
 *
 * <p>Everything identity-carrying survives verbatim from the coarse recipe, at doubled
 * coordinates: the armband, the gas-mask lenses, the bandolier with brass in it, the scratched
 * stolen helmet with the rag round it, the twin fuel bottles, the drape, the wire coil. Anyone
 * who knew the old roster recognises every man in the new one — that is the definition of an
 * upgrade rather than a redesign.
 */
final class FineInfantry {

    private FineInfantry() {
    }

    /** The figure's grid: one tile, sixty-four to a side, two screen pixels per art pixel. */
    static final int SIZE = 64;

    private static final int CX = 32;

    static PixelCanvas draw(boolean regime, int facing, int frame, UnitSprites.Loadout loadout,
                            int scale, int outline) {
        PixelCanvas c = new PixelCanvas(SIZE, SIZE, scale);

        int[] coat = loadout.civilian ? WolfPalette.LEATHER
                : (regime ? WolfPalette.NIGHT : WolfPalette.OLIVE);
        int[] webbing = regime ? WolfPalette.NIGHT : WolfPalette.LEATHER;
        // A civilian's trousers are grey wool, not the same leather as his coat, his cap, his
        // satchel and his boots - drawn all in one ramp the infiltrator was a brown golem.
        int[] trousers = loadout.civilian ? WolfPalette.STONE
                : (regime ? WolfPalette.NIGHT : WolfPalette.LEATHER);
        int cloth = clamp(1 + loadout.clothShift);

        float angle = facing * (float) (Math.PI / 4.0);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        boolean toViewer = dy > 0.35f;
        boolean away = dy < -0.35f;
        int step = frame == 1 ? 2 : 0;
        int drop = loadout.crouched ? 6 : 0;

        c.groundShadow(CX, 56, loadout.crouched ? 16 : 14, 6);

        legs(c, trousers, regime, step, drop);
        coatAndBelt(c, coat, webbing, cloth, drop);
        shoulders(c, coat, cloth, drop, loadout.crouched);

        int top = 20 + drop;
        pack(c, loadout.pack, top, away, webbing);

        if (regime) {
            regimeKit(c, top, coat);
        } else if (loadout.pack == UnitSprites.Pack.BANDOLIER) {
            resistanceKit(c, top, toViewer);
        } else {
            armband(c, 20, top + 5, 2);
        }

        // At the vertical facings the weapon goes to port arms - a diagonal across the chest -
        // rather than pointing at or away from the camera. Foreshortening was tried first and a
        // rifle pointed south was still a rod down the figure's front, and pointed north it was
        // an antenna above the helmet. Port arms is what the dolls this language descends from
        // have always done at those facings, and it reads instantly.
        float weaponDx = dx;
        float weaponDy = dy;
        if (Math.abs(dy) > 0.85f) {
            weaponDx = 0.83f;
            weaponDy = -0.55f;
        }
        arms(c, coat, cloth, weaponDx, weaponDy, top, loadout.kit);

        head(c, regime, loadout.head, top, toViewer, away);
        weapon(c, loadout.kit, weaponDx, weaponDy, top, regime);

        c.outline(outline);
        return c;
    }

    private static int clamp(int shade) {
        return Math.max(0, Math.min(3, shade));
    }

    // --- the body ------------------------------------------------------------------------------

    private static void legs(PixelCanvas c, int[] trousers, boolean regime, int step, int drop) {
        int bootLight = WolfPalette.shade(WolfPalette.LEATHER, regime ? 3 : 2);
        int bootDark = WolfPalette.shade(WolfPalette.LEATHER, 4);

        // Left leg: a column with a slight taper, the boot a wider block with a lit toe line.
        c.panel(23, 40 - step + drop, 8, 11 - drop, trousers, 2);
        c.vLine(23, 40 - step + drop, 50 - step, WolfPalette.shade(trousers, 1));
        c.rect(22, 49 - step, 9, 5, bootDark);
        c.hLine(22, 30, 49 - step, bootLight);
        c.hLine(23, 29, 53 - step, WolfPalette.shade(WolfPalette.LEATHER, 4));

        c.panel(33, 40 + step + drop, 8, 11 - drop, trousers, 1);
        c.vLine(40, 40 + step + drop, 50 + step, WolfPalette.shade(trousers, 3));
        c.rect(33, 49 + step, 9, 5, bootDark);
        c.hLine(33, 41, 49 + step, bootLight);
        c.hLine(34, 40, 53 + step, WolfPalette.shade(WolfPalette.LEATHER, 4));
    }

    private static void coatAndBelt(PixelCanvas c, int[] coat, int[] webbing, int cloth,
                                    int drop) {
        int top = 20 + drop;
        // The trunk, slightly waisted: a panel with one column shaved off each side low down.
        c.panel(21, top, 22, 24 - drop, coat, cloth);
        c.vLine(21, top, top + 6, WolfPalette.shade(coat, cloth - 1));
        // Skirt of the coat, darker, over the top of the legs.
        c.rect(20, top + 16 - drop, 25, 8, WolfPalette.shade(coat, cloth + 1));
        c.hLine(20, 44, top + 16 - drop, WolfPalette.shade(coat, cloth - 1));
        c.hLine(20, 44, top + 23 - drop, WolfPalette.shade(coat, 4));
        // Front seam and two brass buttons.
        c.vLine(32, top + 2, top + 20 - drop, WolfPalette.shade(coat, cloth + 2));
        c.px(32, top + 6, WolfPalette.shade(WolfPalette.BRASS, 2));
        c.px(32, top + 12, WolfPalette.shade(WolfPalette.BRASS, 2));
        // A fold line under each shoulder, which is what stops the trunk being a flat slab.
        c.vLine(25, top + 4, top + 14 - drop, WolfPalette.shade(coat, cloth + 1));
        c.vLine(39, top + 4, top + 14 - drop, WolfPalette.shade(coat, cloth + 1));

        // Belt, buckle, and a pouch either side.
        int beltY = top + 16 - drop;
        c.hLine(21, 43, beltY, WolfPalette.shade(webbing, 3));
        c.hLine(21, 43, beltY + 1, WolfPalette.shade(webbing, 4));
        c.rect(22, beltY, 5, 6, WolfPalette.shade(webbing, 2));
        c.hLine(22, 26, beltY + 2, WolfPalette.shade(webbing, 4));
        c.rect(38, beltY, 5, 6, WolfPalette.shade(webbing, 2));
        c.hLine(38, 42, beltY + 2, WolfPalette.shade(webbing, 4));
        c.rect(31, beltY, 3, 2, WolfPalette.shade(WolfPalette.BRASS, 1));
    }

    private static void shoulders(PixelCanvas c, int[] coat, int cloth, int drop,
                                  boolean crouched) {
        int top = 20 + drop;
        int half = crouched ? 13 : 15;
        // Rounded, not square: the ellipse ends are what the fine grid buys over the old panel.
        c.ellipse(CX, top + 1, half, 4, WolfPalette.shade(coat, cloth));
        c.hLine(CX - half + 2, CX + half - 2, top - 2, WolfPalette.shade(coat, cloth - 1));
        c.hLine(CX - half + 1, CX + half - 1, top - 1, WolfPalette.shade(coat, cloth));
    }

    /** The red rag: the Resistance's one saturated mark, tied round the left arm. */
    private static void armband(PixelCanvas c, int x, int y, int shade) {
        c.rect(x, y, 5, 6, WolfPalette.shade(WolfPalette.BLOOD, shade));
        c.hLine(x, x + 4, y, WolfPalette.shade(WolfPalette.BLOOD, shade - 1));
        c.hLine(x, x + 4, y + 5, WolfPalette.shade(WolfPalette.BLOOD, 3));
    }

    // --- kit -----------------------------------------------------------------------------------

    private static void regimeKit(PixelCanvas c, int top, int[] coat) {
        // Chest plate with a rivet line.
        c.panel(24, top + 2, 17, 12, coat, 0);
        c.rivets(25, top + 3, 15, 10, 7, WolfPalette.shade(WolfPalette.STEEL, 1),
                WolfPalette.shade(coat, 4));
        c.hLine(24, 40, top + 13, WolfPalette.shade(coat, 4));

        // Shoulder plates, the right one stencilled.
        c.panel(17, top - 2, 10, 7, coat, 0);
        c.hLine(17, 26, top - 2, WolfPalette.shade(WolfPalette.STEEL, 2));
        c.panel(38, top - 2, 10, 7, coat, 0);
        c.hLine(38, 47, top - 2, WolfPalette.shade(WolfPalette.STEEL, 2));
        // A stencilled unit numeral: two straight bars. The first pass free-handed a squiggle
        // and every figure appeared to be wearing a question mark.
        c.vLine(41, top - 1, top + 2, WolfPalette.shade(WolfPalette.BONE, 1));
        c.vLine(44, top - 1, top + 2, WolfPalette.shade(WolfPalette.BONE, 2));

        // The armband, on black. Brighter than the Resistance rag on purpose.
        c.rect(20, top + 4, 5, 8, WolfPalette.shade(WolfPalette.BLOOD, 1));
        c.hLine(20, 24, top + 4, WolfPalette.shade(WolfPalette.BLOOD, 0));
        c.hLine(20, 24, top + 11, WolfPalette.shade(WolfPalette.BLOOD, 3));
    }

    private static void resistanceKit(PixelCanvas c, int top, boolean toViewer) {
        // Bandolier: one clean diagonal, two pixels wide, brass in every third loop. The first
        // fine pass wove three diagonals across the chest - strip, seam and sleeve - and the
        // figure wore a fishnet. One strap is the design; the others had to go.
        for (int i = 0; i < 13; i++) {
            int bx = 23 + i;
            int by = top + 2 + i;
            c.px(bx, by, WolfPalette.shade(WolfPalette.LEATHER, 2));
            c.px(bx + 1, by, WolfPalette.shade(WolfPalette.LEATHER, 3));
            if (i % 3 == 0) {
                c.px(bx, by + 1, WolfPalette.shade(WolfPalette.BRASS, 1));
            }
        }
        // Satchel on the hip.
        c.panel(39, top + 13, 9, 9, WolfPalette.LEATHER, 2);
        c.hLine(39, 47, top + 16, WolfPalette.shade(WolfPalette.LEATHER, 4));
        c.px(43, top + 17, WolfPalette.shade(WolfPalette.BRASS, 2));

        armband(c, 20, top + 5, 2);

        if (toViewer) {
            // Scarf at the collar, fully below the jaw. It started over the chin and the larger
            // fine-grid face painted straight over it - a feature that renders under another is
            // not a feature.
            c.rect(26, top + 1, 13, 4, WolfPalette.shade(WolfPalette.BONE, 3));
            c.hLine(26, 38, top + 1, WolfPalette.shade(WolfPalette.BONE, 2));
        }
    }

    private static void pack(PixelCanvas c, UnitSprites.Pack pack, int top, boolean away,
                             int[] webbing) {
        switch (pack) {
            case ROCKET_BAG:
                c.panel(38, top + 2, 11, 15, webbing, 2);
                c.vLine(38, top + 2, top + 16, WolfPalette.shade(webbing, 4));
                for (int i = 0; i < 3; i++) {
                    c.vLine(40 + i * 3, top - 2, top + 5,
                            WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    c.px(40 + i * 3, top - 3, WolfPalette.shade(WolfPalette.BLOOD, 1));
                }
                break;
            case CHARGE_BAG:
                // A satchel, not a crate: the first fine pass drew it thirteen square on a
                // twenty-two-wide torso and the man appeared to be delivering furniture.
                c.panel(39, top + 13, 10, 9, webbing, 1);
                c.hLine(39, 48, top + 16, WolfPalette.shade(webbing, 4));
                c.px(42, top + 11, WolfPalette.shade(WolfPalette.BONE, 2));
                c.px(45, top + 11, WolfPalette.shade(WolfPalette.BONE, 2));
                break;
            case WIRE_COIL:
                // Slung high at the shoulder and kept small. At eight radii on the chest it was
                // a cog the size of the man's torso, and he read as carrying a shield.
                c.ellipse(44, top + 4, 6, 6, WolfPalette.shade(WolfPalette.LEATHER, 3));
                c.ellipse(44, top + 4, 4, 4, WolfPalette.shade(WolfPalette.LEATHER, 1));
                c.ellipse(44, top + 4, 2, 2, WolfPalette.shade(WolfPalette.LEATHER, 4));
                break;
            case FUEL_TANKS:
                c.panel(36, top - 1, 7, 19, WolfPalette.GUNMETAL, 2);
                c.vLine(36, top, top + 17, WolfPalette.shade(WolfPalette.GUNMETAL, 0));
                c.panel(44, top + 1, 7, 17, WolfPalette.GUNMETAL, 3);
                c.vLine(44, top + 2, top + 17, WolfPalette.shade(WolfPalette.GUNMETAL, 1));
                c.hLine(36, 42, top - 1, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.hLine(44, 50, top + 1, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.px(38, top + 3, WolfPalette.shade(WolfPalette.STEEL, 0));
                c.px(46, top + 5, WolfPalette.shade(WolfPalette.STEEL, 0));
                break;
            case DRAPE:
                c.rect(19, top + 4, 27, 17, WolfPalette.shade(WolfPalette.NIGHT, 1));
                c.hLine(19, 45, top + 4, WolfPalette.shade(WolfPalette.NIGHT, 0));
                for (int x = 20; x < 45; x += 4) {
                    c.px(x, top + 20, WolfPalette.shade(WolfPalette.NIGHT, 3));
                    c.vLine(x + 2, top + 18, top + 20, WolfPalette.shade(WolfPalette.NIGHT, 2));
                }
                break;
            case SATCHEL_ONLY:
                c.panel(40, top + 12, 9, 11, webbing, 2);
                c.hLine(40, 48, top + 15, WolfPalette.shade(webbing, 4));
                break;
            case BANDOLIER:
            case NONE:
            default:
                break;
        }

        if (away && pack != UnitSprites.Pack.DRAPE && pack != UnitSprites.Pack.FUEL_TANKS) {
            c.rect(22, top, 21, 5, WolfPalette.shade(webbing, 1));
            c.hLine(22, 42, top, WolfPalette.shade(webbing, 0));
            c.vLine(29, top, top + 4, WolfPalette.shade(webbing, 3));
            c.vLine(36, top, top + 4, WolfPalette.shade(webbing, 3));
        }
    }

    // --- arms ----------------------------------------------------------------------------------

    /**
     * Two hands where the weapon will be: one at the grip, one at the fore-end.
     *
     * <p>Hands and nothing else. The first fine pass drew full sleeves from the shoulders, and
     * two diagonal strokes across a chest that already carries a bandolier and a seam turned the
     * figure into a fishnet — the doll's language has never had visible arms, and the language
     * was right. Two small skin blocks under the weapon's line are enough to say held.
     */
    private static void arms(PixelCanvas c, int[] coat, int cloth, float dx, float dy, int top,
                             UnitSprites.Kit kit) {
        int handX = CX;
        int handY = top + 10;
        int skin = WolfPalette.shade(WolfPalette.FLESH, 2);
        int skinDark = WolfPalette.shade(WolfPalette.FLESH, 3);

        if (kit == UnitSprites.Kit.NONE) {
            // No weapon: both hands low at the sides.
            c.rect(20, top + 14, 3, 3, skin);
            c.rect(42, top + 14, 3, 3, skinDark);
            return;
        }

        int gripX = Math.round(handX - dx * 3f);
        int gripY = Math.round(handY - dy * 3f);
        int foreX = Math.round(handX + dx * 7f);
        int foreY = Math.round(handY + dy * 7f);
        c.rect(foreX - 1, foreY, 3, 3, skinDark);
        c.rect(gripX - 1, gripY, 3, 3, skin);
    }

    // --- the head ------------------------------------------------------------------------------

    private static void head(PixelCanvas c, boolean regime, UnitSprites.Head head, int top,
                             boolean toViewer, boolean away) {
        int headY = top - 16;
        boolean regimeHelmet = head == UnitSprites.Head.REGIME_HELMET
                || head == UnitSprites.Head.COVERED_HELMET
                || head == UnitSprites.Head.STOLEN_HELMET;

        // Neck.
        c.rect(30, headY + 12, 5, 5, WolfPalette.shade(
                regimeHelmet && regime ? WolfPalette.NIGHT : WolfPalette.FLESH, 3));

        // Face, for anyone not behind a mask: a round face with a jaw shade and two eye pixels.
        boolean masked = head == UnitSprites.Head.REGIME_HELMET && regime;
        if (!away && !masked) {
            c.ellipse(CX, headY + 10, 7, 7, WolfPalette.shade(WolfPalette.FLESH, 2));
            c.ellipse(CX, headY + 13, 5, 3, WolfPalette.shade(WolfPalette.FLESH, 3));
            c.hLine(27, 37, headY + 15, WolfPalette.shade(WolfPalette.FLESH, 3));
            if (toViewer) {
                c.rect(28, headY + 9, 2, 2, WolfPalette.shade(WolfPalette.NIGHT, 1));
                c.rect(35, headY + 9, 2, 2, WolfPalette.shade(WolfPalette.NIGHT, 1));
                c.hLine(31, 33, headY + 13, WolfPalette.shade(WolfPalette.FLESH, 4));
            }
        }

        switch (head) {
            case REGIME_HELMET: {
                int[] lacquer = WolfPalette.NIGHT;
                // The coal-scuttle: a deep dome with a flared skirt all round.
                c.ellipse(CX, headY + 8, 10, 9, WolfPalette.shade(lacquer, 3));
                c.ellipse(CX, headY + 6, 11, 8, WolfPalette.shade(lacquer, 2));
                c.ellipse(31, headY + 4, 9, 6, WolfPalette.shade(lacquer, 1));
                c.ellipse(29, headY + 3, 5, 3, WolfPalette.shade(lacquer, 0));
                c.hLine(20, 44, headY + 13, WolfPalette.shade(lacquer, 3));
                c.hLine(19, 45, headY + 14, WolfPalette.shade(lacquer, 4));
                c.hLine(20, 44, headY + 15, WolfPalette.shade(lacquer, 4));
                if (toViewer) {
                    // Gas mask: two burning lenses and a filter canister.
                    c.rect(25, headY + 8, 15, 7, WolfPalette.shade(lacquer, 4));
                    c.rect(25, headY + 8, 5, 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
                    c.rect(35, headY + 8, 5, 4, WolfPalette.shade(WolfPalette.BLOOD, 1));
                    c.px(26, headY + 8, WolfPalette.shade(WolfPalette.BLOOD, 0));
                    c.px(36, headY + 8, WolfPalette.shade(WolfPalette.BLOOD, 0));
                    c.rect(30, headY + 13, 5, 5, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                    c.hLine(30, 34, headY + 15, WolfPalette.shade(WolfPalette.GUNMETAL, 4));
                }
                break;
            }
            case STOLEN_HELMET: {
                int[] steel = WolfPalette.GUNMETAL;
                c.ellipse(CX, headY + 6, 11, 8, WolfPalette.shade(steel, 3));
                c.ellipse(31, headY + 4, 9, 6, WolfPalette.shade(steel, 2));
                c.ellipse(29, headY + 3, 5, 3, WolfPalette.shade(steel, 1));
                c.hLine(20, 44, headY + 13, WolfPalette.shade(steel, 4));
                // The rag tied round it so their own side does not shoot them.
                c.hLine(21, 43, headY + 5, WolfPalette.shade(WolfPalette.BLOOD, 2));
                c.hLine(21, 43, headY + 6, WolfPalette.shade(WolfPalette.BLOOD, 3));
                c.px(21, headY + 5, WolfPalette.shade(WolfPalette.BLOOD, 1));
                // A scratch where the insignia was.
                c.px(38, headY + 4, WolfPalette.shade(steel, 0));
                c.px(39, headY + 5, WolfPalette.shade(steel, 0));
                break;
            }
            case COVERED_HELMET: {
                // Cover, veil, scrim: the whole head wrapped, only the eyes left. The first fine
                // pass let the bottom of the bare face stick out under the dome, and every
                // facing wore a crescent of chin like a tan collar.
                int[] cover = WolfPalette.NIGHT;
                c.ellipse(CX, headY + 6, 11, 8, WolfPalette.shade(cover, 2));
                c.ellipse(31, headY + 4, 9, 6, WolfPalette.shade(cover, 1));
                c.rect(26, headY + 9, 13, 8, WolfPalette.shade(cover, 3));
                c.hLine(20, 44, headY + 13, WolfPalette.shade(cover, 3));
                c.hLine(21, 43, headY + 14, WolfPalette.shade(cover, 4));
                c.hLine(23, 41, headY + 15, WolfPalette.shade(cover, 4));
                if (toViewer) {
                    c.rect(28, headY + 10, 2, 2, WolfPalette.shade(WolfPalette.FLESH, 2));
                    c.rect(35, headY + 10, 2, 2, WolfPalette.shade(WolfPalette.FLESH, 2));
                }
                for (int i = 0; i < 7; i++) {
                    c.px(22 + i * 3, headY + 14 + (i % 2), WolfPalette.shade(WolfPalette.OLIVE, 2));
                }
                break;
            }
            case GHILLIE: {
                int[] rag = WolfPalette.OLIVE;
                c.ellipse(CX, headY + 8, 13, 9, WolfPalette.shade(rag, 3));
                c.ellipse(31, headY + 6, 11, 7, WolfPalette.shade(rag, 2));
                for (int i = 0; i < 13; i++) {
                    int x = 20 + i * 2;
                    c.vLine(x, headY + 13, headY + 15 + (i % 3), WolfPalette.shade(rag, 4));
                    c.px(x, headY + 12, WolfPalette.shade(rag, 1));
                }
                break;
            }
            case HOOD: {
                int[] cloth = WolfPalette.LEATHER;
                c.ellipse(CX, headY + 6, 11, 11, WolfPalette.shade(cloth, 3));
                c.ellipse(CX, headY + 4, 9, 9, WolfPalette.shade(cloth, 2));
                c.rect(26, headY + 7, 13, 8, WolfPalette.shade(cloth, 4));
                c.hLine(26, 38, headY + 7, WolfPalette.shade(cloth, 1));
                if (toViewer) {
                    c.rect(28, headY + 10, 2, 2, WolfPalette.shade(WolfPalette.FLESH, 3));
                    c.rect(35, headY + 10, 2, 2, WolfPalette.shade(WolfPalette.FLESH, 3));
                }
                break;
            }
            case BANDANA: {
                int[] hair = WolfPalette.LEATHER;
                c.ellipse(CX, headY + 6, 9, 7, WolfPalette.shade(hair, 4));
                c.hLine(23, 41, headY + 6, WolfPalette.shade(WolfPalette.BLOOD, 2));
                c.hLine(23, 41, headY + 7, WolfPalette.shade(WolfPalette.BLOOD, 3));
                c.px(23, headY + 8, WolfPalette.shade(WolfPalette.BLOOD, 3));
                c.px(22, headY + 9, WolfPalette.shade(WolfPalette.BLOOD, 3));
                // Goggles pushed up, resting just above the bandana. They were at the crown of
                // the head, where two outlined steel blocks poking over the hairline read as a
                // pair of grey ears on every single facing.
                c.hLine(25, 39, headY + 4, WolfPalette.shade(WolfPalette.GUNMETAL, 2));
                c.rect(27, headY + 3, 3, 2, WolfPalette.shade(WolfPalette.STEEL, 1));
                c.rect(35, headY + 3, 3, 2, WolfPalette.shade(WolfPalette.STEEL, 1));
                break;
            }
            case CAP:
            default: {
                // A field cap, not a head of hair: it sits ON the skull, so its widest line is
                // one pixel proud of the face and its brim stops above the eyes. The first fine
                // pass drew it eleven wide and down to the eye row, and every facing read as a
                // man under a mushroom.
                int[] wool = WolfPalette.LEATHER;
                c.ellipse(CX, headY + 3, 8, 4, WolfPalette.shade(wool, 2));
                c.ellipse(31, headY + 2, 6, 3, WolfPalette.shade(wool, 1));
                c.hLine(24, 40, headY + 5, WolfPalette.shade(wool, 3));
                c.hLine(25, 39, headY + 6, WolfPalette.shade(wool, 4));
                c.px(24, headY + 2, WolfPalette.shade(wool, 0));
                break;
            }
        }
    }

    // --- weapons -------------------------------------------------------------------------------

    /**
     * Drawn from the hand outward, so they always point where the unit looks.
     *
     * <p>Foreshortened when it points at or away from the camera. The doll faces the viewer
     * whatever its facing, so a rifle pointed south is pointed at the camera — and drawn at full
     * length it was a pole run straight down through the figure to below the hem of his coat.
     * Nearly half the length comes off at the vertical extremes, which is what an object pointed
     * at you looks like.
     */
    private static void weapon(PixelCanvas c, UnitSprites.Kit kit, float dx, float dy, int top,
                               boolean regime) {
        int handX = CX;
        int handY = top + 10;
        float k = 1f - 0.3f * Math.abs(dy);
        int[] metal = WolfPalette.GUNMETAL;
        int[] stock = WolfPalette.LEATHER;

        switch (kit) {
            case ROCKET: {
                float perpX = -dy;
                float perpY = dx;
                int shoulderX = Math.round(handX - dx * 6f);
                int shoulderY = Math.round(handY - dy * 6f) - 4;
                int tipX = Math.round(shoulderX + dx * 34f * k);
                int tipY = Math.round(shoulderY + dy * 34f * k);
                int backX = Math.round(shoulderX - dx * 12f * k);
                int backY = Math.round(shoulderY - dy * 12f * k);

                c.thickLine(backX, backY, tipX, tipY, 1, WolfPalette.shade(metal, 2));
                c.line(backX, backY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(Math.round(backX + perpX * 2f), Math.round(backY + perpY * 2f),
                        Math.round(tipX + perpX * 2f), Math.round(tipY + perpY * 2f),
                        WolfPalette.shade(metal, 3));

                // Flared venturi.
                c.thickLine(backX, backY, Math.round(backX - dx * 4f),
                        Math.round(backY - dy * 4f), 2, WolfPalette.shade(metal, 3));
                c.px(Math.round(backX - dx * 4f), Math.round(backY - dy * 4f),
                        WolfPalette.shade(metal, 4));

                // Warhead: a dark bulge with one red band.
                int warheadX = Math.round(shoulderX + dx * 28f * k);
                int warheadY = Math.round(shoulderY + dy * 28f * k);
                c.thickLine(warheadX, warheadY, tipX, tipY, 2, WolfPalette.shade(metal, 3));
                c.thickLine(warheadX, warheadY, Math.round(warheadX + dx * 2f),
                        Math.round(warheadY + dy * 2f), 2, WolfPalette.shade(WolfPalette.BLOOD, 1));
                c.px(tipX, tipY, WolfPalette.shade(WolfPalette.NIGHT, 0));

                // Blast shield.
                int shieldX = Math.round(shoulderX + dx * 10f);
                int shieldY = Math.round(shoulderY + dy * 10f);
                c.rect(shieldX - 3, shieldY - 7, 7, 7, WolfPalette.shade(metal, 2));
                c.hLine(shieldX - 3, shieldX + 3, shieldY - 7, WolfPalette.shade(metal, 0));
                c.rect(shieldX - 1, shieldY - 5, 2, 2, WolfPalette.shade(metal, 4));
                break;
            }
            case SNIPER: {
                int tipX = Math.round(handX + dx * 30f * k);
                int tipY = Math.round(handY + dy * 30f * k);
                int buttX = Math.round(handX - dx * 11f * k);
                int buttY = Math.round(handY - dy * 11f * k);
                c.thickLine(buttX, buttY, handX, handY, 1, WolfPalette.shade(stock, 2));
                c.line(buttX, buttY, handX, handY, WolfPalette.shade(stock, 1));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                int scopeX = Math.round(handX + dx * 5f);
                int scopeY = Math.round(handY + dy * 5f) - 3;
                c.thickLine(scopeX, scopeY, Math.round(scopeX + dx * 9f),
                        Math.round(scopeY + dy * 9f), 1, WolfPalette.shade(metal, 0));
                c.px(scopeX, scopeY, WolfPalette.shade(WolfPalette.STEEL, 0));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                break;
            }
            case GRENADE: {
                int throwX = Math.round(handX + dx * 11f);
                int throwY = Math.round(handY + dy * 11f) - 6;
                c.thickLine(handX, handY - 2, throwX, throwY, 1, WolfPalette.shade(stock, 2));
                c.ellipse(throwX, throwY, 3, 3, WolfPalette.shade(metal, 2));
                c.ellipse(throwX - 1, throwY - 1, 1, 1, WolfPalette.shade(metal, 0));
                c.px(throwX, throwY - 4, WolfPalette.shade(WolfPalette.BONE, 2));
                // Spare charges on the belt.
                c.rect(26, top + 17, 2, 3, WolfPalette.shade(metal, 2));
                c.rect(37, top + 17, 2, 3, WolfPalette.shade(metal, 2));
                break;
            }
            case FLAMER: {
                int tipX = Math.round(handX + dx * 22f * k);
                int tipY = Math.round(handY + dy * 22f * k);
                c.thickLine(handX, handY, tipX, tipY, 1, WolfPalette.shade(metal, 2));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.rect(tipX - 2, tipY - 2, 5, 5, WolfPalette.shade(metal, 3));
                c.px(tipX + 2, tipY, WolfPalette.shade(WolfPalette.FIRE, 0));
                c.px(tipX + 2, tipY - 1, WolfPalette.shade(WolfPalette.FIRE, 2));
                c.px(tipX + 3, tipY, WolfPalette.shade(WolfPalette.FIRE, 1));
                // Hose looping back to the tanks.
                c.line(handX - 3, handY + 3, 38, top + 8, WolfPalette.shade(metal, 3));
                c.line(handX - 3, handY + 4, 38, top + 9, WolfPalette.shade(metal, 4));
                break;
            }
            case NONE: {
                int toolX = Math.round(handX + dx * 9f);
                int toolY = Math.round(handY + dy * 9f) + 4;
                c.line(handX - 11, handY + 5, toolX, toolY, WolfPalette.shade(metal, 2));
                c.rect(toolX, toolY - 1, 2, 3, WolfPalette.shade(metal, 0));
                break;
            }
            case SMG: {
                int tipX = Math.round(handX + dx * 20f * k);
                int tipY = Math.round(handY + dy * 20f * k);
                int buttX = Math.round(handX - dx * 9f * k);
                int buttY = Math.round(handY - dy * 9f * k);
                c.thickLine(buttX, buttY, handX, handY, 1, WolfPalette.shade(stock, 1));
                c.thickLine(handX, handY, tipX, tipY, 1, WolfPalette.shade(metal, 2));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                // The magazine, hanging under mid-receiver: the detail that names the weapon.
                int magX = Math.round(handX + dx * 6f);
                int magY = Math.round(handY + dy * 6f);
                c.rect(magX - 1, magY + 2, 3, 6, WolfPalette.shade(metal, 2));
                c.vLine(magX - 1, magY + 2, magY + 7, WolfPalette.shade(metal, 0));
                break;
            }
            case RIFLE:
            default: {
                int tipX = Math.round(handX + dx * 26f * k);
                int tipY = Math.round(handY + dy * 26f * k);
                int buttX = Math.round(handX - dx * 10f * k);
                int buttY = Math.round(handY - dy * 10f * k);
                c.thickLine(buttX, buttY, handX, handY, 1, WolfPalette.shade(stock, 1));
                c.line(buttX, buttY, handX, handY, WolfPalette.shade(stock, 0));
                c.line(handX, handY, tipX, tipY, WolfPalette.shade(metal, 1));
                c.line(handX, handY + 1, tipX, tipY + 1, WolfPalette.shade(metal, 3));
                c.px(tipX, tipY, WolfPalette.shade(metal, 0));
                int boltX = Math.round(handX + dx * 4f);
                int boltY = Math.round(handY + dy * 4f);
                c.px(boltX, boltY + 2, WolfPalette.shade(WolfPalette.BRASS, 1));
                break;
            }
        }
    }
}
