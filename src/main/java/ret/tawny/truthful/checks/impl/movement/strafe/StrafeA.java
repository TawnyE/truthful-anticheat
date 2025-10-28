package ret.tawny.truthful.checks.impl.movement.strafe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.math.Kinematics;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.STRAFE)
@SuppressWarnings("unused")
public final class StrafeA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(30.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isOnGround() || data.getTicksInAir() < 5 || data.isTeleportTick()) {
            return;
        }

        // EXEMPTION: Ignore the upward arc of a jump, where A/D spam is common.
        if (data.getDeltaY() > 0) {
            return;
        }

        double angle = Kinematics.getStrafeAngle(data.getLocation(), data.getDeltaX(), data.getDeltaZ());
        float deltaYaw = Math.abs(data.getDeltaYaw());

        // Increased angle threshold to be more lenient towards legitimate strafing.
        if (angle > 95.0 && deltaYaw < 2.0f) {
            if (buffer.increase(player, 1.0) > 30.0) {
                flag(data, String.format("Invalid air strafe. Angle: %.2f, dY: %.2f", angle, deltaYaw));
            }
        } else {
            buffer.decrease(player, 0.5);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}