package com.ccwolf.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.core.entity.Unit;
import com.ccwolf.core.entity.UnitType;
import com.ccwolf.core.squad.Formation;
import com.ccwolf.core.squad.Squad;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Squads as the player meets them: selecting one, seeing what is left of it, ordering it.
 *
 * <p>The frame this writes is the review: a squad card that reads wrong, or eight selection
 * rings where there should be one bracket, is the sort of thing that is obvious in a picture
 * and invisible in a test assertion.
 */
public class SquadHudTest {

    static {
        Frame.useAwtBackend();
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    private GameSession session;
    private Hud hud;
    private WorldRenderer renderer;

    private void setUp(long seed) {
        session = new GameSession(Faction.RESISTANCE, Difficulty.VETERAN, seed);
        hud = new Hud();
        renderer = new WorldRenderer();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
        session.camera().setMap(session.world().map());
        session.world().setFogEnabled(false);
    }

    /**
     * A squad well clear of the starting base.
     *
     * <p>Placed away from home on purpose: spawning it over the player's own starting units
     * meant a tap could land on a passing rifleman instead of a squad member, and a marquee
     * round one man swept up a bystander. Both made these tests fail for reasons that had
     * nothing to do with squads.
     */
    private Squad squadAtOpenGround() {
        List<Unit> members = new ArrayList<Unit>();
        for (int i = 0; i < 8; i++) {
            members.add(session.world().spawnUnit(session.playerId(), UnitType.PARTISAN,
                    30.5f + (i % 4) * 0.9f, 44.5f + (i / 4) * 0.9f));
        }
        return session.world().formSquad(session.playerId(), members);
    }

    @Test
    public void tappingOneManSelectsHisWholeSquad() {
        setUp(1L);
        Squad squad = squadAtOpenGround();
        assertNotNull(squad);

        Unit member = (Unit) session.world().entity(squad.memberAt(3));
        session.selectAt(member.x(), member.y());

        assertTrue(session.hasSquadSelection());
        assertEquals(1, session.selectedSquads().size());
        assertEquals(squad.strength(), session.selection().size());
    }

    @Test
    public void aMarqueeThatCatchesOneManTakesTheSquad() {
        setUp(2L);
        Squad squad = squadAtOpenGround();
        Unit corner = (Unit) session.world().entity(squad.memberAt(0));

        // A box round one man only.
        session.selectInBox(corner.x() - 0.2f, corner.y() - 0.2f,
                corner.x() + 0.2f, corner.y() + 0.2f);

        assertTrue(session.hasSquadSelection());
        assertEquals("a marquee should not half-select a formation",
                squad.strength(), session.selection().size());
    }

    @Test
    public void selectingOneManDeliberatelyDoesNotBreakHimOut() {
        setUp(3L);
        Squad squad = squadAtOpenGround();
        Unit member = (Unit) session.world().entity(squad.memberAt(2));

        session.selectIndividualAt(member.x(), member.y());

        assertEquals(1, session.selection().size());
        assertTrue(!session.hasSquadSelection());
        // Looking at him is not taking him: he leaves only when given an order of his own.
        assertTrue(member.isInSquad());
        assertEquals(8, squad.strength());
    }

    @Test
    public void cyclingFormationChangesTheShape() {
        setUp(4L);
        Squad squad = squadAtOpenGround();
        Unit member = (Unit) session.world().entity(squad.memberAt(0));
        session.selectAt(member.x(), member.y());

        Formation before = squad.formation();
        session.cycleFormation();
        assertTrue(squad.formation() != before);
    }

    @Test
    public void aSquadRendersWithOneBracketAndACard() throws IOException {
        setUp(5L);
        Squad squad = squadAtOpenGround();

        // Two casualties, so the card has gaps to show and the strength bar is not full.
        ((Unit) session.world().entity(squad.memberAt(2))).kill();
        ((Unit) session.world().entity(squad.memberAt(5))).kill();
        session.world().step();

        Unit member = (Unit) session.world().entity(squad.memberAt(0));
        session.selectAt(member.x(), member.y());
        session.camera().centerOn(member.x(), member.y());
        session.update(0.05f);

        Frame frame = new Frame(WIDTH, HEIGHT);
        frame.surface().clear(0xFF0B0C0A);
        renderer.draw(frame.surface(), session);
        hud.draw(frame.surface(), session, 0L);
        frame.save("squad-selected.png");
        Frame.write(frame.zoom(960, 520, 320, 200, 3), "squad-card-zoom.png");

        assertEquals(6, squad.strength());
    }
}
