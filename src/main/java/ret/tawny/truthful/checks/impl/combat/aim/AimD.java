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

@CheckData(order = 'D', type = CheckType.AIM)
public final class AimD extends Check {

    private final CheckBuffer buffer = new CheckBuffer(12.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper movePacketWrapper) {
        if (!movePacketWrapper.isRotationUpdate())
            return;

        final PlayerData playerData = Truthful.getInstance().getDataManager()
                .getPlayerData(movePacketWrapper.getPlayer());

        if (playerData == null)
            return;

        final Player player = movePacketWrapper.getPlayer();

        if (playerData.isRotationExempt() || playerData.isServerFrozen() ||
                playerData.getTickFreezeGraceTicks() > 0 || playerData.isInsideVehicle() ||
                playerData.isExempt(ret.tawny.truthful.data.ExemptionType.WIND_CHARGE)) {
            return;
        }

        final float deltaYaw = Math.abs(playerData.getDeltaYaw());
        final float deltaPitch = Math.abs(playerData.getDeltaPitch());
        final float yawAccel = Math.abs(deltaYaw - Math.abs(playerData.getLastDeltaYaw()));
        final float pitchAccel = Math.abs(deltaPitch - Math.abs(playerData.getLastDeltaPitch()));

        if (deltaYaw > 1.0F && deltaPitch > 1.0F && yawAccel < 1.0E-3F && pitchAccel < 1.0E-3F) {
            if (buffer.increase(player, 0.25) > 8.0) {
                flag(playerData, String.format("Perfect Accel. Y: %.5f, P: %.5f", yawAccel, pitchAccel));
                buffer.reset(player, 4.0);
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