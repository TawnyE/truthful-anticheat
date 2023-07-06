package org.voitac.anticheat.listener;

import com.comphenix.protocol.events.PacketEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.data.DataManager;
import org.voitac.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerItemSwitchPacketWrapper;
import org.voitac.anticheat.wrapper.impl.server.position.SetPositionPacketWrapper;

// TODO
// Remove and use direct packets
public final class PlayerListener implements Listener {

    private final DataManager dataManager;

    public PlayerListener(){
        this.dataManager = AntiCheat.getInstance().getDataManager();
        Bukkit.getPluginManager().registerEvents(this, AntiCheat.getInstance().getPlugin());
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
        if(!(event.getDamager() instanceof Player))
            return;
        final PlayerData data = this.dataManager.getPlayerData((Player) event.getDamager());
        if(data == null)
            return;
        data.setLastTarget(event.getEntity());
    }

    public void onPacket(final PacketEvent packetEvent) {
        AntiCheat.getInstance().getScheduler().onPacket(packetEvent);

        final Player player = packetEvent.getPlayer();
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);

        if(packetEvent.getPacketType().isClient()) {
            switch(packetEvent.getPacketID()) {
                case 0x28: // INCOMING_ITEM_CHANGE
                    final PlayerItemSwitchPacketWrapper itemSwitchPacketWrapper = new PlayerItemSwitchPacketWrapper(packetEvent);

                    playerData.setLastSlot(playerData.getCurrentSlot());
                    playerData.setCurrentSlot(itemSwitchPacketWrapper.getSlot());
                    break;
            }
        }else {
            switch(packetEvent.getPacketID()) {
                case 0x64: // OUTGOING_SET_POSITION
                    final SetPositionPacketWrapper setPositionPacketWrapper = new SetPositionPacketWrapper(packetEvent);

                    playerData.acceptTeleport(setPositionPacketWrapper);
                    break;
            }
        }
    }
}
