package ret.tawny.truthful.config.api;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import ret.tawny.truthful.TruthfulPlugin;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Configuration {

    private final TruthfulPlugin plugin;
    private final FileConfiguration config;

    // In-Memory Cache for Netty Thread Performance (Zero Lock Contention)
    private final Map<String, Boolean> checkEnabledCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> checkLagbackEnabledCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> checkLagbackVlCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> punishmentEnabledCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> punishmentVlCache = new ConcurrentHashMap<>();
    private final Map<String, String> punishmentCommandCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> bandwaveQueueCache = new ConcurrentHashMap<>();

    public Configuration(final TruthfulPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        invalidateAndPreloadCache();
    }

    private void invalidateAndPreloadCache() {
        checkEnabledCache.clear();
        checkLagbackEnabledCache.clear();
        checkLagbackVlCache.clear();
        punishmentEnabledCache.clear();
        punishmentVlCache.clear();
        punishmentCommandCache.clear();
        bandwaveQueueCache.clear();
    }

    public synchronized void save() {
        try {
            this.config.save(new File(this.plugin.getDataFolder(), "config.yml"));
            invalidateAndPreloadCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getBedrockPrefix() {
        return this.config.getString("settings.bedrock-prefix", ".");
    }

    public String getPluginDisplayName() {
        return color(this.config.getString("settings.plugin-name", "&9&lT&e&lAC"));
    }

    // --- CACHED CHECK GETTERS & SETTERS ---

    public boolean isCheckEnabled(String checkType, String checkOrder) {
        String key = checkType + "." + checkOrder;
        return checkEnabledCache.computeIfAbsent(key,
                k -> this.config.getBoolean("checks." + checkType + "." + checkOrder + ".enabled", true));
    }

    public synchronized void setCheckEnabled(String checkType, String checkOrder, boolean enabled) {
        this.config.set("checks." + checkType + "." + checkOrder + ".enabled", enabled);
        checkEnabledCache.put(checkType + "." + checkOrder, enabled);
        this.save();
    }

    public boolean isCheckLagbackEnabled(String checkType, String checkOrder) {
        if (!isLagbacks()) return false;
        String key = checkType + "." + checkOrder;
        return checkLagbackEnabledCache.computeIfAbsent(key, k -> {
            String path = "checks." + checkType + "." + checkOrder + ".lagback.enabled";
            if (this.config.contains(path)) {
                return this.config.getBoolean(path);
            }
            return isMovementType(CheckType.valueOf(checkType));
        });
    }

    public synchronized void setCheckLagbackEnabled(String checkType, String checkOrder, boolean enabled) {
        this.config.set("checks." + checkType + "." + checkOrder + ".lagback.enabled", enabled);
        checkLagbackEnabledCache.put(checkType + "." + checkOrder, enabled);
        this.save();
    }

    public int getCheckLagbackVl(String checkType, String checkOrder) {
        String key = checkType + "." + checkOrder;
        return checkLagbackVlCache.computeIfAbsent(key,
                k -> this.config.getInt("checks." + checkType + "." + checkOrder + ".lagback.vl", 5));
    }

    public synchronized void setCheckLagbackVl(String checkType, String checkOrder, int vl) {
        this.config.set("checks." + checkType + "." + checkOrder + ".lagback.vl", vl);
        checkLagbackVlCache.put(checkType + "." + checkOrder, vl);
        this.save();
    }

    private boolean isMovementType(CheckType type) {
        return type == CheckType.SIMULATION || type == CheckType.VELOCITY ||
                type == CheckType.SPOOF || type == CheckType.SCAFFOLD ||
                type == CheckType.PHASE || type == CheckType.TIMER || type == CheckType.BEDROCK || type == CheckType.LAG;
    }

    // --- PUNISHMENTS ---

    public boolean isPunishmentEnabled(String checkType, String checkOrder) {
        String key = checkType + "." + checkOrder;
        return punishmentEnabledCache.computeIfAbsent(key,
                k -> this.config.getBoolean("checks." + checkType + "." + checkOrder + ".punishment.enabled", false));
    }

    public synchronized void setPunishmentEnabled(String checkType, String checkOrder, boolean enabled) {
        this.config.set("checks." + checkType + "." + checkOrder + ".punishment.enabled", enabled);
        punishmentEnabledCache.put(checkType + "." + checkOrder, enabled);
        this.save();
    }

    public int getPunishmentVl(String checkType, String checkOrder) {
        String key = checkType + "." + checkOrder;
        return punishmentVlCache.computeIfAbsent(key,
                k -> this.config.getInt("checks." + checkType + "." + checkOrder + ".punishment.vl", 20));
    }

    public String getPunishmentCommand(String checkType, String checkOrder) {
        String key = checkType + "." + checkOrder;
        return punishmentCommandCache.computeIfAbsent(key,
                k -> this.config.getString("checks." + checkType + "." + checkOrder + ".punishment.command", "kick %player% Unfair Advantage"));
    }

    public boolean shouldQueueBandwave(String checkType, String checkOrder) {
        String key = checkType + "." + checkOrder;
        return bandwaveQueueCache.computeIfAbsent(key,
                k -> this.config.getBoolean("checks." + checkType + "." + checkOrder + ".punishment.bandwave", false));
    }

    // --- OPTIONS ---

    public int getAutoClickerMaxCps() { return this.config.getInt("checks.AUTOCLICKER.max_cps", 22); }
    public boolean shouldCountGroundPunches() { return this.config.getBoolean("checks.AUTOCLICKER.count_ground_punches", false); }
    public synchronized void setCountGroundPunches(boolean enabled) {
        this.config.set("checks.AUTOCLICKER.count_ground_punches", enabled);
        this.save();
    }
    public boolean isLagbacks() { return this.config.getBoolean("options.lagback", true); }
    public boolean isDebugMode() { return this.config.getBoolean("options.debug-mode", false); }
    public int getViolationResetInterval() { return Math.max(1, this.config.getInt("options.violation-reset-interval", 5)); }
    public double getMinTps() { return Math.max(1.0, this.config.getDouble("options.min-tps", 18.5)); }
    public boolean isPunishmentBroadcastEnabled() { return this.config.getBoolean("options.punishment.broadcast", true); }
    public boolean isPunishmentAnimationEnabled() { return this.config.getBoolean("options.punishment.lightning", true); }
    public boolean isAlertsAutoEnableOnJoin() { return this.config.getBoolean("options.alerts.auto-enable-on-join", true); }

    public enum LagbackMode {
        BARELY(500, 8.0, false),
        MODERATE(80, 0.0, true),
        STRICT(100, 0.0, true),
        SUPER_STRICT(0, 0.0, true);

        public final int cooldownMs;
        public final double distanceThreshold;
        public final boolean setbackOnAirborne;

        LagbackMode(int cooldownMs, double distanceThreshold, boolean setbackOnAirborne) {
            this.cooldownMs = cooldownMs;
            this.distanceThreshold = distanceThreshold;
            this.setbackOnAirborne = setbackOnAirborne;
        }
    }

    public LagbackMode getLagbackMode() {
        String raw = this.config.getString("options.lagback-mode", "MODERATE").toUpperCase();
        try {
            return LagbackMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return LagbackMode.MODERATE;
        }
    }

    public double getLagbackGroundSnapDistance() {
        return this.config.getDouble("options.lagback-ground-snap-distance", 5.0);
    }

    public List<String> getAlertHoverLines() {
        if (this.config.isList("messages.alert-settings.hover-lines")) {
            return this.config.getStringList("messages.alert-settings.hover-lines");
        }
        return Arrays.asList(
                "&7Check: &f%check%",
                "&7VL: &f%vl%",
                "&7Ping: &f%ping%ms",
                "&7TPS: &f%tps%",
                "&7Client: &f%brand%",
                "&7Debug: &f%debug%"
        );
    }

    public boolean isAlertHoverEnabled() { return this.config.getBoolean("messages.alert-settings.hover-enabled", true); }
    public boolean isAlertShowCheckType() { return this.config.getBoolean("messages.alert-settings.show-check-type", true); }
    public boolean isAlertShowPing() { return this.config.getBoolean("messages.alert-settings.show-ping", true); }
    public boolean isAlertShowTps() { return this.config.getBoolean("messages.alert-settings.show-tps", true); }
    public boolean isAlertShowVl() { return this.config.getBoolean("messages.alert-settings.show-vl", true); }
    public boolean isAlertShowBrand() { return this.config.getBoolean("messages.alert-settings.show-client-brand", true); }
    public boolean isAlertShowCoords() { return this.config.getBoolean("messages.alert-settings.show-coordinates", false); }

    public boolean isPingKickEnabled() { return this.config.getBoolean("latency-kicker.enabled", true); }
    public int getPingKickThreshold() { return this.config.getInt("latency-kicker.max-ping", 650); }
    public int getPingKickDuration() { return this.config.getInt("latency-kicker.duration-seconds", 3); }
    public int getTransactionPingIntervalTicks() { return Math.max(1, this.config.getInt("latency-kicker.probe-interval-ticks", 2)); }
    public int getEnvironmentScanIntervalTicks() { return Math.max(1, this.config.getInt("options.environment-scan-interval-ticks", 10)); }
    public String getPingKickMessage() { return color(this.config.getString("latency-kicker.kick-message", "&cConnection lost")); }

    public boolean isClientBlockerEnabled() { return this.config.getBoolean("client-blocker.enabled", true); }
    public List<String> getBlockedBrands() { return this.config.getStringList("client-blocker.blocked-brands"); }
    public String getClientBlockerMessage() { return color(this.config.getString("client-blocker.kick-message", "&cBlocked client brand.")); }

    public String getAlertMessage() { return color(this.config.getString("messages.alert", "&8[&cTruthful&8] &c%player% &ffailed &c%check% &8(&fVL:%vl%&8) &7%debug%")); }
    public String getBrandMessage() { return color(this.config.getString("messages.brand", "&8[&cTruthful&8] &7Client Brand: &c%player% &7using &f%brand%")); }
    public String getPunishmentBroadcast() { return color(this.config.getString("messages.punishment-broadcast", "&8[&cTruthful&8] &c%player% &fhas been removed from the server for cheating.")); }
    public String getOnlyPlayersMessage() { return color(this.config.getString("messages.commands.only-players", "&cOnly players can use this command.")); }
    public String getNoPermissionMessage() { return color(this.config.getString("messages.commands.no-permission", "&cNo permission.")); }
    public String getNoDataMessage() { return color(this.config.getString("messages.commands.no-data", "&cError: No data found.")); }
    public String getAlertsEnabledMessage() { return color(this.config.getString("messages.alerts-enabled", "&8[&bTruthful&8] &7Alerts have been &aenabled&7.")); }
    public String getAlertsDisabledMessage() { return color(this.config.getString("messages.alerts-disabled", "&8[&bTruthful&8] &7Alerts have been &cdisabled&7.")); }

    public boolean isBandwaveEnabled() { return this.config.getBoolean("bandwave.enabled", true); }
    public int getBandwaveIntervalSeconds() { return Math.max(1, this.config.getInt("bandwave.interval-seconds", 5)); }
    public String getBandwaveSweepMessage() { return color(this.config.getString("messages.bandwave-sweep", "&8[&5&lBANDWAVE&8] &7#%position% swept by the band wave: &f%player%")); }
    public String getBandwaveQueuedMessage() { return color(this.config.getString("messages.bandwave-queued", "&8[&5&lBANDWAVE&8] &f%player% &7was added to the BANDWAVE queue.")); }
    public String getBandwaveDuplicateMessage() { return color(this.config.getString("messages.bandwave-duplicate", "&8[&5&lBANDWAVE&8] &f%player% &7is already queued.")); }
    public String getBandwaveStartedMessage() { return color(this.config.getString("messages.bandwave-started", "&8[&5&lBANDWAVE&8] &7Execution started.")); }
    public String getBandwaveStoppedMessage() { return color(this.config.getString("messages.bandwave-stopped", "&8[&5&lBANDWAVE&8] &7Execution stopped.")); }
    public String getBandwaveEmptyMessage() { return color(this.config.getString("messages.bandwave-empty", "&8[&5&lBANDWAVE&8] &7Queue is empty.")); }
    public String getBandwaveClearedMessage() { return color(this.config.getString("messages.bandwave-cleared", "&8[&5&lBANDWAVE&8] &7Queue cleared.")); }
    public String getBandwaveStatusMessage() { return color(this.config.getString("messages.bandwave-status", "&8[&5&lBANDWAVE&8] &7Running: &f%running% &8| &7Queued: &f%queued%")); }
    public String getBandwaveListHeader() { return color(this.config.getString("messages.bandwave-list-header", "&8[&5&lBANDWAVE&8] &7Queued players (&f%queued%&7):")); }
    public String getBandwaveListEntry() { return color(this.config.getString("messages.bandwave-list-entry", "&8 - &f%position%. %player%")); }
    public String getBandwaveUsageMessage() { return color(this.config.getString("messages.bandwave-usage", "&cUsage: /bandwave <add|remove|list|start|stop|clear|status> [player]")); }
    public String getBandwaveNotEnabledMessage() { return color(this.config.getString("messages.bandwave-disabled", "&8[&5&lBANDWAVE&8] &cBANDWAVE is disabled in config.")); }
    public String getBandwaveRemovedMessage() { return color(this.config.getString("messages.bandwave-removed", "&8[&5&lBANDWAVE&8] &f%player% &7was removed from the queue.")); }
    public String getBandwaveScheduleMessage() { return color(this.config.getString("messages.bandwave-schedule", "&8[&5&lBANDWAVE&8] &7Auto-start: &f%enabled% &8| &7Day: &f%day% &8| &7Time: &f%time%")); }

    public boolean isBandwaveAutoStartEnabled() { return this.config.getBoolean("bandwave.auto-start.enabled", false); }
    public DayOfWeek getBandwaveAutoStartDay() {
        String raw = this.config.getString("bandwave.auto-start.day", "SUNDAY");
        try { return DayOfWeek.valueOf(raw.toUpperCase()); } catch (Exception ignored) { return DayOfWeek.SUNDAY; }
    }
    public LocalTime getBandwaveAutoStartTime() {
        String raw = this.config.getString("bandwave.auto-start.time", "18:00");
        try { return LocalTime.parse(raw); } catch (DateTimeParseException ignored) { return LocalTime.of(18, 0); }
    }

    public boolean isDiscordEnabled() { return this.config.getBoolean("discord.enabled", false); }
    public String getDiscordWebhookUrl() { return this.config.getString("discord.webhook-url", ""); }
    public int getDiscordMinVl() { return this.config.getInt("discord.min-vl", 10); }
    public String getDiscordUsername() { return this.config.getString("discord.username", "Truthful Watchdog"); }
    public String getDiscordAvatarUrl() { return this.config.getString("discord.avatar-url", ""); }
    public int getDiscordEmbedColor() { return this.config.getInt("discord.embed.color", 16733525); }
    public String getDiscordFooter() { return this.config.getString("discord.embed.footer", "Truthful Anti-Cheat"); }
    public boolean isDiscordHeadEnabled() { return this.config.getBoolean("discord.embed.show-player-head", true); }

    public int getQueueMaxEntries() { return Math.max(1, this.config.getInt("sync.max-entries", 50)); }
    public long getQueueTtlMillis() { return Math.max(1000L, this.config.getLong("sync.entry-ttl-millis", 5000L)); }

    public boolean validateOrRepair() {
        int maxEntries = this.config.getInt("sync.max-entries", 50);
        long ttl = this.config.getLong("sync.entry-ttl-millis", 5000L);
        int probeInterval = this.config.getInt("latency-kicker.probe-interval-ticks", 2);
        if (maxEntries <= 0 || ttl <= 0 || probeInterval <= 0) {
            plugin.getLogger().severe("Invalid configuration detected: sync.max-entries, sync.entry-ttl-millis and latency-kicker.probe-interval-ticks must be > 0.");
            File configFile = new File(this.plugin.getDataFolder(), "config.yml");
            File broken = new File(this.plugin.getDataFolder(), "config.yml.broken");
            try { Files.copy(configFile.toPath(), broken.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
            this.plugin.saveResource("config.yml", true);
            return false;
        }
        return true;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public synchronized void cleanupOrphanedChecks(Collection<Check> activeChecks) {
        // Disabled file-saving cleanup to preserve user comments in config.yml
    }
}