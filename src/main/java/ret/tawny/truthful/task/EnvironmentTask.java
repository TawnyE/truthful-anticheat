package ret.tawny.truthful.task;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.scheduler.BukkitRunnable;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;

/**
 * Scheduled task to handle heavy environment checks.
 * Optimized to run more frequently (5 ticks) for accurate proximity detection.
 */
public class EnvironmentTask extends BukkitRunnable {

    private int playerIndex = 0;

    @Override
    public void run() {
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (players.length == 0) return;

        int processed = 0;
        int limit = Math.min(players.length, 5);

        while (processed < limit) {
            if (playerIndex >= players.length) {
                playerIndex = 0;
            }

            Player player = players[playerIndex];
            playerIndex++;
            processed++;

            PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
            if (data == null) continue;

            if (Truthful.getInstance().isBedrockPlayer(player)) continue;

            boolean vehicleNearby = false;
            boolean entityNearby = false;

            // Radius 2.0 covers immediate surroundings.
            // Using a smaller radius improves performance while maintaining detection.
            for (Entity entity : player.getNearbyEntities(2.0, 2.0, 2.0)) {

                // Skip self
                if (entity.getEntityId() == player.getEntityId()) continue;

                // Vehicle check
                if (entity instanceof Vehicle) {
                    vehicleNearby = true;
                }
                // General Entity / Boat check (Boats are sometimes not instanceof Vehicle in older APIs or weird forks)
                else if (entity.getType().name().contains("BOAT") || entity.getType().name().contains("MINECART")) {
                    vehicleNearby = true;
                }

                // Living entity check (for phasing/crowding)
                if (entity.getType().isAlive()) {
                    entityNearby = true;
                }

                if (vehicleNearby && entityNearby) {
                    break;
                }
            }

            // Ensure vehicle state is true if they are actually riding one
            if (player.isInsideVehicle()) {
                vehicleNearby = true;
            }

            data.setNearVehicle(vehicleNearby);
            data.setNearEntity(entityNearby);
        }
    }
}