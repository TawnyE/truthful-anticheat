package org.voitac.anticheat.checks.impl.combat.aim;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'B', type = CheckType.AIM)
public final class RotationB extends Check {

    private final Map<Player, Double> buffer = new HashMap<>();

    @Override
    public void onRelMove(final RelMovePacketWrapper movePacketWrapper) {
        if(!movePacketWrapper.isRotationUpdate())
            return;
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(movePacketWrapper.getPlayer());

        if(playerData == null)
            return;

        final float deltaYaw = Math.abs(playerData.getDeltaYaw());
        final float deltaPitch = Math.abs(playerData.getDeltaPitch());
        final float yawAccel = Math.abs(deltaYaw - Math.abs(playerData.getLastDeltaYaw()));
        final float pitchAccel = Math.abs(deltaPitch - Math.abs(playerData.getLastDeltaPitch()));

        if(deltaYaw > 1.0F && deltaPitch > 1.0F && yawAccel < 1.0E-3F && pitchAccel < 1.0E-3F) {
            final double current = buffer.getOrDefault(movePacketWrapper.getPlayer(), 0.0D) + 1.0D;
            buffer.put(movePacketWrapper.getPlayer(), current);

            if(current > 8.0D) {
                formattedFlag(this, playerData, "Perfect rotation acceleration");
                buffer.put(movePacketWrapper.getPlayer(), 4.0D);
            }
        }else {
            buffer.put(movePacketWrapper.getPlayer(), Math.max(0.0D, buffer.getOrDefault(movePacketWrapper.getPlayer(), 0.0D) - 1.5D));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
