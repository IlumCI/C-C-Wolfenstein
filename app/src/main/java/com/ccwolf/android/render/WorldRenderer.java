package com.ccwolf.android.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import com.ccwolf.android.GameSession;
import com.ccwolf.android.art.SpriteAtlas;
import com.ccwolf.android.art.UnitSprites;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the battlefield from the baked sprite atlas.
 *
 * <p>Three things here matter as much as the sprites themselves:
 *
 * <ul>
 *   <li><b>Interpolation.</b> The simulation ticks twenty times a second. Drawing entities at
 *       their raw tick positions on a 60 Hz screen looks like a slideshow, so everything is
 *       drawn blended between the last tick and the current one.</li>
 *   <li><b>Depth sorting.</b> Everything is drawn back to front by its southern edge, so a
 *       soldier standing in front of a bunker is drawn in front of it.</li>
 *   <li><b>Nearest-neighbour scaling.</b> Filtering is off everywhere; smoothed pixel art
 *       stops being pixel art.</li>
 * </ul>
 */
public final class WorldRenderer {

    /** Ticks each infantry walk frame is held for. */
    private static final int WALK_FRAME_TICKS = 4;

    private final SpriteAtlas atlas = SpriteAtlas.get();
    private final Paint sprite = new Paint();
    private final Paint flat = new Paint();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dst = new Rect();

    /**
     * The cold cast over anything a Saboteur has switched off. Applied as a colour filter
     * rather than a rectangle so it follows the artwork — a wash drawn over the footprint
     * leaves a building's chimneys their normal colour and puts a visible square around a
     * unit's feet.
     */
    private final PorterDuffColorFilter deadTint =
            new PorterDuffColorFilter(0x663C78B4, PorterDuff.Mode.SRC_ATOP);

    /** Reused each frame so a drawing pass allocates nothing. */
    private final List<Entity> drawOrder = new ArrayList<Entity>(256);

    private boolean dragging;
    private float dragX0;
    private float dragY0;
    private float dragX1;
    private float dragY1;

    private final Comparator<Entity> byDepth = new Comparator<Entity>() {
        @Override
        public int compare(Entity a, Entity b) {
            // Southern edge decides: a structure's footprint extends below its centre.
            float ay = a.y() + (a.isBuilding() ? ((Building) a).tilesHigh() / 2f : 0f);
            float by = b.y() + (b.isBuilding() ? ((Building) b).tilesHigh() / 2f : 0f);
            return Float.compare(ay, by);
        }
    };

    public WorldRenderer() {
        sprite.setFilterBitmap(false);
        sprite.setAntiAlias(false);
        sprite.setDither(false);
        flat.setStyle(Paint.Style.FILL);
        flat.setAntiAlias(false);
        stroke.setStyle(Paint.Style.STROKE);
        text.setColor(Palette.HUD_TEXT);
    }

    public void setSelectionBox(boolean active, float x0, float y0, float x1, float y1) {
        this.dragging = active;
        this.dragX0 = x0;
        this.dragY0 = y0;
        this.dragX1 = x1;
        this.dragY1 = y1;
    }

    public void draw(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        WorldView view = session.view();

        canvas.save();
        canvas.clipRect(camera.viewLeft(), camera.viewTop(),
                camera.viewLeft() + camera.viewWidth(), camera.viewTop() + camera.viewHeight());

        drawTerrain(canvas, session);
        // Ground marks go straight onto the terrain, under everything standing on it.
        session.fx().drawDecals(canvas, camera);
        drawGroundEffects(canvas, session);
        drawEntities(canvas, session);
        // Rounds in flight and particles go over the top of everything alive.
        session.fx().drawOverlay(canvas, camera);
        drawAirEffects(canvas, session);
        drawPlacementGhost(canvas, session);
        drawFog(canvas, session);
        drawSelectionBox(canvas);

        canvas.restore();
        if (view.isGameOver()) {
            drawOutcome(canvas, session);
        }
    }

    // --- terrain --------------------------------------------------------------------------

