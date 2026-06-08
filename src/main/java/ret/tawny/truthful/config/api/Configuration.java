package ret.tawny.truthful.config.api;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import ret.tawny.truthful.TruthfulPlugin;
import ret.tawny.truthful.checks.api.data.CheckType;

import java.util.Arrays;
import java.util.Collections;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class Configuration {

    private final TruthfulPlugin plugin;
    private final FileConfiguration config;

    public Configuration(final TruthfulPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public synchronized void save() {
        try {
            this.config.save(new File(this.plugin.getDataFolder(), "config.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized String getBedrockPrefix() {
        return this.config.getString("settings.bedrock-prefix", ".");
    }

    public synchronized boolean isCheckEnabled(String checkType, String checkOrder) {
        return this.config.getBoolean("checks." + checkType + "." + checkOrder + ".enabled", true);
    }

    public synchronized void setCheckEnabled(String checkType, String checkOrder, boolean enabled) {
        this.config.set("checks." + checkType + "." + checkOrder + ".enabled", enabled);
        this.save();
    }

    // --- PER-CHECK LAGBACK LOGIC ---

    public synchronized boolean isCheckLagbackEnabled(String checkType, String checkOrder) {
        // Fixed lagback logic: if global lagbacks are disabled, no check should have lagback regardless of specific settings
        if (!isLagbacks()) return false;
        
        // If global setting is on, THEN check the specific checks.X.Y.lagback.enabled path
        String path = "checks." + checkType + "." + checkOrder + ".lagback.enabled";
        if (this.config.contains(path)) {
            return this.config.getBoolean(path);
        }
        
        // Fallback to movement type check if path doesn't exist
        return isMovementType(CheckType.valueOf(checkType));
    }

    public synchronized void setCheckLagbackEnabled(String checkType, String checkOrder, boolean enabled) {
        this.config.set("checks." + checkType + "." + checkOrder + ".lagback.enabled", enabled);
        this.save();
    }
    
    public synchronized boolean isCheckLagbackVlSet(String checkType, String checkOrder) {
        return this.config.contains("checks." + checkType + "." + checkOrder + ".lagback.vl");
    }

    public synchronized int getCheckLagbackVl(String checkType, String checkOrder) {
        return this.config.getInt("checks." + checkType + "." + checkOrder + ".lagback.vl", 5);
    }
    
    public synchronized void setCheckLagbackVl(String checkType, String checkOrder, int vl) {
        this.config.set("checks." + checkType + "." + checkOrder + ".lagback.vl", vl);
        this.save();
    }

    private boolean isMovementType(CheckType type) {
        return type == CheckType.SIMULATION || type == CheckType.VELOCITY ||
                type == CheckType.SPOOF || type == CheckType.SCAFFOLD ||
                type == CheckType.PHASE || type == CheckType.TIMER || type == CheckType.BEDROCK;
    }

    // --- PUNISHMENTS ---

    public synchronized boolean isPunishmentEnabled(String checkType, String checkOrder) {
        String path = "checks." + checkType + "." + checkOrder + ".punishment.enabled";
        // If the path doesn't exist in config, default to false
        if (!this.config.contains(path)) {
            return false;
        }
        return this.config.getBoolean(path, false);
    }

    public synchronized void setPunishmentEnabled(String checkType, String checkOrder, boolean enabled) {
        this.config.set("checks." + checkType + "." + checkOrder + ".punishment.enabled", enabled);
        this.save();
    }

    public synchronized int getPunishmentVl(String checkType, String checkOrder) {
        return this.config.getInt("checks." + checkType + "." + checkOrder + ".punishment.vl", 20);
    }

    public synchronized String getPunishmentCommand(String checkType, String checkOrder) {
        return this.config.getString("checks." + checkType + "." + checkOrder + ".punishment.command", "kick %player% Unfair Advantage");
    }

    public synchronized boolean shouldQueueBandwave(String checkType, String checkOrder) {
        return this.config.getBoolean("checks." + checkType + "." + checkOrder + ".punishment.bandwave", false);
    }

    // --- OPTIONS ---

    public synchronized int getAutoClickerMaxCps() { return this.config.getInt("checks.AUTOCLICKER.max_cps", 22); }
    public synchronized boolean shouldCountGroundPunches() { return this.config.getBoolean("checks.AUTOCLICKER.count_ground_punches", false); }
    public synchronized void setCountGroundPunches(boolean enabled) {
        this.config.set("checks.AUTOCLICKER.count_ground_punches", enabled);
        this.save();
    }
    public synchronized boolean isLagbacks() { return this.config.getBoolean("options.lagback", true); }
    public synchronized boolean isDebugMode() { return this.config.getBoolean("options.debug-mode", false); }
    public synchronized int getViolationResetInterval() { return this.config.getInt("options.violation-reset-interval", 5); }
    public synchronized double getMinTps() { return this.config.getDouble("options.min-tps", 18.5); }
    public synchronized boolean isPunishmentBroadcastEnabled() { return this.config.getBoolean("options.punishment.broadcast", true); }
    public synchronized boolean isPunishmentAnimationEnabled() { return this.config.getBoolean("options.punishment.lightning", true); }
    public synchronized boolean isAlertsAutoEnableOnJoin() { return this.config.getBoolean("options.alerts.auto-enable-on-join", true); }

    // --- LAGBACK SEVERITY MODE ---

    /**
     * Lagback severity modes control how aggressively the anti-cheat corrects player positions.
     * BARELY: Minimal intervention — only when far from safe position.
     * MODERATE: Balanced — setback when moderately displaced or airborne during a flag.
     * STRICT: Aggressive — setback every flag with short cooldown.
     * SUPER_STRICT: Grim-like — instant setback, no cooldown, freeze until confirmed.
     */
    public enum LagbackMode {
        BARELY(500, 8.0, false),
        // FIX: cooldown 250→80ms, distance 3.0→0.0.
        // The old 3.0 threshold let players fly 3 blocks between setbacks.
        // The old 250ms cooldown gave ~5 ticks of uncontested movement.
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

    public synchronized LagbackMode getLagbackMode() {
        String raw = this.config.getString("options.lagback-mode", "MODERATE").toUpperCase();
        try {
            return LagbackMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return LagbackMode.MODERATE;
        }
    }

    /** Maximum distance from safe location before using ground-snap teleport to prevent seizures. */
    public synchronized double getLagbackGroundSnapDistance() {
        return this.config.getDouble("options.lagback-ground-snap-distance", 5.0);
    }

    // --- CUSTOMIZABLE ALERT HOVER TEXT ---

    public synchronized java.util.List<String> getAlertHoverLines() {
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

    public synchronized boolean isAlertHoverEnabled() {
        return this.config.getBoolean("messages.alert-settings.hover-enabled", true);
    }

    public synchronized boolean isAlertShowCheckType() {
        return this.config.getBoolean("messages.alert-settings.show-check-type", true);
    }

    public synchronized boolean isAlertShowPing() {
        return this.config.getBoolean("messages.alert-settings.show-ping", true);
    }

    public synchronized boolean isAlertShowTps() {
        return this.config.getBoolean("messages.alert-settings.show-tps", true);
    }

    public synchronized boolean isAlertShowVl() {
        return this.config.getBoolean("messages.alert-settings.show-vl", true);
    }

    public synchronized boolean isAlertShowBrand() {
        return this.config.getBoolean("messages.alert-settings.show-client-brand", true);
    }

    public synchronized boolean isAlertShowCoords() {
        return this.config.getBoolean("messages.alert-settings.show-coordinates", false);
    }

    // --- LATENCY KICKER ---

    public synchronized boolean isPingKickEnabled() { return this.config.getBoolean("latency-kicker.enabled", true); }
    public synchronized int getPingKickThreshold() { return this.config.getInt("latency-kicker.max-ping", 500); }
    public synchronized int getPingKickDuration() { return this.config.getInt("latency-kicker.duration-seconds", 3); }
    public synchronized int getTransactionPingIntervalTicks() { return Math.max(1, this.config.getInt("latency-kicker.probe-interval-ticks", 2)); }
    public synchronized int getEnvironmentScanIntervalTicks() { return Math.max(1, this.config.getInt("options.environment-scan-interval-ticks", 10)); }
    public synchronized String getPingKickMessage() { return color(this.config.getString("latency-kicker.kick-message", "&cConnection lost")); }

    // --- CLIENT BLOCKER ---

    public synchronized boolean isClientBlockerEnabled() { return this.config.getBoolean("client-blocker.enabled", true); }
    public synchronized List<String> getBlockedBrands() { return this.config.getStringList("client-blocker.blocked-brands"); }
    public synchronized String getClientBlockerMessage() { return color(this.config.getString("client-blocker.kick-message", "&cBlocked client brand.")); }

    // --- MESSAGES ---

    public synchronized String getAlertMessage() { return color(this.config.getString("messages.alert", "&8[&cTruthful&8] &c%player% &ffailed &c%check% &8(&fVL:%vl%&8) &7%debug%")); }
    public synchronized String getBrandMessage() { return color(this.config.getString("messages.brand", "&8[&cTruthful&8] &7Client Brand: &c%player% &7using &f%brand%")); }
    public synchronized String getPunishmentBroadcast() { return color(this.config.getString("messages.punishment-broadcast", "&8[&cTruthful&8] &c%player% &fhas been removed from the server for cheating.")); }
    public synchronized String getOnlyPlayersMessage() { return color(this.config.getString("messages.commands.only-players", "&cOnly players can use this command.")); }
    public synchronized String getNoPermissionMessage() { return color(this.config.getString("messages.commands.no-permission", "&cNo permission.")); }
    public synchronized String getNoDataMessage() { return color(this.config.getString("messages.commands.no-data", "&cError: No data found.")); }
    public synchronized String getAlertsEnabledMessage() { return color(this.config.getString("messages.alerts-enabled", "&8[&bTruthful&8] &7Alerts have been &aenabled&7.")); }
    public synchronized String getAlertsDisabledMessage() { return color(this.config.getString("messages.alerts-disabled", "&8[&bTruthful&8] &7Alerts have been &cdisabled&7.")); }

    // --- BANDWAVE ---

    public synchronized boolean isBandwaveEnabled() { return this.config.getBoolean("bandwave.enabled", true); }
    public synchronized int getBandwaveIntervalSeconds() { return Math.max(1, this.config.getInt("bandwave.interval-seconds", 5)); }
    public synchronized String getBandwaveSweepMessage() { return color(this.config.getString("messages.bandwave-sweep", "&8[&5&lBANDWAVE&8] &7#%position% swept by the band wave: &f%player%")); }
    public synchronized String getBandwaveQueuedMessage() { return color(this.config.getString("messages.bandwave-queued", "&8[&5&lBANDWAVE&8] &f%player% &7was added to the BANDWAVE queue.")); }
    public synchronized String getBandwaveDuplicateMessage() { return color(this.config.getString("messages.bandwave-duplicate", "&8[&5&lBANDWAVE&8] &f%player% &7is already queued.")); }
    public synchronized String getBandwaveStartedMessage() { return color(this.config.getString("messages.bandwave-started", "&8[&5&lBANDWAVE&8] &7Execution started.")); }
    public synchronized String getBandwaveStoppedMessage() { return color(this.config.getString("messages.bandwave-stopped", "&8[&5&lBANDWAVE&8] &7Execution stopped.")); }
    public synchronized String getBandwaveEmptyMessage() { return color(this.config.getString("messages.bandwave-empty", "&8[&5&lBANDWAVE&8] &7Queue is empty.")); }
    public synchronized String getBandwaveClearedMessage() { return color(this.config.getString("messages.bandwave-cleared", "&8[&5&lBANDWAVE&8] &7Queue cleared.")); }
    public synchronized String getBandwaveStatusMessage() { return color(this.config.getString("messages.bandwave-status", "&8[&5&lBANDWAVE&8] &7Running: &f%running% &8| &7Queued: &f%queued%")); }
    public synchronized String getBandwaveListHeader() { return color(this.config.getString("messages.bandwave-list-header", "&8[&5&lBANDWAVE&8] &7Queued players (&f%queued%&7):")); }
    public synchronized String getBandwaveListEntry() { return color(this.config.getString("messages.bandwave-list-entry", "&8 - &f%position%. %player%")); }
    public synchronized String getBandwaveUsageMessage() { return color(this.config.getString("messages.bandwave-usage", "&cUsage: /bandwave <add|remove|list|start|stop|clear|status> [player]")); }
    public synchronized String getBandwaveNotEnabledMessage() { return color(this.config.getString("messages.bandwave-disabled", "&8[&5&lBANDWAVE&8] &cBANDWAVE is disabled in config.")); }
    public synchronized String getBandwaveRemovedMessage() { return color(this.config.getString("messages.bandwave-removed", "&8[&5&lBANDWAVE&8] &f%player% &7was removed from the queue.")); }
    public synchronized String getBandwaveScheduleMessage() { return color(this.config.getString("messages.bandwave-schedule", "&8[&5&lBANDWAVE&8] &7Auto-start: &f%enabled% &8| &7Day: &f%day% &8| &7Time: &f%time%")); }

    public synchronized boolean isBandwaveAutoStartEnabled() { return this.config.getBoolean("bandwave.auto-start.enabled", false); }
    public synchronized DayOfWeek getBandwaveAutoStartDay() {
        String raw = this.config.getString("bandwave.auto-start.day", "SUNDAY");
        try { return DayOfWeek.valueOf(raw.toUpperCase()); } catch (Exception ignored) { return DayOfWeek.SUNDAY; }
    }
    public synchronized LocalTime getBandwaveAutoStartTime() {
        String raw = this.config.getString("bandwave.auto-start.time", "18:00");
        try { return LocalTime.parse(raw); } catch (DateTimeParseException ignored) { return LocalTime.of(18, 0); }
    }

    // --- DISCORD & MISC ---

    public synchronized String getPluginDisplayName() { return this.config.getString("settings.plugin-name", "TruthfulAC"); }
    public synchronized boolean isDiscordEnabled() { return this.config.getBoolean("discord.enabled", false); }
    public synchronized String getDiscordWebhookUrl() { return this.config.getString("discord.webhook-url", ""); }
    public synchronized int getDiscordMinVl() { return this.config.getInt("discord.min-vl", 5); }
    public synchronized String getDiscordUsername() { return this.config.getString("discord.username", "Truthful Watchdog"); }
    public synchronized String getDiscordAvatarUrl() { return this.config.getString("discord.avatar-url", ""); }
    public synchronized int getDiscordEmbedColor() { return this.config.getInt("discord.embed.color", 16733525); }
    public synchronized String getDiscordFooter() { return this.config.getString("discord.embed.footer", "Truthful Anti-Cheat"); }
    public synchronized boolean isDiscordHeadEnabled() { return this.config.getBoolean("discord.embed.show-player-head", true); }

    public synchronized int getQueueMaxEntries() { return Math.max(1, this.config.getInt("sync.max-entries", 50)); }
    public synchronized long getQueueTtlMillis() { return Math.max(1000L, this.config.getLong("sync.entry-ttl-millis", 5000L)); }

    public synchronized boolean validateOrRepair() {
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

    public synchronized void cleanupOrphanedChecks(java.util.Collection<ret.tawny.truthful.checks.api.Check> activeChecks) {
        org.bukkit.configuration.ConfigurationSection section = this.config.getConfigurationSection("checks");
        if (section == null) return;
        boolean changed = false;

        for (String type : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection typeSection = section.getConfigurationSection(type);
            if (typeSection == null) continue;

            for (String order : typeSection.getKeys(false)) {
                if (isCheckOptionKey(type, order)) {
                    continue;
                }

                boolean found = false;
                for (ret.tawny.truthful.checks.api.Check check : activeChecks) {
                    if (check.getType().name().equals(type) && String.valueOf(check.getOrder()).equals(order)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    typeSection.set(order, null);
                    changed = true;
                }
            }

            if (typeSection.getKeys(false).isEmpty()) {
                section.set(type, null);
                changed = true;
            }
        }

        if (changed) {
            this.save();
            this.plugin.getLogger().info("Cleaned up orphaned checks from configuration.");
        }
    }

    private boolean isCheckOptionKey(String type, String key) {
        return "AUTOCLICKER".equals(type) && ("max_cps".equals(key) || "count_ground_punches".equals(key));
    }
}
