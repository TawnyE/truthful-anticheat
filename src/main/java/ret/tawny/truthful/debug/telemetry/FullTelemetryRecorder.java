package ret.tawny.truthful.debug.telemetry;

import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FullTelemetryRecorder {

    private static final class ActiveRecording {
        final CommandSender initiator;
        final Player target;
        final int durationTicks;
        final int startTick;
        final List<JsonObject> frames = new ArrayList<>();
        final File outputFile;

        ActiveRecording(CommandSender initiator, Player target, int durationTicks, int startTick, File outputFile) {
            this.initiator = initiator;
            this.target = target;
            this.durationTicks = durationTicks;
            this.startTick = startTick;
            this.outputFile = outputFile;
        }
    }

    private final ConcurrentHashMap<UUID, ActiveRecording> activeRecordings = new ConcurrentHashMap<>();

    public boolean isRecording(Player target) {
        return activeRecordings.containsKey(target.getUniqueId());
    }

    public void startRecording(CommandSender initiator, Player target, int durationSeconds) {
        int durationTicks = durationSeconds * 20;
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        int currentTick = data != null ? data.getTicksTracked() : 0;

        File folder = new File(Truthful.getInstance().getPlugin().getDataFolder(), "recordings/telemetry");
        if (!folder.exists()) folder.mkdirs();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File outputFile = new File(folder, String.format("%s_%s.jsonl", target.getName(), timestamp));

        ActiveRecording rec = new ActiveRecording(initiator, target, durationTicks, currentTick, outputFile);
        activeRecordings.put(target.getUniqueId(), rec);

        initiator.sendMessage(String.format("§aStarted AI Telemetry Recording for §f%s §a(%ds) -> %s",
                target.getName(), durationSeconds, outputFile.getName()));
    }

    public void stopRecording(Player target) {
        ActiveRecording rec = activeRecordings.remove(target.getUniqueId());
        if (rec != null) {
            flushRecording(rec);
            rec.initiator.sendMessage(String.format("§aStopped AI Telemetry Recording for §f%s§a. Saved %d frames.",
                    target.getName(), rec.frames.size()));
        }
    }

    public void recordTick(PlayerData data) {
        if (data == null || data.getPlayer() == null) return;
        Player player = data.getPlayer();
        ActiveRecording rec = activeRecordings.get(player.getUniqueId());
        if (rec == null) return;

        int ticksElapsed = data.getTicksTracked() - rec.startTick;
        if (ticksElapsed > rec.durationTicks) {
            stopRecording(player);
            return;
        }

        JsonObject frame = new JsonObject();
        frame.addProperty("tick", data.getTicksTracked());
        frame.addProperty("timestamp", System.currentTimeMillis());
        frame.addProperty("x", data.getX());
        frame.addProperty("y", data.getY());
        frame.addProperty("z", data.getZ());
        frame.addProperty("deltaX", data.getDeltaX());
        frame.addProperty("deltaY", data.getDeltaY());
        frame.addProperty("deltaZ", data.getDeltaZ());
        frame.addProperty("deltaXZ", data.getDeltaXZ());

        frame.addProperty("yaw", data.getYaw());
        frame.addProperty("pitch", data.getPitch());
        frame.addProperty("deltaYaw", data.getDeltaYaw());
        frame.addProperty("deltaPitch", data.getDeltaPitch());

        frame.addProperty("onGround", data.isOnGround());
        frame.addProperty("clientGround", data.isClientGround());
        frame.addProperty("serverGround", data.isServerGround());
        frame.addProperty("airTicks", data.getAirTicks());
        frame.addProperty("groundTicks", data.getGroundTicks());

        frame.addProperty("sprinting", data.isSprinting());
        frame.addProperty("sneaking", data.isSneaking());
        frame.addProperty("usingItem", data.isUsingItem());
        frame.addProperty("gliding", data.isGliding());
        frame.addProperty("inLiquid", data.isInLiquid());
        frame.addProperty("onClimbable", data.isOnClimbable());

        frame.addProperty("ping", data.getPing());
        frame.addProperty("timerBalance", data.getTransactionTimerBalance());
        frame.addProperty("hasVelocity", data.hasVelocity());

        rec.frames.add(frame);
    }

    private void flushRecording(ActiveRecording rec) {
        Truthful.getInstance().getServerScheduler().runAsync(() -> {
            try (FileWriter writer = new FileWriter(rec.outputFile, false)) {
                for (JsonObject frame : rec.frames) {
                    writer.write(frame.toString() + "\n");
                }
            } catch (IOException e) {
                Truthful.getInstance().getPlugin().getLogger().warning("Failed to save AI telemetry recording: " + e.getMessage());
            }
        });
    }
}
