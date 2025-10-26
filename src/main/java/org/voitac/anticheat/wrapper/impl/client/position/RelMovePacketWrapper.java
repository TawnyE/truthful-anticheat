package org.voitac.anticheat.wrapper.impl.client.position;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.base.Objects;
import org.voitac.anticheat.wrapper.api.PacketWrapper;

import java.util.List;

public final class RelMovePacketWrapper extends PacketWrapper {
    /**
     * Player Location Coordinates
     */
    private final double x, y, z;

    /**
     * Player Rotation Data
     */
    private final float yaw, pitch;

    /**
     * Client Ground State, unreliable and can be spoofed
     */
    @Deprecated
    private final boolean ground;

    public RelMovePacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        final List<Double> doublesIn = packetContainer.getDoubles().getValues();
        final List<Float> floatsIn = packetContainer.getFloat().getValues();
        final List<Boolean> booleansIn = packetContainer.getBooleans().getValues();

        this.x = doublesIn.size() > 0 ? doublesIn.get(0) : this.player.getLocation().getX();
        this.y = doublesIn.size() > 1 ? doublesIn.get(1) : this.player.getLocation().getY();
        this.z = doublesIn.size() > 2 ? doublesIn.get(2) : this.player.getLocation().getZ();

        this.yaw = floatsIn.size() > 0 ? floatsIn.get(0) : this.player.getLocation().getYaw();
        this.pitch = floatsIn.size() > 1 ? floatsIn.get(1) : this.player.getLocation().getPitch();

        this.ground = !booleansIn.isEmpty() && booleansIn.get(0);
    }

    public static boolean isRelMove(final PacketType packetType) {
        return packetType.equals(PacketType.Play.Client.POSITION) || packetType.equals(PacketType.Play.Client.POSITION_LOOK) ||
                packetType.equals(PacketType.Play.Client.GROUND) || packetType.equals(PacketType.Play.Client.LOOK) ||
                packetType.equals(PacketType.Play.Client.FLYING);
    }

    /**
     *
     * @return X Coordinate
     */
    public double getX() {
        return x;
    }

    /**
     *
     * @return Y Coordinate
     */
    public double getY() {
        return y;
    }

    /**
     *
     * @return Z Coordinate
     */
    public double getZ() {
        return z;
    }

    /**
     *
     * @return Yaw Rotation
     */
    public float getYaw() {
        return yaw;
    }

    /**
     *
     * @return Pitch Rotation
     */
    public float getPitch() {
        return pitch;
    }

    /**
     *
     * @return Client Ground State
     * @deprecated Any hacked client can spoof a fake value
     */
    @Deprecated
    public boolean isGround() {
        return ground;
    }

    /**
     *
     * @return Packet is a still flying update
     * @deprecated In 1.9+
     */
    @Deprecated
    public boolean isStillFlying() {
        return this.type.equals(PacketType.Play.Client.GROUND) || this.type.equals(PacketType.Play.Client.FLYING);
    }

    /**
     *
     * @return Packet updates client position
     */
    public boolean isPositionUpdate() {
        return this.type.equals(PacketType.Play.Client.POSITION) || this.isPosRotUpdate();
    }

    /**
     *
     * @return Packet updates clients rotation state
     */
    public boolean isRotationUpdate() {
        return this.type.equals(PacketType.Play.Client.LOOK) || this.isPosRotUpdate();
    }

    /**
     *
     * @return Packet updates both Position and Rotation
     */
    public boolean isPosRotUpdate() {
        return this.type.equals(PacketType.Play.Client.POSITION_LOOK);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("X", this.x).add("Y", this.y).add("Z", this.z).add("Yaw", this.yaw).add("Pitch", this.pitch).toString();
    }
}
