package com.ccwolf.android.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import com.ccwolf.android.GameSession;
import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.sim.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sidebar: money and power, the minimap, the three build tabs and their buttons, and a
 * readout of whatever is currently selected.
 *
 * <p>Layout is recomputed whenever the surface changes size and cached as rectangles, which
 * are then used both for drawing and for hit testing, so the two can never disagree.
 */
public final class Hud {

    /** Which build list the sidebar is showing. */
    public enum Tab { STRUCTURES, INFANTRY, VEHICLES }

    /** One build button: either a structure or a unit, never both. */
    private static final class Slot {
        final RectF rect = new RectF();
        BuildingType building;
        UnitType unit;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Sprites sprites = new Sprites();
    private final Minimap minimap = new Minimap();
    private final RectF rect = new RectF();

    private final List<Slot> slots = new ArrayList<Slot>();
    private final RectF[] tabRects = {new RectF(), new RectF(), new RectF()};
    private final RectF stopButton = new RectF();
    private final RectF pauseButton = new RectF();

    /** The build grid is a fixed 2x3: six slots is enough for the longest tab. */
    private static final int COLUMNS = 2;
    private static final int ROWS = 3;

    private Tab tab = Tab.STRUCTURES;
    private float left;
    private float width;
    private int screenWidth;
    private int screenHeight;
    private float scale = 1f;

    public void layout(int screenWidth, int screenHeight, float density) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.scale = Math.max(1f, density);

        width = Math.max(160f * scale, Math.min(screenWidth * 0.24f, 320f * scale));
        left = screenWidth - width;

        float pad = 6f * scale;
        float y = pad;

        // The minimap is deliberately smaller than the sidebar is wide: the build buttons
        // matter more, and every structure has to be reachable without scrolling.
        float minimapSize = Math.min(width - pad * 2, screenHeight * 0.24f);
        minimap.setBounds(left + (width - minimapSize) / 2f, y, minimapSize);
        y += minimapSize + pad;

        // Resource strip sits under the minimap.
        y += 22f * scale + pad;

        float tabWidth = (width - pad * 4) / 3f;
        for (int i = 0; i < tabRects.length; i++) {
            tabRects[i].set(left + pad + i * (tabWidth + pad), y,
                    left + pad + i * (tabWidth + pad) + tabWidth, y + 26f * scale);
        }
        y += 26f * scale + pad;

        buildSlots(y, pad);
    }

