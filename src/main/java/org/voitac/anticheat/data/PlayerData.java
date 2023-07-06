package org.voitac.anticheat.data;

import org.voitac.anticheat.sync.TeleportQueue;
import org.voitac.anticheat.sync.VelocityQueue;
import org.voitac.anticheat.utils.player.PlayerUtils;
import org.voitac.anticheat.utils.world.WorldUtils;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.voitac.anticheat.wrapper.impl.server.position.SetPositionPacketWrapper;

import java.util.ArrayList;
import java.util.List;

public final class PlayerData {
    /**
     * The player the data is for
     */
    private final Player player;

    /**
     * Velocities the player has yet to process
     * Calculated based on ping
     */
    private final VelocityQueue velocities;

    /**
     * Velocities the player has yet to process
     * Calculated based on ping
     */
    private final TeleportQueue teleports;

    /**
     * Most recently accepted teleport
     */
    private TeleportQueue.Teleport teleport;

    /**
     * How many extra packets we can expect
     * This will be decremented on the acceptance of the corresponding teleport
     */
    private final List teleportUpdateBuffers = new ArrayList<TeleportQueue.TeleportBuffer>();

    private double x, lastX, deltaX, lastDeltaX,
    y, lastY, deltaY, lastDeltaY,
    z, lastZ, deltaZ, lastDeltaZ,
    lastDeltaXZ, deltaXZ;

    private float yaw, lastYaw, deltaYaw, lastDeltaYaw,
    pitch, lastPitch, deltaPitch, lastDeltaPitch;

    private Location location, lastLocation, lastGroundLocation;

    private int vl, ticksTracked, ticksInAir, ticksFalling, ticksSinceAbility, ticksFlying;

    private int lastSlot, currentSlot;

    /**
     * Should never be used for any exemptions when > 1 ticks
     * <p/>
     * Otherwise, a client could easily fly by triggering a setback on tick(0), responding on tick(1), and flying on tick(>1)
     */
    private int ticksSinceTeleport;

    /**
     * Ping based on transaction response
     */
    private long ping;

    /**
     * Serverside Reliable Ground
     */
    private boolean onGround, lastGround;

    /**
     * Unreliable client ground
     */
    @Deprecated
    private boolean clientGround, lastClientGround;

    /**
     * World Info
     */
    private boolean falling;

    /**
     * collided on the Y Axis
     */
    private boolean collidedVertically;

    /**
     * collided on the X Axis
     */
    private boolean collidedHorizontally;

    /**
     *
     */
    private boolean inLiquid;
    /**
     * Players consumption state, blocking, eating, drawing a bow, etc
     */
    private boolean isConsuming;
    /**
     * Every position update the player is not yet synced
     * Once the player has responded to the corresponding transaction he is synced until the next position update
     */
    private boolean synced;

    /**
     * Last Entity the player has interacted with
     */
    private Entity lastTarget;

