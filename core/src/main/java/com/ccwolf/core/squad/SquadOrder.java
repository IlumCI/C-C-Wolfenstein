package com.ccwolf.core.squad;

/**
 * What a squad has been told to do.
 *
 * <p>An enum and a few fields rather than the {@code Order} interface the units use. A squad has
 * four things it can be doing and the logic for all of them lives in one place, so a hierarchy
 * of classes would be ceremony around a switch. Routing and digging in will be added here when
 * they arrive.
 */
public enum SquadOrder {

    /** Stand where you are. Still fights whatever comes into range. */
    HOLD,

    /** Get to a place. Does not stop for a fight it does not have to have. */
    MOVE,

    /** Get to a place, but stop and deal with anything hostile on the way. */
    ATTACK_MOVE,

    /** Kill one specific thing, and follow it to do so. */
    ATTACK
}
