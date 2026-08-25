package com.ccwolf.audio;

/**
 * Process-wide audio backend, installed once by the platform shell — the audio twin of
 * {@code Gfx.install}.
 *
 * <p>One deliberate difference from graphics: the default here is a silent sink rather than an
 * exception. A frame cannot be drawn into nothing, so an uninstalled graphics backend is a
 * programming error worth throwing over. Sound is different — the headless benchmark, the unit
 * tests and a build machine with no audio device are all legitimate places for the game to run
 * mutely, and none of them should need to know audio exists.
 */
public final class AudioOut {

    /** Accepts start/pause/stop and does nothing at all. */
    private static final AudioSink SILENT = new AudioSink() {
        @Override
        public void start(int sampleRate, AudioSource source) {
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void stop() {
        }
    };

    /** Plays nothing, truthfully. */
    private static final MusicSink NO_MUSIC = new MusicSink() {
        @Override
        public boolean playLoop() {
            return false;
        }

        @Override
        public void stop() {
        }
    };

    private static AudioSink sink = SILENT;
    private static MusicSink music = NO_MUSIC;

    private AudioOut() {
    }

    /** Called once by the platform shell during startup. Later calls replace the sink. */
    public static void install(AudioSink audioSink) {
        sink = audioSink == null ? SILENT : audioSink;
    }

    /** True when a real backend was installed — i.e. sound can actually be heard. */
    public static boolean isInstalled() {
        return sink != SILENT;
    }

    /** Never null; silent when nothing was installed. */
    public static AudioSink sink() {
        return sink;
    }

    /** Called by the platform shell when it can play a drop-in music file. */
    public static void installMusic(MusicSink musicSink) {
        music = musicSink == null ? NO_MUSIC : musicSink;
    }

    /** Never null; a truthful no-op when no backend (or no file) exists. */
    public static MusicSink music() {
        return music;
    }
}
