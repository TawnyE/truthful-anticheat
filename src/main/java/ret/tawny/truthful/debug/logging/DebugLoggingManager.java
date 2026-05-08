package ret.tawny.truthful.debug.logging;

import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugLoggingManager {

    private final Map<UUID, Boolean> loggingPlayers = new ConcurrentHashMap<>();
    private final File logDir;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    public DebugLoggingManager() {
        this.logDir = new File(Truthful.getInstance().getPlugin().getDataFolder(), "debug_logs");
        if (!this.logDir.exists()) {
            this.logDir.mkdirs();
        }
    }

    public boolean isLogging(Player player) {
        return loggingPlayers.containsKey(player.getUniqueId());
    }

    public void toggleLogging(Player admin, Player target) {
        UUID targetUUID = target.getUniqueId();
        if (isLogging(target)) {
            loggingPlayers.remove(targetUUID);
            admin.sendMessage("§8[§bTruthful§8] §cStopped debug logging for " + target.getName());
        } else {
            loggingPlayers.put(targetUUID, true);
            admin.sendMessage("§8[§bTruthful§8] §aStarted debug logging for " + target.getName() + " to " + target.getName() + ".log");
        }
    }

    public void logFlag(Player player, String checkName, int vl, String debug) {
        if (!isLogging(player)) return;

        File logFile = new File(logDir, player.getName() + ".log");
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = dateFormat.format(new Date());
                out.println("[" + timestamp + "] " + checkName + " (VL: " + vl + ") | " + debug);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
