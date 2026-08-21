package com.ccwolf.android.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import com.ccwolf.android.GameSession;
import com.ccwolf.android.art.SpriteAtlas;
import com.ccwolf.android.art.WolfPalette;
import com.ccwolf.core.api.PlayerCommand;
import com.ccwolf.core.api.WorldView;
import com.ccwolf.core.economy.ProductionItem;
import com.ccwolf.core.economy.ProductionQueue;
import com.ccwolf.core.entity.Building;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
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

    /** Which build list the sidebar is showing. */
    public enum Tab { STRUCTURES, INFANTRY, VEHICLES }

    /** One build button: either a structure or a unit, never both. */
    private static final class Slot {
        final RectF rect = new RectF();
        BuildingType building;
        UnitType unit;
    }

    private static final int COLUMNS = 2;
    private static final int ROWS = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint();
    private final SpriteAtlas atlas = SpriteAtlas.get();
    private final Minimap minimap = new Minimap();
    private final RectF rect = new RectF();
    private final Rect dst = new Rect();

    private final List<Slot> slots = new ArrayList<Slot>();
    private final RectF[] tabRects = {new RectF(), new RectF(), new RectF()};
    private final RectF stopButton = new RectF();
    private final RectF sellButton = new RectF();
    private final RectF repairButton = new RectF();
    private final RectF pauseButton = new RectF();

    private Tab tab = Tab.STRUCTURES;
    private float left;
    private float width;
    private int screenWidth;
    private int screenHeight;
    private float scale = 1f;

    public Hud() {
        sprite.setFilterBitmap(false);
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

        y += 22f * scale + pad; // resource strip

        float tabWidth = (width - pad * 4) / 3f;
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

    public RectF tabRect(int index) {
        return tabRects[index];
    }

    // --- drawing --------------------------------------------------------------------------

    public void draw(Canvas canvas, GameSession session, long nowMs) {
        WorldView view = session.view();

        drawIronPanel(canvas);
        minimap.draw(canvas, session);
        drawMinimapFrame(canvas);
        drawResources(canvas, view);
        drawTabs(canvas);
        drawSlots(canvas, session);
        drawSelectionReadout(canvas, session);
        drawControls(canvas, session);
        drawToast(canvas, session, nowMs);
    }

    /** Riveted plate, so the interface looks like it was bolted together in a workshop. */
    private void drawIronPanel(Canvas canvas) {
        paint.setColor(Palette.HUD_BG);
        canvas.drawRect(left, 0, screenWidth, screenHeight, paint);

        // Vertical seam against the battlefield, lit on its left edge.
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 1));
        canvas.drawRect(left, 0, left + 2f * scale, screenHeight, paint);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        canvas.drawRect(left + 2f * scale, 0, left + 3f * scale, screenHeight, paint);

        // Rivets down the seam.
        float rivetX = left + 7f * scale;
        for (float y = 12f * scale; y < screenHeight; y += 26f * scale) {
            paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 0));
            canvas.drawCircle(rivetX, y, 2f * scale, paint);
            paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 3));
            canvas.drawCircle(rivetX + 0.6f * scale, y + 0.6f * scale, 1.2f * scale, paint);
        }
    }

    private void drawMinimapFrame(Canvas canvas) {
        RectF bounds = minimap.bounds();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * scale);
        paint.setColor(WolfPalette.shade(WolfPalette.BRASS, 2));
        canvas.drawRect(bounds.left - 2, bounds.top - 2, bounds.right + 2, bounds.bottom + 2,
                paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawResources(Canvas canvas, WorldView view) {
        float pad = 6f * scale;
        float y = minimap.bounds().bottom + pad;

        rect.set(left + pad, y, screenWidth - pad, y + 22f * scale);
        drawPlate(canvas, rect, false);

        paint.setColor(Palette.GOLD);
        paint.setTextSize(15f * scale);
        paint.setFakeBoldText(true);
        canvas.drawText(String.format(Locale.ROOT, "%,d", view.credits()),
                left + pad * 2, y + 16f * scale, paint);
        paint.setFakeBoldText(false);

        float barLeft = left + width * 0.52f;
        float barRight = screenWidth - pad * 2;
        paint.setColor(0xFF15160F);
        canvas.drawRect(barLeft, y + 6f * scale, barRight, y + 16f * scale, paint);

        float ratio = view.powerProduced() == 0 ? 1f
                : Math.min(1f, view.powerDrawn() / (float) view.powerProduced());
        paint.setColor(view.isLowPower() ? Palette.POWER_LOW : Palette.POWER_OK);
        canvas.drawRect(barLeft, y + 6f * scale,
                barLeft + (barRight - barLeft) * ratio, y + 16f * scale, paint);

        paint.setColor(view.isLowPower() ? Palette.POWER_LOW : Palette.HUD_TEXT_DIM);
        paint.setTextSize(9f * scale);
        canvas.drawText(view.isLowPower() ? "LOW POWER" : "POWER", barLeft, y + 5f * scale,
                paint);
    }

    private void drawTabs(Canvas canvas) {
        String[] labels = {"BUILD", "INF", "VEH"};
        paint.setTextSize(12f * scale);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < tabRects.length; i++) {
            boolean active = tab.ordinal() == i;
            drawPlate(canvas, tabRects[i], active);
            paint.setColor(active ? Palette.GOLD : Palette.HUD_TEXT_DIM);
            canvas.drawText(labels[i], tabRects[i].centerX(),
                    tabRects[i].centerY() + 4f * scale, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** A bevelled iron plate: light top-left, dark bottom-right, same as the sprites. */
    private void drawPlate(Canvas canvas, RectF r, boolean lit) {
        paint.setColor(lit ? Palette.HUD_PANEL_LIT : Palette.HUD_PANEL);
        canvas.drawRect(r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * scale);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, lit ? 0 : 1));
        canvas.drawLine(r.left, r.top, r.right, r.top, paint);
        canvas.drawLine(r.left, r.top, r.left, r.bottom, paint);
        paint.setColor(WolfPalette.shade(WolfPalette.GUNMETAL, 4));
        canvas.drawLine(r.left, r.bottom, r.right, r.bottom, paint);
        canvas.drawLine(r.right, r.top, r.right, r.bottom, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSlots(Canvas canvas, GameSession session) {
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

            drawPlate(canvas, slot.rect, available);
            canvas.save();
            canvas.clipRect(slot.rect);
            drawSlotIcon(canvas, slot, faction, available);
            canvas.restore();

            // Name and price sit on their own strip, so a tall icon cannot collide with them.
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

            // A dead button says why it is dead, instead of leaving the player guessing.
            if (blocker != null && !blocker.startsWith("Needs " + cost)) {
                paint.setColor(Palette.HUD_TEXT_DIM);
                canvas.drawText(ellipsize(blocker, slot.rect.width() - 4f * scale),
                        slot.rect.centerX(), slot.rect.bottom - 3f * scale, paint);
            } else {
                paint.setColor(view.credits() >= cost ? Palette.GOLD : Palette.HEALTH_POOR);
                canvas.drawText(String.valueOf(cost), slot.rect.centerX(),
                        slot.rect.bottom - 3f * scale, paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);

            drawSlotProgress(canvas, session, slot);
        }
    }

    /** Build buttons show the actual game sprite, so the icon and the unit always match. */
    private void drawSlotIcon(Canvas canvas, Slot slot, Faction faction, boolean available) {
        Bitmap icon;
        float boxH = slot.rect.height() * 0.62f;
        float cx = slot.rect.centerX();
        float cy = slot.rect.top + slot.rect.height() * 0.32f;

        if (slot.building != null) {
            icon = atlas.building(slot.building, faction, 0);
        } else {
            icon = atlas.unit(slot.unit, faction, 2, slot.unit.isHarvester() ? 2 : 0);
        }

        float aspect = icon.getWidth() / (float) icon.getHeight();
        float h = boxH;
        float w = h * aspect;
        if (w > slot.rect.width() * 0.8f) {
            w = slot.rect.width() * 0.8f;
            h = w / aspect;
        }
        dst.set(Math.round(cx - w / 2f), Math.round(cy - h / 2f),
                Math.round(cx + w / 2f), Math.round(cy + h / 2f));
        sprite.setAlpha(available ? 255 : 110);
        canvas.drawBitmap(icon, null, dst, sprite);
        sprite.setAlpha(255);
    }

    /** Queue depth, build progress and the ready-to-place state. */
    private void drawSlotProgress(Canvas canvas, GameSession session, Slot slot) {
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
            canvas.drawRect(slot.rect, paint);
            paint.setColor(Palette.SELECTION);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(12f * scale);
            canvas.drawText("READY", slot.rect.centerX(), slot.rect.centerY(), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        } else if (isHead) {
            // Fills bottom-up like a C&C build clock.
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
        } else if (session.hasSelection()) {
            textLine = session.selection().size() + " units selected";
        } else {
            textLine = "Nothing selected";
        }
        paint.setColor(Palette.HUD_TEXT_DIM);
        canvas.drawText(ellipsize(textLine, width - 12f * scale), left + 6f * scale, y, paint);
    }

    private void drawControls(Canvas canvas, GameSession session) {
        GameSession.PointerMode mode = session.pointerMode();
        drawButton(canvas, stopButton, "STOP", session.hasSelection(), false);
        drawButton(canvas, pauseButton, session.isPaused() ? "RESUME" : "PAUSE", true, false);
        drawButton(canvas, sellButton, "SELL", true,
                mode == GameSession.PointerMode.SELL);
        drawButton(canvas, repairButton, "REPAIR", true,
                mode == GameSession.PointerMode.REPAIR);
    }

    private void drawButton(Canvas canvas, RectF r, String label, boolean enabled,
                            boolean armed) {
        drawPlate(canvas, r, armed);
        paint.setColor(armed ? Palette.GOLD
                : (enabled ? Palette.HUD_TEXT : Palette.HUD_TEXT_DIM));
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
        paint.setColor(0xC0101109);
        canvas.drawRect(cx - textWidth / 2f - 10f * scale, y - 18f * scale,
                cx + textWidth / 2f + 10f * scale, y + 6f * scale, paint);
        paint.setColor(Palette.GOLD);
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
        if (sellButton.contains(x, y)) {
            session.togglePointerMode(GameSession.PointerMode.SELL);
            return true;
        }
        if (repairButton.contains(x, y)) {
            session.togglePointerMode(GameSession.PointerMode.REPAIR);
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

    private String ellipsize(String value, float maxWidth) {
        if (paint.measureText(value) <= maxWidth) {
            return value;
        }
        String out = value;
        while (out.length() > 1 && paint.measureText(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }
}
