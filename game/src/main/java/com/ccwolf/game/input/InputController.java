package com.ccwolf.game.input;

import com.ccwolf.game.GameSession;
import com.ccwolf.game.render.Camera;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.Minimap;
import com.ccwolf.game.render.WorldRenderer;

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

    public boolean onPointer(PointerEvent event) {
        float x = event.x();
        float y = event.y();

        switch (event.action()) {
            case DOWN:
                return onDown(x, y);

            case POINTER_DOWN:
                // A second finger always means camera work; abandon any box in progress.
                if (event.count() >= 2 && mode != Mode.SIDEBAR) {
                    mode = Mode.CAMERA;
                    renderer.setSelectionBox(false, 0, 0, 0, 0);
                    lastSpan = event.span();
                    lastFocusX = event.focusX();
                    lastFocusY = event.focusY();
                }
                return true;

            case MOVE:
                return onMove(event);

            case POINTER_UP:
                if (event.count() <= 2) {
                    // Dropping back to one finger: stop the camera gesture cleanly.
                    mode = Mode.IDLE;
                    renderer.setSelectionBox(false, 0, 0, 0, 0);
                }
                return true;

            case UP:
                return onUp(x, y);

            case CANCEL:
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

    private boolean onMove(PointerEvent event) {
        float x = event.x();
        float y = event.y();

        if (mode == Mode.CAMERA && event.count() >= 2) {
            float newSpan = event.span();
            float newFocusX = event.focusX();
            float newFocusY = event.focusY();

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

        // What a tap means depends on the pointer mode and the selection; the session owns
        // that decision so the same rules apply however the tap arrived.
        session.tapWorld(worldX, worldY, longPress);
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

}
