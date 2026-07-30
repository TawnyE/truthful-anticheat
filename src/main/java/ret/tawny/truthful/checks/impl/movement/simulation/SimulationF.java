package ret.tawny.truthful.checks.impl.movement.simulation;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.movement.MovementCheckSupport;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;
import ret.tawny.truthful.utils.world.PhysicsConstants;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'F', type = CheckType.SIMULATION, displayName = "Simulation(F)")
public final class SimulationF extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0D);

    private static final String[] INPUT_NAMES = {
            "IDLE", "[W]", "[S]", "[D]", "[A]",
            "[W+D]", "[W+A]", "[S+D]", "[S+A]"
    };

    private static final double[][] INPUT_VECTORS = {
            {0.0, 0.0}, {0.0, 1.0}, {0.0, -1.0}, {1.0, 0.0}, {-1.0, 0.0},
            {1.0, 1.0}, {-1.0, 1.0}, {1.0, -1.0}, {-1.0, -1.0}
    };

    private static final class State {
        int violationTicks;
        double speedLeak;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final PlayerData data = wrapper.getPlayerData();
        if (data == null || data.isGliding() || data.isInsideVehicle()) return;

        if (MovementCheckSupport.skipForPrediction(data) || MovementCheckSupport.isInGraceWindow(data)) return;

        final State st = states.computeIfAbsent(data.getPlayer().getUniqueId(), k -> new State());

        final double actualX = data.getDeltaX();
        final double actualY = data.getDeltaY();
        final double actualZ = data.getDeltaZ();
        final double actualXZ = data.getDeltaXZ();

        if (actualXZ < 0.003D && data.getLastDeltaXZ() < 0.003D && Math.abs(actualY) < 0.003D) {
            st.violationTicks = 0;
            st.speedLeak = 0.0D;
            return;
        }

        final boolean onGround = data.isServerGround() || data.isClientGround();
        final boolean wasOnGround = data.isLastGround();
        final int ticksNow = data.getTicksTracked();

        // 1. Friction & Environmental Slowdons
        final double friction = onGround ? computeGroundFriction(data) : PhysicsConstants.AIR_DRAG_XZ;
        double slowMult = computeBlockSlowdownMultiplier(data, ticksNow);

        double lastX = data.getLastDeltaX();
        double lastZ = data.getLastDeltaZ();
        double lastXZ = data.getLastDeltaXZ();

        double[] momentumScales = { 1.0D };
        if (!onGround && lastXZ > 0.20D) {
            momentumScales = new double[]{ 1.0D, 0.85D, 0.70D, 0.55D };
        }

        Vector activeVel = data.getVelocities().getQueuedVelocityVector();
        double velX = activeVel.getX();
        double velY = activeVel.getY();
        double velZ = activeVel.getZ();

        // 2. Rotation Candidate
        float currentYaw = data.getYaw();
        float lastYaw = data.getLastYaw();
        float interpolatedYaw = lastYaw + (currentYaw - lastYaw) * 0.5f;
        float[] candidateYaws = { currentYaw, lastYaw, interpolatedYaw };

        // 3. Multi-Input 3D Vector Simulation Engine
        double min3DVectorError = Double.MAX_VALUE;
        String bestInputName = "IDLE";
        double bestPredXZ = 0.0D;
        double bestPredY = 0.0D;

        boolean[] sprintStates = { data.isSprinting(), false };
        boolean isJumpTakeoff = wasOnGround && !onGround && data.getAirTicks() <= 1 && actualY > 0.38D;

        double[] candidateYPredictions = predictVerticalCandidates(data, actualY, data.getLastDeltaY(), onGround, velY);

        for (float yaw : candidateYaws) {
            double yawRad = Math.toRadians(yaw);
            double sinYaw = Math.sin(yawRad);
            double cosYaw = Math.cos(yawRad);

            double lookX = -sinYaw;
            double lookZ = cosYaw;
            double moveDotLook = (actualX * lookX + actualZ * lookZ);

            for (double momScale : momentumScales) {
                double priorX = lastX * momScale * friction;
                double priorZ = lastZ * momScale * friction;

                for (boolean sprinting : sprintStates) {
                    for (int i = 0; i < INPUT_VECTORS.length; i++) {
                        double strafe = INPUT_VECTORS[i][0];
                        double forward = INPUT_VECTORS[i][1];

                        if (moveDotLook > 0.05D && forward < 0) {
                            continue;
                        }

                        double len = Math.hypot(strafe, forward);
                        if (len >= 1.0D) {
                            strafe /= len;
                            forward /= len;
                        }

                        double accelFactor = computeInputAcceleration(data, onGround, friction, sprinting) * slowMult;

                        double inputX = (strafe * cosYaw - forward * sinYaw) * accelFactor;
                        double inputZ = (forward * cosYaw + strafe * sinYaw) * accelFactor;

                        if (sprinting && isJumpTakeoff) {
                            inputX -= sinYaw * 0.2D;
                            inputZ += cosYaw * 0.2D;
                        }

                        double simX = priorX + inputX + velX;
                        double simZ = priorZ + inputZ + velZ;

                        if (isWallObstructed(data, simX, simZ)) {
                            if (Math.abs(actualX) < 0.01D) simX = actualX;
                            if (Math.abs(actualZ) < 0.01D) simZ = actualZ;
                        }

                        double hErr = Math.hypot(actualX - simX, actualZ - simZ);

                        for (double simY : candidateYPredictions) {
                            double vErr = Math.abs(actualY - simY);
                            double total3DError = Math.sqrt(hErr * hErr + vErr * vErr);

                            if (total3DError < min3DVectorError) {
                                min3DVectorError = total3DError;
                                bestInputName = INPUT_NAMES[i];
                                bestPredXZ = Math.hypot(simX, simZ);
                                bestPredY = simY;
                            }
                        }
                    }
                }
            }
        }

        // 4. Dynamic Tolerance Evaluation
        boolean wallTouch = isWallObstructed(data, actualX, actualZ);
        double allowedError = 0.065D + (onGround ? 0.020D : 0.035D) + (wallTouch ? 0.10D : 0.0D);

        // Speed leak only accumulates if actual speed significantly exceeds prediction AND vector error is high
        if (actualXZ > bestPredXZ + 0.015D && !data.hasVelocity() && min3DVectorError > allowedError) {
            st.speedLeak += (actualXZ - bestPredXZ);
        } else {
            st.speedLeak = Math.max(0.0D, st.speedLeak - 0.02D);
        }

        double absoluteMaxGroundSpeed = MovementCheckSupport.computeMaxHorizontalSpeed(data) + 0.025D;
        boolean hardCapBreached = onGround && actualXZ > absoluteMaxGroundSpeed && !data.hasVelocity();

        boolean isVectorMismatch = (min3DVectorError > allowedError && st.speedLeak > 0.080D) || hardCapBreached || min3DVectorError > (allowedError * 1.8D);

        if (isVectorMismatch) {
            st.violationTicks++;
            double excess = Math.max(min3DVectorError - allowedError, st.speedLeak);

            String debugMsg = String.format(
                    "Vector Mismatch | Error: %.3fm (Allowed: %.3fm) | Leak: %.3fm | Best Match: %s | Got (XZ:%.3f, Y:%.3f) (Pred XZ:%.3f, Y:%.3f)",
                    min3DVectorError, allowedError, st.speedLeak, bestInputName, actualXZ, actualY, bestPredXZ, bestPredY
            );

            debugVerbose(String.format("Moved (%.3f, %.3f, %.3f) | Match: %s | 3D Error: %.3fm -> FAIL",
                    actualX, actualY, actualZ, bestInputName, min3DVectorError));

            if (st.violationTicks >= 2) {
                if (buffer.increase(data.getPlayer(), 1.5 + excess * 12.0) > 4.0D) {
                    flag(data, debugMsg, 2.0 + excess * 15.0);
                    buffer.reset(data.getPlayer(), 2.0D);
                    st.violationTicks = 0;
                    st.speedLeak = 0.0D;
                }
            }
        } else {
            st.violationTicks = 0;
            buffer.decrease(data.getPlayer(), 0.15);
            debugVerbose(String.format("Moved (%.3f, %.3f, %.3f) | Match: %s | 3D Error: %.3fm -> PASS",
                    actualX, actualY, actualZ, bestInputName, min3DVectorError));
        }
    }

    private double[] predictVerticalCandidates(PlayerData data, double actualY, double lastY, boolean onGround, double velY) {
        double gravity = data.getGravity();
        int levitation = data.getPotionLevel(PotionEffectType.LEVITATION);
        int slowFalling = data.getPotionLevel(PotionEffectType.SLOW_FALLING);

        if (levitation > 0) {
            double target = 0.05D * levitation;
            double predLev = (lastY + (target - lastY) * 0.2D) * PhysicsConstants.AIR_DRAG_Y + velY;
            return new double[]{ predLev, actualY };
        }

        double predGravity = (lastY - gravity) * PhysicsConstants.AIR_DRAG_Y + velY;
        if (slowFalling > 0) predGravity = Math.max(predGravity, -0.01D * PhysicsConstants.AIR_DRAG_Y);

        int jumpBoost = data.getPotionLevel(PotionEffectType.JUMP_BOOST);
        double predJump = data.getJumpStrength() + (jumpBoost * 0.1D) + velY;
        double predStep = Math.max(0.601D, data.getStepHeight() + 0.01D);

        if (data.isInWeb()) {
            predGravity *= 0.05D;
        }

        if (data.isUnderBlock() && actualY <= 0.0D) {
            predGravity = 0.0D;
            predJump = 0.0D;
        }

        if (onGround && Math.abs(actualY) < 0.005D) {
            return new double[]{ 0.0D, predGravity, predJump, velY };
        }

        if (onGround && actualY > 0.0D && actualY <= predStep) {
            return new double[]{ actualY, predGravity, predJump, velY };
        }

        return new double[]{ predGravity, predJump, 0.0D, velY };
    }

    private double computeInputAcceleration(PlayerData data, boolean onGround, double friction, boolean sprinting) {
        double base = data.getWalkSpeed();
        int speedLevel = data.getPotionLevel(PotionEffectType.SPEED);
        if (speedLevel > 0) base *= 1.0D + 0.20D * speedLevel;

        int slowLevel = data.getPotionLevel(PotionEffectType.SLOWNESS);
        if (slowLevel > 0) base *= Math.max(0.0D, 1.0D - 0.15D * slowLevel);

        if (sprinting) base *= 1.30D;
        if (data.isSneaking()) base *= 0.30D;

        if (onGround) {
            double f3 = Math.max(0.048D, friction * friction * friction);
            return base * (0.16277136D / f3);
        }

        return base * (sprinting ? 0.26D : 0.20D);
    }

    private double computeBlockSlowdownMultiplier(PlayerData data, int ticksNow) {
        double mult = 1.0D;

        if (data.isUsingItem() && data.isSlowItem()) mult *= 0.20D;
        if (data.getMovementContext().isHoney()) mult *= 0.40D;
        if (data.isInWeb()) mult *= 0.25D;

        if (ticksNow - data.getLastSoulSandTick() < 8 && data.getEnchantLevel("soul_speed") == 0) {
            mult *= 0.40D;
        }

        if (data.isExempt(ExemptionType.POWDER_SNOW)) {
            ItemStack boots = data.getPlayer().getInventory().getBoots();
            boolean leatherBoots = boots != null && boots.getType() == Material.LEATHER_BOOTS;
            if (!leatherBoots) mult *= 0.30D;
        }

        return mult;
    }

    private double computeGroundFriction(PlayerData data) {
        double x = data.getX();
        double y = data.getY() - 0.2D;
        double z = data.getZ();

        float maxFriction = 0.6F;

        for (double ox = -0.3D; ox <= 0.3D; ox += 0.3D) {
            for (double oz = -0.3D; oz <= 0.3D; oz += 0.3D) {
                int bx = (int) Math.floor(x + ox);
                int by = (int) Math.floor(y);
                int bz = (int) Math.floor(z + oz);
                float f = BlockPropertyRegistry.getFriction(data.getWorldCache().getBlockState(bx, by, bz));
                if (f > maxFriction) maxFriction = f;
            }
        }

        return maxFriction * 0.91D;
    }

    private boolean isWallObstructed(PlayerData data, double simX, double simZ) {
        double x = data.getX();
        double y = data.getY();
        double z = data.getZ();
        double r = 0.35D;

        return hasSolid(data, x + r, y + 0.20D, z) || hasSolid(data, x - r, y + 0.20D, z)
                || hasSolid(data, x, y + 0.20D, z + r) || hasSolid(data, x, y + 0.20D, z - r)
                || hasSolid(data, x + r, y + 1.20D, z) || hasSolid(data, x - r, y + 1.20D, z)
                || hasSolid(data, x, y + 1.20D, z + r) || hasSolid(data, x, y + 1.20D, z - r);
    }

    private boolean hasSolid(PlayerData data, double x, double y, double z) {
        return BlockPropertyRegistry.isSolid(data.getWorldCache().getBlockState((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        states.remove(event.getPlayer().getUniqueId());
    }
}