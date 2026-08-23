package com.ccwolf.core.combat;

/**
 * Weapon-class versus armour-class damage multipliers, in the spirit of the original
 * Command &amp; Conquer warhead tables: rifles shred infantry and bounce off plate, rockets
 * do the reverse.
 *
 * <p>All balance for "what counters what" lives here and in the unit stat tables; nothing
 * else in the simulation hard-codes a match-up.
 */
public final class DamageTable {

    /**
     * Every cell starts as NaN and must be written before the class finishes loading.
     *
     * <p>A plain {@code float[][]} starts at zero, and a zero here is indistinguishable from a
     * deliberate zero — so a weapon class added without a row would silently do 1 damage to
     * everything (the floor in {@link #damage}) and nothing would ever say so. Even the test
     * that looks like it guards this, {@code nothingIsEverFullyImmune}, passes for an all-zero
     * row because of that same floor.
     *
     * <p>NaN is a value nobody can mean, so the static check below turns a missing row into a
     * failure at class load — in the game, the harness and every test alike, rather than only
     * where somebody remembered to assert.
     */
    private static final float[][] MULTIPLIER =
            new float[WeaponClass.values().length][ArmorClass.values().length];

    static {
        for (float[] row : MULTIPLIER) {
            java.util.Arrays.fill(row, Float.NaN);
        }

        set(WeaponClass.SMALL_ARMS, 1.00f, 0.40f, 0.15f, 0.20f);
        // A rifle round through the eye slit kills a man and does nothing to a tank.
        set(WeaponClass.SNIPER, 1.60f, 0.25f, 0.08f, 0.10f);
        // Fragmentation: good against troops in the open and against masonry, poor on plate.
        set(WeaponClass.GRENADE, 1.15f, 0.70f, 0.35f, 1.10f);
        set(WeaponClass.ROCKET, 0.55f, 1.20f, 1.45f, 1.00f);
        set(WeaponClass.CANNON, 0.75f, 1.00f, 0.90f, 0.85f);
        set(WeaponClass.FLAME, 1.40f, 0.80f, 0.40f, 0.90f);
        set(WeaponClass.MELEE, 1.35f, 0.70f, 0.30f, 0.25f);
        set(WeaponClass.OCCULT, 1.00f, 1.00f, 1.00f, 1.00f);
        // Shellfire: murderous to men and masonry, and much less use against plate than its
        // weight suggests - a howitzer is not an anti-tank gun and should not be bought as one.
        set(WeaponClass.ARTILLERY, 1.50f, 0.90f, 0.55f, 1.35f);

        // The burst, not the cloud. Almost nothing: a gas shell that also blew things apart
        // would be an artillery shell with a bonus, and the doctrine is a trade, not a bonus.
        set(WeaponClass.GAS, 0.30f, 0.10f, 0.05f, 0.10f);

        for (WeaponClass w : WeaponClass.values()) {
            for (ArmorClass a : ArmorClass.values()) {
                if (Float.isNaN(MULTIPLIER[w.ordinal()][a.ordinal()])) {
                    throw new IllegalStateException("DamageTable has no row for "
                            + w + " against " + a + " - add one above, and decide what it "
                            + "should be rather than letting it default");
                }
            }
        }
    }

    private DamageTable() {
    }

    private static void set(WeaponClass w, float flesh, float light, float heavy, float concrete) {
        float[] row = MULTIPLIER[w.ordinal()];
        row[ArmorClass.FLESH.ordinal()] = flesh;
        row[ArmorClass.LIGHT.ordinal()] = light;
        row[ArmorClass.HEAVY.ordinal()] = heavy;
        row[ArmorClass.CONCRETE.ordinal()] = concrete;
    }

    public static float multiplier(WeaponClass weapon, ArmorClass armor) {
        return MULTIPLIER[weapon.ordinal()][armor.ordinal()];
    }

    /** Damage actually dealt, rounded down but never below 1 so nothing is fully immune. */
    public static int damage(int baseDamage, WeaponClass weapon, ArmorClass armor) {
        int dealt = (int) (baseDamage * multiplier(weapon, armor));
        return Math.max(1, dealt);
    }
}
