package org.voitac.anticheat.checks.api.data;

import org.voitac.anticheat.checks.api.Check;

public enum CheckType {
    FLY("Fly"),
    SPEED("Speed"),
    JESUS("Jesus"),
    SPOOF("Spoof"),
    SPRINT("Sprint"),
    BAD_PACKET("Bad Packet"),
    SCAFFOLD("Scaffold"),
    TIMER("Timer"),
    HITBOX("Hit Box"),
    AIM("Aim"),
    PACKET_ORDER("Packet Order");

    private final String name;

    CheckType(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    /**
     *
     * @return Return Friendly Check Name
     */
    // Allows a solo check group to only go by its CheckType eg Aim' '
    public String getName(final Check check) {
        return this.name + (check.getOrder() == ' ' ? "" : check.getOrder());
    }
}
