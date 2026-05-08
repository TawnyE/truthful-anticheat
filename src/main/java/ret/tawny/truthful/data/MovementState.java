package ret.tawny.truthful.data;

/**
 * Packet-driven movement state used by prediction and checks.
 */
public final class MovementState {

    private boolean onGround;
    private int lastGroundTick = -1000;
    private int lastVelocityTick = -1000;
    private int lastTeleportTick = -1000;
    private int jumpTicks;
    private int airTicks;
    private int glideTicks;

    public void update(PlayerData data) {
        final int tick = data.getTicksTracked();
        this.onGround = data.isServerGround() || data.isClientGround();

        if (onGround) {
            lastGroundTick = tick;
            airTicks = 0;
            jumpTicks = 0;
        } else {
            airTicks++;
            if (data.getDeltaY() > 0.0D) {
                jumpTicks++;
            }
        }

        if (data.isGliding()) {
            glideTicks++;
        } else {
            glideTicks = 0;
        }

        if (data.hasVelocity()) {
            lastVelocityTick = tick;
        }

        if (data.isTeleportTick()) {
            lastTeleportTick = tick;
        }
    }

    public boolean isOnGround() { return onGround; }
    public int getLastGroundTick() { return lastGroundTick; }
    public int getLastVelocityTick() { return lastVelocityTick; }
    public int getLastTeleportTick() { return lastTeleportTick; }
    public int getJumpTicks() { return jumpTicks; }
    public int getAirTicks() { return airTicks; }
    public int getGlideTicks() { return glideTicks; }
}
