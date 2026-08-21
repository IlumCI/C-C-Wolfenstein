package com.ccwolf.game.render;

import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.fog.FogGrid;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.game.GameSession;
import com.ccwolf.game.art.SpriteAtlas;
import com.ccwolf.game.art.UnitSprites;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Rect;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.TextAlign;
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
    private final Brush sprite = new Brush();
    private final Brush flat = new Brush();
    private final Brush stroke = new Brush().setAntiAlias(true);
    private final Brush text = new Brush().setAntiAlias(true);
    private final Rect dst = new Rect();

    /** Where the frame goes. Off unless something switches it on. */
    private final RenderProfiler profiler = new RenderProfiler();

    /** The ground, baked into blocks so a frame does not redraw it a tile at a time. */
    private final TerrainCache terrainCache = new TerrainCache();

    /**
     * The cold cast over anything a Saboteur has switched off. Applied as a tint on the
     * artwork rather than as a rectangle so it follows the sprite's shape — a wash drawn over
     * the footprint leaves a building's chimneys their normal colour and puts a visible square
     * around a unit's feet.
     */
    private static final int DEAD_TINT = 0x663C78B4;

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
        sprite.setSmoothScaling(false);
        sprite.setAntiAlias(false);
        flat.setAntiAlias(false);
        text.setColor(Palette.HUD_TEXT);
    }

    public RenderProfiler profiler() {
        return profiler;
    }

    /** How many blocks have been composited since startup — a still camera should stop. */
    public int terrainBakes() {
        return terrainCache.bakes();
    }

    public int terrainBlocksResident() {
        return terrainCache.blocksResident();
    }

    public void setSelectionBox(boolean active, float x0, float y0, float x1, float y1) {
        this.dragging = active;
        this.dragX0 = x0;
        this.dragY0 = y0;
        this.dragX1 = x1;
        this.dragY1 = y1;
    }

    public void draw(Surface surface, GameSession session) {
        Camera camera = session.camera();
        WorldView view = session.view();

        surface.pushClip(camera.viewLeft(), camera.viewTop(),
                camera.viewLeft() + camera.viewWidth(), camera.viewTop() + camera.viewHeight());

        profiler.beginFrame();

        profiler.begin(RenderProfiler.Pass.TERRAIN);
        drawTerrain(surface, session);
        profiler.end(RenderProfiler.Pass.TERRAIN);

        // Ground marks go straight onto the terrain, under everything standing on it.
        profiler.begin(RenderProfiler.Pass.DECALS);
        session.fx().drawDecals(surface, camera);
        profiler.end(RenderProfiler.Pass.DECALS);

        profiler.begin(RenderProfiler.Pass.GROUND_FX);
        drawGroundEffects(surface, session);
        profiler.end(RenderProfiler.Pass.GROUND_FX);

        profiler.begin(RenderProfiler.Pass.ENTITIES);
        drawEntities(surface, session);
        profiler.end(RenderProfiler.Pass.ENTITIES);

        // Rounds in flight and particles go over the top of everything alive.
        profiler.begin(RenderProfiler.Pass.OVERLAY_FX);
        session.fx().drawOverlay(surface, camera);
        profiler.end(RenderProfiler.Pass.OVERLAY_FX);

        profiler.begin(RenderProfiler.Pass.AIR_FX);
        drawAirEffects(surface, session);
        profiler.end(RenderProfiler.Pass.AIR_FX);

        profiler.begin(RenderProfiler.Pass.GHOST);
        drawPlacementGhost(surface, session);
        profiler.end(RenderProfiler.Pass.GHOST);

        profiler.begin(RenderProfiler.Pass.FOG);
        drawFog(surface, session);
        profiler.end(RenderProfiler.Pass.FOG);

        profiler.begin(RenderProfiler.Pass.SELECTION);
        drawSelectionBox(surface);
        profiler.end(RenderProfiler.Pass.SELECTION);

        surface.popClip();
        if (view.isGameOver()) {
            drawOutcome(surface, session);
        }
    }

    // --- terrain --------------------------------------------------------------------------

    private void drawTerrain(Surface surface, GameSession session) {
        Camera camera = session.camera();
        TileMap map = session.world().map();

        int x0 = camera.firstVisibleTileX();
        int y0 = camera.firstVisibleTileY();
        int x1 = camera.lastVisibleTileX();
        int y1 = camera.lastVisibleTileY();

        // Blocks, not tiles. Drawing the ground a tile at a time was 84% of a frame and cost
        // exactly as much with four units on the map as with a thousand.
        int bx0 = Math.floorDiv(x0, TerrainCache.BLOCK_TILES);
        int by0 = Math.floorDiv(y0, TerrainCache.BLOCK_TILES);
        int bx1 = Math.floorDiv(x1, TerrainCache.BLOCK_TILES);
        int by1 = Math.floorDiv(y1, TerrainCache.BLOCK_TILES);

        int drawn = 0;
        for (int by = by0; by <= by1; by++) {
            for (int bx = bx0; bx <= bx1; bx++) {
                Image block = terrainCache.block(map, bx, by);
                int tileX = bx * TerrainCache.BLOCK_TILES;
                int tileY = by * TerrainCache.BLOCK_TILES;
                surface.drawImage(block,
                        camera.screenX(tileX), camera.screenY(tileY),
                        camera.screenX(tileX + TerrainCache.BLOCK_TILES),
                        camera.screenY(tileY + TerrainCache.BLOCK_TILES),
                        sprite);
                drawn++;
            }
        }
        profiler.countDraws(RenderProfiler.Pass.TERRAIN, drawn);
    }

    /** Destination rectangle for a tile, rounded so neighbouring tiles never leave a seam. */
    private void tileRect(Camera camera, int tileX, int tileY) {
        dst.set(Math.round(camera.screenX(tileX)), Math.round(camera.screenY(tileY)),
                Math.round(camera.screenX(tileX + 1)), Math.round(camera.screenY(tileY + 1)));
    }

    // --- entities -------------------------------------------------------------------------

    private void drawEntities(Surface surface, GameSession session) {
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
                drawBuilding(surface, session, (Building) e);
            } else {
                drawUnit(surface, session, (Unit) e);
            }
        }
    }

    private void drawBuilding(Surface surface, GameSession session, Building b) {
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
            sprite.setTint(DEAD_TINT);
        }
        surface.drawImage(atlas.building(b.type(), faction, b.damageState()), dst, sprite);
        if (dead) {
            sprite.setTint(0);
        }

        // A defence with the power cut gets a cold wash and a dead lamp; without this there is
        // no way to tell a blacked-out turret from a live one.
        if (b.type().weapon() != null && !b.isPowered()) {
            flat.setColor(0x66101828);
            surface.fillRect(dst, flat);
            flat.setColor(Palette.POWER_LOW);
            surface.fillRect(left + px * 0.35f, top - px * 0.22f,
                    left + px * 0.65f, top - px * 0.06f, flat);
        }

        // The turret's barrel traverses onto its target.
        if (b.type() == BuildingType.FLAK_TURRET && b.isPowered()) {
            surface.drawImage(atlas.flakBarrel(turretFacing(session, b)), dst, sprite);
        }

        if (dead) {
            drawDisabledArcs(surface, view.tick(), b.id(), left, top, right, bottom, px);
        }
        if (b.isRepairing()) {
            drawRepairMark(surface, session, left, top, right);
        }
        if (!b.isComplete()) {
            drawProgressBar(surface, left, bottom, right - left, b.constructionFraction());
        }
        if (isSelected(session, b)) {
            drawBrackets(surface, left, top, right, bottom, view.isPrimary(b));
        }
        if (b.healthFraction() < 1f) {
            drawHealthBar(surface, left, top - px * 0.16f, right - left, b.healthFraction());
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

    private void drawUnit(Surface surface, GameSession session, Unit u) {
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
        Image bitmap = atlas.unit(u.type(), faction, facingIndex(u.facing()), unitFrame(view, u));

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
            sprite.setTint(DEAD_TINT);
        }
        surface.drawImage(bitmap, dst, sprite);
        sprite.setAlpha(255);
        sprite.setTint(0);

        if (dead) {
            drawDisabledArcs(surface, view.tick(), u.id(), centreX - half, centreY - half,
                    centreX + half, centreY + half, px);
        }
        if (isSelected(session, u)) {
            float r = px * 0.5f;
            drawBrackets(surface, Math.round(cx - r), Math.round(cy - r), Math.round(cx + r),
                    Math.round(cy + r), false);
        }
        if (u.healthFraction() < 1f) {
            drawHealthBar(surface, cx - px * 0.4f, cy - px * 0.55f, px * 0.8f,
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
    private void drawDisabledArcs(Surface surface, int tick, int id, int left, int top, int right,
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
                thick(surface, prevX, prevY, x, y, dot * 2);
                flat.setColor(Palette.ARC_CORE);
                surface.fillRect(x, y, x + dot, y + dot, flat);
                prevX = x;
                prevY = y;
            }
        }
    }

    /** A chunky pixel line between two points, drawn as stepped squares. */
    private void thick(Surface surface, int x0, int y0, int x1, int y1, int size) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / Math.max(1, size / 2) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x0 + (x1 - x0) * t;
            float y = y0 + (y1 - y0) * t;
            surface.fillRect(x, y, x + size, y + size, flat);
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
    private void drawGroundEffects(Surface surface, GameSession session) {
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
            surface.drawImage(atlas.wreck(fx.variant), dst, sprite);
            sprite.setAlpha(255);
        }
    }

    private void drawAirEffects(Surface surface, GameSession session) {
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
                    surface.fillCircle(camera.screenX(fx.x), camera.screenY(fx.y),
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

    private void drawPlacementGhost(Surface surface, GameSession session) {
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
        drawGhostAt(surface, session, type, tileX, tileY);
    }

    /**
     * The build ghost: the structure at half opacity plus a per-tile legality grid, so it is
     * obvious which corner of a footprint is the one hanging over a rock.
     */
    public void drawGhostAt(Surface surface, GameSession session, BuildingType type, int tileX,
                            int tileY) {
        Camera camera = session.camera();
        WorldView view = session.view();
        int originX = tileX - type.tilesWide() / 2;
        int originY = tileY - type.tilesHigh() / 2;

        dst.set(Math.round(camera.screenX(originX)), Math.round(camera.screenY(originY)),
                Math.round(camera.screenX(originX + type.tilesWide())),
                Math.round(camera.screenY(originY + type.tilesHigh())));
        sprite.setAlpha(140);
        surface.drawImage(atlas.building(type, view.faction(), 0), dst, sprite);
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
                surface.fillRect(dst, flat);
                stroke.setColor(tileClear ? 0x9964E064 : 0xCCE05A50);
                surface.strokeRect(dst, stroke);
            }
        }
    }

    private void drawFog(Surface surface, GameSession session) {
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

        // Runs, not tiles. Fog comes in contiguous regions - the far side of the map is one
        // unbroken sheet of unexplored - so filling it a tile at a time asks for hundreds of
        // rectangles where a few dozen say the same thing.
        int drawn = 0;
        for (int y = y0; y <= y1; y++) {
            int runStart = -1;
            byte runState = FogGrid.VISIBLE;

            for (int x = x0; x <= x1 + 1; x++) {
                // One past the end closes whatever run is open.
                byte state = x > x1 ? FogGrid.VISIBLE : fog.state(x, y);
                if (state == runState) {
                    continue;
                }
                if (runStart >= 0) {
                    flat.setColor(runState == FogGrid.UNEXPLORED
                            ? Palette.FOG_UNEXPLORED : Palette.FOG_EXPLORED);
                    surface.fillRect(camera.screenX(runStart), camera.screenY(y),
                            camera.screenX(x), camera.screenY(y + 1), flat);
                    drawn++;
                }
                runStart = state == FogGrid.VISIBLE ? -1 : x;
                runState = state;
            }
        }
        profiler.countDraws(RenderProfiler.Pass.FOG, drawn);
    }

    private void drawSelectionBox(Surface surface) {
        if (!dragging) {
            return;
        }
        float left = Math.min(dragX0, dragX1);
        float top = Math.min(dragY0, dragY1);
        float right = Math.max(dragX0, dragX1);
        float bottom = Math.max(dragY0, dragY1);

        flat.setColor(0x2E9BE07A);
        surface.fillRect(left, top, right, bottom, flat);
        stroke.setColor(Palette.SELECTION);
        stroke.setStrokeWidth(2f);
        surface.fillRect(left, top, right, bottom, stroke);
    }

    /** Corner brackets rather than a ring: they never hide the sprite they mark. */
    private void drawBrackets(Surface surface, int left, int top, int right, int bottom,
                              boolean primary) {
        float arm = Math.max(4f, (right - left) * 0.28f);
        stroke.setColor(primary ? Palette.GOLD : Palette.SELECTION);
        stroke.setStrokeWidth(Math.max(1.8f, (right - left) * 0.05f));

        surface.drawLine(left, top, left + arm, top, stroke);
        surface.drawLine(left, top, left, top + arm, stroke);
        surface.drawLine(right, top, right - arm, top, stroke);
        surface.drawLine(right, top, right, top + arm, stroke);
        surface.drawLine(left, bottom, left + arm, bottom, stroke);
        surface.drawLine(left, bottom, left, bottom - arm, stroke);
        surface.drawLine(right, bottom, right - arm, bottom, stroke);
        surface.drawLine(right, bottom, right, bottom - arm, stroke);
    }

    /** A pulsing cross over a structure the repair crews are working on. */
    private void drawRepairMark(Surface surface, GameSession session, int left, int top,
                                int right) {
        float px = session.camera().tilePx();
        boolean lit = (session.view().tick() / 6) % 2 == 0;
        flat.setColor(lit ? Palette.HEALTH_GOOD : Palette.HUD_TEXT_DIM);
        float cx = (left + right) / 2f;
        float cy = top + px * 0.3f;
        float arm = px * 0.22f;
        surface.fillRect(cx - arm, cy - arm / 3f, cx + arm, cy + arm / 3f, flat);
        surface.fillRect(cx - arm / 3f, cy - arm, cx + arm / 3f, cy + arm, flat);
    }

    private void drawHealthBar(Surface surface, float left, float top, float width,
                               float fraction) {
        float height = Math.max(3f, width * 0.08f);
        flat.setColor(0xCC0C0C0A);
        surface.fillRect(left - 1, top - 1, left + width + 1, top + height + 1, flat);
        flat.setColor(Palette.health(fraction));
        surface.fillRect(left, top, left + width * fraction, top + height, flat);
    }

    private void drawProgressBar(Surface surface, float left, float bottom, float width,
                                 float fraction) {
        flat.setColor(0xCC0C0C0A);
        surface.fillRect(left, bottom + 2, left + width, bottom + 7, flat);
        flat.setColor(Palette.GOLD);
        surface.fillRect(left, bottom + 2, left + width * fraction, bottom + 7, flat);
    }

    private void drawOutcome(Surface surface, GameSession session) {
        boolean won = session.view().hasWon();
        flat.setColor(0xC0000000);
        surface.fillRect(0, 0, surface.width(), surface.height(), flat);

        text.setAlign(TextAlign.CENTER);
        text.setBold(true);
        text.setColor(won ? Palette.GOLD : Palette.REGIME);
        text.setTextSize(surface.height() * 0.13f);
        surface.drawText(won ? "VALLEY HELD" : "OVERRUN", surface.width() / 2f,
                surface.height() * 0.45f, text);

        text.setBold(false);
        text.setColor(Palette.HUD_TEXT);
        text.setTextSize(surface.height() * 0.05f);
        surface.drawText("Tap to start a new skirmish", surface.width() / 2f,
                surface.height() * 0.58f, text);
        text.setAlign(TextAlign.LEFT);
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
