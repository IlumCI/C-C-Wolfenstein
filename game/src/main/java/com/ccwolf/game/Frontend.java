package com.ccwolf.game;

import com.ccwolf.core.entity.Entity;
import com.ccwolf.core.map.MapCatalog;
import com.ccwolf.game.audio.AudioDirector;
import com.ccwolf.game.audio.GameAudio;
import com.ccwolf.game.render.WorldRenderer;
import com.ccwolf.gfx.Surface;
import java.util.List;

/**
 * The screen flow both shells share: title, setup, match, and the ways between them.
 *
 * <p>Before this existed each shell held a nullable session and a setup screen and duplicated
 * the "which screen am I on" logic in its input handler. Now there is exactly one flow to get
 * right, and — just as importantly — exactly one place that owns the audio consequences of
 * changing screens: every transition silences the previous owner's voices, which is also what
 * stops abandoned matches leaking their storm beds into the mixer's voice pool forever.
 */
public final class Frontend {

    public enum Screen { TITLE, SETUP, MATCH }

    private final boolean showQuit;

    private TitleMenu title;
    private MatchSetup setup = new MatchSetup();
    private GameSession session;
    private GameSession demo;
    private AudioDirector menuAudio;

    private Screen screen = Screen.TITLE;
    private long seed;
    private long demoSeed;

    private float width;
    private float height;
    private float scale = 1f;

    /** Where the demo camera is drifting to; refreshed from the fighting every few seconds. */
    private float demoTargetX;
    private float demoTargetY;
    private float demoRetarget;
    private float demoElapsed;

    /** The demo hands the whole match to whoever is watching. Ten minutes is generous. */
    private static final float DEMO_MAX_SECONDS = 600f;

    public Frontend(long seed, boolean showQuit) {
        this.seed = seed;
        this.demoSeed = seed ^ 0xDE30L;
        this.showQuit = showQuit;
        this.title = new TitleMenu(showQuit);
        enterTitle();
    }

    // --- accessors ------------------------------------------------------------------------

    public Screen screen() {
        return screen;
    }

    /** The live match; null unless {@link #screen()} is MATCH. */
    public GameSession session() {
        return session;
    }

    public void layout(float screenWidth, float screenHeight, float density) {
        this.width = screenWidth;
        this.height = screenHeight;
        this.scale = density;
        title.layout(screenWidth, screenHeight, density);
        setup.layout(screenWidth, screenHeight, density);
        if (demo != null) {
            layoutDemo();
        }
    }

    // --- frame ----------------------------------------------------------------------------

    /** Pumps whichever screen is alive. The shell calls this every loop iteration. */
    public void update(float dt) {
        if (screen == Screen.MATCH) {
            session.update(dt);
            return;
        }
        // Menu side: the storm plays and the demo fights on.
        if (menuAudio != null) {
            menuAudio.update(dt);
        }
        if (demo != null) {
            demo.update(dt);
            driftDemoCamera(dt);
            demoElapsed += dt;
            if (demo.world().isGameOver() || demoElapsed > DEMO_MAX_SECONDS) {
                startDemo();
            }
        }
    }

    /**
     * Draws the menu screens; the shell draws the match itself (it owns the Hud). The demo is
     * rendered through the shell's renderer so the terrain cache is shared with real matches.
     */
    public void draw(Surface surface, WorldRenderer renderer, long nowMs) {
        if (screen == Screen.TITLE) {
            if (demo != null) {
                renderer.draw(surface, demo);
            }
            title.draw(surface, nowMs);
        } else if (screen == Screen.SETUP) {
            setup.draw(surface);
        }
    }

    // --- input ----------------------------------------------------------------------------

    /**
     * A tap while on a menu screen. Returns true when the tap asked to quit the process —
     * the shell decides what quitting means on its platform.
     */
    public boolean tap(float x, float y) {
        if (screen == Screen.TITLE) {
            switch (title.tap(x, y)) {
                case SKIRMISH:
                    click();
                    screen = Screen.SETUP;
                    break;
                case QUIT:
                    return true;
                default:
                    break;
            }
        } else if (screen == Screen.SETUP) {
            if (setup.tap(x, y)) {
                beginMatch();
            } else {
                click();
            }
        }
        return false;
    }

