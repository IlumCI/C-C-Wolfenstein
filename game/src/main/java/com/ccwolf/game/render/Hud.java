package com.ccwolf.game.render;

import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.squad.Squad;
import com.ccwolf.core.squad.SquadOrder;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.game.GameSession;
import com.ccwolf.game.art.SpriteAtlas;
import com.ccwolf.game.art.WolfPalette;
import com.ccwolf.gfx.Brush;
import com.ccwolf.gfx.Image;
import com.ccwolf.gfx.Rect;
import com.ccwolf.gfx.Surface;
import com.ccwolf.gfx.TextAlign;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sidebar: riveted iron, a minimap, money and power, three build tabs, and the base
 * management controls.
 *
 * <p>Layout is computed once per surface size into rectangles that are used for both drawing
 * and hit testing, so what you see and what you can tap can never drift apart.
 */
public final class Hud {

    /**
     * Which build list the sidebar is showing.
     *
     * <p>Defences were split out of the structures tab when the roster grew: eight structures
     * do not fit six slots, and paging a build list is worse than naming the two groups.
     */
    public enum Tab { BASE, DEFENCE, INFANTRY, VEHICLES }

    /** One build button: either a structure or a unit, never both. */
    private static final class Slot {
        final Rect rect = new Rect();
        BuildingType building;
        UnitType unit;
    }

    private static final int COLUMNS = 2;
    private static final int ROWS = 3;

    private final Brush paint = new Brush().setAntiAlias(true);
    private final Brush sprite = new Brush();
    private final SpriteAtlas atlas = SpriteAtlas.get();
    private final Minimap minimap = new Minimap();
    private final Rect rect = new Rect();
    private final Rect dst = new Rect();

    private final List<Slot> slots = new ArrayList<Slot>();
    private final Rect[] tabRects = {new Rect(), new Rect(), new Rect(), new Rect()};
    private final Rect stopButton = new Rect();
    private final Rect sellButton = new Rect();
    private final Rect repairButton = new Rect();
    private final Rect pauseButton = new Rect();

    /** The paused overlay's choices, centered over the battlefield. */
    private final Rect pausedResume = new Rect();
    private final Rect pausedSave = new Rect();
    private final Rect pausedAbandon = new Rect();
    private final Rect pausedMute = new Rect();

    private Tab tab = Tab.BASE;
    private float left;
    private float width;
    private int screenWidth;
    private int screenHeight;
    private float scale = 1f;

    /** Height of the strip under the credits plate that carries the army's condition. */
    private static final float STAMINA_BAND_DP = 12f;

    public Hud() {
        sprite.setSmoothScaling(false);
        sprite.setAntiAlias(false);
    }

    public void layout(int screenWidth, int screenHeight, float density) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.scale = Math.max(1f, density);

        width = Math.max(160f * scale, Math.min(screenWidth * 0.24f, 320f * scale));
        left = screenWidth - width;

        float pad = 6f * scale;
        float y = pad;

        float minimapSize = Math.min(width - pad * 2, screenHeight * 0.24f);
        minimap.setBounds(left + (width - minimapSize) / 2f, y, minimapSize);
        y += minimapSize + pad;

        // Resource strip, plus a thin band under it for the army's condition. Adding the band
        // without widening this pushed it under the tab row, where it drew behind the buttons
        // with its label sliced in half.
        y += 22f * scale + STAMINA_BAND_DP * scale + pad;

        float tabWidth = (width - pad * 5) / 4f;
        for (int i = 0; i < tabRects.length; i++) {
            tabRects[i].set(left + pad + i * (tabWidth + pad), y,
                    left + pad + i * (tabWidth + pad) + tabWidth, y + 26f * scale);
        }
        y += 26f * scale + pad;

