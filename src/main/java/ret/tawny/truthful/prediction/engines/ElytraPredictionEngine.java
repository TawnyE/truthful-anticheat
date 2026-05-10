package ret.tawny.truthful.prediction.engines;

import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.PhysicsConstants;


public final class ElytraPredictionEngine {

    private final PlayerData data;

    private double predictedHorizontal;
    private double predictedVertical;
    private double predictedDeltaX;
    private double predictedDeltaZ;

    public ElytraPredictionEngine(PlayerData data) {
        this.data = data;
    }

    public void predict(double lastHorizontal, double lastDeltaY) {
        float pitch = data.getPitch();
        float yaw = data.getYaw();

        double pitchRad = Math.toRadians(pitch);
        double yawRad = Math.toRadians(yaw);

        // Calculate exact Look Vector
        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad); // Pitch < 0 is looking UP in Bukkit
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double horizLook = Math.sqrt(lookX * lookX + lookZ * lookZ);
        double horizMotion = lastHorizontal;

        double motionX = data.getLastDeltaX();
        double motionY = lastDeltaY;
        double motionZ = data.getLastDeltaZ();

        // 1. Base Elytra Gravity
        motionY -= PhysicsConstants.GRAVITY;

        // 2. Lift Mechanics (Converting fall speed into forward momentum)
        if (motionY < 0.0D && horizLook > 0.0D) {
            double cosPitchSq = Math.cos(pitchRad) * Math.cos(pitchRad);
            double lift = motionY * -0.1D * cosPitchSq;
            motionY += lift;
            motionX += (lookX * lift) / horizLook;
            motionZ += (lookZ * lift) / horizLook;
        }

        // 3. Dive Thrust / Climb Drag (Converting horizontal speed into vertical altitude change)
        if (horizLook > 0.0D) {
            if (lookY < 0.0D) {
                // Looking Down: Gain downward speed, lose horizontal speed slightly
                double dive = horizMotion * -lookY * 0.04D;
                motionY -= dive * 3.2D;
                motionX += (lookX * dive) / horizLook;
                motionZ += (lookZ * dive) / horizLook;
            } else if (lookY > 0.0D) {
                // Looking Up: Gain height, lose horizontal speed heavily
                double climb = horizMotion * lookY * 0.04D;
                motionY += climb * 3.2D;
                motionX -= (lookX * climb) / horizLook;
                motionZ -= (lookZ * climb) / horizLook;
            }
        }

        // 4. Aerodynamic Steering (Bending momentum toward look direction)
        if (horizLook > 0.0D) {
            motionX += ((lookX / horizLook) * horizMotion - motionX) * 0.1D;
            motionZ += ((lookZ / horizLook) * horizMotion - motionZ) * 0.1D;
        }

        // 5. Firework Boost Acceleration
        // Fireworks apply thrust in the direction you are looking, not just flat XZ speed.
        int ticksSinceFirework = data.getTicksTracked() - data.getLastFireworkTick();
        if (ticksSinceFirework >= 0 && ticksSinceFirework <= 5) {
            // Vanilla firework logic adds directional thrust over time
            motionX += lookX * 0.1D + (lookX * 1.5D - motionX) * 0.5D;
            motionY += lookY * 0.1D + (lookY * 1.5D - motionY) * 0.5D;
            motionZ += lookZ * 0.1D + (lookZ * 1.5D - motionZ) * 0.5D;
        }

        // 6. Elytra Drag Application
        motionX *= 0.99D;
        motionY *= 0.98D;
        motionZ *= 0.99D;

        // Commit predictions
        this.predictedDeltaX = motionX;
        this.predictedVertical = motionY;
        this.predictedDeltaZ = motionZ;
        this.predictedHorizontal = Math.hypot(motionX, motionZ);
    }

    public double getPredictedHorizontal() { return predictedHorizontal; }
    public double getPredictedVertical() { return predictedVertical; }
    public double getPredictedDeltaX() { return predictedDeltaX; }
    public double getPredictedDeltaZ() { return predictedDeltaZ; }
}