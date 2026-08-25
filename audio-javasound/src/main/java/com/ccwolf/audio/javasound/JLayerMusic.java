package com.ccwolf.audio.javasound;

import com.ccwolf.audio.MusicSink;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javazoom.jl.player.Player;

/**
 * Desktop menu music: loops a player-supplied {@code music/menu.mp3} beside the working
 * directory. The file is the player's own and never ships with the game — the slot is
 * gitignored, and an empty slot simply means the storm plays alone.
 */
public final class JLayerMusic implements MusicSink {

    private static final String SLOT = "music/menu.mp3";

    private Thread player;
    private volatile boolean running;
    private volatile Player current;

    @Override
    public synchronized boolean playLoop() {
        if (player != null) {
            return true;
        }
        final File file = new File(SLOT);
        if (!file.isFile()) {
            return false;
        }
        running = true;
        player = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        Player p = new Player(new BufferedInputStream(
                                new FileInputStream(file)));
                        current = p;
                        p.play();
                        p.close();
                    } catch (IOException | javazoom.jl.decoder.JavaLayerException e) {
                        // A corrupt or undecodable file: give up quietly, once.
                        System.err.println("[music] cannot play " + SLOT + ": "
                                + e.getMessage());
                        return;
                    }
                }
            }
        }, "cc-wolfenstein-music");
        player.setDaemon(true);
        player.start();
        return true;
    }

    @Override
    public synchronized void stop() {
        running = false;
        Player p = current;
        if (p != null) {
            p.close();
        }
        if (player != null) {
            try {
                player.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            player = null;
        }
        current = null;
    }
}
