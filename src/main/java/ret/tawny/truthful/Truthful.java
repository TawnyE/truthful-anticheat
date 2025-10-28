package ret.tawny.truthful;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.floodgate.api.FloodgateApi;
import ret.tawny.truthful.checks.registry.CheckRegistry;
import ret.tawny.truthful.compensation.Scheduler;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.DataManager;
import ret.tawny.truthful.listener.PacketListener;
import ret.tawny.truthful.listener.PlayerListener;
import ret.tawny.truthful.version.VersionManager;

public final class Truthful {
    private static final Truthful INSTANCE = new Truthful();

    private boolean floodgateSupportEnabled = false;
    private FloodgateApi floodgateApi = null;
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

        // Hook into Floodgate (the API for Geyser)
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                this.floodgateApi = FloodgateApi.getInstance();
                this.floodgateSupportEnabled = true;
                plugin.getLogger().info("Successfully hooked into Floodgate. Bedrock players will be exempt from checks.");
            } catch (Exception e) {
                plugin.getLogger().warning("Found Floodgate, but failed to hook into its API. Bedrock player exemption may not work.");
            }
        }

        // Initialize core components that have no dependencies.
        this.versionManager = new VersionManager();
        this.dataManager = new DataManager();
        this.scheduler = new Scheduler();

        // Load the version adapter.
        this.versionManager.load();

        // Now, initialize components that DEPEND on the core ones.
        this.checkManager = new CheckRegistry();
        this.playerListener = new PlayerListener();
        this.packetListener = new PacketListener(this.checkManager);

        // Finalize the setup by registering events for the loaded checks.
        this.checkManager.init();
    }

    public void shutdown() {
        // Future shutdown logic can go here
    }

    /**
     * Safely checks if a player is connected via Floodgate (Bedrock).
     * @param player The player to check.
     * @return true if the player is a Bedrock player, false otherwise.
     */
    public boolean isBedrockPlayer(Player player) {
        if (!floodgateSupportEnabled || floodgateApi == null || player == null) {
            return false;
        }
        return floodgateApi.isFloodgatePlayer(player.getUniqueId());
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