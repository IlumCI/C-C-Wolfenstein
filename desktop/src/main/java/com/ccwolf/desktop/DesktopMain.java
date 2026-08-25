package com.ccwolf.desktop;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.audio.AudioOut;
import com.ccwolf.audio.javasound.JLayerMusic;
import com.ccwolf.audio.javasound.JavaSoundSink;
import com.ccwolf.game.Frontend;
import com.ccwolf.game.GameSession;
import com.ccwolf.game.audio.GameAudio;
import com.ccwolf.game.input.InputController;
import com.ccwolf.game.input.PointerEvent;
import com.ccwolf.game.render.Hud;
import com.ccwolf.game.render.WorldRenderer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import com.ccwolf.gfx.java2d.Java2DImages;
import com.ccwolf.gfx.java2d.Java2DSurface;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The desktop shell: a window, a clock, and a mouse.
 *
 * <p>This exists as much for development as for players. The simulation is being pushed towards
 * several hundred units a side, and a real window with a real profiler attached is the only
 * honest way to see whether a battle that size actually holds together — rendering test frames
 * one at a time tells you what a moment looked like, not whether the thing runs.
 */
public final class DesktopMain {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final long TARGET_FRAME_MS = 16;

    public static void main(String[] args) {
        long seed = argLong(args, "--seed", 7L);
        Difficulty difficulty = Difficulty.valueOf(argString(args, "--difficulty", "VETERAN"));

        Java2DImages.install();

        int headlessFrames = (int) argLong(args, "--headless", 0L);
        if (headlessFrames <= 0) {
            // Headless runs are measurements and must stay mute; a windowed run gets sound.
            // The sink starts lazily on first mixer use, so the setup screen is silent.
            AudioOut.install(new JavaSoundSink());
            AudioOut.installMusic(new JLayerMusic());
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    GameAudio.shutdown();
                }
            }, "cc-wolfenstein-audio-shutdown"));
        }

        final WorldRenderer renderer = new WorldRenderer();
        final Hud hud = new Hud();
        hud.layout(WIDTH, HEIGHT, 2f);

        if (headlessFrames > 0) {
            // Headless runs are measurements, not matches: they skip the setup screen and take
            // the command line's word for everything, as they always have.
            GameSession session = new GameSession(argString(args, "--map", "kreisau"),
                    Faction.RESISTANCE, difficulty, seed, null, null);
            session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
            renderHeadless(session, renderer, hud, headlessFrames);
            return;
        }

        final long matchSeed = seed;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                GamePanel panel = new GamePanel(matchSeed, renderer, hud);
                JFrame frame = new JFrame("C&C: Wolfenstein");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setContentPane(panel);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                panel.requestFocusInWindow();
                panel.start();
            }
        });
    }

    /**
     * Runs the game with no window, drawing every frame into a buffer and timing it.
     *
     * <p>Two things this is for. It makes the desktop build verifiable on a build machine with
     * no display. And it measures the render half of the frame budget on its own, which matters
     * because at several hundred units a side the renderer, not the simulation, is expected to
     * be what runs out first.
     */
    private static void renderHeadless(GameSession session, WorldRenderer renderer, Hud hud,
            int frames) {
        BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        renderer.profiler().setEnabled(true);
        long slowest = 0L;
        long total = 0L;
        // Discarded: the first frames measure the JIT compiling the renderer, not the renderer.
        int warmup = Math.min(120, frames / 3);
        int measured = 0;

        for (int i = 0; i < frames; i++) {
            session.update(1f / 60f);

            long started = System.nanoTime();
            Graphics2D g = buffer.createGraphics();
            try {
                Java2DSurface surface = new Java2DSurface(g, WIDTH, HEIGHT);
                surface.clear(0xFF0B0C0A);
                renderer.draw(surface, session);
                hud.draw(surface, session, i * 16L);
            } finally {
                g.dispose();
            }
            long elapsed = System.nanoTime() - started;
            if (i == warmup) {
                // Discard the warm-up frames' timings along with their wall clock.
                renderer.profiler().reset();
            }
            if (i >= warmup) {
                total += elapsed;
                slowest = Math.max(slowest, elapsed);
                measured++;
            }
        }

        double meanMs = total / (double) Math.max(1, measured) / 1_000_000.0;
        System.out.printf("Rendered %d frames (%d measured, %d warm-up): "
                        + "%.2f ms mean, %.2f ms worst (%.0f fps)%n",
                frames, measured, warmup, meanMs, slowest / 1_000_000.0, 1000.0 / meanMs);
        System.out.println("Units alive: " + session.world().units().size()
                + ", tick " + session.world().tick());
        System.out.println();
        System.out.println(renderer.profiler().report());
    }

    /** Draws into an offscreen buffer, then blits — the same shape as the Android loop. */
    private static final class GamePanel extends JPanel {

        private final long seed;
        private final WorldRenderer renderer;
        private final Hud hud;

        /** Title, setup and match in one flow; the match exists only past the setup screen. */
        private final Frontend frontend;
        private InputController input;

        private final BufferedImage frame =
                new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        private final PointerEvent pointer = new PointerEvent();

        /**
         * Desktop has one cursor, so pinch-to-zoom has no equivalent gesture. The wheel drives
         * the camera directly instead of pretending to be a second finger; the right button
         * grabs the map and drags it.
         */
        private boolean panning;
        private float panLastX;
        private float panLastY;

        /** For the double-press of a group digit that jumps the camera to the group. */
        private int lastGroupKey = -1;
        private long lastGroupKeyAtMs;

        GamePanel(long seed, WorldRenderer renderer, Hud hud) {
            this.seed = seed;
            this.renderer = renderer;
            this.hud = hud;
            frontend = new Frontend(seed, true);
            frontend.layout(WIDTH, HEIGHT, 2f);
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setFocusable(true);
            installListeners();
        }

        /** The current match, or null while a menu screen is up. */
        private GameSession session() {
            return frontend.screen() == Frontend.Screen.MATCH ? frontend.session() : null;
        }

        /** Routes a tap to the flow, and finishes match setup the moment one begins. */
        private void menuTap(float x, float y) {
            boolean before = frontend.screen() == Frontend.Screen.MATCH;
            if (frontend.tap(x, y)) {
                System.exit(0);
            }
            if (!before && frontend.screen() == Frontend.Screen.MATCH) {
                GameSession session = frontend.session();
                session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);
                input = new InputController(session, hud, renderer, 2f);
                input.setAbandonListener(new Runnable() {
                    @Override
                    public void run() {
                        frontend.abandonMatch();
                    }
                });
            }
        }

        private void installListeners() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    GameSession session = session();
                    if (session == null) {
                        menuTap(e.getX(), e.getY());
                        return;
                    }
                    if (session.world().isGameOver()) {
                        // Any click on the outcome screen leads back out to the title.
                        frontend.abandonMatch();
                        return;
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        panning = true;
                        panLastX = e.getX();
                        panLastY = e.getY();
                        return;
                    }
                    input.onPointer(pointer.single(PointerEvent.Action.DOWN, e.getX(), e.getY()));
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    GameSession session = session();
                    if (session == null) {
                        return;
                    }
                    if (panning) {
                        // Grab-the-map: the ground follows the cursor. This was a stub for
                        // an embarrassingly long time - the flag flipped and nothing moved.
                        session.camera().panByPixels(e.getX() - panLastX, e.getY() - panLastY);
                        panLastX = e.getX();
                        panLastY = e.getY();
                        return;
                    }
                    input.onPointer(pointer.single(PointerEvent.Action.MOVE, e.getX(), e.getY()));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (session() == null) {
                        return;
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        panning = false;
                        return;
                    }
                    input.onPointer(pointer.single(PointerEvent.Action.UP, e.getX(), e.getY()));
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    GameSession session = session();
                    if (session == null) {
                        return;
                    }
                    float factor = e.getWheelRotation() < 0 ? 1.12f : 1f / 1.12f;
                    session.camera().zoomBy(factor, e.getX(), e.getY());
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    GameSession session = session();
                    if (session == null) {
                        return;
                    }
                    // Control groups: Ctrl+digit remembers the selection, digit recalls it,
                    // and a quick second press of the same digit jumps the camera to it.
                    int code = e.getKeyCode();
                    if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) {
                        int slot = code - KeyEvent.VK_0;
                        if (e.isControlDown()) {
                            session.assignControlGroup(slot);
                        } else if (session.recallControlGroup(slot)) {
                            long now = System.currentTimeMillis();
                            if (slot == lastGroupKey && now - lastGroupKeyAtMs < 450) {
                                session.centerCameraOnSelection();
                            }
                            lastGroupKey = slot;
                            lastGroupKeyAtMs = now;
                        }
                        return;
                    }

                    float step = 64f;
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                            session.camera().panByPixels(step, 0f);
                            break;
                        case KeyEvent.VK_RIGHT:
                            session.camera().panByPixels(-step, 0f);
                            break;
                        case KeyEvent.VK_UP:
                            session.camera().panByPixels(0f, step);
                            break;
                        case KeyEvent.VK_DOWN:
                            session.camera().panByPixels(0f, -step);
                            break;
                        case KeyEvent.VK_SPACE:
                            session.setPaused(!session.isPaused());
                            break;
                        case KeyEvent.VK_ESCAPE:
                            // Esc pauses; Esc again abandons the field. The same ladder the
                            // Android back button climbs.
                            if (session.isPaused()) {
                                frontend.abandonMatch();
                            } else {
                                session.setPaused(true);
                            }
                            break;
                        case KeyEvent.VK_M:
                            GameAudio.setMuted(!GameAudio.isMuted());
                            break;
                        case KeyEvent.VK_F:
                            // Desktop only, and deliberately: the plates are full at two rows,
                            // and Android has no key path at all.
                            renderer.toggleControlDetail();
                            break;
                        default:
                            break;
                    }
                }
            });
        }

        void start() {
            Thread loop = new Thread(new Runnable() {
                @Override
                public void run() {
                    long previous = System.nanoTime();
                    while (true) {
                        long now = System.nanoTime();
                        // The frontend pumps whichever screen is alive - menu ambience and
                        // the attract-mode demo need the clock as much as a match does.
                        frontend.update((now - previous) / 1_000_000_000f);
                        previous = now;

                        render();
                        repaint();

                        long frameMs = (System.nanoTime() - now) / 1_000_000L;
                        if (frameMs < TARGET_FRAME_MS) {
                            try {
                                Thread.sleep(TARGET_FRAME_MS - frameMs);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            }, "cc-wolfenstein-desktop");
            loop.setDaemon(true);
            loop.start();
        }

        private void render() {
            Graphics2D g = frame.createGraphics();
            try {
                Java2DSurface surface = new Java2DSurface(g, WIDTH, HEIGHT);
                surface.clear(0xFF0B0C0A);
                GameSession current = session();
                if (current == null) {
                    frontend.draw(surface, renderer, System.currentTimeMillis());
                } else {
                    renderer.draw(surface, current);
                    hud.draw(surface, current, System.currentTimeMillis());
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(frame, 0, 0, null);
        }
    }

    private static String argString(String[] args, String name, String fallback) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static long argLong(String[] args, String name, long fallback) {
        String raw = argString(args, name, null);
        return raw == null ? fallback : Long.parseLong(raw);
    }

    private DesktopMain() {
    }
}
