package ret.tawny.truthful.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.registry.CheckRegistry;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

public final class PacketListener {

    public PacketListener(final CheckRegistry checkManager) {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(Truthful.getInstance().getPlugin(), ListenerPriority.HIGH,
                // List of packets to listen to
                PacketType.Play.Client.ABILITIES,
                PacketType.Play.Client.BLOCK_DIG,
                PacketType.Play.Client.BLOCK_PLACE,
                PacketType.Play.Client.USE_ITEM,
                PacketType.Play.Client.CUSTOM_PAYLOAD,
                PacketType.Play.Client.ENTITY_ACTION,

                // Player Movement Packets (FLYING is removed)
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK,
                PacketType.Play.Client.LOOK,

                PacketType.Play.Client.HELD_ITEM_SLOT,
                PacketType.Play.Client.ARM_ANIMATION,
                PacketType.Play.Client.SPECTATE,

                // Server-side packets
                PacketType.Play.Server.ENTITY_VELOCITY,
                PacketType.Play.Server.ENTITY_TELEPORT,
                PacketType.Play.Server.POSITION
        ) {
            @Override
            public void onPacketReceiving(final PacketEvent event) {
                final Player player = event.getPlayer();
                final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

                if (data == null) {
                    return; // Player data not loaded yet, ignore packet
                }

                Truthful.getInstance().getPlayerListener().onPacket(event);
                checkManager.getCollection().forEach(check -> check.onPacketPlayerReceive(event));

                if (RelMovePacketWrapper.isRelMove(event.getPacketType())) {
                    final RelMovePacketWrapper relMovePacketWrapper = new RelMovePacketWrapper(event);
                    data.update(relMovePacketWrapper);
                    checkManager.getCollection().forEach(check -> check.onRelMove(relMovePacketWrapper));
                }
            }

            @Override
            public void onPacketSending(final PacketEvent event) {
                Truthful.getInstance().getPlayerListener().onPacket(event);
                checkManager.getCollection().forEach(check -> check.onPacketPlaySend(event));
            }
        });
    }
}