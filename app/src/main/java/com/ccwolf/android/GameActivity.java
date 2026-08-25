package com.ccwolf.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.ccwolf.android.audio.AndroidAudioSink;
import com.ccwolf.android.audio.MediaPlayerMusic;
import com.ccwolf.android.gfx.AndroidImages;
import com.ccwolf.audio.AudioOut;
import com.ccwolf.game.audio.GameAudio;

/**
 * The whole app: one fullscreen, landscape activity hosting the game surface.
 *
 * <p>Launching lands on the title screen — the attract-mode demo fighting behind the name —
 * and the shared {@link com.ccwolf.game.Frontend} runs everything from there.
 */
public final class GameActivity extends Activity {

    private GameSurfaceView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Before anything can bake a sprite, or play a cue.
        AndroidImages.install();
        AudioOut.install(new AndroidAudioSink());
        AudioOut.installMusic(new MediaPlayerMusic(this));

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = new GameSurfaceView(this, System.currentTimeMillis());
        setContentView(view);
        goFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            goFullscreen();
            GameAudio.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Losing focus pauses the match rather than letting the AI play on without you —
        // and stops the speaker: background apps that keep making noise get uninstalled.
        if (view != null) {
            view.pauseGame();
        }
        GameAudio.pause();
    }

    @Override
    public void onBackPressed() {
        // The ladder: fighting -> paused -> title -> out. Each press climbs one rung.
        if (view != null && view.session() != null) {
            if (!view.session().isPaused()) {
                view.session().setPaused(true);
                view.session().showMessage("Paused - back again to abandon the field");
            } else {
                view.abandonMatch();
            }
            return;
        }
        super.onBackPressed();
    }

    @SuppressWarnings("deprecation")
    private void goFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
