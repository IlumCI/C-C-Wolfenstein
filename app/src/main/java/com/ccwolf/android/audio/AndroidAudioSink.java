package com.ccwolf.android.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import com.ccwolf.audio.AudioSink;
import com.ccwolf.audio.AudioSource;

/**
 * Android audio output: a streaming {@code AudioTrack} fed by its own thread.
 *
 * <p>Deliberately the mirror of the desktop's JavaSound sink: the game synthesized every cue in
 * memory, so SoundPool (which wants files) is the wrong tool — a stream the software mixer
 * fills is the right one, and it means both platforms hear the identical mix. Needs no Context,
 * so it installs beside {@code AndroidImages} before the activity even calls super.
 */
public final class AndroidAudioSink implements AudioSink {

    private static final int PUMP_FRAMES = 1024;

    private AudioTrack track;
    private Thread pump;
    private volatile boolean running;
    private volatile boolean paused;

    @Override
    public synchronized void start(int sampleRate, final AudioSource source) {
        if (pump != null) {
            return;
        }
        int minBytes = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBytes <= 0) {
            return;
        }
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(minBytes, PUMP_FRAMES * 4 * 4))
                    .build();
            track.play();
        } catch (RuntimeException e) {
            // A device with no audio output is a device with no audio output.
            track = null;
            return;
        }

        running = true;
        pump = new Thread(new Runnable() {
            @Override
            public void run() {
                short[] frames = new short[PUMP_FRAMES * 2];
                while (running) {
                    if (paused) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    int rendered = source.render(frames, PUMP_FRAMES);
                    for (int i = rendered * 2; i < frames.length; i++) {
                        frames[i] = 0;
                    }
                    // Blocking write: the track's buffer paces the pump, as on desktop.
                    track.write(frames, 0, frames.length);
                }
            }
        }, "cc-wolfenstein-audio");
        pump.setDaemon(true);
        pump.start();
    }

    @Override
    public void pause() {
        if (track == null) {
            return;
        }
        paused = true;
        track.pause();
        track.flush();
    }

    @Override
    public void resume() {
        if (track == null) {
            return;
        }
        paused = false;
        track.play();
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (pump != null) {
            try {
                pump.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pump = null;
        }
        if (track != null) {
            track.release();
            track = null;
        }
    }
}
