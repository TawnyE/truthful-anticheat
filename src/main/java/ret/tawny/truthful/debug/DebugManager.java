package ret.tawny.truthful.debug;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.commands.impl.CommandManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DebugManager {
    private final Map<UUID, List<PacketLog>> recordingData = new HashMap<>();
    private final Map<UUID, String> debuggingChecks = new ConcurrentHashMap<>();

    public boolean isRecording(Player player) {
        return recordingData.containsKey(player.getUniqueId());
    }

    public void startRecording(Player player) {
        recordingData.put(player.getUniqueId(), new ArrayList<>());
    }

    public void stopRecording(Player player) {
        List<PacketLog> logs = recordingData.remove(player.getUniqueId());
        if (logs != null) {
            saveRecording(player, logs);
        }
    }

    public void log(Player player, PacketReceiveEvent event) {
        if (isRecording(player)) {
            recordingData.get(player.getUniqueId()).add(new PacketLog(event));
        }
    }

    public void log(Player player, PacketSendEvent event) {
        if (isRecording(player)) {
            recordingData.get(player.getUniqueId()).add(new PacketLog(event));
        }
    }

    private void saveRecording(Player player, List<PacketLog> logs) {
        File dataFolder = Truthful.getInstance().getPlugin().getDataFolder();
        File recordingsFolder = new File(dataFolder, "recordings");
        if (!recordingsFolder.exists()) {
            recordingsFolder.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = String.format("%s_%s_%s.csv", player.getName(), player.getUniqueId(), timestamp);
        File recordingFile = new File(recordingsFolder, fileName);

        try (FileWriter writer = new FileWriter(recordingFile)) {
            writer.write("timestamp,direction,packetName,packetDetails\n");
            for (PacketLog log : logs) {
                writer.write(log.toString() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- LIVE DEBUGGING SYSTEM ---

    public boolean isDebuggingCheck(Player player) {
        return debuggingChecks.containsKey(player.getUniqueId());
    }

    public String getDebuggingCheck(Player player) {
        return debuggingChecks.get(player.getUniqueId());
    }

    public void startDebuggingCheck(Player player, String checkName) {
        debuggingChecks.put(player.getUniqueId(), checkName);
    }

    public void stopDebuggingCheck(Player player) {
        debuggingChecks.remove(player.getUniqueId());
    }

    /**
     * Sends a Gray/Verbose message to the Action Bar.
     * Updates live without spamming chat history.
     */
    public void sendVerbose(String checkName, String info) {
        String text = "§8[§7" + checkName + "§8] §f" + info;

        // Convert legacy color codes for Adventure/PacketEvents
        Component component = LegacyComponentSerializer.legacySection().deserialize(text);
        WrapperPlayServerActionBar packet = new WrapperPlayServerActionBar(component);

        for (UUID uuid : CommandManager.debuggers) {
            Player debugger = Bukkit.getPlayer(uuid);
            if (debugger != null) {
                String focus = debuggingChecks.get(uuid);
                // Only send if they are debugging THIS check (or ALL)
                if (focus != null && (focus.equalsIgnoreCase("ALL") || focus.contains(checkName))) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(debugger, packet);
                }
            }
        }
    }

    /**
     * Sends a Yellow/Warning message to Chat.
     * Indicates buffer increase or suspicion.
     */
    public void sendSuspicion(String checkName, String info, double buffer) {
        String message = "§8[§e" + checkName + "§8] §e" + info + " §7(Buf: " + String.format("%.1f", buffer) + ")";

        for (UUID uuid : CommandManager.debuggers) {
            Player debugger = Bukkit.getPlayer(uuid);
            if (debugger != null) {
                String focus = debuggingChecks.get(uuid);
                if (focus != null && (focus.equalsIgnoreCase("ALL") || focus.contains(checkName))) {
                    debugger.sendMessage(message);
                }
            }
        }
    }
}