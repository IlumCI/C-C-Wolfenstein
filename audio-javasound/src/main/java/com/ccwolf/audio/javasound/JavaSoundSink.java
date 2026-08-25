package com.ccwolf.audio.javasound;

import com.ccwolf.audio.AudioSink;
import com.ccwolf.audio.AudioSource;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Desktop audio output: a {@code SourceDataLine} fed by a daemon thread.
 *
 * <p>The thread pulls from the game's {@link AudioSource} and writes to the line, and the
 * line's own buffer provides the slack between the two clocks. {@code line.write} blocks when
 * the device is full, which is exactly the pacing we want — no timers, no sleeps tuned by hand.
 *
 * <p>A machine with no audio device (a build box, a CI container) is not an error: the sink
 * logs once and degrades to doing nothing, because a game that cannot be built where it cannot
 * be heard would be a worse trade.
 */
public final class JavaSoundSink implements AudioSink {

    /** Frames pulled per trip to the device; the knob that trades latency for underruns. */
    private static final int PUMP_FRAMES = 1024;

    private SourceDataLine line;
    private Thread pump;
    private volatile boolean running;
    private volatile boolean paused;

    @Override
    public synchronized void start(int sampleRate, final AudioSource source) {
        if (pump != null) {
            return;
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
        try {
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(
                    SourceDataLine.class, format));
            // Four pump buffers of device-side slack: deep enough to ride out a GC pause,
            // shallow enough that a shot is heard the frame it is fired.
            line.open(format, PUMP_FRAMES * 4 * 4);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("[audio] no output line available, running silent: "
                    + e.getMessage());
            line = null;
            return;
        }

        running = true;
        pump = new Thread(new Runnable() {
            @Override
            public void run() {
                short[] frames = new short[PUMP_FRAMES * 2];
                byte[] bytes = new byte[PUMP_FRAMES * 4];
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
                    for (int i = 0; i < frames.length; i++) {
                        bytes[i * 2] = (byte) frames[i];
                        bytes[i * 2 + 1] = (byte) (frames[i] >> 8);
                    }
                    line.write(bytes, 0, bytes.length);
                }
            }
        }, "cc-wolfenstein-audio");
        pump.setDaemon(true);
        pump.start();
    }

    @Override
    public void pause() {
        if (line == null) {
            return;
        }
        paused = true;
        line.flush();
    }

    @Override
    public void resume() {
        paused = false;
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
        if (line != null) {
            line.flush();
            line.close();
            line = null;
        }
    }
}
