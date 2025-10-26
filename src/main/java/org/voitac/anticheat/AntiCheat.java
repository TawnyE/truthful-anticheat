package org.voitac.anticheat;

import org.bukkit.plugin.Plugin;
import org.voitac.anticheat.checks.registry.CheckRegistry;
import org.voitac.anticheat.compensation.Scheduler;
import org.voitac.anticheat.config.api.Configuration;
import org.voitac.anticheat.data.DataManager;
import org.voitac.anticheat.listener.PacketListener;
import org.voitac.anticheat.listener.PlayerListener;
import org.voitac.anticheat.listener.TransactionListener;

public final class AntiCheat {
    private static final AntiCheat INSTANCE = new AntiCheat();

    private final CheckRegistry checkManager;

    private final DataManager dataManager;

    private final Configuration configuration;

    private final Scheduler scheduler;

    private TransactionListener transactionListener;

    private PlayerListener playerListener;

    private Plugin plugin;

    private AntiCheat() {
        this.checkManager = new CheckRegistry();
        this.dataManager = new DataManager();
        this.configuration = new Configuration();
        this.scheduler = new Scheduler();
    }

    /**
     * Called when the plugin is enabled
     * @param plugin
     */
    public void start(final Plugin plugin) {
        this.plugin = plugin;
        this.playerListener = new PlayerListener();
        this.transactionListener = new TransactionListener();
        this.checkManager.init();
        new PacketListener(this.checkManager);
    }

    /**
     * Called when the plugin is disabled
     */
    public void shutdown() {

    }

    public static AntiCheat getInstance() {
        return INSTANCE;
    }

    public DataManager getDataManager() {
        return this.dataManager;
    }

    public CheckRegistry getCheckManager() {
        return this.checkManager;
    }

    public TransactionListener getTransactionListener() {
        return this.transactionListener;
    }

    public Configuration getConfiguration() {
        return this.configuration;
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
