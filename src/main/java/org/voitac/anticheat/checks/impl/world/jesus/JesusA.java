package org.voitac.anticheat.checks.impl.world.jesus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.world.WorldUtils;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.HashMap;
import java.util.Map;

@CheckData(order = 'A', type = CheckType.JESUS)
public final class JesusA extends Check {

    private final Map<Player, Double> buffer = new HashMap<>();

    @Override
    public void onRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        if(!relMovePacketWrapper.isPositionUpdate())
            return;

        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);

        if(playerData == null)
            return;

        if(player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle())
            return;

        if(!playerData.isInLiquid()) {
            buffer.put(player, Math.max(0.0D, buffer.getOrDefault(player, 0.0D) - 1.0D));
            return;
        }

        if(player.isSwimming())
            return;

        final double deltaY = playerData.getDeltaY();

        if(deltaY > -0.02D && deltaY < 0.05D && playerData.getTicksInAir() > 6 && !WorldUtils.nearBlock(player)) {
            double current = buffer.getOrDefault(player, 0.0D) + 1.0D;
            if(current > 5.0D) {
                formattedFlag(this, playerData, String.format("Liquid travel %.3f", deltaY));
                current = 2.5D;
            }
            buffer.put(player, current);
        }else {
            buffer.put(player, Math.max(0.0D, buffer.getOrDefault(player, 0.0D) - 0.75D));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