    /**
     *
     * @param player
     * Constructs a record of information about a player vital to the anticheat
     */
    public PlayerData(final Player player) {
        this.player = player;
        this.lastLocation = player.getLocation();

        this.velocities = new VelocityQueue();
        this.teleports = new TeleportQueue();

        this.currentSlot = player.getInventory().getHeldItemSlot();
        this.lastSlot = -1;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Location getLastLocation() {
        return this.lastLocation;
    }

    public void update(final RelMovePacketWrapper event) {
        if(event.getPlayer() != this.player)
            throw new IllegalArgumentException("Tried to update player with another players data");
        ++this.ticksTracked;

        if(event.getPlayer().isFlying()) {
            this.ticksSinceAbility = 0;
            ++this.ticksFlying;
        }else {
            this.ticksFlying = 0;
            ++this.ticksSinceAbility;
        }

        this.lastLocation = new Location(this.player.getWorld(), this.x, this.y, this.z, this.yaw, this.pitch);
        this.location = new Location(this.player.getWorld(), event.isPositionUpdate() ? event.getX() : this.x,
                event.isPositionUpdate() ? event.getY() : this.y, event.isPositionUpdate() ? event.getZ() : this.z,
                event.isRotationUpdate() ? event.getYaw() : this.yaw, event.isRotationUpdate() ? event.getPitch() : this.pitch);

        if(!WorldUtils.safeGround(this.player) && this.lastGround)
            this.lastGroundLocation = this.lastLocation;

        // Set last variables
        this.lastDeltaX = this.deltaX;
        this.lastDeltaY = this.deltaY;
        this.lastDeltaZ = this.deltaZ;
        this.lastDeltaYaw = this.deltaYaw;
        this.lastDeltaPitch = this.deltaPitch;
        this.lastDeltaXZ = this.deltaXZ;

        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;

        this.lastGround = this.onGround;
        this.lastClientGround = this.clientGround;

        // New
        this.x = this.location.getX();
        this.y = this.location.getY();
        this.z = this.location.getZ();
        this.yaw = this.location.getYaw();
        this.pitch = this.location.getPitch();

        this.deltaX =  this.x - this.lastX;
        this.deltaY = this.y - this.lastY;
        this.deltaZ = this.z - this.lastZ;
        this.deltaYaw = this.yaw - this.lastYaw;
        this.deltaPitch =  this.pitch - this.lastPitch;

        this.deltaXZ = Math.hypot(this.deltaX, this.deltaZ);

        this.onGround = WorldUtils.safeGround(this.player);
        this.clientGround = this.player.isOnGround();

        // Bunch of retarded code, should do this better
        if(this.deltaY < 0 && !this.onGround && !this.falling) {
            this.falling = true;
        }else if (this.onGround) {
            this.ticksFalling = 0;
            this.falling = false;
        }

        if(this.falling)
            ++this.ticksFalling;

        if(!this.onGround) {
            ++this.ticksInAir;
        }else
            this.ticksInAir = 0;

        if(PlayerUtils.getDistance(this, this.lastTarget) > 6) {
            this.lastTarget = null;
        }

        scan: {
            for (int i = -1; i < 1; ++i) {
                for (int l = 0; l < 1; ++l) {
                    for (int j = 0; j < 1; ++j) {
                        if (WorldUtils.ground(player.getWorld().getBlockAt(player.getLocation().add(i, j, l)).getType())) {
                            this.collidedHorizontally = true;
                            break scan;
                        }
                    }
                }
            }
        }

        this.collidedVertically = (WorldUtils.ground(player.getWorld().getBlockAt(player.getLocation().add(0, -1, 0)).getType()) ||
                WorldUtils.ground(player.getWorld().getBlockAt(player.getLocation().add(0, 2, 0)).getType()));
    }

    public void acceptTeleport(final SetPositionPacketWrapper positionPacketWrapper) {
        final Location location = new Location(this.player.getWorld(),
                positionPacketWrapper.getX(), positionPacketWrapper.getY(), positionPacketWrapper.getZ(),
                positionPacketWrapper.getYaw(), positionPacketWrapper.getPitch());

        this.teleports.add(new TeleportQueue.Teleport(location, WorldUtils.getWorldTicks(this.player.getWorld())));
    }

    /**
     *
     * @return Player's overall violation count, not linked to a specific check
     */
    public int getVl() {
        return this.vl;
    }

    /**
     * @implNote Increments VL by 1
     * @return Incremented VL
     */
    public int increment() {
        return ++this.vl;
    }

    /**
     *
     * @return Ticks The Player has been falling, ignores the players deltaY and instead opts to increment as long as the player has fallen
     */
    public int getTicksFalling() {
        return this.ticksFalling;
    }

    /**
     *
     * @return Returns if the player has fallen, even if the players Y motion is positive, it will return true if they have fallen before this and have not landed
     */
    public boolean hasFallen() {
        return this.falling;
    }

    /**
     *
     * @return Players X Coordinate
     */
    public double getX() {
        return x;
    }

    /**
     *
     * @return Players Last X Coordinate
     */
    public double getLastX() {
        return lastX;
    }

    /**
     *
     * @return Players Delta X
     */
    public double getDeltaX() {
        return deltaX;
    }

    /**
     *
     * @return Players Last Delta X
     */
    public double getLastDeltaX() {
        return lastDeltaX;
    }

    /**
     *
     * @return Players Y Coordinate
     */
    public double getY() {
        return y;
    }

    /**
     *
     * @return Players Last Y Coordinate
     */
    public double getLastY() {
        return lastY;
    }

    /**
     *
     * @return Players Delta Y
     */
    public double getDeltaY() {
        return deltaY;
    }

    /**
     *
     * @return Players Last Delta Y
     */
    public double getLastDeltaY() {
        return lastDeltaY;
    }

    /**
     *
     * @return Players Z Coordinate
     */
    public double getZ() {
        return z;
    }

    /**
     *
     * @return Players Last Z Coordinate
     */
    public double getLastZ() {
        return lastZ;
    }

    /**
     *
     * @return Players Delta Z
     */
    public double getDeltaZ() {
        return deltaZ;
    }

    /**
     *
     * @return Players Last Delta Z
     */
    public double getLastDeltaZ() {
        return lastDeltaZ;
    }

    /**
     *
     * @return Players Delta XZ
     */
    public double getDeltaXZ() {
        return deltaXZ;
    }

    /**
     *
     * @return Players Delta XZ
     */
    public double getLastDeltaXZ() {
        return lastDeltaXZ;
    }

    /**
     *
     * @return Players Yaw Rotation
     */
    public float getYaw() {
        return yaw;
    }

    /**
     *
     * @return Players Last Yaw Rotation
     */
    public float getLastYaw() {
        return lastYaw;
    }

    /**
     *
     * @return Players Delta Yaw
     */
    public float getDeltaYaw() {
        return deltaYaw;
    }

    /**
     *
     * @return Players Last Delta Yaw
     */
    public float getLastDeltaYaw() {
        return lastDeltaYaw;
    }

    /**
     *
     * @return Players Pitch Rotation
     */
    public float getPitch() {
        return pitch;
    }

    /**
     *
     * @return Players Last Pitch Rotation
     */
    public float getLastPitch() {
        return lastPitch;
    }

    /**
     *
     * @return Players Delta Pitch
     */
    public float getDeltaPitch() {
        return deltaPitch;
    }

    /**
     *
     * @return Players Last Delta Pitch
     */
    public float getLastDeltaPitch() {
        return lastDeltaPitch;
    }

    /**
     *
     * @return Players Location in their World
     */
    public Location getLocation() {
        return location;
    }

    /**
     *
     * @return Players Last Ground Location
     */
    public Location getLastGroundLocation() {
        return lastGroundLocation;
    }

    public boolean isTeleportTick() {
        return this.ticksSinceTeleport <= 1;
    }

    /**
     *
     * @return Players Time Existing
     */
    public int getTicksTracked() {
        return ticksTracked;
    }

    /**
     *
     * @return Players Serverside Ground State
     */
    public boolean isOnGround() {
        return onGround;
    }

    /**
     *
     * @return Players Last Serverside Ground State
     */
    public boolean isLastGround() {
        return lastGround;
    }


    /**
     *
     * @return Players ClientSide Ground State
     */
    public boolean isClientGround() {
        return clientGround;
    }


    /**
     *
     * @return Players Last ClientSide Ground State
     */
    public boolean isLastClientGround() {
        return lastClientGround;
    }


    /**
     *
     * @return Players deltaXZ > 0
     */
    public boolean isMoving() {
        return this.deltaXZ > 0;
    }


    /**
     *
     * @return Ticks that the player has been offground
     */
    public int getTicksInAir() {
        return this.ticksInAir;
    }

    /**
     *
     * @return The player is currently using an item
     */
    public boolean isConsuming() {
        return this.isConsuming;
    }


    /**
     *
     * @return Ticks Since the player has been able to fly, this permission is granted by the server and can not be spoofed
     */
    public int getTicksSinceAbility() {
        return this.ticksSinceAbility;
    }


    /**
     *
     * @return Ticks the player has been flying, not to be confused with getTicksInAir
     */
    public int getTicksFlying() {
        return this.ticksFlying;
    }


    /**
     *
     * @return Players Ping as calculated by transaction response
     */
    public long getPing() {
        return this.ping;
    }


    /**
     *
     * @param ping - new ping
     * @return Player's Data in reference
     */
    public PlayerData setPing(final long ping) {
        this.ping = ping;
        final int size = this.teleports.size();
        for(int i = 0; i < size; ++i) {
            final TeleportQueue.Teleport teleport = this.teleports.get(i);
            teleport.tick();
            if(teleport.hasReceived()) {
                this.ticksSinceTeleport = 0;
                this.teleport = teleport;
                this.velocities.clear();
                this.teleports.remove(teleport);

                final int bufferCount = this.teleportUpdateBuffers.size();
                for(int l = 0; l < bufferCount; ++l) {
                    final TeleportQueue.TeleportBuffer teleportBuffer = (TeleportQueue.TeleportBuffer) this.teleportUpdateBuffers.get(l);
                    if(teleportBuffer.tick())
                        this.teleportUpdateBuffers.remove(teleportBuffer);
                }
                this.teleportUpdateBuffers.add(new TeleportQueue.TeleportBuffer(this.player.getWorld()));
            }
        }
        return this;
    }

    private void processPosLook(final RelMovePacketWrapper relMovePacketWrapper) {
        // A client could technically respond to a teleport with a Position update only
        // This would not happen in vanilla and there would be no advantage for a cheat to do this
        // And by ignoring them a client doing this will flag timer
        // We will need to time out our buffer just in case however
        if(!relMovePacketWrapper.isPosRotUpdate())
            return;


    }

    /**
     *
     * @return Players Collision state on the Y Axis
     */
    public boolean isCollidedVertically() {
        return this.collidedVertically;
    }


    /**
     *
     * @return Players Collision state on the XZ Axis
     */
    public boolean isCollidedHorizontally() {
        return this.collidedHorizontally;
    }

    /**
     *
     * @return The last entity the player has interacted with
     */
    public Entity getLastTarget() {
        return this.lastTarget;
    }

    /**
     *
     * @param entity
     * Set the last entity the player has interacted with
     */
    public void setLastTarget(final Entity entity) {
        this.lastTarget = entity;
    }

    /**
     *
     * @param synced
     * Set the player's transaction sync state
     */
    public void setSynced(final boolean synced) {
        this.synced = synced;
    }

    /**
     *
     * @return Returns the list of waiting velocities, head element is one closest to being accepted
     */
    public VelocityQueue getVelocities() {
        return velocities;
    }

    public int getCurrentSlot() {
        return currentSlot;
    }

    public int getLastSlot() {
        return lastSlot;
    }

    public void setCurrentSlot(final int slot) {
        this.currentSlot = slot;
    }

    public void setLastSlot(final int slot) {
        this.lastSlot = slot;
    }
}