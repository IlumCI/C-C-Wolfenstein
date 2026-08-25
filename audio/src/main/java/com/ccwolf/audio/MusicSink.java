package com.ccwolf.audio;

/**
 * Long-form music playback — a different animal from the cue mixer.
 *
 * <p>The mixer plays synthesized PCM the game owns. Music is the one place a file enters the
 * picture, and deliberately as a <em>drop-in</em>: the repository ships no recordings (it can't
 * — a licensed track is not ours to distribute), but a player can put their own file in a
 * well-known local slot and the platform backend will loop it. No file, no music, no error.
 */
public interface MusicSink {

    /**
     * Starts looping the local menu-music file if one exists. Returns true when something is
     * actually playing; false when the slot is empty or the platform cannot decode it.
     */
    boolean playLoop();

    /** Stops playback and releases the decoder. Safe to call when nothing plays. */
    void stop();
}