    private void drawTerrain(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        TileMap map = session.world().map();

        int x0 = camera.firstVisibleTileX();
        int y0 = camera.firstVisibleTileY();
        int x1 = camera.lastVisibleTileX();
        int y1 = camera.lastVisibleTileY();

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                Terrain terrain = map.terrain(x, y);
                int ore = map.ore(x, y);
                Bitmap tile = (terrain == Terrain.ORE && ore > 0)
                        ? atlas.ore(x, y, ore) : atlas.terrain(terrain, x, y);
                tileRect(camera, x, y);
                canvas.drawBitmap(tile, null, dst, sprite);
            }
        }
    }

    /** Destination rectangle for a tile, rounded so neighbouring tiles never leave a seam. */
    private void tileRect(Camera camera, int tileX, int tileY) {
        dst.set(Math.round(camera.screenX(tileX)), Math.round(camera.screenY(tileY)),
                Math.round(camera.screenX(tileX + 1)), Math.round(camera.screenY(tileY + 1)));
    }

    // --- entities -------------------------------------------------------------------------

    private void drawEntities(Canvas canvas, GameSession session) {
        WorldView view = session.view();
        drawOrder.clear();

        for (int i = 0; i < view.buildings().size(); i++) {
            Building b = view.buildings().get(i);
            if (view.isDiscovered(b)) {
                drawOrder.add(b);
            }
        }
        for (int i = 0; i < view.units().size(); i++) {
            Unit u = view.units().get(i);
            if (view.isDiscovered(u)) {
                drawOrder.add(u);
            }
        }
        Collections.sort(drawOrder, byDepth);

        for (int i = 0; i < drawOrder.size(); i++) {
            Entity e = drawOrder.get(i);
            if (e.isBuilding()) {
                drawBuilding(canvas, session, (Building) e);
            } else {
                drawUnit(canvas, session, (Unit) e);
            }
        }
    }

    private void drawBuilding(Canvas canvas, GameSession session, Building b) {
        Camera camera = session.camera();
        WorldView view = session.view();
        float px = camera.tilePx();

        int left = Math.round(camera.screenX(b.tileX()));
        int top = Math.round(camera.screenY(b.tileY()));
        int right = Math.round(camera.screenX(b.tileX() + b.tilesWide()));
        int bottom = Math.round(camera.screenY(b.tileY() + b.tilesHigh()));
        if (right < camera.viewLeft() || bottom < camera.viewTop()
                || left > camera.viewLeft() + camera.viewWidth()
                || top > camera.viewTop() + camera.viewHeight()) {
            return;
        }

        Faction faction = view.world().player(b.ownerId()).faction();
        dst.set(left, top, right, bottom);
        boolean dead = view.isDisabled(b);
        if (dead) {
            sprite.setColorFilter(deadTint);
        }
        canvas.drawBitmap(atlas.building(b.type(), faction, b.damageState()), null, dst, sprite);
        if (dead) {
            sprite.setColorFilter(null);
        }

        // A defence with the power cut gets a cold wash and a dead lamp; without this there is
        // no way to tell a blacked-out turret from a live one.
        if (b.type().weapon() != null && !b.isPowered()) {
            flat.setColor(0x66101828);
            canvas.drawRect(dst, flat);
            flat.setColor(Palette.POWER_LOW);
            canvas.drawRect(left + px * 0.35f, top - px * 0.22f,
                    left + px * 0.65f, top - px * 0.06f, flat);
        }

        // The turret's barrel traverses onto its target.
        if (b.type() == BuildingType.FLAK_TURRET && b.isPowered()) {
            canvas.drawBitmap(atlas.flakBarrel(turretFacing(session, b)), null, dst, sprite);
        }

        if (dead) {
            drawDisabledArcs(canvas, view.tick(), b.id(), left, top, right, bottom, px);
        }
        if (b.isRepairing()) {
            drawRepairMark(canvas, session, left, top, right);
        }
        if (!b.isComplete()) {
            drawProgressBar(canvas, left, bottom, right - left, b.constructionFraction());
        }
        if (isSelected(session, b)) {
            drawBrackets(canvas, left, top, right, bottom, view.isPrimary(b));
        }
        if (b.healthFraction() < 1f) {
            drawHealthBar(canvas, left, top - px * 0.16f, right - left, b.healthFraction());
        }
    }

    /** Points a turret at whatever it can see, falling back to facing the enemy base. */
    private int turretFacing(GameSession session, Building turret) {
        float range = turret.weapon() == null ? 6f : turret.weapon().range();
        Entity target = session.view().world()
                .findNearestEnemy(turret.ownerId(), turret.x(), turret.y(), range, false);
        if (target == null) {
            target = session.view().world()
                    .findNearestEnemyAnywhere(turret.ownerId(), turret.x(), turret.y(), true);
        }
        if (target == null) {
            return 0;
        }
        return facingIndex((float) Math.atan2(target.y() - turret.y(), target.x() - turret.x()));
    }

    private void drawUnit(Canvas canvas, GameSession session, Unit u) {
        Camera camera = session.camera();
        WorldView view = session.view();
        float px = camera.tilePx();
        float alpha = session.interpolation();

        float cx = camera.screenX(u.renderX(alpha));
        float cy = camera.screenY(u.renderY(alpha));

        // Sprites are authored one tile across for infantry, a little over for vehicles.
        float size = px * (u.type().isVehicle()
                ? UnitSprites.VEHICLE_SIZE / (float) UnitSprites.TILE : 1f);
        if (cx + size < camera.viewLeft() || cy + size < camera.viewTop()
                || cx - size > camera.viewLeft() + camera.viewWidth()
                || cy - size > camera.viewTop() + camera.viewHeight()) {
            return;
        }

        Faction faction = view.world().player(u.ownerId()).faction();
        Bitmap bitmap = atlas.unit(u.type(), faction, facingIndex(u.facing()), unitFrame(view, u));

        int half = Math.round(size / 2f);
        int centreX = Math.round(cx);
        int centreY = Math.round(cy);
        dst.set(centreX - half, centreY - half, centreX + half, centreY + half);

        // Your own concealed units are ghosted, so you can tell at a glance which of your
        // scouts the enemy currently cannot see. Enemy stealth units never reach this far:
        // isDiscovered already filtered them out.
        boolean hidden = view.isHiddenAlly(u);
        boolean dead = view.isDisabled(u);
        if (hidden) {
            sprite.setAlpha(125);
        }
        if (dead) {
            sprite.setColorFilter(deadTint);
        }
        canvas.drawBitmap(bitmap, null, dst, sprite);
        sprite.setAlpha(255);
        sprite.setColorFilter(null);

        if (dead) {
            drawDisabledArcs(canvas, view.tick(), u.id(), centreX - half, centreY - half,
                    centreX + half, centreY + half, px);
        }
        if (isSelected(session, u)) {
            float r = px * 0.5f;
            drawBrackets(canvas, Math.round(cx - r), Math.round(cy - r), Math.round(cx + r),
                    Math.round(cy + r), false);
        }
        if (u.healthFraction() < 1f) {
            drawHealthBar(canvas, cx - px * 0.4f, cy - px * 0.55f, px * 0.8f,
                    u.healthFraction());
        }
    }

    /** Walk cycle for infantry, tread shimmer for vehicles, cargo level for harvesters. */
    private int unitFrame(WorldView view, Unit u) {
        if (u.type().isHarvester()) {
            if (u.oreCarried() <= 0) {
                return 0;
            }
            return u.oreCarried() >= u.oreCapacity() / 2 ? 2 : 1;
        }
        if (!u.isMoving()) {
            return 0;
        }
        // Offset by id so a squad does not march in perfect lockstep.
        return ((view.tick() + u.id()) / WALK_FRAME_TICKS) % UnitSprites.WALK_FRAMES;
    }

    /** Radians to one of eight sprite facings, 0 being east. */
    public static int facingIndex(float radians) {
        int index = (int) Math.round(radians / (Math.PI / 4.0));
        return ((index % 8) + 8) % 8;
    }

    /**
     * Sparks crawling over something a Saboteur has switched off.
     *
     * <p>Drawn as stepped square pixels rather than smooth strokes so the arcs sit in the same
     * art style as everything else. The jitter is derived from the tick and the entity id, so
     * it crackles frame to frame without needing per-entity state, and two disabled things side
     * by side do not flicker in lockstep.
     */
    private void drawDisabledArcs(Canvas canvas, int tick, int id, int left, int top, int right,
            int bottom, float px) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        // One pixel of the sprite grid, so the arcs scale with the zoom.
        int dot = Math.max(1, Math.round(px / UnitSprites.TILE));
        int seed = id * 7919 + (tick / 2) * 104729;

        for (int arc = 0; arc < 5; arc++) {
            int state = scramble(seed + arc * 31337);
            int x = left + (int) ((state >>> 8 & 0xFF) / 255f * width);
            int y = bottom - (int) ((state >>> 16 & 0xFF) / 255f * (height * 0.6f));
            int steps = 6 + (state >>> 3 & 5);
            int prevX = x;
            int prevY = y;

            for (int step = 0; step < steps; step++) {
                state = scramble(state);
                // Arcs wander sideways and climb: sparks read as rising off the hull.
                x += ((state >>> 5 & 3) - 1) * dot * 2;
                y -= dot * 3;
                if (x < left - dot * 2 || x > right + dot * 2 || y < top - height * 0.4f) {
                    break;
                }
                // Join the steps, otherwise the sparks read as unrelated dots rather than a
                // discharge crawling over the thing.
                flat.setColor(Palette.ARC);
                thick(canvas, prevX, prevY, x, y, dot * 2);
                flat.setColor(Palette.ARC_CORE);
                canvas.drawRect(x, y, x + dot, y + dot, flat);
                prevX = x;
                prevY = y;
            }
        }
    }

    /** A chunky pixel line between two points, drawn as stepped squares. */
    private void thick(Canvas canvas, int x0, int y0, int x1, int y1, int size) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / Math.max(1, size / 2) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x0 + (x1 - x0) * t;
            float y = y0 + (y1 - y0) * t;
            canvas.drawRect(x, y, x + size, y + size, flat);
        }
    }

    /** Cheap integer hash; only ever used to jitter effects, never the simulation. */
    private static int scramble(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        return value & 0x7FFFFFFF;
    }

    // --- effects --------------------------------------------------------------------------

    /** Wrecks and craters sit on the ground, under everything still alive. */
    private void drawGroundEffects(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        float px = camera.tilePx();
        List<GameSession.Effect> effects = session.effects();

        for (int i = 0; i < effects.size(); i++) {
            GameSession.Effect fx = effects.get(i);
            if (fx.kind != GameSession.Effect.Kind.WRECK) {
                continue;
            }
            int half = Math.round(px * 0.55f);
            int cx = Math.round(camera.screenX(fx.x));
            int cy = Math.round(camera.screenY(fx.y));
            dst.set(cx - half, cy - half, cx + half, cy + half);
            // Wrecks fade over their last moments rather than blinking away.
            sprite.setAlpha(fx.progress() > 0.85f
                    ? (int) (255 * (1f - (fx.progress() - 0.85f) / 0.15f)) : 255);
            canvas.drawBitmap(atlas.wreck(fx.variant), null, dst, sprite);
            sprite.setAlpha(255);
        }
    }

    private void drawAirEffects(Canvas canvas, GameSession session) {
        Camera camera = session.camera();
        float px = camera.tilePx();
        List<GameSession.Effect> effects = session.effects();

        for (int i = 0; i < effects.size(); i++) {
            GameSession.Effect fx = effects.get(i);
            switch (fx.kind) {
                case MOVE_PING:
                case ATTACK_PING: {
                    boolean hostile = fx.kind == GameSession.Effect.Kind.ATTACK_PING;
                    float t = fx.progress();
                    stroke.setColor(hostile ? Palette.HEALTH_POOR : Palette.SELECTION);
                    stroke.setAlpha((int) (255 * (1f - t)));
                    stroke.setStrokeWidth(Math.max(1.5f, px * 0.06f));
                    canvas.drawCircle(camera.screenX(fx.x), camera.screenY(fx.y),
                            px * (0.7f - 0.45f * t), stroke);
                    stroke.setAlpha(255);
                    break;
                }
                default:
                    break;
            }
        }
    }

    // --- overlays -------------------------------------------------------------------------

    private void drawPlacementGhost(Canvas canvas, GameSession session) {
        BuildingType type = session.placing();
        if (type == null) {
            return;
        }
        Camera camera = session.camera();
        int tileX = session.placeTileX();
        int tileY = session.placeTileY();
        if (tileX < 0 || tileY < 0) {
            tileX = (int) camera.worldX(camera.viewLeft() + camera.viewWidth() / 2f);
            tileY = (int) camera.worldY(camera.viewTop() + camera.viewHeight() / 2f);
        }
        drawGhostAt(canvas, session, type, tileX, tileY);
    }

    /**
     * The build ghost: the structure at half opacity plus a per-tile legality grid, so it is
     * obvious which corner of a footprint is the one hanging over a rock.
     */
    public void drawGhostAt(Canvas canvas, GameSession session, BuildingType type, int tileX,
                            int tileY) {
        Camera camera = session.camera();
        WorldView view = session.view();
        int originX = tileX - type.tilesWide() / 2;
        int originY = tileY - type.tilesHigh() / 2;

        dst.set(Math.round(camera.screenX(originX)), Math.round(camera.screenY(originY)),
                Math.round(camera.screenX(originX + type.tilesWide())),
                Math.round(camera.screenY(originY + type.tilesHigh())));
        sprite.setAlpha(140);
        canvas.drawBitmap(atlas.building(type, view.faction(), 0), null, dst, sprite);
        sprite.setAlpha(255);

        // Per-tile legality: a thin wash plus an outline. Filling each tile solid, as this
        // first did, buried the ghost under a slab of green and told the player nothing.
        boolean allClear = view.canPlaceCentred(type, tileX, tileY);
        stroke.setStrokeWidth(Math.max(1f, camera.tilePx() * 0.04f));
        for (int ty = originY; ty < originY + type.tilesHigh(); ty++) {
            for (int tx = originX; tx < originX + type.tilesWide(); tx++) {
                boolean tileClear = !view.world().grid().isBlocked(tx, ty) && allClear;
                tileRect(camera, tx, ty);
                flat.setColor(tileClear ? 0x2264E064 : 0x44E05A50);
                canvas.drawRect(dst, flat);
                stroke.setColor(tileClear ? 0x9964E064 : 0xCCE05A50);
                canvas.drawRect(dst, stroke);
            }
        }
    }

    private void drawFog(Canvas canvas, GameSession session) {
        WorldView view = session.view();
        if (!view.isFogEnabled()) {
            return;
        }
        Camera camera = session.camera();
        FogGrid fog = view.fog();

        int x0 = camera.firstVisibleTileX();
        int y0 = camera.firstVisibleTileY();
        int x1 = camera.lastVisibleTileX();
        int y1 = camera.lastVisibleTileY();

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                byte state = fog.state(x, y);
                if (state == FogGrid.VISIBLE) {
                    continue;
                }
                flat.setColor(state == FogGrid.UNEXPLORED
                        ? Palette.FOG_UNEXPLORED : Palette.FOG_EXPLORED);
                tileRect(camera, x, y);
                canvas.drawRect(dst, flat);
            }
        }
    }

    private void drawSelectionBox(Canvas canvas) {
        if (!dragging) {
            return;
        }
        float left = Math.min(dragX0, dragX1);
        float top = Math.min(dragY0, dragY1);
        float right = Math.max(dragX0, dragX1);
        float bottom = Math.max(dragY0, dragY1);

        flat.setColor(0x2E9BE07A);
        canvas.drawRect(left, top, right, bottom, flat);
        stroke.setColor(Palette.SELECTION);
        stroke.setStrokeWidth(2f);
        canvas.drawRect(left, top, right, bottom, stroke);
    }

    /** Corner brackets rather than a ring: they never hide the sprite they mark. */
    private void drawBrackets(Canvas canvas, int left, int top, int right, int bottom,
                              boolean primary) {
        float arm = Math.max(4f, (right - left) * 0.28f);
        stroke.setColor(primary ? Palette.GOLD : Palette.SELECTION);
        stroke.setStrokeWidth(Math.max(1.8f, (right - left) * 0.05f));

        canvas.drawLine(left, top, left + arm, top, stroke);
        canvas.drawLine(left, top, left, top + arm, stroke);
        canvas.drawLine(right, top, right - arm, top, stroke);
        canvas.drawLine(right, top, right, top + arm, stroke);
        canvas.drawLine(left, bottom, left + arm, bottom, stroke);
        canvas.drawLine(left, bottom, left, bottom - arm, stroke);
        canvas.drawLine(right, bottom, right - arm, bottom, stroke);
        canvas.drawLine(right, bottom, right, bottom - arm, stroke);
    }

    /** A pulsing cross over a structure the repair crews are working on. */
    private void drawRepairMark(Canvas canvas, GameSession session, int left, int top,
                                int right) {
        float px = session.camera().tilePx();
        boolean lit = (session.view().tick() / 6) % 2 == 0;
        flat.setColor(lit ? Palette.HEALTH_GOOD : Palette.HUD_TEXT_DIM);
        float cx = (left + right) / 2f;
        float cy = top + px * 0.3f;
        float arm = px * 0.22f;
        canvas.drawRect(cx - arm, cy - arm / 3f, cx + arm, cy + arm / 3f, flat);
        canvas.drawRect(cx - arm / 3f, cy - arm, cx + arm / 3f, cy + arm, flat);
    }

    private void drawHealthBar(Canvas canvas, float left, float top, float width,
                               float fraction) {
        float height = Math.max(3f, width * 0.08f);
        flat.setColor(0xCC0C0C0A);
        canvas.drawRect(left - 1, top - 1, left + width + 1, top + height + 1, flat);
        flat.setColor(Palette.health(fraction));
        canvas.drawRect(left, top, left + width * fraction, top + height, flat);
    }

    private void drawProgressBar(Canvas canvas, float left, float bottom, float width,
                                 float fraction) {
        flat.setColor(0xCC0C0C0A);
        canvas.drawRect(left, bottom + 2, left + width, bottom + 7, flat);
        flat.setColor(Palette.GOLD);
        canvas.drawRect(left, bottom + 2, left + width * fraction, bottom + 7, flat);
    }

    private void drawOutcome(Canvas canvas, GameSession session) {
        boolean won = session.view().hasWon();
        flat.setColor(0xC0000000);
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), flat);

        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(won ? Palette.GOLD : Palette.REGIME);
        text.setTextSize(canvas.getHeight() * 0.13f);
        canvas.drawText(won ? "VALLEY HELD" : "OVERRUN", canvas.getWidth() / 2f,
                canvas.getHeight() * 0.45f, text);

        text.setFakeBoldText(false);
        text.setColor(Palette.HUD_TEXT);
        text.setTextSize(canvas.getHeight() * 0.05f);
        canvas.drawText("Tap to start a new skirmish", canvas.getWidth() / 2f,
                canvas.getHeight() * 0.58f, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private boolean isSelected(GameSession session, Entity e) {
        List<Integer> selection = session.selection();
        for (int i = 0; i < selection.size(); i++) {
            if (selection.get(i).intValue() == e.id()) {
                return true;
            }
        }
        return false;
    }
}
