package ret.tawny.truthful.data.tracker;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.MathHelper;
import ret.tawny.truthful.utils.math.SensitivityUtil;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

public class PositionTracker {

    private final PlayerData data;
    private final Player player;

    private double x, y, z;
    private double lastX, lastY, lastZ;
    private double deltaX, deltaY, deltaZ;
    private double lastDeltaX, lastDeltaY, lastDeltaZ;
    private float yaw, pitch;
    private float lastYaw, lastPitch;
    private float deltaYaw, deltaPitch;
    private float lastDeltaYaw, lastDeltaPitch;

    private boolean onGroundServer;
    private boolean onGroundClient;
    private boolean lastOnGround;
    private int airTicks;
    private int lastAirTicks;
    private int groundTicks;
    private int lastLandTick = -100;
    private int ticksTracked;

    private boolean nearVehicle;
    private boolean nearEntity;
    private boolean underBlock;
    private boolean inLiquid;
    private boolean onClimbable;
    private boolean inWeb;

    private int lastWebTick = -100;
    private int lastUnderBlockTick = -100;
    private int lastSlimeTick = -100;
    private int lastIceTick = -100;
    private int lastSoulSandTick = -100;

    private double sensitivityGcd;
    private int sensitivityPercent = -1;

    public PositionTracker(PlayerData data, Player player) {
        this.data = data;
        this.player = player;
        Location loc = player.getLocation();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    public void handleUpdate(RelMovePacketWrapper event) {
        this.ticksTracked++;
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.lastDeltaYaw = this.deltaYaw;
        this.lastDeltaPitch = this.deltaPitch;

        if (event.isRotationUpdate()) {
            this.yaw = event.getYaw();
            this.pitch = event.getPitch();
            this.deltaYaw = Math.abs(this.yaw - this.lastYaw) % 360.0F;
            if (this.deltaYaw > 180.0F)
                this.deltaYaw = 360.0F - this.deltaYaw;
            this.deltaPitch = Math.abs(this.pitch - this.lastPitch);
            if (this.deltaPitch > 0.0 && this.deltaPitch < 10.0)
                processSensitivity(this.deltaPitch);
        }


        if (event.isPositionUpdate()) {
            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;
            this.lastDeltaX = this.deltaX;
            this.lastDeltaY = this.deltaY;
            this.lastDeltaZ = this.deltaZ;
            this.lastOnGround = this.onGroundServer;

            this.x = event.getX();
            this.y = event.getY();
            this.z = event.getZ();
            this.deltaX = x - this.lastX;
            this.deltaY = y - this.lastY;
            this.deltaZ = z - this.lastZ;
            this.onGroundClient = event.isGround();

            // PERFORMANCE: Use primitive check to avoid Location object allocation
            this.onGroundServer = WorldUtils.safeGround(x, y, z, data);

            // Check environment state every tick to prevent stale flags causing false positives
            checkEnvironment();

            this.lastAirTicks = this.airTicks;
            if (this.onGroundServer) {
                this.groundTicks++;
                if (this.airTicks > 0) {
                    this.lastLandTick = this.ticksTracked;
                }
                this.airTicks = 0;
            } else {
                this.airTicks++;
                this.groundTicks = 0;
            }
        }
    }

    public void reset(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;

        this.deltaX = 0;
        this.deltaY = 0;
        this.deltaZ = 0;
        this.lastDeltaX = 0;
        this.lastDeltaY = 0;
        this.lastDeltaZ = 0;

        this.yaw = yaw;
        this.pitch = pitch;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.deltaYaw = 0;
        this.deltaPitch = 0;

        this.onGroundServer = true;
        this.groundTicks = 1;
        this.airTicks = 0;
    }

    private void checkEnvironment() {
        this.inLiquid = WorldUtils.isLiquid(player);
        this.onClimbable = WorldUtils.hasClimbableNearby(player);
        this.inWeb = WorldUtils.isInWeb(player);
        if (this.inWeb)
            this.lastWebTick = this.ticksTracked;
        this.underBlock = WorldUtils.isHeadHitter(player);
        if (this.underBlock)
            this.lastUnderBlockTick = this.ticksTracked;

        if (WorldUtils.isNearMaterial(player, org.bukkit.Material.SLIME_BLOCK))
            this.lastSlimeTick = this.ticksTracked;
        if (WorldUtils.isNearIce(player))
            this.lastIceTick = this.ticksTracked;
        if (WorldUtils.isNearMaterial(player, org.bukkit.Material.SOUL_SAND))
            this.lastSoulSandTick = this.ticksTracked;
    }

    private void processSensitivity(float deltaP) {
        long delta = (long) (deltaP * 16777216.0);
        long last = (long) (this.lastDeltaPitch * 16777216.0);
        long gcd = MathHelper.getGcd(delta, last);
        double step = gcd / 16777216.0;
        if (sensitivityGcd == 0.0 || (step < sensitivityGcd && step > 0.0001))
            sensitivityGcd = step;
        if (sensitivityGcd > 0.0001)
            this.sensitivityPercent = SensitivityUtil.getSensitivityFromPitchGCD((float) sensitivityGcd);
    }

    public Location getLocation() {
        return new Location(player.getWorld(), x, y, z, yaw, pitch);
    }

    public Location getLastLocation() {
        return new Location(player.getWorld(), lastX, lastY, lastZ, lastYaw, lastPitch);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getDeltaX() { return deltaX; }
    public double getDeltaY() { return deltaY; }
    public double getDeltaZ() { return deltaZ; }
    public double getLastDeltaX() { return lastDeltaX; }
    public double getLastDeltaY() { return lastDeltaY; }
    public double getLastDeltaZ() { return lastDeltaZ; }
    public double getDeltaXZ() { return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ); }
    public double getLastDeltaXZ() { return Math.sqrt(lastDeltaX * lastDeltaX + lastDeltaZ * lastDeltaZ); }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getLastYaw() { return lastYaw; }
    public float getLastPitch() { return lastPitch; }
    public float getDeltaYaw() { return deltaYaw; }
    public float getDeltaPitch() { return deltaPitch; }
    public float getLastDeltaYaw() { return lastDeltaYaw; }
    public float getLastDeltaPitch() { return lastDeltaPitch; }
    public boolean isOnGround() { return onGroundServer; }
    public boolean isServerGround() { return onGroundServer; }
    public boolean isClientGround() { return onGroundClient; }
    public boolean isLastGround() { return lastOnGround; }
    public int getAirTicks() { return airTicks; }
    public int getLastAirTicks() { return lastAirTicks; }
    public int getGroundTicks() { return groundTicks; }
    public int getLastLandTick() { return lastLandTick; }
    public int getTicksTracked() { return ticksTracked; }
    public boolean isInLiquid() { return inLiquid; }
    public boolean isOnClimbable() { return onClimbable; }
    public boolean isInWeb() { return inWeb; }
    public boolean isUnderBlock() { return underBlock; }
    public boolean isNearVehicle() { return nearVehicle; }
    public void setNearVehicle(boolean b) { this.nearVehicle = b; }
    public boolean isNearEntity() { return nearEntity; }
    public void setNearEntity(boolean b) { this.nearEntity = b; }
    public int getLastWebTick() { return lastWebTick; }
    public int getLastUnderBlockTick() { return lastUnderBlockTick; }
    public int getSensitivityPercent() { return sensitivityPercent; }
    public int getLastSlimeTick() { return lastSlimeTick; }
    public int getLastIceTick() { return lastIceTick; }
    public int getLastSoulSandTick() { return lastSoulSandTick; }
    public float getRotationDeviation(boolean pitch) { return pitch ? deltaPitch : deltaYaw; }

    public double getEyeHeight(boolean legacy, boolean sneaking, boolean swimming) {
        if (swimming || player.isGliding())
            return 0.4;
        if (sneaking)
            return 1.27;
        return PlayerData.EYE_HEIGHT_STANDING;
    }
}
