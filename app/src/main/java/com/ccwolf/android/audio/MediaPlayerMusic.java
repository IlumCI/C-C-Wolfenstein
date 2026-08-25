package com.ccwolf.android.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import com.ccwolf.audio.MusicSink;
import java.io.IOException;

/**
 * Android menu music: loops a player-supplied {@code assets/music/menu.mp3} baked into their
 * own local build. The slot is gitignored — the repository ships no recordings — so a stock
 * build simply has no such asset and the storm plays alone.
 */
public final class MediaPlayerMusic implements MusicSink {

    private static final String SLOT = "music/menu.mp3";

    private final Context context;
    private MediaPlayer player;

    public MediaPlayerMusic(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public synchronized boolean playLoop() {
        if (player != null) {
            return true;
        }
        try {
            AssetFileDescriptor fd = context.getAssets().openFd(SLOT);
            try {
                MediaPlayer p = new MediaPlayer();
                p.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                p.setLooping(true);
                p.prepare();
                p.start();
                player = p;
                return true;
            } finally {
                fd.close();
            }
        } catch (IOException | RuntimeException e) {
            // No asset in this build, or a file MediaPlayer cannot read: no music, no fuss.
            return false;
        }
    }

    @Override
    public synchronized void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
