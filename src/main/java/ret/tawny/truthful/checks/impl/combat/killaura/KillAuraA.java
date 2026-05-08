package ret.tawny.truthful.checks.impl.combat.killaura;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

/**
 * KillAuraA: Zero-Reaction Tracking
 *
 * Logic:
 * Detects aim that tracks a target's movement PERFECTLY instantly.
 * Humans have ~150-200ms reaction time.
 */
@CheckData(order = 'A', type = CheckType.KILLAURA)
public final class KillAuraA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(20.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null)
            return;

        Entity target = data.getLastTarget();
        if (target == null || data.getTicksTracked() - data.getLastHitTick() > 10) {
            buffer.decrease(player, 0.5);
            return;
        }

        // Capture data for main thread
        float startYaw = data.getYaw();
        double fromX = data.getX();
        double fromZ = data.getZ();

        // Schedule check on main thread to access Entity API safely
        Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), () -> {
            if (!player.isOnline() || !target.isValid())
                return;

            Vector targetVel = target.getVelocity();
            // Ignore stationary targets
            if (targetVel.lengthSquared() < 0.08) {
                buffer.decrease(player, 0.2);
                return;
            }

            // Logic:
            // 1. Calculate ideal Yaw to look at target
            Location targetLoc = target.getLocation();
            double dx = targetLoc.getX() - fromX;
            double dz = targetLoc.getZ() - fromZ;
            float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;

            // 2. Difference between current Yaw and Ideal Yaw
            float diff = startYaw - idealYaw;
            diff = diff % 360.0F;
            if (diff >= 180.0F)
                diff -= 360.0F;
            if (diff < -180.0F)
                diff += 360.0F;
            float offset = Math.abs(diff);

            // 3. Perfect Tracking check
            // Humans naturally have jitter and delay. Robotic aim tracks the center too perfectly.
            if (offset < 1.5) {
                // Check for "Artificial Smoothness" or "Robotic Locking"
                // If they track within 1.5 degrees consistently while the target is moving, it's robotic.
                if (buffer.increase(player, 1.2) > 12.0) {
                    flag(data, String.format("Robotic Tracking. Offset: %.2f, TargetVel: %.3f", offset, targetVel.length()));
                    data.executeLagback();
                    buffer.reset(player, 6.0);
                }
            } else if (offset < 3.5) {
                // Small increase for suspicious tracking
                buffer.increase(player, 0.25);
            } else {
                // Decelerate if aiming normally
                buffer.decrease(player, 0.75);
            }
        });
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}