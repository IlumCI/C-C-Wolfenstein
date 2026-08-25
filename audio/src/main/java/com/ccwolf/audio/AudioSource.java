package com.ccwolf.audio;

/**
 * Something that produces a continuous PCM stream on demand — in practice, the game's mixer.
 *
 * <p>The direction of pull is the whole design: the platform's audio thread calls {@link
 * #render} whenever its device buffer has room, and the game never blocks on audio. Triggering
 * a sound is a cheap store into the source; the expensive part, actually filling sample
 * buffers, happens on the platform's schedule.
 */
public interface AudioSource {

    /**
     * Fills {@code interleavedStereo} with up to {@code frames} frames (left, right, left,
     * right...) and returns the number of frames written. Anything not written is silence the
     * caller must supply itself; returning less than {@code frames} is allowed but never
     * required — a mixer with nothing playing writes silence and returns {@code frames}.
     *
     * <p>Called from the platform's audio thread. Implementations must be safe to call
     * concurrently with their own trigger methods, and must not block.
     */
    int render(short[] interleavedStereo, int frames);
}
