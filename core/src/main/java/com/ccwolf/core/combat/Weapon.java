package com.ccwolf.core.combat;

/**
 * A weapon loadout. Ranges are in tiles, cooldowns in simulation ticks
 * (see {@code GameWorld.TICKS_PER_SECOND}).
 */
public enum Weapon {
    /** Resistance rifle: cheap, good against infantry, useless against plate. */
    RIFLE("Kar Rifle", 12, 3.6f, 13, WeaponClass.SMALL_ARMS),
    /** Regime issue submachine gun. Faster and shorter-ranged than the rifle. */
    MP_SMG("MP Sturmgewehr", 7, 3.2f, 9, WeaponClass.SMALL_ARMS),
    /** Shoulder-fired anti-armour rocket. Slow, devastating to vehicles. */
    PANZERSCHRECK("Panzerschreck", 42, 4.6f, 36, WeaponClass.ROCKET),
    /** Pintle gun on the scout jeep. */
    JEEP_MG("Pintle MG", 8, 4.2f, 6, WeaponClass.SMALL_ARMS),
    /** Main gun of a Panzer the Resistance stole and repainted. */
    PANZER_CANNON("75mm Cannon", 34, 4.8f, 26, WeaponClass.CANNON),
    /** The Panzerhund closes and bites. */
    HOUND_JAWS("Servo Jaws", 16, 1.0f, 15, WeaponClass.MELEE),
    /** Ubersoldat arm cannon. */
    UBER_CANNON("Arm Cannon", 32, 4.2f, 26, WeaponClass.CANNON),
    /** Flak-turret style base defence. Long reach, no mobility. */
    TURRET_GUN("Turret Cannon", 26, 6.0f, 22, WeaponClass.CANNON);

    private final String displayName;
    private final int damage;
    private final float range;
    private final int cooldownTicks;
    private final WeaponClass weaponClass;

    Weapon(String displayName, int damage, float range, int cooldownTicks, WeaponClass weaponClass) {
        this.displayName = displayName;
        this.damage = damage;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.weaponClass = weaponClass;
    }

    public String displayName() {
        return displayName;
    }

    public int damage() {
        return damage;
    }

    /** Maximum firing range, in tiles, measured centre to centre. */
    public float range() {
        return range;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public WeaponClass weaponClass() {
        return weaponClass;
    }

    public int damageAgainst(ArmorClass armor) {
        return DamageTable.damage(damage, weaponClass, armor);
    }
}
