package ret.tawny.truthful.debug.recorder.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import ret.tawny.truthful.Truthful;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Manages an active recording session for a specific player.
 * Handles the accumulation of snapshots and exporting to JSON.
 */
public final class RecordingSession {

    private final UUID uuid;
    private final String name;
    private final String clientBrand;
    private final long startTime;
    private final List<TickSnapshot> snapshots;

    public RecordingSession(UUID uuid, String name, String clientBrand) {
        this.uuid = uuid;
        this.name = name;
        this.clientBrand = clientBrand;
        this.startTime = System.currentTimeMillis();
        this.snapshots = new ArrayList<>();
    }

    public void addSnapshot(TickSnapshot snapshot) {
        synchronized (snapshots) {
            snapshots.add(snapshot);
        }
    }

    public int size() {
        return snapshots.size();
    }

    public void saveAndExport() {
        // Run I/O async to prevent lag spikes when saving large recordings
        Bukkit.getScheduler().runTaskAsynchronously(Truthful.getInstance().getPlugin(), () -> {
            try {
                File folder = new File(Truthful.getInstance().getPlugin().getDataFolder(), "recordings");
                if (!folder.exists()) folder.mkdirs();

                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(startTime));
                String fileName = name + "_" + timestamp + ".json";
                File file = new File(folder, fileName);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(this, writer);
                }

                Bukkit.getConsoleSender().sendMessage("§8[§bTruthfulAC§8] §aSaved recording to " + fileName);

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}