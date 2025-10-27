package ret.tawny.truthful.data;

import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class DataManager {
    private final Map<Player, PlayerData> players = new HashMap<>();

    public void enter(final Player player) {
        this.players.put(player, new PlayerData(player));
    }

    public void eliminate(final Player player) {
        this.players.remove(player);
    }

    public Collection<PlayerData> getCollection() {
        return this.players.values();
    }

    public PlayerData getPlayerData(final Player key) {
        return this.players.get(key);
    }

    public Map<Player, PlayerData> getPlayers() {
        return this.players;
    }

    public PlayerData getHighestPing() {
        long highestPing = 0;
        PlayerData laggiest = null;
        for (final PlayerData playerData : this.getCollection()) {
            final long ping = playerData.getPing();
            if (ping > highestPing) {
                highestPing = ping;
                laggiest = playerData;
            }
        }
        return laggiest;
    }
}