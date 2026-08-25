package com.ccwolf.core.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the plain-text {@code .map} format.
 *
 * <pre>
 * name Kreisau Valley
 * size 64 64
 * spawn 10 10
 * spawn 52 50
 * tiles
 * ..........
 * ..**......
 * </pre>
 *
 * <p>Lines starting with {@code #} and blank lines outside the tile block are ignored. Every
 * row after {@code tiles} must be exactly {@code width} glyphs (see {@link Terrain#glyph()}).
 */
public final class MapLoader {

    private MapLoader() {
    }

    public static TileMap parse(String text) {
        return read(new BufferedReader(new java.io.StringReader(text)));
    }

    public static TileMap load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
        try {
            return read(r);
        } finally {
            r.close();
        }
    }

    private static TileMap read(BufferedReader r) {
        String name = "Unnamed";
        int width = -1;
        int height = -1;
        List<int[]> spawns = new ArrayList<int[]>();
        Terrain[] tiles = null;
        int row = 0;
        boolean inTiles = false;

        try {
            String line;
            while ((line = r.readLine()) != null) {
                if (!inTiles) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] parts = trimmed.split("\\s+", 2);
                    String key = parts[0];
                    if ("name".equals(key)) {
                        name = parts.length > 1 ? parts[1] : name;
                    } else if ("size".equals(key)) {
                        String[] wh = parts[1].trim().split("\\s+");
                        width = Integer.parseInt(wh[0]);
                        height = Integer.parseInt(wh[1]);
                    } else if ("spawn".equals(key)) {
                        String[] xy = parts[1].trim().split("\\s+");
                        spawns.add(new int[] {Integer.parseInt(xy[0]), Integer.parseInt(xy[1])});
                    } else if ("tiles".equals(key)) {
                        if (width <= 0 || height <= 0) {
                            throw new IllegalArgumentException("'size' must precede 'tiles'");
                        }
                        tiles = new Terrain[width * height];
                        inTiles = true;
                    } else {
                        throw new IllegalArgumentException("Unknown map directive: " + key);
                    }
                    continue;
                }

                if (row >= height) {
                    break;
                }
                if (line.length() < width) {
                    throw new IllegalArgumentException(
                            "Map row " + row + " is " + line.length() + " glyphs, expected " + width);
                }
                for (int x = 0; x < width; x++) {
                    tiles[row * width + x] = Terrain.fromGlyph(line.charAt(x));
                }
                row++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading map", e);
        }

        if (tiles == null) {
            throw new IllegalArgumentException("Map has no 'tiles' block");
        }
        if (row != height) {
            throw new IllegalArgumentException("Map declared " + height + " rows but had " + row);
        }
        return new TileMap(name, width, height, tiles, spawns);
    }
}
