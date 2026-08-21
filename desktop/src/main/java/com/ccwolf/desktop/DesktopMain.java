package com.ccwolf.desktop;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.game.GameSession;
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

        final GameSession session = new GameSession(Faction.RESISTANCE, difficulty, seed);
        final WorldRenderer renderer = new WorldRenderer();
        final Hud hud = new Hud();
        hud.layout(WIDTH, HEIGHT, 2f);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), HEIGHT);

        if (headlessFrames > 0) {
            renderHeadless(session, renderer, hud, headlessFrames);
            return;
        }

        final InputController input = new InputController(session, hud, renderer, 2f);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                GamePanel panel = new GamePanel(session, renderer, hud, input);
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

        private final GameSession session;
        private final WorldRenderer renderer;
        private final Hud hud;
        private final InputController input;

        private final BufferedImage frame =
                new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        private final PointerEvent pointer = new PointerEvent();

        /**
         * Desktop has one cursor, so pinch-to-zoom has no equivalent gesture. The wheel drives
         * the camera directly instead of pretending to be a second finger.
         */
        private boolean panning;

        GamePanel(GameSession session, WorldRenderer renderer, Hud hud, InputController input) {
            this.session = session;
            this.renderer = renderer;
            this.hud = hud;
            this.input = input;
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setFocusable(true);
            installListeners();
        }

        private void installListeners() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        panning = true;
                        return;
                    }
                    input.onPointer(pointer.single(PointerEvent.Action.DOWN, e.getX(), e.getY()));
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (panning) {
                        return;
                    }
                    input.onPointer(pointer.single(PointerEvent.Action.MOVE, e.getX(), e.getY()));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        panning = false;
                        return;
                    }
                    input.onPointer(pointer.single(PointerEvent.Action.UP, e.getX(), e.getY()));
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
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
                        session.update((now - previous) / 1_000_000_000f);
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
                renderer.draw(surface, session);
                hud.draw(surface, session, System.currentTimeMillis());
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
