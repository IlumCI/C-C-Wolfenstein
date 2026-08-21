package com.ccwolf.android.render;

import com.ccwolf.core.map.TileMap;

/**
 * Maps between tile coordinates and screen pixels for the battlefield viewport (the screen
 * minus the HUD sidebar), and keeps the view inside the map.
 */
public final class Camera {

    private static final float MIN_TILE_PX = 14f;
    private static final float MAX_TILE_PX = 96f;

    /** Centre of the view, in tiles. */
    private float centerX = 0f;
    private float centerY = 0f;
    private float tilePx = 40f;

    private int viewLeft;
    private int viewTop;
    private int viewWidth;
    private int viewHeight;

    private int mapWidth = 1;
    private int mapHeight = 1;

    public void setViewport(int left, int top, int width, int height) {
        this.viewLeft = left;
        this.viewTop = top;
        this.viewWidth = width;
        this.viewHeight = height;
        clamp();
    }

    public void setMap(TileMap map) {
        this.mapWidth = map.width();
        this.mapHeight = map.height();
        clamp();
    }

    public int viewLeft() {
        return viewLeft;
    }

    public int viewTop() {
        return viewTop;
    }

    public int viewWidth() {
        return viewWidth;
    }

    public int viewHeight() {
        return viewHeight;
    }

    public float tilePx() {
        return tilePx;
    }

    public float centerX() {
        return centerX;
    }

    public float centerY() {
        return centerY;
    }

    public void centerOn(float tileX, float tileY) {
        this.centerX = tileX;
        this.centerY = tileY;
        clamp();
    }

    /** Drag the map under the finger: a positive dx moves the view left. */
    public void panByPixels(float dxPixels, float dyPixels) {
        centerX -= dxPixels / tilePx;
        centerY -= dyPixels / tilePx;
        clamp();
    }

    /** Pinch zoom that keeps the tile under the focus point pinned to the focus point. */
    public void zoomBy(float factor, float focusScreenX, float focusScreenY) {
        float worldX = worldX(focusScreenX);
        float worldY = worldY(focusScreenY);
        tilePx = Math.max(MIN_TILE_PX, Math.min(MAX_TILE_PX, tilePx * factor));
        // Re-centre so the focused tile lands back under the fingers.
        centerX = worldX - (focusScreenX - (viewLeft + viewWidth / 2f)) / tilePx;
        centerY = worldY - (focusScreenY - (viewTop + viewHeight / 2f)) / tilePx;
        clamp();
    }

    public float screenX(float tileX) {
        return viewLeft + viewWidth / 2f + (tileX - centerX) * tilePx;
    }

    public float screenY(float tileY) {
        return viewTop + viewHeight / 2f + (tileY - centerY) * tilePx;
    }

    public float worldX(float screenX) {
        return centerX + (screenX - (viewLeft + viewWidth / 2f)) / tilePx;
    }

    public float worldY(float screenY) {
        return centerY + (screenY - (viewTop + viewHeight / 2f)) / tilePx;
    }

    public boolean containsScreenPoint(float x, float y) {
        return x >= viewLeft && y >= viewTop && x < viewLeft + viewWidth && y < viewTop + viewHeight;
    }

    /** First tile column that can be seen, clamped to the map. */
    public int firstVisibleTileX() {
        return Math.max(0, (int) Math.floor(worldX(viewLeft)));
    }

    public int firstVisibleTileY() {
        return Math.max(0, (int) Math.floor(worldY(viewTop)));
    }

    public int lastVisibleTileX() {
        return Math.min(mapWidth - 1, (int) Math.ceil(worldX(viewLeft + viewWidth)));
    }

    public int lastVisibleTileY() {
        return Math.min(mapHeight - 1, (int) Math.ceil(worldY(viewTop + viewHeight)));
    }

    /**
     * Keeps the centre inside the map. When the map is smaller than the viewport on an axis
     * the centre is pinned to the map's middle rather than allowed to drift.
     */
    private void clamp() {
        float halfW = viewWidth / (2f * tilePx);
        float halfH = viewHeight / (2f * tilePx);

        if (halfW * 2 >= mapWidth) {
            centerX = mapWidth / 2f;
        } else {
            centerX = Math.max(halfW, Math.min(mapWidth - halfW, centerX));
        }
        if (halfH * 2 >= mapHeight) {
            centerY = mapHeight / 2f;
        } else {
            centerY = Math.max(halfH, Math.min(mapHeight - halfH, centerY));
        }
    }
}
