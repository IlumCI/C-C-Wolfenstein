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
    TURRET_GUN("Turret Cannon", 26, 6.0f, 22, WeaponClass.CANNON),

    /** Resistance marksman: one shot, one man, a long wait for the next. */
    HUNTING_RIFLE("Scoped Hunting Rifle", 55, 8.0f, 62, WeaponClass.SNIPER),

    /** Regime counter-sniper: slightly further, slightly faster, much more expensive. */
    SCHARFSCHUTZE_RIFLE("Zielfernrohr Rifle", 58, 8.5f, 56, WeaponClass.SNIPER),

    /** A bundled charge, thrown. The Resistance answer to massed infantry. */
    GRENADE_BUNDLE("Bundled Charge", 30, 4.2f, 44, WeaponClass.GRENADE, 1.7f),

    /** Flame projector: short reach, and it catches everything standing together. */
    FLAMMENWERFER("Flammenwerfer", 16, 3.0f, 9, WeaponClass.FLAME, 1.1f),

    /** Sturmpanzer main gun: a heavier shell than anything the Resistance can field. */
    STURM_CANNON("Sturm Cannon", 46, 5.2f, 34, WeaponClass.CANNON, 0.9f),

    /** Nest gun: cheap, fast, and only dangerous to men on foot. */
    NEST_MG("Nest MG", 11, 5.5f, 5, WeaponClass.SMALL_ARMS),

    /** Anti-tank gun: devastating to armour, hopeless against a running man. */
    PAK_GUN("Pak Gun", 44, 6.8f, 33, WeaponClass.CANNON),

    /**
     * A Regime field piece the Resistance took and never gave back.
     *
     * <p>1945, and it shows: one shell at a time, a long wait between them, and a crew who have
     * to be somewhere else before anyone works out where the shells came from.
     */
    FELDKANONE("Stolen Feldkanone", 42, 12f, 110, WeaponClass.ARTILLERY, 2.4f, 6f),

    /**
     * A rack of tubes that empties itself at a piece of ground.
     *
     * <p>Four rounds for one decision, spread across a frontage rather than stacked on a point.
     * That makes it the wrong weapon for one man and the right one for a line of them — the
     * first thing in the game that cares about the shape of what it is shooting at.
     */
    NEBELWERFER("Nebelwerfer-71", 26, 11f, 150, WeaponClass.ARTILLERY, 2.2f, 5f, 4),

    /**
     * Whatever the Regime dug up, mounted on a carriage.
     *
     * <p>It does not throw anything. Cover is no help, earth is no help, and what it mostly
     * does is empty a position of men who are still alive. Slow, ruinously expensive, and it
     * outranges every other thing on the map.
     */
    RESONANZKANONE("Resonanzkanone", 30, 15f, 200, WeaponClass.OCCULT, 3.0f, 8f);

    private final String displayName;
    private final int damage;
    private final float range;
    private final int cooldownTicks;
    private final WeaponClass weaponClass;
    private final float blastRadius;
    private final float minRange;
    private final int salvo;

    Weapon(String displayName, int damage, float range, int cooldownTicks,
           WeaponClass weaponClass) {
        this(displayName, damage, range, cooldownTicks, weaponClass, 0f, 0f);
    }

    Weapon(String displayName, int damage, float range, int cooldownTicks,
           WeaponClass weaponClass, float blastRadius) {
        this(displayName, damage, range, cooldownTicks, weaponClass, blastRadius, 0f);
    }

    Weapon(String displayName, int damage, float range, int cooldownTicks,
           WeaponClass weaponClass, float blastRadius, float minRange) {
        this(displayName, damage, range, cooldownTicks, weaponClass, blastRadius, minRange, 1);
    }

    Weapon(String displayName, int damage, float range, int cooldownTicks,
           WeaponClass weaponClass, float blastRadius, float minRange, int salvo) {
        this.displayName = displayName;
        this.damage = damage;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.weaponClass = weaponClass;
        this.blastRadius = blastRadius;
        this.minRange = minRange;
        this.salvo = salvo;
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

    /**
     * Radius in tiles over which this weapon also hurts everything else, 0 for single-target
     * weapons. Damage falls off linearly to a quarter at the edge, and never touches the
     * firer's own side — friendly fire would make the AI unusable and the player miserable.
     */
    public float blastRadius() {
        return blastRadius;
    }

    public boolean hasBlast() {
        return blastRadius > 0f;
    }

    /**
     * How close is too close, in tiles. Zero for everything that can shoot what it can reach.
     *
     * <p>A gun with a dead zone in the middle of its range is a different weapon from a gun
     * without one, and not only because of the hole: every piece of code in the game reads
     * "out of range" as "walk closer", so a weapon with a lower bound needs callers that know
     * to back off instead. See {@code GameWorld.standOffTile}.
     *
     * <p>Measured to the target's edge, exactly as maximum range is, which means a wide
     * structure counts as too close from further out than a man does. That is the right answer
     * for indirect fire — you cannot depress a howitzer over the wall it is parked against.
     */
    public float minRange() {
        return minRange;
    }

    /** True if this weapon has a dead zone at all — the cheap test before the expensive one. */
    public boolean hasMinRange() {
        return minRange > 0f;
    }

    /**
     * How many rounds one pull of the trigger puts in the air. One for everything aimed.
     *
     * <p>A salvo is not several shots in quick succession — it is a single decision that lands
     * across a frontage. That makes it the right answer to a line of men and the wrong answer
     * to one man, which is a distinction the game did not previously have any way to express.
     */
    public int salvo() {
        return salvo;
    }

    public int damageAgainst(ArmorClass armor) {
        return DamageTable.damage(damage, weaponClass, armor);
    }
}
