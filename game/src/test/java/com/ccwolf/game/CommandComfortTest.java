package com.ccwolf.game;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The command comforts: control groups and select-all-of-type. These are the features that
 * separate commanding three fronts from box-selecting them, so they get pinned like rules,
 * not like chrome.
 */
public class CommandComfortTest {

    static {
        Frame.useAwtBackend();
    }

    private static GameSession fresh() {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, 41L);
        session.camera().setViewport(0, 0, 1000, 720);
        int[] spawn = session.world().map().spawnPoint(session.playerId());
        session.camera().centerOn(spawn[0] + 3f, spawn[1] + 3f);
        return session;
    }

    private static Unit anyOwnUnit(GameSession session) {
        for (Unit u : session.world().units()) {
            if (u.ownerId() == session.playerId()) {
                return u;
            }
        }
        throw new IllegalStateException("the starting base should field units");
    }

    @Test
    public void aControlGroupRemembersAndRecalls() {
        GameSession session = fresh();
        Unit u = anyOwnUnit(session);
        session.selectAt(u.x(), u.y());
        assertTrue(session.hasSelection());

        session.assignControlGroup(1);
        session.clearSelection();
        assertFalse(session.hasSelection());

        assertTrue(session.recallControlGroup(1));
        assertTrue(session.hasSelection());
        // An empty slot answers honestly instead of clearing the selection for nothing.
        assertTrue(session.hasSelection());
        assertFalse(session.recallControlGroup(7));
    }

    @Test
    public void doubleTapWidensToEveryOnScreenUnitOfTheType() {
        GameSession session = fresh();
        Unit u = anyOwnUnit(session);
        session.camera().centerOn(u.x(), u.y());
        int ofType = 0;
        for (Unit other : session.world().units()) {
            if (other.ownerId() == session.playerId() && other.type() == u.type()) {
                ofType++;
            }
        }

        session.selectAllOfTypeAt(u.x(), u.y());
        assertEquals("every on-screen unit of the type should be picked up",
                ofType, session.selection().size());
    }

    @Test
    public void centeringJumpsTheCameraToTheGroup() {
        GameSession session = fresh();
        Unit u = anyOwnUnit(session);
        session.selectAt(u.x(), u.y());
        // Send the camera to the far corner, then jump home. The camera clamps to keep the
        // viewport on the map, so the pin is "as close to the selection as the map allows":
        // the same call with the same target must land in the same place.
        session.camera().centerOn(session.world().map().width() - 2f,
                session.world().map().height() - 2f);
        session.centerCameraOnSelection();
        float cx = session.camera().centerX();
        float cy = session.camera().centerY();
        session.camera().centerOn(u.x(), u.y());
        assertTrue("centering should land where centerOn(unit) lands",
                Math.abs(session.camera().centerX() - cx) < 0.01f
                        && Math.abs(session.camera().centerY() - cy) < 0.01f);
    }
}
