package ret.tawny.truthful.checks.impl.world.scaffold;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.Kinematics;
import ret.tawny.truthful.utils.math.MathHelper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@CheckData(order = 'G', type = CheckType.SCAFFOLD)
@SuppressWarnings("unused")
public final class ScaffoldG extends Check {

    private final Map<UUID, PlayerScaffoldData> scaffoldDataMap = new HashMap<>();
    private static final long GCD_CONSTANT = (long) Math.pow(2, 24);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isTeleportTick()) return;

        // --- Heuristic 1: Movement Vector Analysis (Bridging) ---
        if (data.getTicksInAir() == 1 && data.isLastGround()) {
            double angle = Kinematics.getStrafeAngle(data.getLastLocation(), data.getDeltaX(), data.getDeltaZ());

            // Increased angle to be much more lenient for diagonal bridging.
            if (angle > 150.0 && data.getDeltaXZ() > 0.2) {
                flag(data, "Bridged at an impossible angle. Angle: " + String.format("%.2f", angle));
            }
        }

        // --- Heuristic 2: GCD Aim Analysis ---
        if (data.getDeltaYaw() > 0 && data.getDeltaXZ() > 0.1) {
            PlayerScaffoldData scaffoldData = scaffoldDataMap.computeIfAbsent(player.getUniqueId(), id -> new PlayerScaffoldData());
            scaffoldData.addSample(data.getDeltaYaw());

            if (scaffoldData.isFull()) {
                long totalGcd = 0;
                for (long sample : scaffoldData.samples) {
                    totalGcd = MathHelper.gcd(totalGcd, sample);
                }
                double finalGcd = (double) totalGcd / GCD_CONSTANT;

                if (finalGcd > 0.05) {
                    scaffoldData.violations++;
                    if (scaffoldData.violations > 3) {
                        flag(data, "Detected non-human aim patterns (GCD). Value: " + String.format("%.4f", finalGcd));
                    }
                } else {
                    scaffoldData.violations = Math.max(0, scaffoldData.violations - 1);
                }
                scaffoldData.samples.clear();
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        scaffoldDataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class PlayerScaffoldData {
        private final Deque<Long> samples = new ArrayDeque<>();
        private int violations = 0;
        void addSample(float deltaYaw) {
            if (samples.size() >= 40) samples.removeFirst();
            samples.addLast((long) (deltaYaw * GCD_CONSTANT));
        }
        boolean isFull() {
            return samples.size() == 40;
        }
    }
}