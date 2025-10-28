package ret.tawny.truthful.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.DataManager;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerItemSwitchPacketWrapper;
import ret.tawny.truthful.wrapper.impl.server.position.SetPositionPacketWrapper;

public final class PlayerListener implements Listener {

    private final DataManager dataManager;

    public PlayerListener() {
        this.dataManager = Truthful.getInstance().getDataManager();
        Bukkit.getPluginManager().registerEvents(this, Truthful.getInstance().getPlugin());
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        this.dataManager.enter(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.dataManager.eliminate(event.getPlayer());
    }

    @EventHandler
    public void onAttack(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (Truthful.getInstance().isBedrockPlayer(damager)) return;

        final PlayerData data = this.dataManager.getPlayerData(damager);
        if (data == null) return;
        data.setLastTarget(event.getEntity());
    }

    public void onPacket(final PacketEvent packetEvent) {
        if (Truthful.getInstance().isBedrockPlayer(packetEvent.getPlayer())) return;

        Truthful.getInstance().getScheduler().onPacket(packetEvent);

        final Player player = packetEvent.getPlayer();
        final PlayerData playerData = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (playerData == null) return;

        if (packetEvent.getPacketType().isClient()) {
            if (packetEvent.getPacketType().equals(PacketType.Play.Client.HELD_ITEM_SLOT)) {
                final PlayerItemSwitchPacketWrapper itemSwitchPacketWrapper = new PlayerItemSwitchPacketWrapper(packetEvent);
                playerData.setLastSlot(playerData.getCurrentSlot());
                playerData.setCurrentSlot(itemSwitchPacketWrapper.getSlot());
                playerData.setLastSlotSwitchTime(System.currentTimeMillis());
            } else if (packetEvent.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE)) {
                playerData.setLastBlockPlaceTime(System.currentTimeMillis());
                playerData.setLastBlockPlaceTick(playerData.getTicksTracked());
            }
        } else {
            if (packetEvent.getPacketType().equals(PacketType.Play.Server.POSITION)) {
                final SetPositionPacketWrapper setPositionPacketWrapper = new SetPositionPacketWrapper(packetEvent);
                playerData.acceptTeleport(setPositionPacketWrapper);
            }
        }
    }
}