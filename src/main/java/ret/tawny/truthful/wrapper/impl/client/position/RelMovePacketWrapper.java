package ret.tawny.truthful.wrapper.impl.client.position;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import com.github.retrooper.packetevents.util.Vector3d;
import org.bukkit.entity.Player;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.api.PacketWrapper;

public final class RelMovePacketWrapper extends PacketWrapper {
    private final double x, y, z;
    private final float yaw, pitch;
    private final boolean ground;
    private final boolean hasPosition;
    private final boolean hasRotation;
    private final PlayerData playerData;

    public RelMovePacketWrapper(Object wrapper, Player player, PacketType.Play.Client type, PlayerData data) {
        super(wrapper, player, type);
        this.playerData = data;

        double lastX = data != null ? data.getX() : 0;
        double lastY = data != null ? data.getY() : 0;
        double lastZ = data != null ? data.getZ() : 0;
        float lastYaw = data != null ? data.getYaw() : 0;
        float lastPitch = data != null ? data.getPitch() : 0;

        if (wrapper instanceof WrapperPlayClientPlayerPositionAndRotation) {
            WrapperPlayClientPlayerPositionAndRotation posLook = (WrapperPlayClientPlayerPositionAndRotation) wrapper;
            Vector3d pos = posLook.getPosition();
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.yaw = posLook.getYaw();
            this.pitch = posLook.getPitch();
            this.ground = posLook.isOnGround();
            this.hasPosition = true;
            this.hasRotation = true;
        } else if (wrapper instanceof WrapperPlayClientPlayerPosition) {
            WrapperPlayClientPlayerPosition pos = (WrapperPlayClientPlayerPosition) wrapper;
            Vector3d position = pos.getPosition();
            this.x = position.x;
            this.y = position.y;
            this.z = position.z;
            this.yaw = lastYaw;
            this.pitch = lastPitch;
            this.ground = pos.isOnGround();
            this.hasPosition = true;
            this.hasRotation = false;
        } else if (wrapper instanceof WrapperPlayClientPlayerRotation) {
            WrapperPlayClientPlayerRotation rot = (WrapperPlayClientPlayerRotation) wrapper;
            this.x = lastX;
            this.y = lastY;
            this.z = lastZ;
            this.yaw = rot.getYaw();
            this.pitch = rot.getPitch();
            this.ground = rot.isOnGround();
            this.hasPosition = false;
            this.hasRotation = true;
        } else if (wrapper instanceof WrapperPlayClientPlayerFlying) {
            WrapperPlayClientPlayerFlying flying = (WrapperPlayClientPlayerFlying) wrapper;
            this.x = lastX;
            this.y = lastY;
            this.z = lastZ;
            this.yaw = lastYaw;
            this.pitch = lastPitch;
            this.ground = flying.isOnGround();
            this.hasPosition = false;
            this.hasRotation = false;
        } else if (wrapper instanceof WrapperPlayClientVehicleMove) {
            // FIX: Boat Fly / Vehicle Tick Unfreeze
            WrapperPlayClientVehicleMove vehicleMove = (WrapperPlayClientVehicleMove) wrapper;
            Vector3d pos = vehicleMove.getPosition();
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.yaw = vehicleMove.getYaw();
            this.pitch = vehicleMove.getPitch();
            this.ground = true;
            this.hasPosition = true;
            this.hasRotation = true;
        } else {
            this.x = lastX;
            this.y = lastY;
            this.z = lastZ;
            this.yaw = lastYaw;
            this.pitch = lastPitch;
            this.ground = false;
            this.hasPosition = false;
            this.hasRotation = false;
        }
    }

    public static boolean isRelMove(final PacketTypeCommon type) {
        return type == PacketType.Play.Client.PLAYER_POSITION ||
                type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION ||
                type == PacketType.Play.Client.PLAYER_ROTATION ||
                type == PacketType.Play.Client.PLAYER_FLYING ||
                type == PacketType.Play.Client.VEHICLE_MOVE; // FIX: Unfreeze anti-cheat clock in vehicles
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isGround() { return ground; }
    public boolean isPositionUpdate() { return hasPosition; }
    public boolean isRotationUpdate() { return hasRotation; }
    public PlayerData getPlayerData() { return playerData; }
}