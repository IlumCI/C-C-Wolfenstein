package com.ccwolf.game;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.diag.StateDigest;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.game.save.SaveDir;
import com.ccwolf.game.save.SaveGame;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The save system's one promise, checked with the sharpest instrument the project owns: a
 * loaded game IS the saved game, bit for bit, by the same exact digest the determinism gate
 * uses. Everything else — the file surviving "another instance", re-saving a loaded game,
 * deleting a finished war — hangs off that.
 */
public class SaveLoadTest {

    static {
        Frame.useAwtBackend();
    }

    @After
    public void tearDown() {
        // Static seam: leave nothing behind for the other test classes in this JVM.
        SaveDir.install(null);
    }

    private static File tempDir() throws IOException {
        File dir = Files.createTempDirectory("ccwolf-save-test").toFile();
        dir.deleteOnExit();
        return dir;
    }

    /** A short scripted match: some ticks, some orders, some more ticks. */
    private static GameSession playAWhile(long seed) {
        GameSession session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, seed);
        session.camera().setViewport(0, 0, 1000, 720);
        for (int i = 0; i < 100; i++) {
            session.update(0.05f);
        }
        Unit u = firstOwnUnit(session);
        session.selectAt(u.x(), u.y());
        session.commandAt(u.x() + 4f, u.y() + 2f, false);
        for (int i = 0; i < 100; i++) {
            session.update(0.05f);
        }
        session.selectAt(u.x(), u.y());
        session.commandAt(u.x() - 2f, u.y() + 1f, true);
        for (int i = 0; i < 100; i++) {
            session.update(0.05f);
        }
        return session;
    }

    private static Unit firstOwnUnit(GameSession session) {
        for (Unit u : session.world().units()) {
            if (u.ownerId() == session.playerId()) {
                return u;
            }
        }
        throw new IllegalStateException("no units");
    }

    @Test
    public void aLoadedGameIsTheSavedGame() throws IOException {
        SaveDir.install(tempDir());
        GameSession session = playAWhile(101L);
        long savedDigest = StateDigest.exact(session.world());
        int savedTick = session.world().tick();

        assertTrue(SaveGame.write(session));
        assertTrue(SaveGame.exists());

        // "Another instance": nothing survives but the file.
        SaveGame.Data data = SaveGame.load();
        assertNotNull(data);
        GameSession restored = GameSession.restore(data);

        assertEquals(savedTick, restored.world().tick());
        assertEquals("replay arrived at a different world",
                savedDigest, StateDigest.exact(restored.world()));
    }

    @Test
    public void aRestoredGameCanItselfBeSaved() throws IOException {
        SaveDir.install(tempDir());
        GameSession session = playAWhile(102L);
        assertTrue(SaveGame.write(session));
        int savedCommands = SaveGame.load().commands.size();
        assertTrue("the script should have recorded orders", savedCommands >= 2);

        GameSession restored = GameSession.restore(SaveGame.load());
        // The replay re-submits through the bus, so the restored session's log rebuilt
        // itself; saving again must lose nothing.
        assertTrue(SaveGame.write(restored));
        assertEquals(savedCommands, SaveGame.load().commands.size());

        // And the twice-removed copy still replays to the same world.
        assertEquals(StateDigest.exact(restored.world()),
                StateDigest.exact(GameSession.restore(SaveGame.load()).world()));
    }

    @Test
    public void playOnFromTheLoadDivergesFromNothing() throws IOException {
        // Loading is not the end: the restored session must keep simulating identically to
        // the original had it never stopped. Run both forward and compare again.
        SaveDir.install(tempDir());
        GameSession original = playAWhile(103L);
        assertTrue(SaveGame.write(original));
        GameSession restored = GameSession.restore(SaveGame.load());

        for (int i = 0; i < 200; i++) {
            original.update(0.05f);
            restored.update(0.05f);
        }
        assertEquals("the futures diverged after loading",
                StateDigest.exact(original.world()), StateDigest.exact(restored.world()));
    }

    @Test
    public void noDirectoryMeansNoSavingAndNoCrashing() {
        SaveDir.install(null);
        GameSession session = playAWhile(104L);
        assertFalse(SaveGame.write(session));
        assertFalse(SaveGame.exists());
        assertEquals(null, SaveGame.load());
    }

    @Test
    public void theContinueRowResumesTheFront() throws IOException {
        SaveDir.install(tempDir());
        GameSession session = playAWhile(106L);
        assertTrue(SaveGame.write(session));

        // A fresh frontend - a fresh app launch - offers CONTINUE at the top of the menu.
        Frontend frontend = new Frontend(9L, false);
        frontend.layout(1280, 720, 2f);
        for (int y = (int) (720 * 0.56f);
                y < 720 * 0.64f && frontend.screen() == Frontend.Screen.TITLE; y += 4) {
            frontend.tap(640, y);
        }
        assertEquals(Frontend.Screen.MATCH, frontend.screen());
        assertEquals(session.world().tick(), frontend.session().world().tick());
    }

    @Test
    public void deleteEndsTheWarOnDisk() throws IOException {
        SaveDir.install(tempDir());
        assertTrue(SaveGame.write(playAWhile(105L)));
        assertTrue(SaveGame.exists());
        SaveGame.delete();
        assertFalse(SaveGame.exists());
    }
}
