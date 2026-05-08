package ret.tawny.truthful.version;

import org.bukkit.entity.Player;

public interface IVersionAdapter {

    double getBaseGroundSpeed(Player player);

    double getBaseAirSpeed(Player player);

    boolean isBlocking(Player player);

    /**
     * Gets the player's maximum entity interaction range.
     */
    double getEntityInteractionRange(Player player);

    /**
     * Gets the player's maximum block interaction range.
     * Supports 1.21+ Attributes.
     */
    double getBlockInteractionRange(Player player);

    int getServerVersion();
}