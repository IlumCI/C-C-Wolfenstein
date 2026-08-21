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
    OCCULT
}
