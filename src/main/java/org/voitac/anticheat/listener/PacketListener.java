package org.voitac.anticheat.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.registry.CheckRegistry;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public final class PacketListener {

    private final CheckRegistry checkManager;

    public PacketListener(final CheckRegistry checkManager) {
        this.checkManager = checkManager;
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(AntiCheat.getInstance().getPlugin(), ListenerPriority.HIGH,
                //CPackets

                PacketType.Play.Client.ABILITIES,
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE,
                PacketType.Play.Client.CUSTOM_PAYLOAD,
                PacketType.Play.Client.ENTITY_ACTION,
                // PacketPlayer
                PacketType.Play.Client.GROUND,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK,

                PacketType.Play.Client.PICK_ITEM,
                PacketType.Play.Client.ARM_ANIMATION,
                PacketType.Play.Client.SPECTATE,

                //SPackets
                PacketType.Play.Server.ENTITY_VELOCITY,
                PacketType.Play.Server.ENTITY_TELEPORT
                ) {
            @Override
            public void onPacketReceiving(final PacketEvent event) {
                AntiCheat.getInstance().getPlayerListener().onPacket(event);
                checkManager.getCollection().forEach(check -> check.onPacketPlayerReceive(event));

                if(RelMovePacketWrapper.isRelMove(event.getPacketType())) {
                    final RelMovePacketWrapper relMovePacketWrapper = new RelMovePacketWrapper(event);
                    try {

                        final Player player = event.getPlayer();
                        final PlayerData data = AntiCheat.getInstance().getDataManager().getPlayerData(player);

                        if(data == null) {
                            AntiCheat.getInstance().getDataManager().players().put(event.getPlayer(), new PlayerData(event.getPlayer()));
                            return;
                        }
                        data.update(relMovePacketWrapper);
                        AntiCheat.getInstance().getTransactionListener().handleRelMove(event.getPlayer());
                    } catch (final InvocationTargetException e) {
                        e.printStackTrace();
                    }
                    checkManager.getCollection().forEach(check -> check.onRelMove(relMovePacketWrapper));
                }

                if(event.getPacketType() == PacketType.Play.Client.BLOCK_PLACE) {
                    for(final Field field : event.getPacket().getBlockData().getFields()) {
                        Bukkit.getServer().broadcastMessage(field.getName());
                    }
                }
            }

            @Override
            public void onPacketSending(final PacketEvent event) {
                checkManager.getCollection().forEach(check -> check.onPacketPlaySend(event));
            }
        });
    }
}
