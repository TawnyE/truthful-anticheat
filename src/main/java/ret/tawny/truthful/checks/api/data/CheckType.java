package ret.tawny.truthful.checks.api.data;

import ret.tawny.truthful.checks.api.Check;

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
    RAYCAST("Raycast"),
    PACKET_ORDER("Packet Order");

    private final String name;

    CheckType(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public String getName(final Check check) {
        return this.name + (check.getOrder() == ' ' ? "" : "(" + check.getOrder() + ")");
    }
}