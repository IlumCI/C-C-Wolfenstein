package com.ccwolf.core.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * A single production line: infantry, vehicles or structures. Items are paid for up front
 * (classic C&amp;C behaviour) and refunded in full if cancelled before they finish, so the
 * economy can never leak credits.
 *
 * <p>The head item builds; the rest wait. A finished structure is held at the head as
 * "ready to place" until the player picks a spot.
 */
public final class ProductionQueue {

    /** How many entries one line will accept, mostly to stop a runaway AI queueing forever. */
    public static final int MAX_LENGTH = 8;

    private final List<ProductionItem> items = new ArrayList<ProductionItem>();
    private boolean paused;

    public List<ProductionItem> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    public boolean isFull() {
        return items.size() >= MAX_LENGTH;
    }

    public ProductionItem head() {
        return items.isEmpty() ? null : items.get(0);
    }

    public void add(ProductionItem item) {
        items.add(item);
    }

    public ProductionItem removeHead() {
        return items.isEmpty() ? null : items.remove(0);
    }

    /** Cancels the last queued entry, returning it so the caller can refund its cost. */
    public ProductionItem cancelLast() {
        return items.isEmpty() ? null : items.remove(items.size() - 1);
    }

    /** True when the head item is done and waiting on something (a placement site). */
    public boolean isHeadReady() {
        ProductionItem head = head();
        return head != null && head.isFinished();
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void clear() {
        items.clear();
    }
}
