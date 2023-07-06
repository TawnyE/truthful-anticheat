package org.voitac.anticheat.wrapper.impl.server.position;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.base.Objects;
import org.voitac.anticheat.wrapper.api.PacketWrapper;

import java.util.List;

public final class SetPositionPacketWrapper extends PacketWrapper {
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

    public SetPositionPacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        final List<Double> doublesIn = packetContainer.getDoubles().getValues();
        final List<Float> floatsIn = packetContainer.getFloat().getValues();
        this.x = doublesIn.get(0);
        this.y = doublesIn.get(1);
        this.z = doublesIn.get(2);

        this.yaw = floatsIn.get(0);
        this.pitch = floatsIn.get(1);

        this.ground = packetContainer.getBooleans().getValues().get(0);
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

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("X", this.x).add("Y", this.y).add("Z", this.z).add("Yaw", this.yaw).add("Pitch", this.pitch).toString();
    }
}
