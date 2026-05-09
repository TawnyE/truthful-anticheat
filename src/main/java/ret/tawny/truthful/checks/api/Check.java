package ret.tawny.truthful.checks.api;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Check {

    private final char order;
    private final CheckType checkType;
    private final String formattedName;
    private boolean enabled;

    private final Map<UUID, Integer> violationLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLogTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlertTimes = new ConcurrentHashMap<>();

    public Check() {
        final CheckData checkData = this.getClass().getAnnotation(CheckData.class);
        this.order = checkData.order();
        this.checkType = checkData.type();
        String customName = checkData.displayName();
        if (customName != null && !customName.isEmpty()) {
            this.formattedName = customName + (this.order == ' ' ? "" : " " + this.order);
        } else {
            this.formattedName = this.checkType.getName(this);
        }
    }

    public void init() {
        this.enabled = Truthful.getInstance().getConfiguration().isCheckEnabled(this.checkType.name(),
                String.valueOf(this.order));
    }

    public final void handleQuitBase(final Player player) {
        if (player == null) return;
        final UUID uuid = player.getUniqueId();
        violationLevels.remove(uuid);
        lastLogTimes.remove(uuid);
        lastAlertTimes.remove(uuid);
    }

    public final void clearViolations() {
        violationLevels.clear();
    }

    public void flag(final PlayerData data, final String debug) {
        if (!this.enabled || data == null || data.isExempt())
            return;

        final Player p = data.getPlayer();
        if (p == null || !p.isOnline()) return;

        if (data.isBanning()) {
            if (System.currentTimeMillis() - data.getBanStartTime() > 10000) {
                data.setBanning(false);
                violationLevels.remove(p.getUniqueId());
            } else {
                return;
            }
        }

        final UUID uuid = p.getUniqueId();
        final int vl = violationLevels.merge(uuid, 1, Integer::sum);
        data.addVl(1);

        // Tell PlayerData a flag just occurred so it stops saving "Safe Locations" temporarily
        data.setLastFlagTick(data.getTicksTracked());

        Truthful.getInstance().getDebugLoggingManager().logFlag(p, this.formattedName, vl, debug);

        final Configuration config = Truthful.getInstance().getConfiguration();

        // --- ALERT THROTTLING ---
        final long now = System.currentTimeMillis();
        final long lastAlert = lastAlertTimes.getOrDefault(uuid, 0L);
        final boolean shouldAlert = (now - lastAlert >= 500L);

        if (shouldAlert) {
            lastAlertTimes.put(uuid, now);
            Truthful.getInstance().getDiscordManager().sendAlert(data, this, debug, vl);

            final String rawMessage = config.getAlertMessage();
            final String chatMessage = rawMessage
                    .replace("%player%", p.getName())
                    .replace("%check%", this.formattedName)
                    .replace("%vl%", String.valueOf(vl))
                    .replace("%ping%", String.valueOf(data.getPing()))
                    .replace("%debug%", "")
                    .trim();

            Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), () -> {
                if (!p.isOnline()) return;

                final long nowMillis = System.currentTimeMillis();
                final long lastLog = lastLogTimes.getOrDefault(uuid, 0L);

                if (nowMillis - lastLog > 2000L) {
                    Bukkit.getLogger().info(p.getName() + " failed " + this.formattedName + ": " + debug);
                    lastLogTimes.put(uuid, nowMillis);
                }

                net.md_5.bungee.api.chat.BaseComponent[] components = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(org.bukkit.ChatColor.translateAlternateColorCodes('&', chatMessage));
                if (debug != null && !debug.isEmpty()) {
                    net.md_5.bungee.api.chat.HoverEvent hoverEvent = new net.md_5.bungee.api.chat.HoverEvent(
                            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7" + debug))
                    );
                    for (net.md_5.bungee.api.chat.BaseComponent component : components) {
                        component.setHoverEvent(hoverEvent);
                    }
                }

                for (UUID staffUuid : Truthful.getInstance().getDataManager().getAlertSubscribers()) {
                    Player staff = Bukkit.getPlayer(staffUuid);
                    if (staff != null && staff.isOnline()) {
                        staff.spigot().sendMessage(components);
                    } else {
                        Truthful.getInstance().getDataManager().getAlertSubscribers().remove(staffUuid);
                    }
                }
            });
        }

        // --- CENTRALIZED LAGBACK & PUNISHMENT LOGIC ---
        Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), () -> {
            if (!p.isOnline()) return;

            // 1. Configurable Lagbacks
            boolean lagbackEnabled = config.isCheckLagbackEnabled(checkType.name(), String.valueOf(order));
            int lagbackVl = config.getCheckLagbackVl(checkType.name(), String.valueOf(order));

            if (lagbackEnabled && vl >= lagbackVl && !data.isServerFrozen()) {
                if (!p.isDead()) {
                    data.forceLagback();
                }
            }

            // 2. Punishments
            if (config.isPunishmentEnabled(checkType.name(), String.valueOf(order))) {
                if (vl >= config.getPunishmentVl(checkType.name(), String.valueOf(order))) {

                    data.setBanning(true);
                    if (!data.isServerFrozen()) {
                        data.forceLagback();
                    }

                    Bukkit.getScheduler().runTaskLater(Truthful.getInstance().getPlugin(), () -> {
                        if (config.isPunishmentAnimationEnabled()) {
                            p.getWorld().strikeLightningEffect(p.getLocation());
                        }

                        if (config.shouldQueueBandwave(checkType.name(), String.valueOf(order)) && config.isBandwaveEnabled()) {
                            boolean added = Truthful.getInstance().getBandwaveManager().addPlayer(p.getName());
                            String queueMessage = (added ? config.getBandwaveQueuedMessage() : config.getBandwaveDuplicateMessage())
                                    .replace("%player%", p.getName());
                            Bukkit.getConsoleSender().sendMessage(queueMessage);
                        } else {
                            final String command = config.getPunishmentCommand(checkType.name(), String.valueOf(order))
                                    .replace("%player%", p.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                            if (config.isPunishmentBroadcastEnabled()) {
                                final String broadcastMessage = config.getPunishmentBroadcast()
                                        .replace("%player%", p.getName());
                                Bukkit.broadcastMessage(broadcastMessage);
                            }
                        }
                    }, 20L);
                }
            }
        });

        Truthful.getInstance().getLogManager().log(uuid, p.getName(), this.formattedName, vl, data.getPing(), debug);
    }

    private boolean requiresStableSimulation(CheckType type) {
        // Only movement-dependent checks that rely on prediction/simulation stability
        return type == CheckType.SIMULATION || type == CheckType.VELOCITY ||
                type == CheckType.SPOOF || type == CheckType.SCAFFOLD ||
                type == CheckType.PHASE || type == CheckType.TIMER ||
                type == CheckType.BEDROCK;
    }

    protected void debugVerbose(String message) {
        Truthful.getInstance().getDebugManager().sendVerbose(this.formattedName, message);
    }

    protected void debugSus(String message, double buffer) {
        Truthful.getInstance().getDebugManager().sendSuspicion(this.formattedName, message, buffer);
    }

    public final char getOrder() { return this.order; }
    public final String getFormattedName() { return this.formattedName; }
    public final boolean isEnabled() { return this.enabled; }
    public final void setEnabled(boolean enabled) { this.enabled = enabled; }
    public final CheckType getType() { return this.checkType; }

    public void onPacketPlaySend(PacketSendEvent event) {
        if (canCheck(event.getPlayer())) handlePacketPlaySend(event);
    }
    public void handlePacketPlaySend(PacketSendEvent event) {}

    public void onPacketPlayerReceive(PacketReceiveEvent event) {
        if (canCheck(event.getPlayer())) handlePacketPlayerReceive(event);
    }
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {}
    public void onRelMove(RelMovePacketWrapper event) {
        if (canCheck(event.getPlayer())) handleRelMove(event);
    }
    public void handleRelMove(final RelMovePacketWrapper event) {}

    public final boolean canCheck(Object rawPlayer) {
        if (!(rawPlayer instanceof Player player)) return false;
        if (!enabled) return false;
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isExempt()) return false;

        if (requiresStableSimulation(checkType) && data.shouldSkipChecks()) {
            return false;
        }

        final boolean isBedrockPlayer = Truthful.getInstance().isBedrockPlayer(player);
        if (this.checkType == CheckType.BEDROCK) {
            // Only BEDROCK checks run on bedrock players
            return isBedrockPlayer;
        }

        // FIX: Bedrock players must be fully exempt from ALL Java checks.
        // Geyser's packet translation fundamentally changes rotation precision,
        // packet timing, and hit registration — causing false flags across
        // combat (Aim, KillAura, HitBox, Reach, AutoClicker), movement,
        // and packet checks. Only dedicated BEDROCK checks should run.
        if (isBedrockPlayer) {
            return false;
        }

        return true;
    }

    public void onAttack(final org.bukkit.event.entity.EntityDamageByEntityEvent event) {}
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {}
    public void onBlockBreak(final org.bukkit.event.block.BlockBreakEvent event) {}
    public void onVehicleMove(final org.bukkit.event.vehicle.VehicleMoveEvent event) {}
}
