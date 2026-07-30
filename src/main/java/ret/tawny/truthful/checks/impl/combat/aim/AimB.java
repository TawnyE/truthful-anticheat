// FILE PATH: .\src\main\java\ret\tawny\truthful\checks\impl\combat\aim\AimB.java

package ret.tawny.truthful.checks.impl.combat.aim;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'B', type = CheckType.AIM)
@SuppressWarnings("unused")
public final class AimB extends Check {

    private static final float HEAD_SNAP_THRESHOLD = 50.0F; // Relaxed from generic values
    private static final float SMOOTH_ACCEL_THRESHOLD = 0.001F;
    private static final int SNAP_PATTERN_THRESHOLD = 5; // Increased requirement

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final CheckBuffer snapBuffer = new CheckBuffer(5.0);

    private final Map<UUID, Integer> snapCounts = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper event) {
        if (!event.isRotationUpdate())
            return;

        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isRotationExempt())
            return;

        if (data.isInsideVehicle()) {
            snapBuffer.decrease(player, 0.5);
            buffer.decrease(player, 0.5);
            return;
        }

        if (data.hasVelocity() || data.isExempt(ExemptionType.VELOCITY)) {
            snapBuffer.decrease(player, 0.5);
            return;
        }

        if (Math.abs(data.getPitch()) > 89.0f) {
            snapBuffer.decrease(player, 0.5);
            return;
        }

        // Combat Context: Only check if attacking
        if (data.getTicksTracked() - data.getLastHitTick() > 40) {
            snapBuffer.decrease(player, 0.5);
            snapCounts.remove(player.getUniqueId());
            return;
        }

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            snapBuffer.decrease(player, 0.5);
            return;
        }

        float deltaYaw = data.getRotationDeviation(false);
        float deltaPitch = data.getRotationDeviation(true);
        float lastDeltaYaw = Math.abs(data.getLastDeltaYaw());
        float accelYaw = Math.abs(deltaYaw - lastDeltaYaw);

        // === CHECK 1: HEAD SNAP ===
        // Detects instant large rotations (Snapping to target) repeated frequently
        if (deltaYaw > HEAD_SNAP_THRESHOLD && deltaPitch > 0.5F) {
            snapCounts.merge(player.getUniqueId(), 1, Integer::sum);
            int consecutiveSnaps = snapCounts.get(player.getUniqueId());

            if (consecutiveSnaps >= SNAP_PATTERN_THRESHOLD) {
                if (snapBuffer.increase(player, 2.0) > 6.0) {
                    flag(data, String.format("Head Snap Pattern. Yaw: %.1f, Snaps: %d", deltaYaw, consecutiveSnaps));
                    snapBuffer.reset(player, 2.0);
                    snapCounts.put(player.getUniqueId(), 0);
                }
            }
        } else {
            // Decay count if valid movement occurs
            snapCounts.computeIfPresent(player.getUniqueId(), (k, v) -> Math.max(0, v - 1));
            snapBuffer.decrease(player, 0.1);
        }

        // === CHECK 2: SMOOTH AIM ===
        if (data.isExempt(ExemptionType.WIND_CHARGE)) {
            return;
        }

        // Only check for smooth aim if moving significantly
        if (deltaYaw > 3.0F && deltaPitch > 0.1F) {
            // "Cinematic Camera" check: extremely low acceleration on both axes
            if (accelYaw < SMOOTH_ACCEL_THRESHOLD) {
                if (buffer.increase(player, 0.5) > 12.0) {
                    flag(data, String.format("Smooth Aim. AccelY: %.5f", accelYaw));
                }
            } else {
                buffer.decrease(player, 0.25);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        snapBuffer.remove(event.getPlayer());
        snapCounts.remove(event.getPlayer().getUniqueId());
    }
}