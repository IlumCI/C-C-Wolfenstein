package com.ccwolf.core.sim;

import com.ccwolf.core.map.MapLoader;
import com.ccwolf.core.map.TileMap;

/** Small hand-made maps so simulation tests do not depend on the shipped skirmish map. */
final class TestMaps {

    private TestMaps() {
    }

    /** Featureless grass, no obstacles. */
    static TileMap openField(int width, int height) {
        return build(width, height, '.');
    }

    /** Grass with a square patch of uranium in the middle. */
    static TileMap withOrePatch(int width, int height, int oreX, int oreY, int oreSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("name Test Field\n").append("size ").append(width).append(' ').append(height)
                .append('\n').append("tiles\n");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean ore = x >= oreX && y >= oreY && x < oreX + oreSize && y < oreY + oreSize;
                sb.append(ore ? '*' : '.');
            }
            sb.append('\n');
        }
        return MapLoader.parse(sb.toString());
    }

    private static TileMap build(int width, int height, char glyph) {
        StringBuilder sb = new StringBuilder();
        sb.append("name Test Field\n").append("size ").append(width).append(' ').append(height)
                .append('\n').append("tiles\n");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(glyph);
            }
            sb.append('\n');
        }
        return MapLoader.parse(sb.toString());
    }
}
