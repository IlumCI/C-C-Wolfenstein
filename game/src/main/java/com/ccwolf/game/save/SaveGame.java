package com.ccwolf.game.save;

import com.ccwolf.core.ai.Difficulty;
import com.ccwolf.core.diag.StateDigest;
import com.ccwolf.core.entity.Doctrine;
import com.ccwolf.core.entity.Faction;
import com.ccwolf.game.GameSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The saved game: not a snapshot, a recipe.
 *
 * <p>The file holds what made the match — map, sides, difficulty, seed — and the log of every
 * command the world accepted from the player, with the tick each landed on. Loading rebuilds
 * the match and replays the log through the same deterministic simulation the golden digests
 * police, then checks the arrived-at world against the digest recorded at save time. The whole
 * war fits in a few kilobytes of text a person can read.
 *
 * <p>One slot, called "the front", because that is what it is: the war as you left it.
 */
public final class SaveGame {

    private static final String MAGIC = "ccwolf-save";
    private static final int VERSION = 1;

    /** Everything a save knows, parsed but not yet replayed. */
    public static final class Data {
        public String mapName;
        public Faction faction;
        public Difficulty difficulty;
        public long seed;
        public Doctrine doctrine;
        public Doctrine opponentDoctrine;
        public int tick;
        public long digest;
        public float cameraX;
        public float cameraY;
        /** One entry per accepted command: {@code tick playerId codecLine}. */
        public final List<String> commands = new ArrayList<String>();
    }

    private SaveGame() {
    }

    public static boolean exists() {
        File slot = SaveDir.slot();
        return slot != null && slot.isFile();
    }

    public static void delete() {
        File slot = SaveDir.slot();
        if (slot != null && slot.isFile() && !slot.delete()) {
            System.err.println("[save] could not delete " + slot);
        }
    }

    /** Writes the session to the slot. Quietly refuses when no directory is installed. */
    public static boolean write(GameSession session) {
        File slot = SaveDir.slot();
        if (slot == null) {
            return false;
        }
        // Write beside the slot and rename over it, so a crash mid-write cannot cost the
        // player both the old save and the new one.
        File tmp = new File(slot.getParentFile(), slot.getName() + ".tmp");
        try {
            Writer out = new OutputStreamWriter(new FileOutputStream(tmp),
                    StandardCharsets.UTF_8);
            try {
                out.write(MAGIC + " " + VERSION + "\n");
                out.write("map " + session.mapName() + "\n");
                out.write("faction " + session.faction().name() + "\n");
                out.write("difficulty " + session.difficulty().name() + "\n");
                out.write("seed " + session.seed() + "\n");
                out.write("doctrine " + name(session.doctrine()) + "\n");
                out.write("enemyDoctrine " + name(session.opponentDoctrine()) + "\n");
                out.write("tick " + session.world().tick() + "\n");
                out.write("digest " + StateDigest.exact(session.world()) + "\n");
                out.write("camera " + session.camera().centerX() + " "
                        + session.camera().centerY() + "\n");
                for (String line : session.commandLog()) {
                    out.write("cmd " + line + "\n");
                }
            } finally {
                out.close();
            }
            if (slot.isFile() && !slot.delete()) {
                return false;
            }
            return tmp.renameTo(slot);
        } catch (IOException e) {
            System.err.println("[save] could not write " + slot + ": " + e.getMessage());
            return false;
        }
    }

    /** Parses the slot; null when there is no save or it cannot be read. */
    public static Data load() {
        File slot = SaveDir.slot();
        if (slot == null || !slot.isFile()) {
            return null;
        }
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(slot), StandardCharsets.UTF_8));
            try {
                Data data = new Data();
                String header = in.readLine();
                if (header == null || !header.startsWith(MAGIC)) {
                    return null;
                }
                String line;
                while ((line = in.readLine()) != null) {
                    int space = line.indexOf(' ');
                    if (space < 0) {
                        continue;
                    }
                    String key = line.substring(0, space);
                    String value = line.substring(space + 1);
                    if ("map".equals(key)) {
                        data.mapName = value;
                    } else if ("faction".equals(key)) {
                        data.faction = Faction.valueOf(value);
                    } else if ("difficulty".equals(key)) {
                        data.difficulty = Difficulty.valueOf(value);
                    } else if ("seed".equals(key)) {
                        data.seed = Long.parseLong(value);
                    } else if ("doctrine".equals(key)) {
                        data.doctrine = doctrine(value);
                    } else if ("enemyDoctrine".equals(key)) {
                        data.opponentDoctrine = doctrine(value);
                    } else if ("tick".equals(key)) {
                        data.tick = Integer.parseInt(value);
                    } else if ("digest".equals(key)) {
                        data.digest = Long.parseLong(value);
                    } else if ("camera".equals(key)) {
                        String[] parts = value.split(" ");
                        data.cameraX = Float.parseFloat(parts[0]);
                        data.cameraY = Float.parseFloat(parts[1]);
                    } else if ("cmd".equals(key)) {
                        data.commands.add(value);
                    }
                }
                return data.mapName == null || data.faction == null
                        || data.difficulty == null ? null : data;
            } finally {
                in.close();
            }
        } catch (IOException | RuntimeException e) {
            // A truncated or hand-mangled file is a missing save, not a crash.
            System.err.println("[save] could not read " + slot + ": " + e.getMessage());
            return null;
        }
    }

    private static String name(Doctrine doctrine) {
        return doctrine == null ? "-" : doctrine.name();
    }

    private static Doctrine doctrine(String value) {
        return "-".equals(value) ? null : Doctrine.valueOf(value);
    }
}
