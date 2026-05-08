package ret.tawny.truthful;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.listener.BrandListener;

public final class TruthfulPlugin extends JavaPlugin {
    private Configuration configuration;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        printBanner();
        log("§7Initializing configuration...");

        this.configuration = new Configuration(this);
        if (!this.configuration.validateOrRepair()) {
            getLogger().severe("Configuration was invalid. A backup was written to config.yml.broken and default config restored.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        PacketEvents.getAPI().init();
        PacketEvents.getAPI().getEventManager().registerListener(new BrandListener());

        log("§7Loading core systems...");

        int pluginId = 28120;
        new Metrics(this, pluginId);

        Truthful.getInstance().start(this);

        log("§7Hooking into PacketEvents...");
        log("§7Registering checks...");
        log("§aStartup complete. TruthfulAC is now active.");
        printFooter();
    }

    @Override
    public void onDisable() {
        Truthful.getInstance().shutdown();
        PacketEvents.getAPI().terminate();
        log("§cTruthfulAC has been disabled.");
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public synchronized void reloadConfiguration() {
        this.reloadConfig();
        this.configuration = new Configuration(this);
    }

    private void log(String message) {
        Bukkit.getConsoleSender().sendMessage("§8[§bTruthfulAC§8] " + message);
    }

    private void printBanner() {
        Bukkit.getConsoleSender().sendMessage("§b");
        Bukkit.getConsoleSender().sendMessage("§b  _______       _   _      __       _");
        Bukkit.getConsoleSender().sendMessage("§b |__   __|     | | | |    / _|     | |");
        Bukkit.getConsoleSender().sendMessage("§b    | |_ __ _ _| |_| |__ | |_ _ __ | |");
        Bukkit.getConsoleSender().sendMessage("§b    | | '__| | | __| '_ \\|  _| |  || |");
        Bukkit.getConsoleSender().sendMessage("§b    | | |  | |_| |_| | | | | | |_| | |");
        Bukkit.getConsoleSender().sendMessage("§b    |_|_|   \\__,_|\\__|_| |_|_| \\__,_|_|");
        Bukkit.getConsoleSender().sendMessage("§3           Anti-Cheat Solution v" + getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage("§b");
    }

    private void printFooter() {
        Bukkit.getConsoleSender().sendMessage("§b");
    }
}