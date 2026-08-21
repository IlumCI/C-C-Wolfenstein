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

    private static final float[][] MULTIPLIER =
            new float[WeaponClass.values().length][ArmorClass.values().length];

    static {
        set(WeaponClass.SMALL_ARMS, 1.00f, 0.40f, 0.15f, 0.20f);
        set(WeaponClass.ROCKET, 0.55f, 1.20f, 1.45f, 1.00f);
        set(WeaponClass.CANNON, 0.75f, 1.00f, 0.90f, 0.85f);
        set(WeaponClass.FLAME, 1.40f, 0.80f, 0.40f, 0.90f);
        set(WeaponClass.MELEE, 1.35f, 0.70f, 0.30f, 0.25f);
        set(WeaponClass.OCCULT, 1.00f, 1.00f, 1.00f, 1.00f);
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
