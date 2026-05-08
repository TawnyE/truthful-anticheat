package ret.tawny.truthful.debug.recorder.data;

/**
 * Represents the complete state of a player during a single server tick.
 * This captures physics, environment, and inputs for blackbox analysis.
 */
public final class TickSnapshot {

    public final int tick;
    public final long timestamp;

    // --- POSITION & ROTATION ---
    public final double x, y, z;
    public final float yaw, pitch;
    public final double deltaX, deltaY, deltaZ;

    // --- GROUND STATES ---
    public final boolean clientGround; // What the packet said
    public final boolean serverGround; // What the math said
    public final boolean lastGround;   // Previous tick state

    // --- ENVIRONMENT ---
    public final boolean inLiquid;
    public final boolean onClimbable;
    public final boolean inWeb;
    public final boolean underBlock;
    public final boolean nearVehicle;
    public final boolean nearSlime;

    // --- VELOCITY ---
    public final double velocityX, velocityY, velocityZ;
    public final boolean hasVelocity; // Is velocity currently affecting them?

    // --- PREDICTION (The "Truth") ---
    public final double predictedDeltaX;
    public final double predictedDeltaZ;
    public final double predictedDeltaY;

    // --- ATTRIBUTES & STATE ---
    public final boolean isSprinting;
    public final boolean isSneaking;
    public final boolean isGliding;
    public final int jumpBoostLevel;
    public final int speedLevel;
    public final float friction; // The block friction factor

    public TickSnapshot(int tick, double x, double y, double z, float yaw, float pitch,
                        double dX, double dY, double dZ,
                        boolean cGround, boolean sGround, boolean lGround,
                        boolean liquid, boolean climb, boolean web, boolean roof, boolean vehicle, boolean slime,
                        double vX, double vY, double vZ, boolean hasVel,
                        double pDX, double pDZ, double pDY,
                        boolean sprint, boolean sneak, boolean glide,
                        int jump, int speed, float fric) {
        this.tick = tick;
        this.timestamp = System.currentTimeMillis();
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.deltaX = dX; this.deltaY = dY; this.deltaZ = dZ;
        this.clientGround = cGround;
        this.serverGround = sGround;
        this.lastGround = lGround;
        this.inLiquid = liquid;
        this.onClimbable = climb;
        this.inWeb = web;
        this.underBlock = roof;
        this.nearVehicle = vehicle;
        this.nearSlime = slime;
        this.velocityX = vX; this.velocityY = vY; this.velocityZ = vZ;
        this.hasVelocity = hasVel;
        this.predictedDeltaX = pDX; this.predictedDeltaZ = pDZ; this.predictedDeltaY = pDY;
        this.isSprinting = sprint;
        this.isSneaking = sneak;
        this.isGliding = glide;
        this.jumpBoostLevel = jump;
        this.speedLevel = speed;
        this.friction = fric;
    }
}