package ret.tawny.truthful;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import ret.tawny.truthful.checks.registry.CheckRegistry;
import ret.tawny.truthful.commands.impl.CommandAlerts;
import ret.tawny.truthful.commands.impl.CommandManager;
import ret.tawny.truthful.commands.impl.CommandBandwave;
import ret.tawny.truthful.compensation.CompensationTracker;
import ret.tawny.truthful.compensation.Scheduler;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.DataManager;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.database.LogManager;
import ret.tawny.truthful.debug.DebugManager;
import ret.tawny.truthful.debug.logging.DebugLoggingManager;
import ret.tawny.truthful.gui.GuiManager;
import ret.tawny.truthful.listener.CheckListener;
import ret.tawny.truthful.listener.PlayerListener;
import ret.tawny.truthful.listener.TruthfulPacketListener;
import ret.tawny.truthful.managers.BandwaveManager;
import ret.tawny.truthful.managers.DiscordManager;
import ret.tawny.truthful.task.EnvironmentTask;
import ret.tawny.truthful.utils.bedrock.BedrockUtils;
import ret.tawny.truthful.version.VersionManager;

import java.lang.reflect.Method;

public final class Truthful {
    private static final Truthful INSTANCE = new Truthful();

    public static final boolean USE_MODERN_PING = true;

    private VersionManager versionManager;
    private CheckRegistry checkManager;
    private DataManager dataManager;
    private ret.tawny.truthful.data.world.GlobalWorldCache globalWorldCache;
    private Scheduler scheduler;
    private CompensationTracker compensationTracker;
    private PlayerListener playerListener;
    private LogManager logManager;
    private GuiManager guiManager;
    private DebugManager debugManager;
    private DiscordManager discordManager;
    private DebugLoggingManager debugLoggingManager;
    private BandwaveManager bandwaveManager;
    private Plugin plugin;
    private EnvironmentTask environmentTask;

    private Truthful() {
    }

    public void start(final Plugin plugin) {
        this.plugin = plugin;

        this.versionManager = new VersionManager();
        this.dataManager = new DataManager();
        this.globalWorldCache = new ret.tawny.truthful.data.world.GlobalWorldCache();
        this.scheduler = new Scheduler();
        this.logManager = new LogManager(plugin);
        this.guiManager = new GuiManager();
        this.debugManager = new DebugManager();
        this.discordManager = new DiscordManager();
        this.debugLoggingManager = new DebugLoggingManager();
        this.bandwaveManager = new BandwaveManager();

        this.versionManager.load();
        this.compensationTracker = new CompensationTracker();

        CommandManager commandManager = new CommandManager();
        plugin.getServer().getPluginCommand("truthful").setExecutor(commandManager);
        plugin.getServer().getPluginCommand("truthful").setTabCompleter(commandManager);
        plugin.getServer().getPluginCommand("alerts").setExecutor(new CommandAlerts());
        plugin.getServer().getPluginCommand("bandwave").setExecutor(new CommandBandwave());

        this.checkManager = new CheckRegistry();
        this.playerListener = new PlayerListener();
        new TruthfulPacketListener(this.checkManager);
        new CheckListener();

        this.checkManager.init();

        this.environmentTask = new EnvironmentTask();
        this.environmentTask.runTaskTimer(plugin, 1L, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Entity ent : world.getEntities()) {
                    boolean isPlayer = ent instanceof Player;
                    Location loc = ent.getLocation();
                    this.compensationTracker.handleSpawn(ent.getEntityId(), ent.getUniqueId(),
                            loc.getX(), loc.getY(), loc.getZ(), 0.6, 1.8, isPlayer);
                }
            }
        }, 10L);

        int resetMinutes = getConfiguration().getViolationResetInterval();
        if (resetMinutes > 0) {
            long ticks = (long) resetMinutes * 60 * 20L;
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkManager.resetAllViolations();
                    for (PlayerData data : dataManager.getCollection()) {
                        data.resetTotalViolations();
                    }
                }
            }.runTaskTimer(plugin, ticks, ticks);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (compensationTracker != null) {
                    compensationTracker.tick();
                }
                bandwaveManager.tickAutoStart();
            }
        }.runTaskTimer(plugin, 1L, 1L);

        final long transactionInterval = getConfiguration().getTransactionPingIntervalTicks();
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerData data : dataManager.getCollection()) {
                    Player player = data.getPlayer();
                    if (player == null || !player.isOnline() || isBedrockPlayer(player)) {
                        continue;
                    }

                    short id = (short) data.getNextTransactionId();
                    data.recordTransactionSent(id);

                    if (USE_MODERN_PING) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(id));
                    } else {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                                new WrapperPlayServerWindowConfirmation(0, id, false));
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, transactionInterval, transactionInterval);
    }

    public void shutdown() {
        if (logManager != null) logManager.shutdown();
        if (bandwaveManager != null) bandwaveManager.stop();
        if (environmentTask != null && !environmentTask.isCancelled()) environmentTask.cancel();
        if (dataManager != null) dataManager.teardownAll();
        if (plugin != null) Bukkit.getScheduler().cancelTasks(plugin);
    }

    public boolean isBedrockPlayer(Player player) {
        return BedrockUtils.isBedrock(player);
    }

    public static Truthful getInstance() { return INSTANCE; }
    public VersionManager getVersionManager() { return this.versionManager; }
    public DataManager getDataManager() { return this.dataManager; }
    public ret.tawny.truthful.data.world.GlobalWorldCache getGlobalWorldCache() { return this.globalWorldCache; }
    public CheckRegistry getCheckManager() { return this.checkManager; }
    public Configuration getConfiguration() { return ((TruthfulPlugin) plugin).getConfiguration(); }
    public Plugin getPlugin() { return plugin; }
    public PlayerListener getPlayerListener() { return playerListener; }
    public Scheduler getScheduler() { return scheduler; }
    public CompensationTracker getCompensationTracker() { return compensationTracker; }
    public LogManager getLogManager() { return logManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public DebugManager getDebugManager() { return debugManager; }
    public DiscordManager getDiscordManager() { return discordManager; }
    public DebugLoggingManager getDebugLoggingManager() { return debugLoggingManager; }
    public BandwaveManager getBandwaveManager() { return bandwaveManager; }

    public void reload() {
        if (this.plugin == null) return;
        if (this.plugin instanceof TruthfulPlugin truthfulPlugin) truthfulPlugin.reloadConfiguration();
        if (this.checkManager != null) this.checkManager.init();
    }

    public double getTps() {
        try {
            final Method method = Bukkit.getServer().getClass().getMethod("getTPS");
            final Object value = method.invoke(Bukkit.getServer());
            if (value instanceof double[] tps && tps.length > 0) return tps[0];
        } catch (Throwable t) {
        }
        return 20.0;
    }
}