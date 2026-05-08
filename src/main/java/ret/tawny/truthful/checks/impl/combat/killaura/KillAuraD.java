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

/**
 * KillAuraD: Silent / Packet Rotation / Pitch Lock
 */
@CheckData(order = 'D', type = CheckType.KILLAURA)
public final class KillAuraD extends Check {

    private final CheckBuffer buffer = new CheckBuffer(15.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null)
            return;

        if (data.getTicksTracked() - data.getLastHitTick() > 10) {
            buffer.decrease(player, 0.5);
            return;
        }

        float deltaYaw = Math.abs(data.getDeltaYaw());
        float deltaPitch = Math.abs(data.getDeltaPitch());

        // FIX: Pitch limit exemption
        // Looking straight down (90) or up (-90) often locks pitch at the limit.
        if (Math.abs(data.getPitch()) > 85.0f) {
            buffer.decrease(player, 0.25);
            return;
        }

        // 1. PITCH LOCK
        // Detects rotation where Yaw changes significantly, but Pitch is perfectly
        // static.
        // Human hands create micro-movements in Pitch when swiping Yaw.

        // Increased threshold to 15.0 to filter out legit horizontal swipes.
        if (deltaYaw > 15.0 && deltaPitch == 0.0) {
            if (buffer.increase(player, 1.5) > 10.0) {
                flag(data, String.format("Silent/Pitch Lock. dYaw: %.2f, dPitch: %.5f", deltaYaw, deltaPitch));
                data.executeLagback();
                buffer.reset(player, 5.0);
            }
            return;
        }

        // 2. DIRECTION SNAP
        // Pitch changes largely, but Yaw is static. Less common in Killaura but
        // possible in "Legit Aura".
        // Threshold: Pitch > 10, Yaw < 0.01
        if (deltaPitch > 10.0 && deltaYaw < 0.01 && deltaYaw > 0.0) {
            if (buffer.increase(player, 1.0) > 12.0) {
                flag(data, String.format("Vertical Snap. dPitch: %.2f", deltaPitch));
                data.executeLagback();
            }
        } else {
            buffer.decrease(player, 0.25);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}