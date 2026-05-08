package ret.tawny.truthful.checks.impl.combat.killaura;

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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KillAuraC: Robotic Aim Analysis
 *
 * Logic:
 * 1. Snap & Stop: Humans decelerate. Machines stop instantly.
 * 2. Linear Aim: Humans shake. Machines move in perfect straight lines.
 */
@CheckData(order = 'C', type = CheckType.KILLAURA)
public final class KillAuraC extends Check {

    private final CheckBuffer buffer = new CheckBuffer(12.0);
    private final Map<UUID, Integer> constantAimMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper event) {
        if (!event.isRotationUpdate())
            return;

        final Player player = event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isExempt())
            return;

        // Only analyze aim during or immediately after combat
        if (data.getTicksTracked() - data.getLastHitTick() > 20) {
            constantAimMap.remove(player.getUniqueId());
            return;
        }

        float deltaYaw = Math.abs(data.getDeltaYaw());
        float deltaPitch = Math.abs(data.getDeltaPitch());
        float lastDeltaYaw = Math.abs(data.getLastDeltaYaw());
        float lastDeltaPitch = Math.abs(data.getLastDeltaPitch());

        // === CHECK 1: SNAP & STOP ===
        // Large rotation (> 30 deg) followed immediately by < 0.1 deg rotation.
        // This indicates the KillAura "Locked on", hit, and then stopped aiming.
        boolean snapAndStop = lastDeltaYaw > 30.0 && deltaYaw < 0.1 && deltaYaw > 0.0;

        if (snapAndStop) {
            // Buffer increased slowly to avoid flagging legit "flicks" too easily
            if (buffer.increase(player, 2.0) > 8.0) {
                flag(data, String.format("Snap Aim. Last: %.1f, Now: %.1f", lastDeltaYaw, deltaYaw));
                data.executeLagback();
                buffer.reset(player, 4.0);
            }
        }

        // === CHECK 2: LINEAR AIM (Robotic Smoothness) ===
        // If the speed of rotation is identical for multiple ticks (Variance < 0.001).
        // Human mice always have micro-jitter (e.g., 5.12 -> 5.09 -> 5.15).
        // Machines do (5.00 -> 5.00 -> 5.00).

        float yawDiff = Math.abs(deltaYaw - lastDeltaYaw);
        float pitchDiff = Math.abs(deltaPitch - lastDeltaPitch);

        // Only check if moving mouse fast enough (> 1.5 deg)
        if (deltaYaw > 1.5 && deltaPitch > 1.5) {
            if (yawDiff < 0.005 && pitchDiff < 0.005) {
                int aimTicks = constantAimMap.getOrDefault(player.getUniqueId(), 0) + 1;
                constantAimMap.put(player.getUniqueId(), aimTicks);

                // 3 ticks of perfect linearity is extremely suspicious
                if (aimTicks > 3) {
                    if (buffer.increase(player, 1.5) > 7.0) {
                        flag(data, String.format("Robotic Aim (Linear). Yaw: %.2f", deltaYaw));
                        data.executeLagback();
                        buffer.reset(player, 3.0);
                    }
                }
            } else {
                // Decay
                constantAimMap.computeIfPresent(player.getUniqueId(), (k, v) -> v - 1 <= 0 ? null : v - 1);
            }
        } else {
            constantAimMap.remove(player.getUniqueId());
        }

        // Decay overall buffer
        buffer.decrease(player, 0.1);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        constantAimMap.remove(event.getPlayer().getUniqueId());
    }
}