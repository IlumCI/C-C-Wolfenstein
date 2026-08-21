package com.ccwolf.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ccwolf.android.render.Camera;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.core.map.TileMap;
import org.junit.Before;
import org.junit.Test;

/** Camera maths is plain Java, so it is tested without any Android machinery. */
public class CameraTest {

    private Camera camera;
    private TileMap map;

    @Before
    public void setUp() {
        map = MapCatalog.load(MapCatalog.KREISAU_VALLEY);
        camera = new Camera();
        camera.setMap(map);
        camera.setViewport(0, 0, 1280, 720);
    }

    @Test
    public void screenAndWorldCoordinatesRoundTrip() {
        camera.centerOn(20f, 20f);

        float screenX = camera.screenX(25.5f);
        float screenY = camera.screenY(18.25f);
        assertEquals(25.5f, camera.worldX(screenX), 0.001f);
        assertEquals(18.25f, camera.worldY(screenY), 0.001f);
    }

    @Test
    public void panningMovesTheViewAndStaysOnTheMap() {
        camera.centerOn(32f, 32f);
        float before = camera.centerX();
        camera.panByPixels(-200f, 0f);
        assertTrue("dragging left should move the view right", camera.centerX() > before);

        for (int i = 0; i < 200; i++) {
            camera.panByPixels(-500f, -500f);
        }
        assertTrue(camera.centerX() <= map.width());
        assertTrue(camera.centerY() <= map.height());
        assertTrue(camera.lastVisibleTileX() <= map.width() - 1);
        assertTrue(camera.lastVisibleTileY() <= map.height() - 1);
    }

    @Test
    public void zoomKeepsTheFocusedTileUnderTheFingers() {
        camera.centerOn(30f, 30f);
        float focusX = 400f;
        float focusY = 300f;
        float tileBefore = camera.worldX(focusX);

        camera.zoomBy(1.6f, focusX, focusY);

        assertEquals(tileBefore, camera.worldX(focusX), 0.05f);
    }

    @Test
    public void zoomIsClampedToSaneLimits() {
        for (int i = 0; i < 40; i++) {
            camera.zoomBy(2f, 640f, 360f);
        }
        assertTrue(camera.tilePx() <= 96f);

        for (int i = 0; i < 40; i++) {
            camera.zoomBy(0.5f, 640f, 360f);
        }
        assertTrue(camera.tilePx() >= 14f);
    }

    @Test
    public void visibleTileRangeCoversTheViewport() {
        camera.centerOn(32f, 32f);
        assertTrue(camera.firstVisibleTileX() >= 0);
        assertTrue(camera.firstVisibleTileY() >= 0);
        assertTrue(camera.lastVisibleTileX() > camera.firstVisibleTileX());
        assertTrue(camera.lastVisibleTileY() > camera.firstVisibleTileY());
    }
}
