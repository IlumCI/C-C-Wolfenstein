package com.ccwolf.core.combat;

/** What a target is made of. Half of the damage lookup in {@link DamageTable}. */
public enum ArmorClass {
    /** Unarmoured infantry. */
    FLESH,
    /** Thin-skinned vehicles: jeeps, harvesters, mech-hounds. */
    LIGHT,
    /** Plated walkers and tanks. */
    HEAVY,
    /** Bunkers and other structures. */
    CONCRETE
}
