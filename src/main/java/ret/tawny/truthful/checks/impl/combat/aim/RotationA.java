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

@CheckData(order = 'A', type = CheckType.AIM)
@SuppressWarnings("unused")
public final class RotationA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper movePacketWrapper) {
        if (!movePacketWrapper.isRotationUpdate())
            return;

        final Player player = movePacketWrapper.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (playerData == null) return;

        boolean suspicious = Math.abs(playerData.getLastDeltaYaw()) > 35.0F && Math.abs(playerData.getDeltaYaw()) < 0.5F;

        if (suspicious) {
            if (buffer.increase(player, 1.0) > 5.0) {
                flag(playerData, "Snapped rotation without easing (Pattern Detected)");
                buffer.reset(player, 2.0);
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