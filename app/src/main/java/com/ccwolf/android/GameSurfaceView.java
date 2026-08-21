package com.ccwolf.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.ccwolf.android.input.InputController;
import com.ccwolf.android.render.Hud;
import com.ccwolf.android.render.WorldRenderer;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;

/**
 * The game surface and its render thread.
 *
 * <p>The simulation runs at a fixed 20 Hz inside {@link GameSession}; this thread just draws as
 * often as it can and feeds real elapsed time in, so the game plays at the same speed on a
 * 60 Hz phone and a 120 Hz one.
 */
public final class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final long TARGET_FRAME_MS = 16;

    private GameSession session;
    private final Hud hud = new Hud();
    private final WorldRenderer renderer = new WorldRenderer();
    private InputController input;

    private RenderThread thread;
    private final float density;

    public GameSurfaceView(Context context, Faction faction, Difficulty difficulty, long seed) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        getHolder().addCallback(this);
        setFocusable(true);
        newSession(faction, difficulty, seed);
    }

    private void newSession(Faction faction, Difficulty difficulty, long seed) {
        session = new GameSession(faction, difficulty, seed);
        input = new InputController(session, hud, renderer, density);
        if (getWidth() > 0) {
            applyLayout(getWidth(), getHeight());
        }
    }

    public GameSession session() {
        return session;
    }

    /** Starts a fresh match on the same map, used by the end-of-game overlay. */
    public void restart() {
        Faction faction = session.player().faction();
        newSession(faction, Difficulty.VETERAN, System.currentTimeMillis());
    }

    private void applyLayout(int width, int height) {
        hud.layout(width, height, density);
        session.camera().setViewport(0, 0, (int) hud.sidebarLeft(), height);
        session.camera().setMap(session.world().map());
        int[] spawn = session.world().map().spawnPoint(session.playerId());
        session.camera().centerOn(spawn[0] + 3f, spawn[1] + 3f);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (session.world().isGameOver() && event.getActionMasked() == MotionEvent.ACTION_UP) {
            restart();
            return true;
        }
        return input.onTouch(event);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        thread = new RenderThread(holder);
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        applyLayout(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopThread();
    }

    public void pauseGame() {
        session.setPaused(true);
    }

    private void stopThread() {
        RenderThread t = thread;
        thread = null;
        if (t != null) {
            t.finish();
            boolean retry = true;
            while (retry) {
                try {
                    t.join();
                    retry = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Draws frames and drives the simulation clock. */
    private final class RenderThread extends Thread {

        private final SurfaceHolder holder;
        private volatile boolean running = true;

        RenderThread(SurfaceHolder holder) {
            super("cc-wolfenstein-render");
            this.holder = holder;
        }

        void finish() {
            running = false;
        }

        @Override
        public void run() {
            long previous = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                float delta = (now - previous) / 1_000_000_000f;
                previous = now;

                GameSession current = session;
                if (current != null) {
                    current.update(delta);
                }

                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null && current != null) {
                        synchronized (holder) {
                            canvas.drawColor(0xFF0B0C0A);
                            renderer.draw(canvas, current);
                            hud.draw(canvas, current, System.currentTimeMillis());
                        }
                    }
                } finally {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                    }
                }

                long frameMs = (System.nanoTime() - now) / 1_000_000L;
                if (frameMs < TARGET_FRAME_MS) {
                    try {
                        Thread.sleep(TARGET_FRAME_MS - frameMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                }
            }
        }
    }
}
