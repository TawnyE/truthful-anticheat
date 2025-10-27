package ret.tawny.truthful.checks.api;

import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

public abstract class Check implements Listener {
    private final char order;
    private final CheckType checkType;
    private final String formattedName;
    private final boolean enabled;

    protected Check() {
        final CheckData checkData = this.getClass().getAnnotation(CheckData.class);
        this.order = checkData.order();
        this.checkType = checkData.type();
        this.formattedName = this.checkType.getName(this);
        this.enabled = Truthful.getInstance().getConfiguration().isCheckEnabled(this.checkType.name(), String.valueOf(this.order));

        if (this.enabled) {
            Bukkit.getPluginManager().registerEvents(this, Truthful.getInstance().getPlugin());
        }
    }

    protected void flag(final PlayerData player, final String debug) {
        String message = String.format("§8[§cTruthful§8] §7%s §ffailed §7%s §8(§fx%s§8) §c%s",
                player.getPlayer().getName(), this.formattedName, player.increment(), debug);

        Bukkit.getLogger().info(player.getPlayer().getName() + " failed " + this.formattedName + ": " + debug);

        for (final Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("truthful.alerts")) {
                staff.sendMessage(message);
            }
        }
    }

    public final char getOrder() {
        return this.order;
    }

    public final String getFormattedName() {
        return this.formattedName;
    }

    public final boolean isEnabled() {
        return this.enabled;
    }

    // These methods automatically check if the check is enabled before calling the handler
    public void onPacketPlaySend(PacketEvent event) { if(enabled) handlePacketPlaySend(event); }
    public void onPacketPlayerReceive(PacketEvent event) { if(enabled) handlePacketPlayerReceive(event); }
    public void onRelMove(RelMovePacketWrapper event) { if(enabled) handleRelMove(event); }

    // Abstract handlers for subclasses to implement their specific logic
    public void handlePacketPlaySend(final PacketEvent event) {}
    public void handlePacketPlayerReceive(final PacketEvent event) {}
    public void handleRelMove(final RelMovePacketWrapper event) {}
}