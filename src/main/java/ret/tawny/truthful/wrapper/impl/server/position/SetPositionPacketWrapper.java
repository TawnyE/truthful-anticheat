package ret.tawny.truthful.wrapper.impl.server.position;

import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

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

        // This value is still read for legacy purposes but marked as deprecated.
        this.ground = packetContainer.getBooleans().getValues().get(0);
    }

    /**
     * @return X Coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * @return Y Coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * @return Z Coordinate
     */
    public double getZ() {
        return z;
    }

    /**
     * @return Yaw Rotation
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * @return Pitch Rotation
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * @return Client Ground State
     * @deprecated Any hacked client can spoof a fake value. Use server-side checks.
     */
    @Deprecated
    public boolean isGround() {
        return ground;
    }

    @Override
    public String toString() {
        // Replaced the removed Objects.toStringHelper with standard Java String.format
        return String.format("SetPositionPacket[X=%.2f, Y=%.2f, Z=%.2f, Yaw=%.2f, Pitch=%.2f]",
                this.x, this.y, this.z, this.yaw, this.pitch);
    }
}