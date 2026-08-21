package com.ccwolf.android.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;

/**
 * Every unit and structure, drawn from primitives at draw time. There are no image assets in
 * this project — the whole game is vector shapes, which keeps it tiny, resolution independent
 * and entirely original work.
 *
 * <p>Silhouettes carry the faction identity: Resistance shapes are rounded and improvised,
 * Regime shapes are angular and symmetrical.
 */
public final class Sprites {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    public Sprites() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        detail.setStyle(Paint.Style.FILL);
    }

    // --- units ----------------------------------------------------------------------------

    /**
     * @param cx screen x of the unit's centre
     * @param cy screen y of the unit's centre
     * @param px pixels per tile
     */
    public void drawUnit(Canvas canvas, Unit unit, Faction faction, float cx, float cy, float px) {
        int body = Palette.faction(faction);
        int dark = Palette.factionDark(faction);
        float facing = unit.facing();

        switch (unit.type()) {
            case SCOUT_JEEP:
                drawJeep(canvas, cx, cy, px, facing, body, dark);
                break;
            case PANZERHUND:
                drawHound(canvas, cx, cy, px, facing, body, dark);
                break;
            case CAPTURED_PANZER:
                drawTank(canvas, cx, cy, px, facing, body, dark);
                break;
            case HARVESTER:
                drawHarvester(canvas, unit, cx, cy, px, facing, body, dark);
                break;
            case UBERSOLDAT:
                drawUbersoldat(canvas, cx, cy, px, facing, body, dark);
                break;
            case ROCKETEER:
                drawInfantry(canvas, cx, cy, px, facing, body, dark, true);
                break;
            case PARTISAN:
            case SOLDAT:
            default:
                drawInfantry(canvas, cx, cy, px, facing, body, dark, false);
                break;
        }
    }

    private void drawInfantry(Canvas canvas, float cx, float cy, float px, float facing,
                              int body, int dark, boolean heavyWeapon) {
        float r = px * 0.26f;
        // Shadow first so the figure reads against busy terrain.
        fill.setColor(0x40000000);
        canvas.drawOval(cx - r, cy + r * 0.5f, cx + r, cy + r * 1.4f, fill);

        fill.setColor(dark);
        canvas.drawCircle(cx, cy, r, fill);
        fill.setColor(body);
        canvas.drawCircle(cx, cy - r * 0.15f, r * 0.72f, fill);

        // Helmet.
        fill.setColor(dark);
        canvas.drawCircle(cx, cy - r * 0.45f, r * 0.45f, fill);

        // Weapon, pointing where the soldier is looking.
        stroke.setColor(0xFF20221C);
        stroke.setStrokeWidth(Math.max(1.8f, px * (heavyWeapon ? 0.12f : 0.06f)));
        float len = px * (heavyWeapon ? 0.62f : 0.44f);
        canvas.drawLine(cx, cy, cx + (float) Math.cos(facing) * len,
                cy + (float) Math.sin(facing) * len, stroke);
    }

    private void drawUbersoldat(Canvas canvas, float cx, float cy, float px, float facing,
                                int body, int dark) {
        float w = px * 0.62f;
        float h = px * 0.7f;
        fill.setColor(0x50000000);
        rect.set(cx - w / 2, cy + h * 0.25f, cx + w / 2, cy + h * 0.6f);
        canvas.drawOval(rect, fill);

        // Slab torso and shoulder plates: all straight lines, no curves.
        fill.setColor(dark);
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawRect(rect, fill);
        fill.setColor(body);
        rect.set(cx - w * 0.34f, cy - h * 0.34f, cx + w * 0.34f, cy + h * 0.18f);
        canvas.drawRect(rect, fill);
        fill.setColor(0xFF2B2B28);
        rect.set(cx - w * 0.5f, cy - h * 0.52f, cx + w * 0.5f, cy - h * 0.34f);
        canvas.drawRect(rect, fill);

        stroke.setColor(0xFF151613);
        stroke.setStrokeWidth(Math.max(2f, px * 0.09f));
        float len = px * 0.5f;
        canvas.drawLine(cx, cy, cx + (float) Math.cos(facing) * len,
                cy + (float) Math.sin(facing) * len, stroke);
    }

    private void drawJeep(Canvas canvas, float cx, float cy, float px, float facing,
                          int body, int dark) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(facing), cx, cy);

        float w = px * 0.82f;
        float h = px * 0.5f;
        fill.setColor(0xFF1B1C18);
        rect.set(cx - w / 2, cy - h / 2 - px * 0.06f, cx + w / 2, cy + h / 2 + px * 0.06f);
        canvas.drawRoundRect(rect, px * 0.06f, px * 0.06f, fill);

        fill.setColor(body);
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawRoundRect(rect, px * 0.1f, px * 0.1f, fill);

        fill.setColor(dark);
        rect.set(cx - w * 0.1f, cy - h * 0.34f, cx + w * 0.28f, cy + h * 0.34f);
        canvas.drawRect(rect, fill);

        // Pintle gun over the bonnet.
        stroke.setColor(0xFF151613);
        stroke.setStrokeWidth(Math.max(1.5f, px * 0.05f));
        canvas.drawLine(cx, cy, cx + w * 0.62f, cy, stroke);
        canvas.restore();
    }

    private void drawTank(Canvas canvas, float cx, float cy, float px, float facing,
                          int body, int dark) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(facing), cx, cy);

        float w = px * 0.95f;
        float h = px * 0.66f;
        fill.setColor(0xFF171814);
        rect.set(cx - w / 2, cy - h / 2 - px * 0.07f, cx + w / 2, cy + h / 2 + px * 0.07f);
        canvas.drawRect(rect, fill);

        fill.setColor(dark);
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawRect(rect, fill);

        fill.setColor(body);
        rect.set(cx - w * 0.22f, cy - h * 0.3f, cx + w * 0.22f, cy + h * 0.3f);
        canvas.drawRect(rect, fill);

        stroke.setColor(0xFF151613);
        stroke.setStrokeWidth(Math.max(2f, px * 0.09f));
        canvas.drawLine(cx + w * 0.2f, cy, cx + w * 0.72f, cy, stroke);
        canvas.restore();
    }

    private void drawHound(Canvas canvas, float cx, float cy, float px, float facing,
                           int body, int dark) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(facing), cx, cy);

        float w = px * 0.85f;
        float h = px * 0.4f;
        fill.setColor(0x50000000);
        rect.set(cx - w / 2, cy + h * 0.5f, cx + w / 2, cy + h * 1.1f);
        canvas.drawOval(rect, fill);

        // Four legs as stubs under the hull.
        stroke.setColor(0xFF151613);
        stroke.setStrokeWidth(Math.max(1.5f, px * 0.05f));
        for (int i = 0; i < 2; i++) {
            float lx = cx - w * 0.3f + i * w * 0.55f;
            canvas.drawLine(lx, cy - h * 0.4f, lx - px * 0.06f, cy - h * 0.95f, stroke);
            canvas.drawLine(lx, cy + h * 0.4f, lx - px * 0.06f, cy + h * 0.95f, stroke);
        }

        fill.setColor(dark);
        rect.set(cx - w / 2, cy - h / 2, cx + w * 0.32f, cy + h / 2);
        canvas.drawRect(rect, fill);
        fill.setColor(body);
        rect.set(cx - w * 0.36f, cy - h * 0.28f, cx + w * 0.1f, cy + h * 0.28f);
        canvas.drawRect(rect, fill);

        // Snout, and a single red eye-lamp.
        fill.setColor(0xFF151613);
        path.reset();
        path.moveTo(cx + w * 0.32f, cy - h * 0.42f);
        path.lineTo(cx + w * 0.62f, cy);
        path.lineTo(cx + w * 0.32f, cy + h * 0.42f);
        path.close();
        canvas.drawPath(path, fill);
        fill.setColor(0xFFD84A38);
        canvas.drawCircle(cx + w * 0.34f, cy, Math.max(1.2f, px * 0.045f), fill);
        canvas.restore();
    }

    private void drawHarvester(Canvas canvas, Unit unit, float cx, float cy, float px,
                               float facing, int body, int dark) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(facing), cx, cy);

        float w = px * 0.98f;
        float h = px * 0.7f;
        fill.setColor(0xFF171814);
        rect.set(cx - w / 2, cy - h / 2 - px * 0.06f, cx + w / 2, cy + h / 2 + px * 0.06f);
        canvas.drawRect(rect, fill);

        fill.setColor(dark);
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        canvas.drawRect(rect, fill);

        // Hopper, filling up with uranium as the load grows.
        float load = unit.oreCapacity() == 0 ? 0f
                : unit.oreCarried() / (float) unit.oreCapacity();
        fill.setColor(0xFF2A2C24);
        rect.set(cx - w * 0.34f, cy - h * 0.32f, cx + w * 0.16f, cy + h * 0.32f);
        canvas.drawRect(rect, fill);
        if (load > 0f) {
            fill.setColor(Palette.ORE_RICH);
            rect.set(cx - w * 0.32f, cy + h * 0.3f - h * 0.6f * load, cx + w * 0.14f,
                    cy + h * 0.3f);
            canvas.drawRect(rect, fill);
        }

        // Cutting head at the front.
        fill.setColor(body);
        rect.set(cx + w * 0.2f, cy - h * 0.44f, cx + w * 0.5f, cy + h * 0.44f);
        canvas.drawRect(rect, fill);
        canvas.restore();
    }

    // --- structures -----------------------------------------------------------------------

    public void drawBuilding(Canvas canvas, Building b, Faction faction, float left, float top,
                             float px) {
        float w = b.tilesWide() * px;
        float h = b.tilesHigh() * px;
        int body = Palette.faction(faction);
        int dark = Palette.factionDark(faction);

        drawStructureShell(canvas, b.type(), left, top, w, h, px, body, dark);

        if (!b.isComplete()) {
            drawScaffolding(canvas, left, top, w, h, b.constructionFraction());
        }
    }

    /** Draws the shell only — also used for the translucent placement ghost. */
    public void drawStructureShell(Canvas canvas, BuildingType type, float left, float top,
                                   float w, float h, float px, int body, int dark) {
        fill.setColor(0xFF151613);
        rect.set(left, top, left + w, top + h);
        canvas.drawRect(rect, fill);

        fill.setColor(dark);
        rect.set(left + px * 0.06f, top + px * 0.06f, left + w - px * 0.06f, top + h - px * 0.06f);
        canvas.drawRect(rect, fill);

        fill.setColor(body);
        rect.set(left + px * 0.2f, top + px * 0.2f, left + w - px * 0.2f, top + h - px * 0.2f);
        canvas.drawRect(rect, fill);

        float cx = left + w / 2f;
        float cy = top + h / 2f;
        detail.setColor(0xFF23241F);

        switch (type) {
            case COMMAND_POST:
                // Blockhouse with a mast and a pennant.
                detail.setColor(0xFF2A2B25);
                rect.set(cx - w * 0.22f, cy - h * 0.22f, cx + w * 0.22f, cy + h * 0.22f);
                canvas.drawRect(rect, detail);
                stroke.setColor(0xFF151613);
                stroke.setStrokeWidth(Math.max(1.5f, px * 0.06f));
                canvas.drawLine(left + w * 0.18f, top + h * 0.16f, left + w * 0.18f,
                        top - h * 0.1f, stroke);
                detail.setColor(body);
                path.reset();
                path.moveTo(left + w * 0.18f, top - h * 0.1f);
                path.lineTo(left + w * 0.44f, top - h * 0.02f);
                path.lineTo(left + w * 0.18f, top + h * 0.06f);
                path.close();
                canvas.drawPath(path, detail);
                break;
            case GENERATOR:
                // Two stacks and a bolt.
                detail.setColor(0xFF23241F);
                rect.set(left + w * 0.2f, top + h * 0.06f, left + w * 0.36f, top + h * 0.4f);
                canvas.drawRect(rect, detail);
                rect.set(left + w * 0.62f, top + h * 0.06f, left + w * 0.78f, top + h * 0.4f);
                canvas.drawRect(rect, detail);
                detail.setColor(Palette.GOLD);
                path.reset();
                path.moveTo(cx + w * 0.06f, cy - h * 0.24f);
                path.lineTo(cx - w * 0.1f, cy + h * 0.04f);
                path.lineTo(cx + w * 0.02f, cy + h * 0.04f);
                path.lineTo(cx - w * 0.06f, cy + h * 0.3f);
                path.lineTo(cx + w * 0.12f, cy - h * 0.02f);
                path.lineTo(cx, cy - h * 0.02f);
                path.close();
                canvas.drawPath(path, detail);
                break;
            case REFINERY:
                // Silo plus an unloading bay.
                detail.setColor(0xFF2A2B25);
                canvas.drawCircle(left + w * 0.28f, cy, Math.min(w, h) * 0.26f, detail);
                detail.setColor(Palette.ORE_RICH);
                canvas.drawCircle(left + w * 0.28f, cy, Math.min(w, h) * 0.13f, detail);
                detail.setColor(0xFF1D1E1A);
                rect.set(left + w * 0.56f, top + h * 0.58f, left + w * 0.92f, top + h * 0.94f);
                canvas.drawRect(rect, detail);
                break;
            case BARRACKS:
                // Long hut with a door and a duckboard walk.
                detail.setColor(0xFF1D1E1A);
                rect.set(cx - w * 0.1f, top + h * 0.55f, cx + w * 0.1f, top + h * 0.95f);
                canvas.drawRect(rect, detail);
                stroke.setColor(0xFF2A2B25);
                stroke.setStrokeWidth(Math.max(1f, px * 0.04f));
                for (int i = 1; i < 4; i++) {
                    float y = top + h * (0.18f + i * 0.09f);
                    canvas.drawLine(left + w * 0.16f, y, left + w * 0.84f, y, stroke);
                }
                break;
            case WAR_WORKS:
                // Roller shutter.
                detail.setColor(0xFF1D1E1A);
                rect.set(left + w * 0.22f, top + h * 0.42f, left + w * 0.78f, top + h * 0.94f);
                canvas.drawRect(rect, detail);
                stroke.setColor(0xFF3A3B32);
                stroke.setStrokeWidth(Math.max(1f, px * 0.05f));
                for (int i = 1; i < 5; i++) {
                    float y = top + h * (0.42f + i * 0.1f);
                    canvas.drawLine(left + w * 0.24f, y, left + w * 0.76f, y, stroke);
                }
                break;
            case FLAK_TURRET:
            default:
                detail.setColor(0xFF23241F);
                canvas.drawCircle(cx, cy, Math.min(w, h) * 0.34f, detail);
                stroke.setColor(0xFF151613);
                stroke.setStrokeWidth(Math.max(2f, px * 0.12f));
                canvas.drawLine(cx, cy, cx + w * 0.52f, cy - h * 0.34f, stroke);
                break;
        }
    }

    /** Hatching over a structure that is still going up, with a progress bar under it. */
    private void drawScaffolding(Canvas canvas, float left, float top, float w, float h,
                                 float fraction) {
        stroke.setColor(0x99FFD27A);
        stroke.setStrokeWidth(Math.max(1f, w * 0.03f));
        for (float x = left - h; x < left + w; x += w * 0.18f) {
            canvas.drawLine(x, top + h, x + h, top, stroke);
        }
        fill.setColor(0xAA000000);
        rect.set(left, top + h + 2, left + w, top + h + 6);
        canvas.drawRect(rect, fill);
        fill.setColor(Palette.GOLD);
        rect.set(left, top + h + 2, left + w * fraction, top + h + 6);
        canvas.drawRect(rect, fill);
    }

    /** Icon used on the sidebar buttons: the same silhouette, drawn small. */
    public void drawUnitIcon(Canvas canvas, UnitType type, Faction faction, float cx, float cy,
                             float size) {
        int body = Palette.faction(faction);
        int dark = Palette.factionDark(faction);
        switch (type) {
            case SCOUT_JEEP:
                drawJeep(canvas, cx, cy, size, 0f, body, dark);
                break;
            case PANZERHUND:
                drawHound(canvas, cx, cy, size, 0f, body, dark);
                break;
            case CAPTURED_PANZER:
                drawTank(canvas, cx, cy, size, 0f, body, dark);
                break;
            case HARVESTER:
                fill.setColor(dark);
                rect.set(cx - size * 0.4f, cy - size * 0.28f, cx + size * 0.4f, cy + size * 0.28f);
                canvas.drawRect(rect, fill);
                fill.setColor(Palette.ORE_RICH);
                rect.set(cx - size * 0.3f, cy - size * 0.16f, cx + size * 0.1f, cy + size * 0.16f);
                canvas.drawRect(rect, fill);
                break;
            case UBERSOLDAT:
                drawUbersoldat(canvas, cx, cy, size, 0f, body, dark);
                break;
            case ROCKETEER:
                drawInfantry(canvas, cx, cy, size, 0f, body, dark, true);
                break;
            default:
                drawInfantry(canvas, cx, cy, size, 0f, body, dark, false);
                break;
        }
    }

    public void drawBuildingIcon(Canvas canvas, BuildingType type, Faction faction, float left,
                                 float top, float size) {
        drawStructureShell(canvas, type, left, top, size, size, size * 0.6f,
                Palette.faction(faction), Palette.factionDark(faction));
    }
}