    /**
     * Lays out the build grid.
     *
     * <p>The number of rows is fixed rather than "as many as fit": the structures tab has six
     * entries and every one of them has to be reachable, so the buttons shrink on a short
     * screen instead of quietly falling off the bottom.
     */
    private void buildSlots(float startY, float pad) {
        slots.clear();

        float bottomReserved = 70f * scale;
        float gridBottom = screenHeight - bottomReserved;
        float slotW = (width - pad * 3) / COLUMNS;
        // Leave a strip under the grid for the selection readout, or the bottom row's price
        // tag and the readout text end up on the same pixels.
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

        stopButton.set(left + pad, gridBottom + pad,
                left + width / 2f - pad, gridBottom + pad + 30f * scale);
        pauseButton.set(left + width / 2f, gridBottom + pad,
                left + width - pad, gridBottom + pad + 30f * scale);
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

    /** Screen rectangle of one of the three tab buttons; used for hit-testing in tests. */
    public RectF tabRect(int index) {
        return tabRects[index];
    }

    // --- drawing --------------------------------------------------------------------------

    public void draw(Canvas canvas, GameSession session, long nowMs) {
        Player me = session.view().player();

        paint.setColor(Palette.HUD_BG);
        canvas.drawRect(left, 0, screenWidth, screenHeight, paint);
        paint.setColor(Palette.HUD_BORDER);
        canvas.drawRect(left, 0, left + 2f, screenHeight, paint);

        minimap.draw(canvas, session);
        drawResources(canvas, me);
        drawTabs(canvas);
        drawSlots(canvas, session);
        drawSelectionReadout(canvas, session);
        drawButtons(canvas, session);
        drawToast(canvas, session, nowMs);
    }

    private void drawResources(Canvas canvas, Player me) {
        float pad = 6f * scale;
        float y = minimap.bounds().bottom + pad;

        paint.setColor(Palette.HUD_PANEL);
        rect.set(left + pad, y, screenWidth - pad, y + 22f * scale);
        canvas.drawRect(rect, paint);

        paint.setColor(Palette.GOLD);
        paint.setTextSize(15f * scale);
        paint.setFakeBoldText(true);
        canvas.drawText(String.format(Locale.ROOT, "%,d", me.credits()),
                left + pad * 2, y + 16f * scale, paint);
        paint.setFakeBoldText(false);

        // Power bar: fills green while there is headroom, orange once browned out.
        float barLeft = left + width * 0.52f;
        float barRight = screenWidth - pad * 2;
        paint.setColor(0xFF15160F);
        canvas.drawRect(barLeft, y + 6f * scale, barRight, y + 16f * scale, paint);

        float ratio = me.powerProduced() == 0 ? 1f
                : Math.min(1f, me.powerDrawn() / (float) me.powerProduced());
        paint.setColor(me.isLowPower() ? Palette.POWER_LOW : Palette.POWER_OK);
        canvas.drawRect(barLeft, y + 6f * scale,
                barLeft + (barRight - barLeft) * ratio, y + 16f * scale, paint);

        paint.setColor(Palette.HUD_TEXT_DIM);
        paint.setTextSize(9f * scale);
        canvas.drawText("POWER", barLeft, y + 5f * scale, paint);
    }

    private void drawTabs(Canvas canvas) {
        String[] labels = {"BUILD", "INF", "VEH"};
        paint.setTextSize(12f * scale);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < tabRects.length; i++) {
            boolean active = tab.ordinal() == i;
            paint.setColor(active ? Palette.HUD_PANEL_LIT : Palette.HUD_PANEL);
            canvas.drawRect(tabRects[i], paint);
            paint.setColor(active ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM);
            canvas.drawText(labels[i], tabRects[i].centerX(),
                    tabRects[i].centerY() + 4f * scale, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSlots(Canvas canvas, GameSession session) {
        assignSlots(session);
        WorldView view = session.view();
        Player me = view.player();
        Faction faction = me.faction();

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
            boolean affordable = me.canAfford(cost);

            paint.setColor(available ? Palette.HUD_PANEL : Palette.HUD_BG);
            canvas.drawRect(slot.rect, paint);

            // Icon.
            canvas.save();
            canvas.clipRect(slot.rect);
            if (slot.building != null) {
                float size = slot.rect.height() * 0.48f;
                sprites.drawBuildingIcon(canvas, slot.building, faction,
                        slot.rect.centerX() - size / 2f, slot.rect.top + 4f * scale, size);
            } else {
                // A unit icon is drawn at "tile size", and a soldier only fills about half a
                // tile, so the icon has to be scaled well past the button height to read.
                sprites.drawUnitIcon(canvas, slot.unit, faction, slot.rect.centerX(),
                        slot.rect.top + slot.rect.height() * 0.32f, slot.rect.height() * 0.9f);
            }
            canvas.restore();

            // Name and price sit on their own strip, so a tall icon can never collide with them.
            float stripTop = slot.rect.bottom - 21f * scale;
            paint.setColor(0xCC101109);
            canvas.drawRect(slot.rect.left, stripTop, slot.rect.right, slot.rect.bottom, paint);

            paint.setTextSize(8.5f * scale);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(available ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM);
            String name = slot.building != null ? slot.building.displayName()
                    : slot.unit.displayName();
            canvas.drawText(ellipsize(name, slot.rect.width() - 4f * scale), slot.rect.centerX(),
                    stripTop + 9.5f * scale, paint);
            paint.setColor(affordable ? Palette.GOLD : Palette.HEALTH_POOR);
            canvas.drawText(String.valueOf(cost), slot.rect.centerX(),
                    slot.rect.bottom - 3f * scale, paint);
            paint.setTextAlign(Paint.Align.LEFT);

            drawSlotProgress(canvas, session, slot);

            paint.setColor(Palette.HUD_BORDER);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(slot.rect, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Shows queue depth, build progress, and the "ready to place" state on a slot. */
    private void drawSlotProgress(Canvas canvas, GameSession session, Slot slot) {
        Player me = session.view().player();
        ProductionQueue queue = slot.building != null ? me.structureQueue()
                : me.queueFor(slot.unit.producedBy());

        int queued = 0;
        ProductionItem head = queue.head();
        boolean isHead = false;
        for (int i = 0; i < queue.items().size(); i++) {
            ProductionItem item = queue.items().get(i);
            boolean match = slot.building != null ? item.buildingType() == slot.building
                    : item.unitType() == slot.unit;
            if (match) {
                queued++;
            }
        }
        if (head != null) {
            isHead = slot.building != null ? head.buildingType() == slot.building
                    : head.unitType() == slot.unit;
        }
        if (queued == 0) {
            return;
        }

        if (isHead && head.isFinished()) {
            paint.setColor(0x99000000);
            canvas.drawRect(slot.rect, paint);
            paint.setColor(Palette.SELECTION);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(12f * scale);
            canvas.drawText("READY", slot.rect.centerX(), slot.rect.centerY(), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        } else if (isHead) {
            float p = head.progress();
            paint.setColor(0x77000000);
            canvas.drawRect(slot.rect.left, slot.rect.top,
                    slot.rect.right, slot.rect.bottom - slot.rect.height() * p, paint);
        }

        if (queued > 1) {
            paint.setColor(Palette.HUD_TEXT);
            paint.setTextSize(11f * scale);
            canvas.drawText("x" + queued, slot.rect.left + 3f * scale,
                    slot.rect.top + 12f * scale, paint);
        }
    }

    private void drawSelectionReadout(Canvas canvas, GameSession session) {
        float y = stopButton.top - 8f * scale;
        paint.setTextSize(11f * scale);

        Entity single = session.singleSelection();
        String text;
        if (single != null) {
            text = single.displayName() + "  " + single.hp() + "/" + single.maxHp();
            if (single instanceof Unit) {
                Unit u = (Unit) single;
                if (u.type().isHarvester()) {
                    text += "  [" + u.oreCarried() + "/" + u.oreCapacity() + "]";
                }
            }
        } else if (session.hasSelection()) {
            text = session.selection().size() + " units selected";
        } else {
            text = "Nothing selected";
        }
        paint.setColor(Palette.HUD_TEXT_DIM);
        canvas.drawText(ellipsize(text, width - 12f * scale), left + 6f * scale, y, paint);
    }

    private void drawButtons(Canvas canvas, GameSession session) {
        drawButton(canvas, stopButton, "STOP", session.hasSelection());
        drawButton(canvas, pauseButton, session.isPaused() ? "RESUME" : "PAUSE", true);
    }

    private void drawButton(Canvas canvas, RectF r, String label, boolean enabled) {
        paint.setColor(enabled ? Palette.HUD_PANEL : Palette.HUD_BG);
        canvas.drawRect(r, paint);
        paint.setColor(Palette.HUD_BORDER);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(enabled ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM);
        paint.setTextSize(11f * scale);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, r.centerX(), r.centerY() + 4f * scale, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawToast(Canvas canvas, GameSession session, long nowMs) {
        if (!session.hasMessage(nowMs)) {
            return;
        }
        paint.setTextSize(14f * scale);
        float textWidth = paint.measureText(session.message());
        float cx = left / 2f;
        float y = screenHeight - 28f * scale;
        paint.setColor(0xB0101010);
        canvas.drawRect(cx - textWidth / 2f - 10f * scale, y - 18f * scale,
                cx + textWidth / 2f + 10f * scale, y + 6f * scale, paint);
        paint.setColor(Palette.HUD_TEXT);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(session.message(), cx, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
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
            session.stopSelection();
            return true;
        }
        if (pauseButton.contains(x, y)) {
            session.setPaused(!session.isPaused());
            return true;
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
        return true; // Anything else in the sidebar is still consumed, never falls through.
    }

    private void handleStructureSlot(GameSession session, BuildingType type, boolean longPress) {
        if (longPress) {
            session.cancelLast(PlayerCommand.Line.STRUCTURE);
            session.setPlacing(null);
            return;
        }
        if (session.readyStructure() == type) {
            // Finished and waiting: arm placement mode instead of queueing another.
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

        if (tab == Tab.STRUCTURES) {
            BuildingType[] all = BuildingType.values();
            for (int i = 0; i < all.length && index < slots.size(); i++) {
                if (all[i] == BuildingType.COMMAND_POST) {
                    continue; // Pre-placed; you never build another.
                }
                slots.get(index++).building = all[i];
            }
        } else {
            BuildingType producer = tab == Tab.INFANTRY
                    ? BuildingType.BARRACKS : BuildingType.WAR_WORKS;
            UnitType[] all = UnitType.values();
            for (int i = 0; i < all.length && index < slots.size(); i++) {
                UnitType type = all[i];
                if (type.producedBy() == producer && type.availableTo(faction)) {
                    slots.get(index++).unit = type;
                }
            }
        }
    }

    private String ellipsize(String text, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && paint.measureText(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }
}
