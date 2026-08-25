package com.ccwolf.core.combat;

/** What a weapon fires. The other half of the {@link DamageTable} lookup. */
public enum WeaponClass {
    SMALL_ARMS,
    /** A single aimed shot: lethal to a man, useless against a hull. */
    SNIPER,
    ROCKET,
    CANNON,
    /** Thrown or lobbed, and the only class that lands on more than one target. */
    GRENADE,
    FLAME,
    MELEE,
    /** Regime wonder-weapons: unfashionably even-handed against everything. */
    OCCULT,
    /**
     * Indirect fire: dropped on a place rather than aimed at a thing.
     *
     * <p>The only class delivered by something that takes time to arrive, and therefore the
     * only one that can land where nobody is standing any more.
     */
    ARTILLERY,
    /**
     * A gas shell. The burst itself is almost harmless; what it leaves behind is the weapon.
     *
     * <p>The payload lives in {@code GasLayer}, not in the damage table: the shell's job is to
     * vent a cloud where it lands, and the cloud then obeys weather rather than ballistics.
     */
    GAS
}
