package ret.tawny.truthful.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ret.tawny.truthful.sync.TeleportQueue;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;
import ret.tawny.truthful.wrapper.impl.server.position.SetPositionPacketWrapper;

import java.util.ArrayList;
import java.util.List;

public final class PlayerData {

    // --- Fields ---
    private final Player player;
    private double x, lastX, deltaX, lastDeltaX, y, lastY, deltaY, lastDeltaY, z, lastZ, deltaZ, lastDeltaZ, deltaXZ, lastDeltaXZ;
    private float yaw, lastYaw, deltaYaw, lastDeltaYaw, pitch, lastPitch, deltaPitch, lastDeltaPitch;
    private Location location, lastLocation, lastGroundLocation;
    private int vl, ticksTracked, ticksInAir, ticksFalling, ticksSinceTeleport, ticksSinceAbility, ticksFlying;
    private boolean onGround, lastGround, clientGround, lastClientGround, falling, inLiquid, lastInLiquid, isConsuming, collidedVertically, collidedHorizontally;
    private Entity lastTarget;
    private int currentSlot, lastSlot;
    private long ping;
    private boolean synced;
    private final VelocityQueue velocities;
    private final TeleportQueue teleports;
    private final List<TeleportQueue.TeleportBuffer> teleportUpdateBuffers = new ArrayList<>();

    public PlayerData(final Player player) {
        this.player = player;
        this.lastLocation = player.getLocation();
        this.velocities = new VelocityQueue();
        this.teleports = new TeleportQueue();
        this.currentSlot = player.getInventory().getHeldItemSlot();
        this.lastSlot = -1;
    }

    public void update(final RelMovePacketWrapper event) {
        if (event.getPlayer() != this.player) return;

        this.ping = player.getPing();
        ++this.ticksTracked;
        ++this.ticksSinceTeleport;

        if (player.isFlying()) {
            this.ticksSinceAbility = 0;
            ++this.ticksFlying;
        } else {
            this.ticksFlying = 0;
            ++this.ticksSinceAbility;
        }

        this.lastLocation = new Location(this.player.getWorld(), this.x, this.y, this.z, this.yaw, this.pitch);
        this.location = new Location(this.player.getWorld(), event.isPositionUpdate() ? event.getX() : this.x,
                event.isPositionUpdate() ? event.getY() : this.y, event.isPositionUpdate() ? event.getZ() : this.z,
                event.isRotationUpdate() ? event.getYaw() : this.yaw, event.isRotationUpdate() ? event.getPitch() : this.pitch);

        if (!WorldUtils.safeGround(this.player) && this.lastGround)
            this.lastGroundLocation = this.lastLocation;

        this.lastDeltaX = this.deltaX; this.lastDeltaY = this.deltaY; this.lastDeltaZ = this.deltaZ;
        this.lastDeltaYaw = this.deltaYaw; this.lastDeltaPitch = this.deltaPitch; this.lastDeltaXZ = this.deltaXZ;
        this.lastX = this.x; this.lastY = this.y; this.lastZ = this.z;
        this.lastYaw = this.yaw; this.lastPitch = this.pitch;
        this.lastGround = this.onGround; this.lastClientGround = this.clientGround; this.lastInLiquid = this.inLiquid;

        this.x = this.location.getX(); this.y = this.location.getY(); this.z = this.location.getZ();
        this.yaw = this.location.getYaw(); this.pitch = this.location.getPitch();
        this.deltaX = this.x - this.lastX; this.deltaY = this.y - this.lastY; this.deltaZ = this.z - this.lastZ;
        this.deltaYaw = this.yaw - this.lastYaw; this.deltaPitch = this.pitch - this.lastPitch;
        this.deltaXZ = Math.hypot(this.deltaX, this.deltaZ);

        this.onGround = WorldUtils.safeGround(this.player);

        this.clientGround = event.isGround();

        this.inLiquid = WorldUtils.isLiquid(this.player);

        if (this.deltaY < 0 && !this.onGround && !this.falling) {
            this.falling = true;
        } else if (this.onGround) {
            this.ticksFalling = 0;
            this.falling = false;
        }

        if (this.falling) ++this.ticksFalling;
        if (!this.onGround) ++this.ticksInAir; else this.ticksInAir = 0;
    }

