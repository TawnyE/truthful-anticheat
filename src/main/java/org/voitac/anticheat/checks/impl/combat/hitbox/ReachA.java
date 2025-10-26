package org.voitac.anticheat.checks.impl.combat.hitbox;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.player.PlayerUtils;

import java.util.HashMap;
import java.util.Map;

@CheckData(order = 'A', type = CheckType.HITBOX)
public final class ReachA extends Check {

    private final Map<Player, Double> buffer = new HashMap<>();

    @EventHandler
    public void onAttack(final EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Player))
            return;

        final Player player = (Player) event.getDamager();
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);

        if(playerData == null)
            return;

        final Entity target = event.getEntity();
        final double horizontal = PlayerUtils.getDistanceHz(playerData, target);
        final double vertical = Math.abs(PlayerUtils.getDistanceVert(playerData, target));

        double maxReach = player.isSprinting() ? 3.4D : 3.1D;
        maxReach += Math.min(0.6D, playerData.getPing() * 0.0025D);

        if(vertical > 1.6D)
            maxReach += 0.2D;

        if(horizontal > maxReach) {
            double current = buffer.getOrDefault(player, 0.0D) + (horizontal - maxReach);
            if(current > 3.0D) {
                formattedFlag(this, playerData, String.format("Reach %.3f > %.3f", horizontal, maxReach));
                current = 1.5D;
            }
            buffer.put(player, current);
        }else {
            buffer.put(player, Math.max(0.0D, buffer.getOrDefault(player, 0.0D) - 0.5D));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
