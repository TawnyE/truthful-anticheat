package ret.tawny.truthful.checks.api.data;

import ret.tawny.truthful.checks.api.Check;

public enum CheckType {
    SIMULATION("Simulation"),
    SPOOF("Spoof"),
    BAD_PACKET("BadPacket"),
    SPRINT("Sprint"),
    CRASHER("Crasher"),
    INVALID("Invalid"),
    SCAFFOLD("Scaffold"),
    TIMER("Timer"),
    HITBOX("HitBox"),
    AIM("Aim"),
    RAYCAST("Raycast"),
    PACKET_ORDER("PacketOrder"),
    AUTOCLICKER("AutoClicker"),
    REACH("Reach"),
    FAST_BREAK("FastBreak"),
    PHASE("Phase"),
    KILLAURA("KillAura"),
    BARITONE("Baritone"),
    INVENTORY("Inventory"),
    VELOCITY("Velocity"),
    CRYSTAL("Crystal"),
    ANCHOR("Anchor"),

    // --- NEW TYPE ---
    BEDROCK("Bedrock");

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