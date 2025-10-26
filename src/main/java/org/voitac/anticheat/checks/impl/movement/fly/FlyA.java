package org.voitac.anticheat.checks.impl.movement.fly;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.player.PlayerUtils;
import org.voitac.anticheat.utils.world.WorldUtils;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.HashMap;
import java.util.Map;

@CheckData(order = 'A', type = CheckType.FLY)
public final class FlyA extends Check {

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

        if(playerData.isTeleportTick())
            return;

        if(!playerData.getVelocities().isEmpty())
            return;

        if(playerData.isInLiquid() || WorldUtils.hasClimbableNearby(player))
            return;

        if(PlayerUtils.getPotion(PotionEffectType.SLOW_FALLING, playerData) != null)
            return;

        final double deltaY = playerData.getDeltaY();
        final double predicted = (playerData.getLastDeltaY() - PlayerUtils.OFFSET) * PlayerUtils.GRAVITY;
        final double diff = Math.abs(deltaY - predicted);

        if(playerData.getTicksInAir() > 8 && deltaY > -0.075D && diff > 0.05D && !WorldUtils.nearBlock(player)) {
            double current = buffer.getOrDefault(player, 0.0D) + 1.0D;
            if(current > 6.0D) {
                formattedFlag(this, playerData, String.format("Hover %.3f off %.3f", deltaY, predicted));
                current = 3.0D;
            }
            buffer.put(player, current);
        }else {
            buffer.put(player, Math.max(0.0D, buffer.getOrDefault(player, 0.0D) - 1.0D));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
