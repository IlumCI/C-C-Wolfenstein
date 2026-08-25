package com.ccwolf.game.audio;

import com.ccwolf.audio.AudioOut;

/**
 * The process-wide audio engine: one mixer, one sink, shared by every session.
 *
 * <p>Matches come and go (restart, back to the setup screen) but the device stays open — an
 * AudioTrack or a SourceDataLine is not something to churn per match. So the mixer lives here,
 * lazily started on first use, and each session's director just plays into it.
 *
 * <p>When no sink was installed (tests, headless, the core harness) nothing here starts a
 * thread or opens a device: the silent sink swallows {@code start} and the mixer renders to
 * nobody, costing nothing.
 */
public final class GameAudio {

    private static Mixer mixer;
    private static boolean started;
    private static boolean muted;

    private GameAudio() {
    }

    /** The shared mixer, starting the platform sink on first call. */
    public static synchronized Mixer mixer() {
        if (mixer == null) {
            mixer = new Mixer(SoundBank.get());
        }
        if (!started && AudioOut.isInstalled()) {
            AudioOut.sink().start(WaveCanvas.SAMPLE_RATE, mixer);
            started = true;
        }
        return mixer;
    }

    public static synchronized void setMuted(boolean mute) {
        muted = mute;
        if (mixer != null) {
            mixer.setMasterGain(mute ? 0f : 1f);
        }
    }

    public static synchronized boolean isMuted() {
        return muted;
    }

    /** Platform lifecycle: the app lost the screen. */
    public static synchronized void pause() {
        AudioOut.sink().pause();
    }

    public static synchronized void resume() {
        AudioOut.sink().resume();
    }

    /** Shutdown: release the device. Only the desktop shell bothers; Android just dies. */
    public static synchronized void shutdown() {
        AudioOut.sink().stop();
        started = false;
    }
}
