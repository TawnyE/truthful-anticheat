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
import ret.tawny.truthful.utils.math.Statistics;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@CheckData(order = 'E', type = CheckType.AIM)
public final class AimE extends Check {

    private final CheckBuffer buffer = new CheckBuffer(20.0);
    private final Map<UUID, AimStatsData> dataMap = new ConcurrentHashMap<>();

    private static final int WINDOW_SIZE = 30;

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        // FIX: Boat keyboard steering creates perfectly uniform rotation distributions
        // that trigger the low-entropy robotic aim detection.
        if (data.isInsideVehicle()) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        // Combat context check (40 ticks after last hit)
        if (data.getTicksTracked() - data.getLastHitTick() > 40) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        AimStatsData stats = dataMap.computeIfAbsent(player.getUniqueId(), k -> new AimStatsData());

        float deltaYaw = Math.abs(data.getDeltaYaw());
        float deltaPitch = Math.abs(data.getDeltaPitch());


        if (deltaYaw > 0.4 && deltaPitch > 0.01 && deltaYaw < 30.0) {
            stats.add(deltaYaw, deltaPitch);
        } else {
            if (deltaYaw >= 30.0) {
                buffer.decrease(player, 2.0);
                stats.reset(); // Clear window to prevent "flick-pollution"
            }
            buffer.decrease(player, 0.1);
            return;
        }

        if (stats.isFull()) {
            checkEntropy(player, data, stats);
            checkOutliers(player, data, stats);
        }
    }

    private void checkEntropy(Player player, PlayerData data, AimStatsData stats) {
        double entropyYaw = Statistics.getShannonEntropy(stats.yawSamples);
        double uniqueYaw = (double) Statistics.getDistinct(stats.yawSamples) / stats.yawSamples.size();


        double speedVariance = Statistics.getVariance(stats.yawSamples);


        if (speedVariance > 5.0) {
            buffer.decrease(player, 0.5);
            return;
        }

        // Logic Check: Low Entropy + Low Uniqueness + Low Speed Variance = Machine
        if (entropyYaw < 2.25) {

            if (uniqueYaw < 0.35) {
                double roboticFactor = Math.max(0.1, 1.0 - speedVariance);
                double severity = (entropyYaw < 1.5) ? 2.0 : 0.8;

                if (buffer.increase(player, severity * roboticFactor) > 10.0) {
                    flag(data, String.format("Low Entropy (Robotic). Y: %.2f, Ratio: %.2f, Var: %.2f",
                            entropyYaw, uniqueYaw, speedVariance));
                    buffer.reset(player, 5.0);
                }
            } else {
                buffer.decrease(player, 0.2);
            }
        } else {
            buffer.decrease(player, 0.5);
        }

        double entropyPitch = Statistics.getShannonEntropy(stats.pitchSamples);
        if (Math.abs(entropyYaw - entropyPitch) < 1e-7) {
            if (buffer.increase(player, 1.0) > 6.0) {
                flag(data, "Synthetic Aim (Axial Match)");
            }
        }
    }

    private void checkOutliers(Player player, PlayerData data, AimStatsData stats) {
        // Z-Score Snap Detection
        List<Double> zScores = Statistics.getZScoreOutliers(stats.yawSamples, 3.5);

        if (!zScores.isEmpty() && zScores.size() < 2) {
            boolean extreme = zScores.stream().anyMatch(z -> Math.abs(z) > 6.0);
            if (extreme) {
                if (buffer.increase(player, 1.0) > 12.0) {
                    flag(data, "Statistical Outlier (Snap)");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class AimStatsData {
        final List<Float> yawSamples = new ArrayList<>();
        final List<Float> pitchSamples = new ArrayList<>();

        void add(float yaw, float pitch) {
            yawSamples.add(yaw);
            pitchSamples.add(pitch);
            if (yawSamples.size() > WINDOW_SIZE) {
                yawSamples.remove(0);
                pitchSamples.remove(0);
            }
        }

        void reset() {
            yawSamples.clear();
            pitchSamples.clear();
        }

        boolean isFull() {
            return yawSamples.size() >= WINDOW_SIZE;
        }
    }
}