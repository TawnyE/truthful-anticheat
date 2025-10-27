package ret.tawny.truthful.version;

import org.bukkit.entity.Player;

public interface IVersionAdapter {

    double getBaseGroundSpeed(Player player);

    double getBaseAirSpeed(Player player);

    boolean isBlocking(Player player);

    int getServerVersion();
}