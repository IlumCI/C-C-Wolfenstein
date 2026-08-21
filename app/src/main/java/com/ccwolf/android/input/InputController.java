package com.ccwolf.android.input;

import android.view.MotionEvent;
import com.ccwolf.android.GameSession;
import com.ccwolf.android.render.Camera;
import com.ccwolf.android.render.Hud;
import com.ccwolf.android.render.Minimap;
import com.ccwolf.android.render.WorldRenderer;
import com.ccwolf.core.entity.BuildingType;

/**
 * Touch handling for the battlefield.
 *
 * <ul>
 *   <li>tap — select what is under the finger, or, with a selection, issue the natural order
 *       (attack a hostile, harvest an ore tile, otherwise move)</li>
 *   <li>drag with one finger — rubber-band select</li>
 *   <li>drag with two fingers — pan; pinch to zoom</li>
 *   <li>long press — attack-move to that tile</li>
 *   <li>minimap — tap or drag to jump the camera</li>
 * </ul>
 *
 * <p>One finger deliberately box-selects rather than pans: panning is what the minimap and the
 * second finger are for, and a game where dragging scrolls makes selecting a group impossible.
 */
public final class InputController {

    /** Movement in pixels before a press becomes a drag. */
    private static final float DRAG_SLOP_DP = 12f;

    /** Press duration that counts as a long press. */
    private static final long LONG_PRESS_MS = 420;

    private enum Mode { IDLE, PRESSING, BOX_SELECT, CAMERA, MINIMAP, SIDEBAR }

    private final GameSession session;
    private final Hud hud;
    private final WorldRenderer renderer;
    private final float density;

    private Mode mode = Mode.IDLE;
    private float downX;
    private float downY;
    private long downTime;
    private float lastX;
    private float lastY;

    // Two-finger gesture state.
    private float lastSpan;
    private float lastFocusX;
    private float lastFocusY;

    public InputController(GameSession session, Hud hud, WorldRenderer renderer, float density) {
        this.session = session;
        this.hud = hud;
        this.renderer = renderer;
        this.density = Math.max(1f, density);
    }

    public boolean onTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return onDown(x, y);

            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger always means camera work; abandon any box in progress.
                if (event.getPointerCount() >= 2 && mode != Mode.SIDEBAR) {
                    mode = Mode.CAMERA;
                    renderer.setSelectionBox(false, 0, 0, 0, 0);
                    lastSpan = span(event);
                    lastFocusX = focusX(event);
                    lastFocusY = focusY(event);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                return onMove(event);

            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerCount() <= 2) {
                    // Dropping back to one finger: stop the camera gesture cleanly.
                    mode = Mode.IDLE;
                    renderer.setSelectionBox(false, 0, 0, 0, 0);
                }
                return true;

            case MotionEvent.ACTION_UP:
                return onUp(x, y);

            case MotionEvent.ACTION_CANCEL:
            default:
                mode = Mode.IDLE;
                renderer.setSelectionBox(false, 0, 0, 0, 0);
                return true;
        }
    }

    private boolean onDown(float x, float y) {
        downX = x;
        downY = y;
        lastX = x;
        lastY = y;
        downTime = System.currentTimeMillis();

        if (session.world().isGameOver()) {
            mode = Mode.IDLE;
            return true;
        }
        if (hud.contains(x, y)) {
            mode = hud.minimap().contains(x, y) ? Mode.MINIMAP : Mode.SIDEBAR;
            if (mode == Mode.MINIMAP) {
                jumpCameraTo(x, y);
            }
            return true;
        }

        mode = Mode.PRESSING;
        if (session.placing() != null) {
            updateGhost(x, y);
        }
        return true;
    }

    private boolean onMove(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (mode == Mode.CAMERA && event.getPointerCount() >= 2) {
            float newSpan = span(event);
            float newFocusX = focusX(event);
            float newFocusY = focusY(event);

            session.camera().panByPixels(newFocusX - lastFocusX, newFocusY - lastFocusY);
            if (lastSpan > 1f && newSpan > 1f) {
                session.camera().zoomBy(newSpan / lastSpan, newFocusX, newFocusY);
            }
            lastSpan = newSpan;
            lastFocusX = newFocusX;
            lastFocusY = newFocusY;
            return true;
        }

        if (mode == Mode.MINIMAP) {
            jumpCameraTo(x, y);
            return true;
        }
        if (mode == Mode.SIDEBAR) {
            return true;
        }

        if (session.placing() != null) {
            updateGhost(x, y);
            return true;
        }

        if (mode == Mode.PRESSING && dragged(x, y)) {
            mode = Mode.BOX_SELECT;
        }
        if (mode == Mode.BOX_SELECT) {
            renderer.setSelectionBox(true, downX, downY, x, y);
        }
        lastX = x;
        lastY = y;
        return true;
    }

    private boolean onUp(float x, float y) {
        long heldMs = System.currentTimeMillis() - downTime;
        Mode finished = mode;
        mode = Mode.IDLE;
        renderer.setSelectionBox(false, 0, 0, 0, 0);

        if (session.world().isGameOver()) {
            return true;
        }

        switch (finished) {
            case SIDEBAR:
                hud.handleTap(x, y, session, heldMs >= LONG_PRESS_MS);
                return true;

            case MINIMAP:
            case CAMERA:
                return true;

            case BOX_SELECT: {
                Camera camera = session.camera();
                session.selectInBox(camera.worldX(downX), camera.worldY(downY),
                        camera.worldX(x), camera.worldY(y));
                if (session.hasSelection()) {
                    session.showMessage(session.selection().size() + " selected");
                }
                return true;
            }

            case PRESSING:
            default:
                return onTap(x, y, heldMs >= LONG_PRESS_MS);
        }
    }

    private boolean onTap(float x, float y, boolean longPress) {
        Camera camera = session.camera();
        float worldX = camera.worldX(x);
        float worldY = camera.worldY(y);

        BuildingType placing = session.placing();
        if (placing != null) {
            session.placeAt((int) worldX, (int) worldY);
            return true;
        }

        // With something selected, a tap is an order; on empty ground with nothing selected it
        // is a selection attempt.
        if (session.hasSelection()) {
            com.ccwolf.core.entity.Entity hit = session.entityAt(worldX, worldY);
            boolean ownEntity = hit != null && hit.ownerId() == session.playerId();
            if (ownEntity && !longPress) {
                session.selectAt(worldX, worldY);
            } else {
                session.commandAt(worldX, worldY, longPress);
            }
        } else {
            session.selectAt(worldX, worldY);
        }
        return true;
    }

    private void updateGhost(float screenX, float screenY) {
        Camera camera = session.camera();
        session.setPlaceTile((int) camera.worldX(screenX), (int) camera.worldY(screenY));
    }

    private void jumpCameraTo(float screenX, float screenY) {
        Minimap minimap = hud.minimap();
        session.camera().centerOn(minimap.tileXFor(screenX, session.world().map()),
                minimap.tileYFor(screenY, session.world().map()));
    }

    private boolean dragged(float x, float y) {
        float slop = DRAG_SLOP_DP * density;
        return Math.abs(x - downX) > slop || Math.abs(y - downY) > slop;
    }

    private static float span(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float focusX(MotionEvent e) {
        return (e.getX(0) + e.getX(1)) / 2f;
    }

    private static float focusY(MotionEvent e) {
        return (e.getY(0) + e.getY(1)) / 2f;
    }
}
