package ret.tawny.truthful.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.block.BlockBreakEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;

import java.util.Collection;
import java.util.List;

public final class CheckListener implements Listener {

    public CheckListener() {
        Bukkit.getPluginManager().registerEvents(this, Truthful.getInstance().getPlugin());
    }

    /**
     * Dispatches combat events to checks.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Truthful.getInstance().getCompensationTracker().handleEntitySnapshot(event.getEntity());
        }

        final List<Check> checks = Truthful.getInstance().getCheckManager().getAttackChecks();
        for (Check check : checks) {
            if (check.isEnabled()) {
                check.onAttack(event);
            }
        }
    }

    /**
     * Dispatches block break events.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final List<Check> checks = Truthful.getInstance().getCheckManager().getBlockBreakChecks();
        for (Check check : checks) {
            if (check.isEnabled()) {
                check.onBlockBreak(event);
            }
        }
    }

    /**
     * Dispatches vehicle movement.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(final VehicleMoveEvent event) {
        final List<Check> checks = Truthful.getInstance().getCheckManager().getVehicleMoveChecks();
        for (Check check : checks) {
            if (check.isEnabled()) {
                check.onVehicleMove(event);
            }
        }
    }

    /**
     * Handles per-check data cleanup when a player leaves.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(final PlayerQuitEvent event) {
        // Base cleanup must run for all checks
        final Collection<Check> allChecks = Truthful.getInstance().getCheckManager().getCollection();
        for (Check check : allChecks) {
            check.handleQuitBase(event.getPlayer());
        }

        // Specific cleanup only for checks that override onQuit
        final List<Check> checks = Truthful.getInstance().getCheckManager().getQuitChecks();
        for (Check check : checks) {
            check.onQuit(event);
        }
    }
}