        buildSlots(y, pad);
    }

    /**
     * Lays out the build grid and the control row.
     *
     * <p>The number of rows is fixed rather than "as many as fit": the structures tab has six
     * entries and every one has to be reachable, so buttons shrink on a short screen instead
     * of falling off the bottom.
     */
    private void buildSlots(float startY, float pad) {
        slots.clear();

        // Two rows of buttons plus their padding, measured rather than guessed: the first
        // attempt reserved too little and pushed SELL and REPAIR off the bottom of the screen.
        float buttonHeight = 24f * scale;
        float controls = buttonHeight * 2 + 16f * scale;
        float gridBottom = screenHeight - controls;
        float slotW = (width - pad * 3) / COLUMNS;
        float readoutHeight = 18f * scale;
        float available = gridBottom - startY - readoutHeight;
        float slotH = Math.min(slotW * 0.58f, available / ROWS - pad);

        for (int row = 0; row < ROWS; row++) {
            float y = startY + row * (slotH + pad);
            for (int col = 0; col < COLUMNS; col++) {
                Slot slot = new Slot();
                float x = left + pad + col * (slotW + pad);
                slot.rect.set(x, y, x + slotW, y + slotH);
                slots.add(slot);
            }
        }

        // Two rows of controls: orders on top, base management under them.
        float buttonH = buttonHeight;
        float halfW = (width - pad * 3) / 2f;
        stopButton.set(left + pad, gridBottom + pad, left + pad + halfW,
                gridBottom + pad + buttonH);
        pauseButton.set(left + pad * 2 + halfW, gridBottom + pad, left + width - pad,
                gridBottom + pad + buttonH);
        float secondRow = gridBottom + pad + buttonH + 4f * scale;
        sellButton.set(left + pad, secondRow, left + pad + halfW, secondRow + buttonH);
        repairButton.set(left + pad * 2 + halfW, secondRow, left + width - pad,
                secondRow + buttonH);
    }

    public float sidebarLeft() {
        return left;
    }

    public float sidebarWidth() {
        return width;
    }

    public boolean contains(float x, float y) {
        return x >= left;
    }

    public Minimap minimap() {
        return minimap;
    }

    public Tab tab() {
        return tab;
    }

    public void setTab(Tab tab) {
        this.tab = tab;
    }

    public Rect tabRect(int index) {
        return tabRects[index];
    }

    // --- drawing --------------------------------------------------------------------------

    public void draw(Surface surface, GameSession session, long nowMs) {
        WorldView view = session.view();

        drawIronPanel(surface);
        minimap.draw(surface, session);
        drawMinimapFrame(surface);
        drawResources(surface, view);
        drawTabs(surface);
        drawSlots(surface, session);
        drawSelectionReadout(surface, session);
        drawControls(surface, session);
        drawToast(surface, session, nowMs);
        drawPausedOverlay(surface, session);
    }

    /**
     * The paused screen: the battlefield dimmed under two honest choices. RESUME is the
     * pause button's twin; ABANDON THE FIELD is the way back to the title, spelled out so
     * nobody discovers it by accident with an army in play.
     */
    private void drawPausedOverlay(Surface surface, GameSession session) {
        if (!session.isPaused() || session.world().isGameOver()) {
            return;
        }
        paint.setColor(0x8C000000);
        surface.fillRect(0, 0, left, screenHeight, paint);

        float cx = left / 2f;
        float cy = screenHeight * 0.4f;
        paint.setColor(Palette.HUD_TEXT);
        paint.setTextSize(22f * scale);
        paint.setBold(true);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText("PAUSED", cx, cy, paint);
        paint.setBold(false);

        float bw = 110f * scale;
        float bh = 22f * scale;
        pausedResume.set(cx - bw, cy + 18f * scale, cx + bw, cy + 18f * scale + bh);
        pausedSave.set(cx - bw, pausedResume.bottom + 8f * scale,
                cx + bw, pausedResume.bottom + 8f * scale + bh);
        pausedAbandon.set(cx - bw, pausedSave.bottom + 8f * scale,
                cx + bw, pausedSave.bottom + 8f * scale + bh);
        pausedMute.set(cx - bw, pausedAbandon.bottom + 8f * scale,
                cx + bw, pausedAbandon.bottom + 8f * scale + bh);
        drawButton(surface, pausedResume, "RESUME", true, false);
        drawButton(surface, pausedSave, "SAVE THE FRONT", true, false);
        drawButton(surface, pausedAbandon, "ABANDON THE FIELD", true, true);
        // The one place both platforms can reach the mute: phones have no M key.
        drawButton(surface, pausedMute,
                com.ccwolf.game.audio.GameAudio.isMuted() ? "SOUND: OFF" : "SOUND: ON",
                true, false);
        paint.setAlign(TextAlign.LEFT);
    }

    /** True when a tap landed on the paused overlay's RESUME. Only meaningful while paused. */
    public boolean pausedResumeHit(float x, float y) {
        return pausedResume.contains(x, y);
    }

    /** True when a tap landed on ABANDON THE FIELD. Only meaningful while paused. */
    public boolean pausedAbandonHit(float x, float y) {
        return pausedAbandon.contains(x, y);
    }

    /** True when a tap landed on the sound toggle. Only meaningful while paused. */
    public boolean pausedMuteHit(float x, float y) {
        return pausedMute.contains(x, y);
    }

    /** True when a tap landed on SAVE THE FRONT. Only meaningful while paused. */
    public boolean pausedSaveHit(float x, float y) {
        return pausedSave.contains(x, y);
    }

    /** Riveted plate, so the interface looks like it was bolted together in a workshop. */
    private void drawIronPanel(Surface surface) {
        paint.setColor(Palette.HUD_BG);
        surface.fillRect(left, 0, screenWidth, screenHeight, paint);

        // Vertical seam against the battlefield, lit on its left edge.
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        surface.fillRect(left, 0, left + 2f * scale, screenHeight, paint);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        surface.fillRect(left + 2f * scale, 0, left + 3f * scale, screenHeight, paint);

        // Rivets down the seam.
        float rivetX = left + 7f * scale;
        for (float y = 12f * scale; y < screenHeight; y += 26f * scale) {
            paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 0));
            surface.fillCircle(rivetX, y, 2f * scale, paint);
            paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 3));
            surface.fillCircle(rivetX + 0.6f * scale, y + 0.6f * scale, 1.2f * scale, paint);
        }
    }

    /**
     * How worn the army is, under the credits plate.
     *
     * <p>The campaign-scale counterpart to a squad's morale, and the one number that says
     * whether a long match is still even. Without it the player has no way of knowing that
     * their army is the tired one.
     */
    private void drawStamina(Surface surface, WorldView view, float y) {
        float pad = 6f * scale;
        float labelWidth = 34f * scale;
        float barLeft = left + pad + labelWidth;
        float barRight = screenWidth - pad;
        float top = y + 3f * scale;
        float bottom = top + 5f * scale;

        paint.setTextSize(9f * scale);
        paint.setColor(Palette.HUD_TEXT_DIM);
        paint.setAlign(TextAlign.LEFT);
        surface.drawText("ARMY", left + pad, bottom, paint);

        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        surface.fillRect(barLeft, top, barRight, bottom, paint);
        float stamina = view.stamina();
        paint.setColor(stamina > 0.6f ? Palette.HEALTH_GOOD
                : (stamina > 0.3f ? Palette.HEALTH_FAIR : Palette.HEALTH_POOR));
        surface.fillRect(barLeft, top, barLeft + (barRight - barLeft) * stamina, bottom, paint);
    }

    private void drawMinimapFrame(Surface surface) {
        Rect bounds = minimap.bounds();
        paint.setStrokeWidth(2f * scale);
        paint.setColor(WolfPalette.shade(WolfPalette.BRASS, 2));
        surface.strokeRect(bounds.left - 2, bounds.top - 2, bounds.right + 2, bounds.bottom + 2,
                paint);
    }

    private void drawResources(Surface surface, WorldView view) {
        float pad = 6f * scale;
        float y = minimap.bounds().bottom + pad;

        rect.set(left + pad, y, screenWidth - pad, y + 22f * scale);
        drawPlate(surface, rect, false);
        drawStamina(surface, view, y + 22f * scale);

        paint.setColor(Palette.GOLD);
        paint.setTextSize(15f * scale);
        paint.setBold(true);
        surface.drawText(String.format(Locale.ROOT, "%,d", view.credits()),
                left + pad * 2, y + 16f * scale, paint);
        paint.setBold(false);

        float barLeft = left + width * 0.52f;
        float barRight = screenWidth - pad * 2;
        paint.setColor(0xFF15160F);
        surface.fillRect(barLeft, y + 6f * scale, barRight, y + 16f * scale, paint);

        float ratio = view.powerProduced() == 0 ? 1f
                : Math.min(1f, view.powerDrawn() / (float) view.powerProduced());
        paint.setColor(view.isLowPower() ? Palette.POWER_LOW : Palette.POWER_OK);
        surface.fillRect(barLeft, y + 6f * scale,
                barLeft + (barRight - barLeft) * ratio, y + 16f * scale, paint);

        paint.setColor(view.isLowPower() ? Palette.POWER_LOW : Palette.HUD_TEXT_DIM);
        paint.setTextSize(9f * scale);
        surface.drawText(view.isLowPower() ? "LOW POWER" : "POWER", barLeft, y + 5f * scale,
                paint);
    }

    private void drawTabs(Surface surface) {
        String[] labels = {"BASE", "DEF", "INF", "VEH"};
        paint.setTextSize(10.5f * scale);
        paint.setAlign(TextAlign.CENTER);
        for (int i = 0; i < tabRects.length; i++) {
            boolean active = tab.ordinal() == i;
            drawPlate(surface, tabRects[i], active);
            paint.setColor(active ? Palette.GOLD : Palette.HUD_TEXT_DIM);
            surface.drawText(labels[i], tabRects[i].centerX(),
                    tabRects[i].centerY() + 4f * scale, paint);
        }
        paint.setAlign(TextAlign.LEFT);
    }

    /** A bevelled iron plate: light top-left, dark bottom-right, same as the sprites. */
    private void drawPlate(Surface surface, Rect r, boolean lit) {
        paint.setColor(lit ? Palette.HUD_PANEL_LIT : Palette.HUD_PANEL);
        surface.fillRect(r, paint);
        paint.setStrokeWidth(1.5f * scale);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, lit ? 0 : 1));
        surface.drawLine(r.left, r.top, r.right, r.top, paint);
        surface.drawLine(r.left, r.top, r.left, r.bottom, paint);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        surface.drawLine(r.left, r.bottom, r.right, r.bottom, paint);
        surface.drawLine(r.right, r.top, r.right, r.bottom, paint);
    }

    private void drawSlots(Surface surface, GameSession session) {
        assignSlots(session);
        WorldView view = session.view();
        Faction faction = view.faction();

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.building == null && slot.unit == null) {
                continue;
            }

            // One source of truth for "can I build this, and if not, why not".
            String blocker = slot.building != null ? view.blockerFor(slot.building)
                    : view.blockerFor(slot.unit);
            boolean available = blocker == null;
            int cost = slot.building != null ? slot.building.cost() : slot.unit.cost();

            drawPlate(surface, slot.rect, available);
            surface.pushClip(slot.rect);
            drawSlotIcon(surface, slot, faction, available);
            surface.popClip();

            // Name and price sit on their own strip, so a tall icon cannot collide with them.
            float stripTop = slot.rect.bottom - 21f * scale;
            paint.setColor(0xCC101109);
            surface.fillRect(slot.rect.left, stripTop, slot.rect.right, slot.rect.bottom, paint);

            paint.setTextSize(8.5f * scale);
            paint.setAlign(TextAlign.CENTER);
            paint.setColor(available ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM);
            String name = slot.building != null ? slot.building.displayName()
                    : slot.unit.displayName();
            surface.drawText(ellipsize(surface, name, slot.rect.width() - 4f * scale), slot.rect.centerX(),
                    stripTop + 9.5f * scale, paint);

            // A dead button says why it is dead, instead of leaving the player guessing.
            if (blocker != null && !blocker.startsWith("Needs " + cost)) {
                paint.setColor(Palette.HUD_TEXT_DIM);
                surface.drawText(ellipsize(surface, blocker, slot.rect.width() - 4f * scale),
                        slot.rect.centerX(), slot.rect.bottom - 3f * scale, paint);
            } else {
                paint.setColor(view.credits() >= cost ? Palette.GOLD : Palette.HEALTH_POOR);
                surface.drawText(String.valueOf(cost), slot.rect.centerX(),
                        slot.rect.bottom - 3f * scale, paint);
            }
            paint.setAlign(TextAlign.LEFT);

            drawSlotProgress(surface, session, slot);
        }
    }

    /** Build buttons show the actual game sprite, so the icon and the unit always match. */
    private void drawSlotIcon(Surface surface, Slot slot, Faction faction, boolean available) {
        Image icon;
        float boxH = slot.rect.height() * 0.62f;
        float cx = slot.rect.centerX();
        float cy = slot.rect.top + slot.rect.height() * 0.32f;

        if (slot.building != null) {
            icon = atlas.building(slot.building, faction, 0);
        } else {
            icon = atlas.unit(slot.unit, faction, 2, slot.unit.isHarvester() ? 2 : 0);
        }

        float aspect = icon.width() / (float) icon.height();
        float h = boxH;
        float w = h * aspect;
        if (w > slot.rect.width() * 0.8f) {
            w = slot.rect.width() * 0.8f;
            h = w / aspect;
        }
        dst.set(Math.round(cx - w / 2f), Math.round(cy - h / 2f),
                Math.round(cx + w / 2f), Math.round(cy + h / 2f));
        sprite.setAlpha(available ? 255 : 110);
        surface.drawImage(icon, dst, sprite);
        sprite.setAlpha(255);
    }

    /** Queue depth, build progress and the ready-to-place state. */
    private void drawSlotProgress(Surface surface, GameSession session, Slot slot) {
        WorldView view = session.view();
        ProductionQueue queue = slot.building != null ? view.structureQueue()
                : view.queueFor(slot.unit.producedBy());

        int queued = 0;
        for (int i = 0; i < queue.items().size(); i++) {
            ProductionItem item = queue.items().get(i);
            boolean match = slot.building != null ? item.buildingType() == slot.building
                    : item.unitType() == slot.unit;
            if (match) {
                queued++;
            }
        }
        if (queued == 0) {
            return;
        }

        ProductionItem head = queue.head();
        boolean isHead = head != null && (slot.building != null
                ? head.buildingType() == slot.building : head.unitType() == slot.unit);

        if (isHead && head.isFinished()) {
            paint.setColor(0x99000000);
            surface.fillRect(slot.rect, paint);
            paint.setColor(Palette.SELECTION);
            paint.setAlign(TextAlign.CENTER);
            paint.setTextSize(12f * scale);
            surface.drawText("READY", slot.rect.centerX(), slot.rect.centerY(), paint);
            paint.setAlign(TextAlign.LEFT);
        } else if (isHead) {
            // Fills bottom-up like a C&C build clock.
            float p = head.progress();
            paint.setColor(0x77000000);
            surface.fillRect(slot.rect.left, slot.rect.top,
                    slot.rect.right, slot.rect.bottom - slot.rect.height() * p, paint);
        }

        if (queued > 1) {
            paint.setColor(Palette.HUD_TEXT);
            paint.setTextSize(11f * scale);
            surface.drawText("x" + queued, slot.rect.left + 3f * scale,
                    slot.rect.top + 12f * scale, paint);
        }
    }

    private void drawSelectionReadout(Surface surface, GameSession session) {
        WorldView view = session.view();
        float y = stopButton.top - 7f * scale;
        paint.setTextSize(10f * scale);

        Entity single = session.singleSelection();
        String textLine;
        if (single != null) {
            textLine = single.displayName() + "  " + single.hp() + "/" + single.maxHp();
            if (single instanceof Unit) {
                Unit u = (Unit) single;
                if (u.type().isHarvester()) {
                    textLine += "  [" + u.oreCarried() + "/" + u.oreCapacity() + "]";
                }
            } else {
                Building b = (Building) single;
                if (view.isPrimary(b)) {
                    textLine += "  PRIMARY";
                } else if (b.type().isProducer()) {
                    textLine += "  (tap again: primary)";
                }
                if (b.isRepairing()) {
                    textLine += "  REPAIRING";
                }
            }
        } else if (session.hasSquadSelection()) {
            Squad squad = session.singleSelectedSquad();
            if (squad != null) {
                drawSquadCard(surface, squad, left, y, width);
                return;
            }
            textLine = session.selectedSquads().size() + " squads selected";
        } else if (session.hasSelection()) {
            textLine = session.selection().size() + " units selected";
        } else {
            textLine = "Nothing selected";
        }
        paint.setColor(Palette.HUD_TEXT_DIM);
        surface.drawText(ellipsize(surface, textLine, width - 12f * scale), left + 6f * scale, y, paint);
    }

    /**
     * What is left of a squad, at a glance.
     *
     * <p>Strength as pips rather than a number: a glance at a row of lamps says "five of eight"
     * faster than reading it does, and a squad that has been chewed up should be obvious
     * without counting.
     */
    private void drawSquadCard(Surface surface, Squad squad, float left, float y, float width) {
        // Everything sits at or above the baseline the single-line version used. Below it is
        // the button row, and the first attempt drew the strength line straight through it.
        paint.setColor(Palette.HUD_TEXT);
        paint.setTextSize(11f * scale);
        surface.drawText(ellipsize(surface, squad.type().displayName() + " Squad",
                width - 12f * scale), left + 6f * scale, y - 24f * scale, paint);

        // One pip per slot: lit for a man still standing, dark for a gap.
        float pipSize = 5f * scale;
        float gap = 2f * scale;
        float pipY = y - 18f * scale;
        for (int slot = 0; slot < squad.slotCount(); slot++) {
            float px = left + 6f * scale + slot * (pipSize + gap);
            paint.setColor(squad.memberAt(slot) >= 0
                    ? Palette.HEALTH_GOOD : WolfPalette.shade(WolfPalette.GUNMETAL, 3));
            surface.fillRect(px, pipY, px + pipSize, pipY + pipSize, paint);
        }

        // Morale, because a squad's nerve decides whether it holds and there is no way to
        // read it off the men themselves.
        float barLeft = left + 6f * scale;
        float barRight = left + width - 12f * scale;
        float barTop = y - 10f * scale;
        float barBottom = barTop + 3f * scale;
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        surface.fillRect(barLeft, barTop, barRight, barBottom, paint);
        paint.setColor(squad.isBroken() ? Palette.HEALTH_POOR
                : (squad.morale() < 40 ? Palette.HEALTH_FAIR : Palette.HEALTH_GOOD));
        surface.fillRect(barLeft, barTop,
                barLeft + (barRight - barLeft) * squad.morale() / 100f, barBottom, paint);

        paint.setColor(squad.isBroken() ? Palette.HEALTH_POOR : Palette.HUD_TEXT_DIM);
        paint.setTextSize(10f * scale);
        String state = squad.isBroken() ? "BROKEN - falling back"
                : squad.strength() + "/" + squad.initialStrength()
                        + "   " + (squad.order() == SquadOrder.ENTRENCH ? "DUG IN"
                                : squad.formation().name());
        if (!squad.isBroken() && squad.shortfall() > 0) {
            state += "   -" + squad.shortfall();
        }
        surface.drawText(ellipsize(surface, state, width - 12f * scale),
                left + 6f * scale, y, paint);
    }

    private void drawControls(Surface surface, GameSession session) {
        GameSession.PointerMode mode = session.pointerMode();
        // The same plate, saying what it will actually do. A squad told to stop digs in; a
        // battery told to stop has nothing useful to stop doing, so it offers the one order
        // that only it can take. There is no room for a fifth button - buildSlots reserves
        // exactly two rows, with a comment recording that an earlier attempt pushed SELL and
        // REPAIR off the bottom of the screen.
        drawButton(surface, stopButton,
                session.hasArtillerySelection() ? "BOMBARD"
                        : session.hasSquadSelection() ? "DIG IN" : "STOP",
                session.hasSelection(), mode == GameSession.PointerMode.BOMBARD);
        drawButton(surface, pauseButton, session.isPaused() ? "RESUME" : "PAUSE", true, false);
        // With a squad up, the two structure controls give way to the two that act on it.
        // There is no room on a phone for both sets, and they are never wanted at once.
        if (session.hasSquadSelection()) {
            Squad squad = session.singleSelectedSquad();
            drawButton(surface, sellButton, "FORM", squad != null, false);
            drawButton(surface, repairButton,
                    squad != null && squad.shortfall() > 0 ? "REINFORCE" : "BREAK UP",
                    squad != null, false);
            return;
        }
        drawButton(surface, sellButton, "SELL", true,
                mode == GameSession.PointerMode.SELL);
        drawButton(surface, repairButton, "REPAIR", true,
                mode == GameSession.PointerMode.REPAIR);
    }

    private void drawButton(Surface surface, Rect r, String label, boolean enabled,
                            boolean armed) {
        drawPlate(surface, r, armed);
        paint.setColor(armed ? Palette.GOLD
                : (enabled ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM));
        paint.setTextSize(11f * scale);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText(label, r.centerX(), r.centerY() + 4f * scale, paint);
        paint.setAlign(TextAlign.LEFT);
    }

    private void drawToast(Surface surface, GameSession session, long nowMs) {
        if (!session.hasMessage(nowMs)) {
            return;
        }
        paint.setTextSize(14f * scale);
        float textWidth = surface.measureText(session.message(), paint);
        float cx = left / 2f;
        float y = screenHeight - 28f * scale;
        paint.setColor(0xC0101109);
        surface.fillRect(cx - textWidth / 2f - 10f * scale, y - 18f * scale,
                cx + textWidth / 2f + 10f * scale, y + 6f * scale, paint);
        paint.setColor(Palette.GOLD);
        paint.setAlign(TextAlign.CENTER);
        surface.drawText(session.message(), cx, y, paint);
        paint.setAlign(TextAlign.LEFT);
    }

    // --- interaction ----------------------------------------------------------------------

    /**
     * Handles a tap inside the sidebar.
     *
     * @param longPress true for a long press, which cancels the last queued item
     * @return true if the tap was consumed
     */
    public boolean handleTap(float x, float y, GameSession session, boolean longPress) {
        if (!contains(x, y)) {
            return false;
        }

        for (int i = 0; i < tabRects.length; i++) {
            if (tabRects[i].contains(x, y)) {
                tab = Tab.values()[i];
                return true;
            }
        }

        if (stopButton.contains(x, y)) {
            // Kept in step with drawControls, which the file's own comment warns about.
            if (session.hasArtillerySelection()) {
                session.togglePointerMode(GameSession.PointerMode.BOMBARD);
            } else {
                session.stopSelection();
            }
            return true;
        }
        if (pauseButton.contains(x, y)) {
            session.setPaused(!session.isPaused());
            return true;
        }
        // The same two plates carry the squad controls when a squad is up, matching what
        // drawControls put there.
        if (session.hasSquadSelection()) {
            if (sellButton.contains(x, y)) {
                session.cycleFormation();
                return true;
            }
            if (repairButton.contains(x, y)) {
                Squad squad = session.singleSelectedSquad();
                if (squad != null && squad.shortfall() > 0) {
                    session.reinforceSelection();
                } else {
                    session.breakUpSelection();
                }
                return true;
            }
        } else {
            if (sellButton.contains(x, y)) {
                session.togglePointerMode(GameSession.PointerMode.SELL);
                return true;
            }
            if (repairButton.contains(x, y)) {
                session.togglePointerMode(GameSession.PointerMode.REPAIR);
                return true;
            }
        }

        assignSlots(session);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!slot.rect.contains(x, y)) {
                continue;
            }
            if (slot.building != null) {
                handleStructureSlot(session, slot.building, longPress);
            } else if (slot.unit != null) {
                if (longPress) {
                    session.cancelLast(slot.unit.producedBy() == BuildingType.WAR_WORKS
                            ? PlayerCommand.Line.VEHICLE : PlayerCommand.Line.INFANTRY);
                } else {
                    session.queueUnit(slot.unit);
                }
            }
            return true;
        }
        return true; // Anything else in the sidebar is consumed, never falls through.
    }

    private void handleStructureSlot(GameSession session, BuildingType type, boolean longPress) {
        if (longPress) {
            session.cancelLast(PlayerCommand.Line.STRUCTURE);
            session.setPlacing(null);
            return;
        }
        if (session.readyStructure() == type) {
            session.setPlacing(type);
            session.showMessage("Tap the ground to place the " + type.displayName());
            return;
        }
        session.queueBuilding(type);
    }

    /** Fills the slot list for the active tab. */
    private void assignSlots(GameSession session) {
        Faction faction = session.view().faction();
        int index = 0;

        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).building = null;
            slots.get(i).unit = null;
        }

        if (tab == Tab.BASE || tab == Tab.DEFENCE) {
            BuildingType[] all = BuildingType.values();
            for (int i = 0; i < all.length && index < slots.size(); i++) {
                BuildingType type = all[i];
                if (type == BuildingType.COMMAND_POST) {
                    continue; // Pre-placed; you never build another.
                }
                boolean defence = type.weapon() != null;
                if (defence != (tab == Tab.DEFENCE)) {
                    continue;
                }
                slots.get(index++).building = type;
            }
        } else {
            BuildingType producer = tab == Tab.INFANTRY
                    ? BuildingType.BARRACKS : BuildingType.WAR_WORKS;
            UnitType[] all = UnitType.values();
            for (int i = 0; i < all.length && index < slots.size(); i++) {
                UnitType type = all[i];
                // Aircraft share the vehicle tab: they are machines from a pad the way tanks
                // are machines from the works, and four tabs is already the plate's limit.
                boolean listed = type.producedBy() == producer
                        || (tab == Tab.VEHICLES && type.producedBy() == BuildingType.HELIPAD);
                if (listed && type.availableTo(faction)) {
                    slots.get(index++).unit = type;
                }
            }
        }
    }

    /**
     * Trims a label to fit, with an ellipsis.
     *
     * <p>Takes the surface because text width depends on the platform's font, which is the one
     * thing about drawing text the game cannot work out for itself.
     */
    private String ellipsize(Surface surface, String value, float maxWidth) {
        if (surface.measureText(value, paint) <= maxWidth) {
            return value;
        }
        String out = value;
        while (out.length() > 1 && surface.measureText(out + "…", paint) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }
}
