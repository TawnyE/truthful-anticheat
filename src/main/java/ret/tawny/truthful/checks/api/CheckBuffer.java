package ret.tawny.truthful.checks.api;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CheckBuffer {
    private final Map<UUID, Double> bufferMap = new HashMap<>();
    private final double max;

    public CheckBuffer(double max) {
        this.max = max;
    }

    public double increase(Player player, double amount) {
        double current = bufferMap.getOrDefault(player.getUniqueId(), 0.0) + amount;
        bufferMap.put(player.getUniqueId(), current);
        return current;
    }

    public void decrease(Player player, double amount) {
        double current = bufferMap.getOrDefault(player.getUniqueId(), 0.0);
        bufferMap.put(player.getUniqueId(), Math.max(0, current - amount));
    }

    public boolean exceedsMax(Player player) {
        return bufferMap.getOrDefault(player.getUniqueId(), 0.0) > max;
    }

    public void reset(Player player, double value) {
        bufferMap.put(player.getUniqueId(), value);
    }

    public void remove(Player player) {
        bufferMap.remove(player.getUniqueId());
    }
}