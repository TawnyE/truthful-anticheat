package ret.tawny.truthful.data;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ret.tawny.truthful.sync.TeleportQueue;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.utils.math.RollingAverage;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;
import ret.tawny.truthful.wrapper.impl.server.position.SetPositionPacketWrapper;

import java.util.ArrayList;
import java.util.List;

public final class PlayerData {

    // --- Fields ---
    private final Player player;
    private double x, lastX, deltaX, y, lastY, deltaY, lastDeltaY, z, lastZ, deltaZ, deltaXZ, lastDeltaXZ;
    private float yaw, lastYaw, deltaYaw, lastDeltaYaw, pitch, lastPitch, deltaPitch, lastDeltaPitch;
    private Location location, lastLocation, lastGroundLocation;
    private int vl, ticksTracked, ticksInAir, ticksOnGround, ticksSinceTeleport, ticksSinceAbility;
    private boolean onGround, lastGround, clientGround, lastClientGround;
    private boolean inLiquid, lastInLiquid, onClimbable, underBlock;
    private Entity lastTarget;
    private int currentSlot, lastSlot;
    private long ping;
    private long lastSlotSwitchTime, lastBlockPlaceTime;
    private int lastBlockPlaceTick;
    public final RollingAverage timerSpeed = new RollingAverage(20);
    private final VelocityQueue velocities = new VelocityQueue();
    private final TeleportQueue teleports = new TeleportQueue();

    public PlayerData(final Player player) {
        this.player = player;
        this.location = player.getLocation();
        this.lastLocation = player.getLocation();
        this.currentSlot = player.getInventory().getHeldItemSlot();
        this.lastSlot = player.getInventory().getHeldItemSlot();
        this.lastSlotSwitchTime = System.currentTimeMillis();
        this.lastBlockPlaceTime = -1L;
    }

    public void update(final RelMovePacketWrapper event) {
        if (event.getPlayer() != this.player) return;
        this.ping = player.getPing();
        ++this.ticksTracked;
        ++this.ticksSinceTeleport;

        this.lastLocation = this.location;
        this.lastX = this.x; this.lastY = this.y; this.lastZ = this.z;
        this.lastYaw = this.yaw; this.lastPitch = this.pitch;
        this.lastGround = this.onGround; this.lastClientGround = this.clientGround;
        this.lastDeltaY = this.deltaY;
        this.lastDeltaXZ = this.deltaXZ;
        this.lastDeltaYaw = this.deltaYaw;
        this.lastDeltaPitch = this.deltaPitch;
        this.lastInLiquid = this.inLiquid;

        this.location = new Location(player.getWorld(), event.isPositionUpdate() ? event.getX() : this.x, event.isPositionUpdate() ? event.getY() : this.y, event.isPositionUpdate() ? event.getZ() : this.z, event.isRotationUpdate() ? event.getYaw() : this.yaw, event.isRotationUpdate() ? event.getPitch() : this.pitch);
        this.x = this.location.getX(); this.y = this.location.getY(); this.z = this.location.getZ();
        this.yaw = this.location.getYaw(); this.pitch = this.location.getPitch();

        this.deltaX = this.x - this.lastX; this.deltaY = this.y - this.lastY;
        this.deltaZ = this.z - this.lastZ;
        this.deltaYaw = this.yaw - this.lastYaw; this.deltaPitch = this.pitch - this.lastPitch;
        this.deltaXZ = Math.hypot(this.deltaX, this.deltaZ);

        this.onGround = WorldUtils.safeGround(player);
        this.clientGround = event.isGround();
        this.inLiquid = WorldUtils.isLiquid(player);
        this.onClimbable = WorldUtils.hasClimbableNearby(player);
        this.underBlock = WorldUtils.isSolid(player.getEyeLocation().clone().add(0, 0.5, 0).getBlock());

        if (this.onGround) {
            this.ticksOnGround++;
            this.ticksInAir = 0;
            this.lastGroundLocation = this.location;
        } else {
            this.ticksInAir++;
            this.ticksOnGround = 0;
        }
    }

    public void acceptTeleport(final SetPositionPacketWrapper positionPacketWrapper) {
        this.ticksSinceTeleport = 0;
        final World world = player.getWorld();
        final Location location = new Location(world, positionPacketWrapper.getX(), positionPacketWrapper.getY(), positionPacketWrapper.getZ(), positionPacketWrapper.getYaw(), positionPacketWrapper.getPitch());
        this.teleports.add(new TeleportQueue.Teleport(location, WorldUtils.getWorldTicks(world)));
    }

    private boolean isCollidingHorizontally(Player player) {
        Location loc = player.getLocation();
        org.bukkit.util.Vector dir = loc.getDirection().normalize();
        Block front = loc.clone().add(dir.multiply(0.4)).getBlock();
        return front.getType().isSolid();
    }

    // --- ALL GETTERS AND SETTERS ---
    public Player getPlayer() { return this.player; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getLastYaw() { return lastYaw; }
    public float getPitch() { return pitch; }
    public float getLastPitch() { return lastPitch; }
    public int getVl() { return vl; }
    public int increment() { return ++vl; }
    public double getDeltaX() { return deltaX; }
    public double getDeltaY() { return deltaY; }
    public double getLastDeltaY() { return lastDeltaY; }
    public double getDeltaZ() { return deltaZ; }
    public double getDeltaXZ() { return deltaXZ; }
    public double getLastDeltaXZ() { return lastDeltaXZ; }
    public float getDeltaYaw() { return deltaYaw; }
    public float getLastDeltaYaw() { return lastDeltaYaw; }
    public float getDeltaPitch() { return deltaPitch; }
    public float getLastDeltaPitch() { return lastDeltaPitch; }
    public Location getLocation() { return location; }
    public Location getLastLocation() { return lastLocation; }
    public int getTicksInAir() { return ticksInAir; }
    public int getTicksOnGround() { return ticksOnGround; }
    public int getTicksTracked() { return ticksTracked; }
    public int getTicksSinceAbility() { return ticksSinceAbility; }
    public boolean isOnGround() { return onGround; }
    public boolean isLastGround() { return lastGround; }
    public Location getLastGroundLocation() { return lastGroundLocation; }
    public boolean isInLiquid() { return inLiquid; }
    public boolean wasInLiquid() { return lastInLiquid; }
    public boolean isUnderBlock() { return underBlock; }
    public boolean isOnClimbable() { return onClimbable; }
    public boolean isTeleportTick() { return ticksSinceTeleport <= 2; }
    public boolean isCollidedHorizontally() { return isCollidingHorizontally(this.player); }
    public long getPing() { return ping; }
    public VelocityQueue getVelocities() { return velocities; }
    public long getLastBlockPlaceTime() { return lastBlockPlaceTime; }
    public void setLastBlockPlaceTime(long time) { this.lastBlockPlaceTime = time; }
    public int getLastBlockPlaceTick() { return lastBlockPlaceTick; }
    public void setLastBlockPlaceTick(int tick) { this.lastBlockPlaceTick = tick; }
    public long getLastSlotSwitchTime() { return lastSlotSwitchTime; }
    public void setLastSlotSwitchTime(long time) { this.lastSlotSwitchTime = time; }
    public Entity getLastTarget() { return lastTarget; }
    public void setLastTarget(Entity target) { this.lastTarget = target; }
    public int getCurrentSlot() { return currentSlot; }
    public void setCurrentSlot(int slot) { this.currentSlot = slot; }
    public int getLastSlot() { return lastSlot; }
    public void setLastSlot(int slot) { this.lastSlot = slot; }
}