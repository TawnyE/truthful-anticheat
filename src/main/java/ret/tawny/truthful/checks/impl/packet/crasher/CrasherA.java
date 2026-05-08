package ret.tawny.truthful.checks.impl.packet.crasher;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

/**
 * CrasherA - CustomPayload Crasher Protection
 * Blocks oversized payloads and exploit attempts
 */
@CheckData(order = 'A', type = CheckType.CRASHER)
@SuppressWarnings("unused")
public final class CrasherA extends Check {

    private static final int MAX_CHANNEL_LENGTH = 32;
    private static final int MAX_PAYLOAD_SIZE = 32767;
    private static final int SUSPICIOUS_PAYLOAD_SIZE = 30000;
    private static final int BOOK_MAX_PAYLOAD = 25000;

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            final Player player = (Player) event.getPlayer();
            final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
            if (data == null)
                return;

            if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
                return;
            }

            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
            String channel = wrapper.getChannelName();

            if (channel == null) {
                return;
            }

            // Check 1: Channel Name Length
            if (channel.length() > MAX_CHANNEL_LENGTH) {
                event.setCancelled(true);
                flag(data, String.format("Invalid channel length: %d (max: %d)", channel.length(), MAX_CHANNEL_LENGTH));
                return;
            }

            // Check 2: Payload Size
            byte[] payload = wrapper.getData();
            if (payload == null) {
                return;
            }

            int payloadSize = payload.length;
            boolean isBookChannel = channel.contains("book") || channel.contains("MC|BEdit")
                    || channel.contains("MC|BSign");
            int maxAllowedSize = isBookChannel ? BOOK_MAX_PAYLOAD : SUSPICIOUS_PAYLOAD_SIZE;

            // Block massive payloads (crash exploit)
            if (payloadSize > MAX_PAYLOAD_SIZE) {
                event.setCancelled(true);
                flag(data, String.format("Oversized payload (Crasher): %d bytes", payloadSize));
                return;
            }

            // Flag suspicious payloads
            if (payloadSize > maxAllowedSize) {
                event.setCancelled(true);
                if (buffer.increase(player, 2.0) > 3.0) {
                    flag(data, String.format("Large payload on %s: %d bytes", channel, payloadSize));
                    buffer.reset(player, 1.0);
                }
            } else {
                buffer.decrease(player, 0.1);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}
