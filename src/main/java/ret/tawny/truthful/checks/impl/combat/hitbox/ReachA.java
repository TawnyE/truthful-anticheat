package ret.tawny.truthful.checks.impl.combat.hitbox;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.player.PlayerUtils;

@CheckData(order = 'A', type = CheckType.HITBOX)
public final class ReachA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(3.0);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        final Player player = (Player) event.getDamager();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (playerData == null) return;

        final Entity target = event.getEntity();
        final double horizontal = PlayerUtils.getDistanceHz(playerData, target);

        // 1.8 has slightly longer reach
        double maxReach = Truthful.getInstance().getVersionManager().getAdapter().getServerVersion() <= 8 ? 3.2D : 3.0D;

        if (player.isSprinting()) maxReach += 0.2D;
        maxReach += (playerData.getPing() / 100.0) * 0.15; // Ping compensation

        if (horizontal > maxReach) {
            if (buffer.increase(player, horizontal - maxReach) > 3.0) {
                flag(playerData, String.format("Reach %.2f > %.2f", horizontal, maxReach));
                buffer.reset(player, 1.5);
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