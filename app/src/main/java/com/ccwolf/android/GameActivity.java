package com.ccwolf.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.ccwolf.android.gfx.AndroidImages;
import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.entity.Faction;

/**
 * The whole app: one fullscreen, landscape activity hosting the game surface.
 *
 * <p>There is no menu yet — launching drops you straight into a skirmish on Kreisau Valley as
 * the Resistance against a Veteran-difficulty Regime opponent.
 */
public final class GameActivity extends Activity {

    private GameSurfaceView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Before anything can bake a sprite.
        AndroidImages.install();

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Recruit difficulty by default: the opening minute is the player's only chance to
        // get a barracks up, and a Veteran opponent is at the gate before that happens.
        view = new GameSurfaceView(this, Faction.RESISTANCE, Difficulty.RECRUIT,
                System.currentTimeMillis());
        setContentView(view);
        goFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            goFullscreen();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Losing focus pauses the match rather than letting the AI play on without you.
        if (view != null) {
            view.pauseGame();
        }
    }

    @Override
    public void onBackPressed() {
        if (view != null && !view.session().isPaused()) {
            view.session().setPaused(true);
            view.session().showMessage("Paused - back again to quit");
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
