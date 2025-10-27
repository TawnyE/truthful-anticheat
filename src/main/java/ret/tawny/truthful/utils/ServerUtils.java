package ret.tawny.truthful.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ret.tawny.truthful.data.PlayerData;

public final class ServerUtils {
    private ServerUtils(){}

    public static void kick(final Player player, final String reason) {
        player.kickPlayer(reason);
    }

    public static void ban(final Player player, final String reason) {
        Bukkit.getServer().banIP(player.getAddress().getHostString());
    }

    /**
     *
     * @return Rounded tick delay from a player based on ping, eg 130 ping would be 2 ticks behind, or 270 would be 5 ticks behind
     */
    public static int getTickDelay(final PlayerData playerData) {
        return Math.round(playerData.getPing() / 50.0F);
    }
}
