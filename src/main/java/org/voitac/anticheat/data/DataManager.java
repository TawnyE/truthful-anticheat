package org.voitac.anticheat.data;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;

public final class DataManager {
    private final HashMap<Player, PlayerData> players = new HashMap<>();

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

    public HashMap<Player, PlayerData> players() {
        return this.players;
    }

    public PlayerData getHighestPing() {
        long highestPing = 0;
        PlayerData laggiest = null;
        for(final PlayerData playerData : this.getCollection()) {
            final long ping = playerData.getPing();
            if(playerData.getPing() > highestPing) {
                highestPing = ping;
                laggiest = playerData;
            }
        }
        return laggiest;
    }
}
