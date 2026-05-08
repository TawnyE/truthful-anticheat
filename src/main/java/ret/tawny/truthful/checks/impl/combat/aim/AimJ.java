package ret.tawny.truthful.checks.impl.combat.aim;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@CheckData(order = 'J', type = CheckType.AIM)
public final class AimJ extends Check {

    private static final int SAMPLE_SIZE = 20;
    private static final double MIN_ROTATION = 0.5; // Ignore micro-movements (idle noise)

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, JitterData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isRotationExempt())
            return;

        // FIX: Boat A/D steering creates alternating left-right camera movements
        // that mimic synthetic jitter oscillation patterns.
        if (data.isInsideVehicle()) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        // Only check if attacking (Combat context)
        if (data.getTicksTracked() - data.getLastHitTick() > 40) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            return;
        }

        float deltaYaw = data.getDeltaYaw();
        float deltaPitch = data.getDeltaPitch();

        // We need raw signed deltas to detect direction changes
        float rawDeltaYaw = data.getYaw() - data.getLastYaw();
        rawDeltaYaw = wrapAngle(rawDeltaYaw); // Handle 360 wrapping

        // Ignore small movements where mouse resolution noise dominates
        if (Math.abs(deltaYaw) < MIN_ROTATION && Math.abs(deltaPitch) < MIN_ROTATION) {
            return;
        }

        JitterData jitter = dataMap.computeIfAbsent(player.getUniqueId(), k -> new JitterData());
        jitter.addSample(rawDeltaYaw);

        if (!jitter.isReady())
            return;

        // === ANALYSIS ===
        double oscillationRate = jitter.getOscillationRate();
        double averageMagnitude = jitter.getAverageMagnitude();

        // 1. High Oscillation Frequency
        // If direction flips > 80% of the time (e.g. 16/20 ticks), it's likely
        // synthetic.
        if (oscillationRate > 0.8) {

            // Filter: Must be moving the mouse significantly.
            if (averageMagnitude > 1.5) {
                if (buffer.increase(player, 1.0) > 8.0) {
                    flag(data, String.format("Synthetic Jitter. Rate: %.2f, Mag: %.2f", oscillationRate,
                            averageMagnitude));
                    buffer.reset(player, 4.0);
                }
            }
        }
        // 2. Perfect Balance (Zero-Sum Jitter)
        else if (oscillationRate > 0.6) {
            double displacement = Math.abs(jitter.getNetDisplacement());
            double totalPath = jitter.getTotalPath();

            // High movement, but net result is staying in same place
            if (totalPath > 20.0 && displacement < 2.0) {
                if (buffer.increase(player, 1.0) > 10.0) {
                    flag(data, String.format("Stationary Jitter. Path: %.1f, Net: %.1f", totalPath, displacement));
                }
            } else {
                buffer.decrease(player, 0.2);
            }
        } else {
            buffer.decrease(player, 0.25);
        }
    }

    private float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F)
            angle -= 360.0F;
        if (angle < -180.0F)
            angle += 360.0F;
        return angle;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class JitterData {
        private final Deque<Float> yawDeltas = new ArrayDeque<>();

        void addSample(float delta) {
            yawDeltas.addLast(delta);
            while (yawDeltas.size() > SAMPLE_SIZE) {
                yawDeltas.pollFirst();
            }
        }

        boolean isReady() {
            return yawDeltas.size() >= SAMPLE_SIZE;
        }

        double getOscillationRate() {
            int flips = 0;
            Float prev = null;
            for (Float curr : yawDeltas) {
                if (prev != null) {
                    // Check if signs are different (one positive, one negative)
                    if (Math.signum(curr) != Math.signum(prev)) {
                        flips++;
                    }
                }
                prev = curr;
            }
            return (double) flips / (yawDeltas.size() - 1);
        }

        double getAverageMagnitude() {
            return yawDeltas.stream().mapToDouble(Math::abs).average().orElse(0.0);
        }

        double getNetDisplacement() {
            return yawDeltas.stream().mapToDouble(Float::doubleValue).sum();
        }

        double getTotalPath() {
            return yawDeltas.stream().mapToDouble(Math::abs).sum();
        }
    }
}