package ret.tawny.truthful.checks.impl.world;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import ret.tawny.truthful.data.PlayerData;

public final class WorldCheckSupport {
    private WorldCheckSupport() {}

    public static boolean skipBasic(PlayerData data, Player player) {
        // FIXED: Removed data.isMovementExempt().
        // Teleport/Join exemptions should not give players a free pass to use
        // Scaffold, FastBreak, or Phase exploits.
        return data == null
                || player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || data.isServerFrozen()
                || data.isInsideVehicle()
                || data.isGliding();
    }

    public static double severity(double value, double low, double med, double high) {
        if (value >= high) return 5.0D;
        if (value >= med) return 1.0D;
        return low;
    }
}