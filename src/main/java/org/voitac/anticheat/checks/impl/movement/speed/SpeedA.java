package org.voitac.anticheat.checks.impl.movement.speed;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
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

@CheckData(order = 'A', type = CheckType.SPEED)
public final class SpeedA extends Check {

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

        if(player.getAllowFlight() || player.isFlying() || player.isGliding() || player.isInsideVehicle())
            return;

        if(!playerData.getVelocities().isEmpty())
            return;

        if(playerData.isInLiquid() || playerData.wasInLiquid())
            return;

        if(playerData.getTicksSinceAbility() < 3)
            return;

        double max = playerData.isOnGround() ? (player.isSprinting() ? PlayerUtils.BASE_PURE_SPRINT : PlayerUtils.BASE_PURE)
                : 0.36D;

        final PotionEffect speedEffect = PlayerUtils.hasSpeed(playerData);
        if(speedEffect != null) {
            max += 0.0573D * (speedEffect.getAmplifier() + 1);
        }

        if(WorldUtils.hasLowFrictionBelow(player)) {
            max += 0.3D;
        }

        final double horizontal = playerData.getDeltaXZ();

        if(horizontal > max) {
            double current = buffer.getOrDefault(player, 0.0D) + Math.min(1.5D, horizontal - max);
            if(current > 4.0D) {
                formattedFlag(this, playerData, String.format("Speed %.3f > %.3f", horizontal, max));
                current = 2.0D;
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
