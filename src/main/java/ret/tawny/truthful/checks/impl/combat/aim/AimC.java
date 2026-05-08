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

// TAWNY Please change this to something else this is duplicated.

@CheckData(order = 'C', type = CheckType.AIM)
@SuppressWarnings("unused")
public final class AimC extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isRotationExempt() || player.getGameMode().equals(org.bukkit.GameMode.SPECTATOR) ||
                data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            return;
        }

        float pitch = data.getPitch();

        if (Math.abs(pitch) > 90.0) {
            if (buffer.increase(player, 1.0) > 1.0) {
                flag(data, "Invalid Pitch: " + pitch);
            }
        } else {
            buffer.decrease(player, 0.1);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
