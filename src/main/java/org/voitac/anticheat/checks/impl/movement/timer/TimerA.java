package org.voitac.anticheat.checks.impl.movement.timer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.HashMap;
import java.util.Map;

@CheckData(order = 'A', type = CheckType.TIMER)
public final class TimerA extends Check {

    private final Map<Player, Long> lastFlying = new HashMap<>();
    private final Map<Player, Double> buffer = new HashMap<>();

    @Override
    public void onRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if(!relMovePacketWrapper.isPositionUpdate())
            return;

        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);

        if(playerData == null)
            return;

        if(playerData.isTeleportTick())
            return;

        final long now = System.currentTimeMillis();
        final long last = lastFlying.getOrDefault(player, now);
        lastFlying.put(player, now);

        if(last == now)
            return;

        final long delay = now - last;

        if(delay < 45L && playerData.getPing() < 225L) {
            double current = buffer.getOrDefault(player, 0.0D) + (45.0D - delay) * 0.25D;
            if(current > 10.0D) {
                formattedFlag(this, playerData, "Packets arriving too quickly");
                current = 5.0D;
            }
            buffer.put(player, current);
        }else {
            buffer.put(player, Math.max(0.0D, buffer.getOrDefault(player, 0.0D) - 1.0D));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        lastFlying.remove(player);
        buffer.remove(player);
    }
}
