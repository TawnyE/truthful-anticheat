package ret.tawny.truthful.wrapper.impl.server.position;

import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

import java.util.List;

public final class SetPositionPacketWrapper extends PacketWrapper {
    private final double x, y, z;
    private final float yaw, pitch;
    private final boolean ground;

    public SetPositionPacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);

        // This is the robust, correct way to safely read from ProtocolLib's structure modifiers.
        // We get the list of values and check the size before accessing an index.
        final List<Double> doublesIn = packetContainer.getDoubles().getValues();
        final List<Float> floatsIn = packetContainer.getFloat().getValues();
        final List<Boolean> booleansIn = packetContainer.getBooleans().getValues();

        this.x = !doublesIn.isEmpty() ? doublesIn.get(0) : player.getLocation().getX();
        this.y = doublesIn.size() > 1 ? doublesIn.get(1) : player.getLocation().getY();
        this.z = doublesIn.size() > 2 ? doublesIn.get(2) : player.getLocation().getZ();

        this.yaw = !floatsIn.isEmpty() ? floatsIn.get(0) : player.getLocation().getYaw();
        this.pitch = floatsIn.size() > 1 ? floatsIn.get(1) : player.getLocation().getPitch();

        this.ground = !booleansIn.isEmpty() && booleansIn.get(0);
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
        return String.format("SetPositionPacket[X=%.2f, Y=%.2f, Z=%.2f, Yaw=%.2f, Pitch=%.2f]",
                this.x, this.y, this.z, this.yaw, this.pitch);
    }
}