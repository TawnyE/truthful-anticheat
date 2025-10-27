package ret.tawny.truthful;

import org.bukkit.plugin.Plugin;
import ret.tawny.truthful.checks.registry.CheckRegistry;
import ret.tawny.truthful.compensation.Scheduler;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.DataManager;
import ret.tawny.truthful.listener.PacketListener;
import ret.tawny.truthful.listener.PlayerListener;
import ret.tawny.truthful.version.VersionManager;

public final class Truthful {
    private static final Truthful INSTANCE = new Truthful();

    private VersionManager versionManager;
    private CheckRegistry checkManager;
    private DataManager dataManager;
    private Scheduler scheduler;
    private PlayerListener playerListener;
    private PacketListener packetListener;
    private Plugin plugin;

    private Truthful() {
        // Private constructor for singleton
    }

    public void start(final Plugin plugin) {
        this.plugin = plugin;

        // --- CORRECTED INITIALIZATION ORDER ---
        // 1. Initialize core components that have no dependencies.
        this.versionManager = new VersionManager();
        this.dataManager = new DataManager();
        this.scheduler = new Scheduler(); // Moved UP

        // 2. Load the version adapter.
        this.versionManager.load();

        // 3. Now, initialize components that DEPEND on the core ones.
        // The CheckRegistry can now safely construct checks that use the Scheduler.
        this.checkManager = new CheckRegistry();
        this.playerListener = new PlayerListener();
        this.packetListener = new PacketListener(this.checkManager);

        // 4. Finalize the setup by registering events for the loaded checks.
        this.checkManager.init();
    }

    public void shutdown() {
        // Future shutdown logic
    }

    public static Truthful getInstance() {
        return INSTANCE;
    }

    public VersionManager getVersionManager() {
        return this.versionManager;
    }

    public DataManager getDataManager() {
        return this.dataManager;
    }

    public CheckRegistry getCheckManager() {
        return this.checkManager;
    }

    public Configuration getConfiguration() {
        return ((TruthfulPlugin) plugin).getConfiguration();
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }
}