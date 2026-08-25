package com.ccwolf.game.save;

import java.io.File;

/**
 * Where saved games live — the one thing about persistence a platform must decide.
 *
 * <p>Reading and writing files is plain {@code java.io} on both platforms; only the directory
 * differs (the user's home on desktop, the app's private files dir on Android). So the seam is
 * a directory, installed once by the shell like the graphics and audio backends, and with the
 * same forgiving default: nothing installed means saving quietly does nothing, which is what
 * the headless benchmark and the tests want.
 */
public final class SaveDir {

    private static File dir;

    private SaveDir() {
    }

    public static void install(File directory) {
        dir = directory;
    }

    public static boolean isInstalled() {
        return dir != null;
    }

    /** The single save slot's file, or null when no directory was installed. */
    public static File slot() {
        if (dir == null) {
            return null;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, "the-front.save");
    }
}
