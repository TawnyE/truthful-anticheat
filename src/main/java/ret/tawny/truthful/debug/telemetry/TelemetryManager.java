package ret.tawny.truthful.debug.telemetry;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ret.tawny.truthful.data.PlayerData;

public final class TelemetryManager {

    private final FlagRecorder flagRecorder;
    private final FullTelemetryRecorder fullTelemetryRecorder;

    public TelemetryManager() {
        this.flagRecorder = new FlagRecorder();
        this.fullTelemetryRecorder = new FullTelemetryRecorder();
    }

    public FlagRecorder getFlagRecorder() {
        return flagRecorder;
    }

    public FullTelemetryRecorder getFullTelemetryRecorder() {
        return fullTelemetryRecorder;
    }

    public void onPlayerTick(PlayerData data) {
        if (data != null && fullTelemetryRecorder.isRecording(data.getPlayer())) {
            fullTelemetryRecorder.recordTick(data);
        }
    }

    public void recordFlag(PlayerData data, String checkName, String details, double buffer, double threshold) {
        if (flagRecorder != null) {
            flagRecorder.recordFlag(data, checkName, details, buffer, threshold);
        }
    }

    public void startTelemetryRecording(CommandSender initiator, Player target, int durationSeconds) {
        if (fullTelemetryRecorder != null) {
            fullTelemetryRecorder.startRecording(initiator, target, durationSeconds);
        }
    }

    public void stopTelemetryRecording(Player target) {
        if (fullTelemetryRecorder != null) {
            fullTelemetryRecorder.stopRecording(target);
        }
    }
}
