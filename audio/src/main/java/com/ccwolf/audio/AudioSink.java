package com.ccwolf.audio;

/**
 * The narrow waist between the game and a platform's audio output.
 *
 * <p>A sink has exactly one job: pull PCM from an {@link AudioSource} and put it on the
 * speaker. Everything with judgement in it — what to play, how loud, from where — lives on the
 * game side of this interface, in the mixer that implements the source. That is what makes a
 * second backend cheap, the same argument {@code Surface} makes for graphics.
 */
public interface AudioSink {

    /**
     * Starts pulling from {@code source} at {@code sampleRate} Hz, 16-bit interleaved stereo,
     * on the sink's own thread. Calling start on a started sink is a no-op.
     */
    void start(int sampleRate, AudioSource source);

    /** Stops pulling and silences output, keeping the device open. No-op when not started. */
    void pause();

    /** Resumes a paused sink. No-op when not started or not paused. */
    void resume();

    /** Stops the thread and releases the device. The sink cannot be restarted afterwards. */
    void stop();
}