    @Deprecated
    public PlayerData setPing(final long ping) {
        this.ping = ping;
        return this;
    }

    @Deprecated
    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public void acceptTeleport(final SetPositionPacketWrapper positionPacketWrapper) {
        final World world = player.getWorld();
        final Location location = new Location(world, positionPacketWrapper.getX(), positionPacketWrapper.getY(), positionPacketWrapper.getZ(), positionPacketWrapper.getYaw(), positionPacketWrapper.getPitch());
        this.teleports.add(new TeleportQueue.Teleport(location, WorldUtils.getWorldTicks(world)));
    }

    // --- ALL GETTERS AND SETTERS ---
    public Player getPlayer() { return this.player; }
    public double getX() { return x; }
    public double getLastX() { return lastX; }
    public double getDeltaX() { return deltaX; }
    public double getLastDeltaX() { return lastDeltaX; }
    public double getY() { return y; }
    public double getLastY() { return lastY; }
    public double getDeltaY() { return deltaY; }
    public double getLastDeltaY() { return lastDeltaY; }
    public double getZ() { return z; }
    public double getLastZ() { return lastZ; }
    public double getDeltaZ() { return deltaZ; }
    public double getLastDeltaZ() { return lastDeltaZ; }
    public double getDeltaXZ() { return deltaXZ; }
    public double getLastDeltaXZ() { return lastDeltaXZ; }
    public float getYaw() { return yaw; }
    public float getLastYaw() { return lastYaw; }
    public float getDeltaYaw() { return deltaYaw; }
    public float getLastDeltaYaw() { return lastDeltaYaw; }
    public float getPitch() { return pitch; }
    public float getLastPitch() { return lastPitch; }
    public float getDeltaPitch() { return deltaPitch; }
    public float getLastDeltaPitch() { return lastDeltaPitch; }
    public Location getLocation() { return location; }
    public Location getLastLocation() { return lastLocation; }
    public Location getLastGroundLocation() { return lastGroundLocation; }
    public int getVl() { return this.vl; }
    public int increment() { return ++this.vl; }
    public int getTicksTracked() { return ticksTracked; }
    public int getTicksInAir() { return this.ticksInAir; }
    public int getTicksFalling() { return ticksFalling; }
    public int getTicksFlying() { return ticksFlying; }
    public boolean isOnGround() { return onGround; }
    public boolean isLastGround() { return lastGround; }
    public boolean isClientGround() { return clientGround; }
    public boolean isLastClientGround() { return lastClientGround; }
    public long getPing() { return this.ping; }
    public int getCurrentSlot() { return currentSlot; }
    public void setCurrentSlot(int slot) { this.currentSlot = slot; }
    public int getLastSlot() { return lastSlot; }
    public void setLastSlot(int slot) { this.lastSlot = slot; }
    public Entity getLastTarget() { return lastTarget; }
    public void setLastTarget(Entity lastTarget) { this.lastTarget = lastTarget; }
    public VelocityQueue getVelocities() { return velocities; }
    public boolean isTeleportTick() { return this.ticksSinceTeleport <= 1; }
    public int getTicksSinceAbility() { return this.ticksSinceAbility; }
    public boolean wasInLiquid() { return this.lastInLiquid; }
    public boolean isInLiquid() { return this.inLiquid; }
    public boolean hasFallen() { return falling; }
    public boolean isConsuming() { return isConsuming; }
    public boolean isCollidedVertically() { return collidedVertically; }
    public boolean isCollidedHorizontally() { return collidedHorizontally; }
}