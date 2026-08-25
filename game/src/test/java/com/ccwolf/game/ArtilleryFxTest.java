package com.ccwolf.game;

import static org.junit.Assert.assertTrue;

import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.map.Terrain;
import com.ccwolf.core.map.TileMap;
import com.ccwolf.core.order.BombardOrder;
import java.io.IOException;
import org.junit.Test;

/**
 * A barrage, rendered, at the two moments that matter.
 *
 * <p>Everything about artillery that can be wrong is wrong in a way a unit test cannot see: a
 * shell that reads as a bullet, an arc with no shadow under it so the round looks displaced
 * rather than airborne, a crater that vanishes before anyone looks at it, an explosion that
 * plays twice. These are frames to look at.
 */
public class ArtilleryFxTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    @Test
    public void aBarrageInFlightAndOnImpact() throws IOException {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 21L);
        Hud hud = new Hud();
        WorldRenderer renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.camera().setMap(session.world().map());
        session.world().setFogEnabled(false);

        TileMap map = session.world().map();
        int[] spot = openGround(map);

        // A battery, a crowd for it to shell, and a trench for the crowd to be caught in.
        Unit gun = session.world().spawnUnit(0, UnitType.FELDKANONE,
                spot[0] + 0.5f, spot[1] + 9.5f);
        for (int i = 0; i < 6; i++) {
            session.world().spawnUnit(1, UnitType.SOLDAT,
                    spot[0] + 0.5f + (i % 3) * 0.8f, spot[1] + 0.5f + (i / 3) * 0.8f);
            map.setCover(spot[0] + (i % 3), spot[1] + (i / 3), TileMap.MAX_COVER);
        }
        // Framed on the middle of the arc rather than on either end of it, so the gun, the
        // round and the ground it is falling on are all in the same picture.
        session.camera().centerOn(spot[0] + 1f, spot[1] + 5f);
        session.camera().zoomBy(1.5f, WIDTH / 4f, HEIGHT / 2f);
        gun.setOrder(new BombardOrder(spot[0], spot[1]));

        // Wind on until something is actually in the air, then catch it mid-arc.
        int guard = 0;
        while (session.world().shells().count() == 0 && guard++ < 200) {
            session.update(1f / 20f);
        }
        assertTrue("the gun should have fired by now", session.world().shells().count() > 0);
        // Roughly half way along. Six render frames, as this first did, is a tenth of the
        // flight and catches the round still sitting on the muzzle.
        int flight = session.world().shells().impactTick(0) - session.world().tick();
        for (int i = 0; i < Math.max(1, flight / 2); i++) {
            session.update(1f / 20f);
        }
        save(session, renderer, hud, "artillery-in-flight.png");

        // Then past the landing, close enough that the smoke has not blown away.
        while (session.world().shells().count() > 0 && guard++ < 400) {
            session.update(1f / 20f);
        }
        for (int i = 0; i < 8; i++) {
            session.update(1f / 60f);
        }
        save(session, renderer, hud, "artillery-impact.png");
    }

    private void save(GameSession session, WorldRenderer renderer, Hud hud, String name)
            throws IOException {
        Frame frame = new Frame(WIDTH, HEIGHT);
        frame.surface().clear(0xFF0B0C0A);
        renderer.draw(frame.surface(), session);
        hud.draw(frame.surface(), session, 0L);
        frame.save(name);
    }

    /**
     * A patch of open ground with room for the whole scene, not merely one grass tile.
     *
     * <p>The first version of this took the first grass tile it found and placed everything
     * relative to it, which put the battery in the middle of the river and most of the frame
     * under water. A review frame is only worth rendering if you can see what is in it.
     */
    private int[] openGround(TileMap map) {
        for (int y = 20; y < map.height() - 16; y++) {
            for (int x = 20; x < map.width() - 16; x++) {
                if (isClear(map, x, y)) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException("no open ground big enough to stage a barrage on");
    }

    /** True if everything from a few tiles left of here to well below it is plain grass. */
    private boolean isClear(TileMap map, int originX, int originY) {
        for (int dy = -2; dy <= 12; dy++) {
            for (int dx = -3; dx <= 5; dx++) {
                if (map.terrain(originX + dx, originY + dy) != Terrain.GRASS) {
                    return false;
                }
            }
        }
        return true;
    }
}
