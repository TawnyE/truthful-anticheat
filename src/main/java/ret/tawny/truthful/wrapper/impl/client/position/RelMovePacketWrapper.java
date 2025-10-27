package ret.tawny.truthful.wrapper.impl.client.position;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Location;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

import java.util.List;

public final class RelMovePacketWrapper extends PacketWrapper {
    private final double x, y, z;
    private final float yaw, pitch;
    private final boolean ground;

    public RelMovePacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
        final Location playerLoc = player.getLocation();

        List<Double> doublesIn = packetContainer.getDoubles().getValues();
        List<Float> floatsIn = packetContainer.getFloat().getValues();
        List<Boolean> booleansIn = packetContainer.getBooleans().getValues();

        this.x = !doublesIn.isEmpty() ? doublesIn.get(0) : playerLoc.getX();
        this.y = doublesIn.size() > 1 ? doublesIn.get(1) : playerLoc.getY();
        this.z = doublesIn.size() > 2 ? doublesIn.get(2) : playerLoc.getZ();

        this.yaw = !floatsIn.isEmpty() ? floatsIn.get(0) : playerLoc.getYaw();
        this.pitch = floatsIn.size() > 1 ? floatsIn.get(1) : playerLoc.getPitch();

        this.ground = !booleansIn.isEmpty() && booleansIn.get(0);
    }

    public static boolean isRelMove(final PacketType packetType) {
        // This method remains static because it's a general utility and doesn't depend on a specific packet instance.
        return packetType == PacketType.Play.Client.POSITION ||
                packetType == PacketType.Play.Client.POSITION_LOOK ||
                packetType == PacketType.Play.Client.LOOK ||
                packetType == PacketType.Play.Client.FLYING;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    /**
     * @return Client Ground State
     */
    // This method is now an instance method (non-static), which fixes the error.
    public boolean isGround() {
        return ground;
    }

    /**
     * @return Packet updates client position
     */
    public boolean isPositionUpdate() {
        return this.type == PacketType.Play.Client.POSITION || this.type == PacketType.Play.Client.POSITION_LOOK;
    }

    /**
     * @return Packet updates clients rotation state
     */
    public boolean isRotationUpdate() {
        return this.type == PacketType.Play.Client.LOOK || this.type == PacketType.Play.Client.POSITION_LOOK;
    }
}