    /** Back to the title from a match: pause-abandon, or any tap on the outcome screen. */
    public void abandonMatch() {
        session = null;
        enterTitle();
    }

    // --- transitions ----------------------------------------------------------------------

    private void beginMatch() {
        // The menu hands over the speakers: its storm and clicks die here, and the match's
        // own director starts fresh beds on its first update. This stopAll is also what
        // keeps serial matches from stacking abandoned ambience loops in the voice pool.
        GameAudio.mixer().stopAll();
        menuAudio = null;
        demo = null;
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        session = new GameSession(setup.mapName(), setup.faction(), setup.difficulty(), seed,
                setup.doctrine(), null);
        screen = Screen.MATCH;
    }

    private void enterTitle() {
        GameAudio.mixer().stopAll();
        screen = Screen.TITLE;
        // Fresh screens: MatchSetup's started-flag is one-way, and the menu's ambience
        // restarts cleanly from a new director.
        setup = new MatchSetup();
        title = new TitleMenu(showQuit);
        menuAudio = new AudioDirector(seed, GameAudio.mixer());
        if (width > 0) {
            title.layout(width, height, scale);
            setup.layout(width, height, scale);
        }
        startDemo();
    }

    private void startDemo() {
        demoSeed = demoSeed * 6364136223846793005L + 1442695040888963407L;
        demo = GameSession.demo(MapCatalog.KREISAU_VALLEY, demoSeed);
        demoElapsed = 0f;
        demoRetarget = 0f;
        if (width > 0) {
            layoutDemo();
        }
    }

    private void layoutDemo() {
        demo.camera().setViewport(0, 0, (int) width, (int) height);
        // Pulled back for scenery: the fighting reads as a battle, not as a close-up.
        demo.camera().zoomBy(0.6f, width / 2f, height / 2f);
        demoTargetX = demo.camera().centerX();
        demoTargetY = demo.camera().centerY();
    }

    /**
     * The camera follows the war. While the armies are far apart it tours the two bases in
     * turn — the global centroid of two distant armies is the empty ground between them,
     * which the first cut spent its whole opening staring at. Once the armies close, the
     * midpoint of their centroids is the front, and the front is the show.
     */
    private void driftDemoCamera(float dt) {
        demoRetarget -= dt;
        if (demoRetarget <= 0f) {
            demoRetarget = 4f;
            List<? extends Entity> units = demo.world().units();
            float sx0 = 0f;
            float sy0 = 0f;
            int n0 = 0;
            float sx1 = 0f;
            float sy1 = 0f;
            int n1 = 0;
            for (int i = 0; i < units.size(); i++) {
                Entity u = units.get(i);
                if (u.ownerId() == 0) {
                    sx0 += u.x();
                    sy0 += u.y();
                    n0++;
                } else {
                    sx1 += u.x();
                    sy1 += u.y();
                    n1++;
                }
            }
            if (n0 > 0 && n1 > 0) {
                float cx0 = sx0 / n0;
                float cy0 = sy0 / n0;
                float cx1 = sx1 / n1;
                float cy1 = sy1 / n1;
                float dx = cx0 - cx1;
                float dy = cy0 - cy1;
                if (dx * dx + dy * dy < 24f * 24f) {
                    demoTargetX = (cx0 + cx1) / 2f;
                    demoTargetY = (cy0 + cy1) / 2f;
                } else {
                    demoTour = !demoTour;
                    demoTargetX = demoTour ? cx0 : cx1;
                    demoTargetY = demoTour ? cy0 : cy1;
                }
            } else if (n0 + n1 > 0) {
                demoTargetX = (sx0 + sx1) / (n0 + n1);
                demoTargetY = (sy0 + sy1) / (n0 + n1);
            }
        }
        float k = Math.min(1f, dt * 0.6f);
        demo.camera().centerOn(
                demo.camera().centerX() + (demoTargetX - demo.camera().centerX()) * k,
                demo.camera().centerY() + (demoTargetY - demo.camera().centerY()) * k);
    }

    /** Which base the demo camera is visiting while the armies are still far apart. */
    private boolean demoTour;

    private void click() {
        if (menuAudio != null) {
            menuAudio.uiClick();
        }
    }
}
