package com.ccwolf.android;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import com.ccwolf.android.art.BuildingSprites;
import com.ccwolf.android.art.EffectSprites;
import com.ccwolf.android.art.SpriteAtlas;
import com.ccwolf.android.art.UnitSprites;
import com.ccwolf.core.entity.BuildingType;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.Terrain;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Renders every sprite in the game onto contact sheets and writes them to build/test-frames.
 *
 * <p>This is the art review loop. There is no emulator on a build machine, so without this
 * there is no way to see whether a sprite recipe produced a soldier or a smudge — and pixel art
 * written blind is exactly the kind of code that compiles perfectly and looks wrong.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SpriteContactSheetTest {

    /** Sprites are drawn at 3x so the pixels are legible in a review. */
    private static final int ZOOM = 3;
    private static final int PAD = 8;
    private static final int LABEL_H = 14;
    private static final int BACKDROP = 0xFF23251E;

    private final Paint paint = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpriteContactSheetTest() {
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        text.setColor(0xFFD8D4BC);
        text.setTextSize(11f);
    }

    @Test
    public void everyUnitInEveryFacing() throws IOException {
        SpriteAtlas atlas = SpriteAtlas.get();
        int cell = UnitSprites.VEHICLE_SIZE * ZOOM + PAD;
        int rows = 0;
        for (UnitType type : UnitType.values()) {
            rows += type.faction() == null ? 2 : 1;
        }

        Bitmap sheet = Bitmap.createBitmap(cell * UnitSprites.FACINGS + 120,
                rows * (cell + LABEL_H) + PAD, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(BACKDROP);

        int y = PAD;
        for (UnitType type : UnitType.values()) {
            for (Faction faction : Faction.values()) {
                if (!type.availableTo(faction)) {
                    continue;
                }
                canvas.drawText(type.displayName() + " / " + faction.name(), 4, y + 10, text);
                for (int facing = 0; facing < UnitSprites.FACINGS; facing++) {
                    Bitmap sprite = atlas.unit(type, faction, facing, 0);
                    assertNotNull(type + " facing " + facing + " missing", sprite);
                    assertTrue("sprite is blank: " + type + " facing " + facing,
                            hasContent(sprite));
                    drawScaled(canvas, sprite, 120 + facing * cell, y + LABEL_H);
                }
                y += cell + LABEL_H;
            }
        }
        save(sheet, "sheet-units.png");
    }

    @Test
    public void unitAnimationFrames() throws IOException {
        SpriteAtlas atlas = SpriteAtlas.get();
        int cell = UnitSprites.VEHICLE_SIZE * ZOOM + PAD;
        Bitmap sheet = Bitmap.createBitmap(cell * 6 + 130,
                UnitType.values().length * (cell + LABEL_H) + PAD, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(BACKDROP);

        int y = PAD;
        for (UnitType type : UnitType.values()) {
            Faction faction = type.faction() == null ? Faction.RESISTANCE : type.faction();
            canvas.drawText(type.displayName(), 4, y + 10, text);
            int column = 0;
            for (int frame = 0; frame < (type.isHarvester() ? 3 : 2); frame++) {
                for (int facing : new int[] {2, 4, 6}) {
                    drawScaled(canvas, atlas.unit(type, faction, facing, frame),
                            130 + column * cell, y + LABEL_H);
                    column++;
                }
            }
            y += cell + LABEL_H;
        }
        save(sheet, "sheet-frames.png");
    }

    @Test
    public void everyStructureForBothSidesAndEveryDamageState() throws IOException {
        SpriteAtlas atlas = SpriteAtlas.get();
        int cell = 3 * BuildingSprites.TILE * ZOOM + PAD;
        Bitmap sheet = Bitmap.createBitmap(cell * 6 + 130,
                BuildingType.values().length * (cell + LABEL_H) + PAD, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(BACKDROP);

        int y = PAD;
        for (BuildingType type : BuildingType.values()) {
            canvas.drawText(type.displayName(), 4, y + 10, text);
            int column = 0;
            for (Faction faction : Faction.values()) {
                for (int damage = 0; damage < BuildingSprites.DAMAGE_STATES; damage++) {
                    Bitmap sprite = atlas.building(type, faction, damage);
                    assertNotNull(sprite);
                    assertTrue("blank structure: " + type, hasContent(sprite));
                    drawScaled(canvas, sprite, 130 + column * cell, y + LABEL_H);
                    column++;
                }
            }
            y += cell + LABEL_H;
        }
        save(sheet, "sheet-structures.png");
    }

    @Test
    public void terrainVariantsOreLevelsAndEffects() throws IOException {
        SpriteAtlas atlas = SpriteAtlas.get();
        int cell = 24 * ZOOM + PAD;
        Bitmap sheet = Bitmap.createBitmap(cell * 8 + 130, cell * 10 + 120,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(BACKDROP);

        int y = PAD;
        for (Terrain terrain : Terrain.values()) {
            canvas.drawText(terrain.name(), 4, y + 20, text);
            for (int variant = 0; variant < 4; variant++) {
                drawScaled(canvas, atlas.terrain(terrain, variant, 0), 130 + variant * cell, y);
            }
            y += cell;
        }

        canvas.drawText("URANIUM", 4, y + 20, text);
        for (int level = 0; level < 3; level++) {
            drawScaled(canvas, atlas.ore(level, 0, level == 0 ? 50 : (level == 1 ? 300 : 700)),
                    130 + level * cell, y);
        }
        y += cell;

        canvas.drawText("EXPLOSION", 4, y + 20, text);
        for (int frame = 0; frame < EffectSprites.EXPLOSION_FRAMES; frame++) {
            drawScaled(canvas, atlas.explosion(frame / (float) EffectSprites.EXPLOSION_FRAMES),
                    130 + frame * cell, y);
        }
        y += cell + PAD;

        canvas.drawText("WRECK / FLASH", 4, y + 20, text);
        for (int variant = 0; variant < 4; variant++) {
            drawScaled(canvas, atlas.wreck(variant), 130 + variant * cell, y);
        }
        drawScaled(canvas, atlas.muzzleFlash(0), 130 + 4 * cell, y);
        drawScaled(canvas, atlas.muzzleFlash(1), 130 + 5 * cell, y);

        save(sheet, "sheet-terrain.png");
    }

    @Test
    public void theAtlasBakesEverythingUpFront() {
        SpriteAtlas atlas = SpriteAtlas.get();
        // 7 units x facings x frames, 6 structures x 2 sides x 3 states, terrain, effects.
        assertTrue("suspiciously few sprites baked: " + atlas.size(), atlas.size() > 150);
        assertNotNull(atlas.unit(UnitType.PARTISAN, Faction.RESISTANCE, 0, 0));
        assertNotNull(atlas.building(BuildingType.COMMAND_POST, Faction.REGIME, 2));
        assertNotNull(atlas.terrain(Terrain.GRASS, 3, 7));
        assertNotNull(atlas.flakBarrel(5));
    }

    // --- helpers --------------------------------------------------------------------------

    private void drawScaled(Canvas canvas, Bitmap sprite, int x, int y) {
        Rect dst = new Rect(x, y, x + sprite.getWidth() * ZOOM, y + sprite.getHeight() * ZOOM);
        canvas.drawBitmap(sprite, null, dst, paint);
    }

    /** A sprite that is entirely transparent means the recipe drew nothing. */
    private boolean hasContent(Bitmap bitmap) {
        int opaque = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    opaque++;
                }
            }
        }
        return opaque > bitmap.getWidth() * bitmap.getHeight() / 20;
    }

    private void save(Bitmap bitmap, String name) throws IOException {
        File dir = new File("build/test-frames");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        FileOutputStream out = new FileOutputStream(new File(dir, name));
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            out.close();
        }
    }
}
