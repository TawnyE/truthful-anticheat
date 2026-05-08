package ret.tawny.truthful.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

public class BrandListener extends PacketListenerAbstract {

    public BrandListener() {
        super(PacketListenerPriority.LOW);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Older 1.20.2 - 1.21.1 method (intercept custom payload)
        if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            WrapperConfigClientPluginMessage wrapper = new WrapperConfigClientPluginMessage(event);
            if (isBrand(wrapper.getChannelName())) {
                String brand = decode(wrapper.getData());
                TruthfulPacketListener.updatePlayerBrand(player, brand);
            }
        }
        else if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
            if (isBrand(wrapper.getChannelName())) {
                String brand = decode(wrapper.getData());
                TruthfulPacketListener.updatePlayerBrand(player, brand);
            }
        }
        // In 1.21.2+, Mojang replaced "minecraft:brand" with ClientInformation changes
        // This is handled natively in PlayerListener.java on join.
    }

    private boolean isBrand(String channel) {
        if (channel == null) return false;
        return channel.equals("minecraft:brand") || channel.equals("MC|Brand");
    }

    private String decode(byte[] data) {
        if (data == null || data.length == 0) return "Unknown";

        try {
            int offset = 0;
            int length = 0;
            int numRead = 0;

            byte read;
            do {
                if (offset >= data.length) break;
                read = data[offset];
                int value = (read & 0x7F);
                length |= (value << (7 * numRead));
                numRead++;
                offset++;
                if (numRead > 5) throw new RuntimeException("VarInt too big");
            } while ((read & 0x80) != 0);

            if (length > 0 && (data.length - offset) == length) {
                String clean = new String(data, offset, length, StandardCharsets.UTF_8).trim();
                if (!clean.isEmpty() && isReadable(clean)) {
                    return clean;
                }
            }
        } catch (Exception ignored) { }

        try {
            String raw = new String(data, StandardCharsets.UTF_8);
            String clean = raw.replaceAll("[^\\x20-\\x7E]", "").trim();

            if (clean.length() > 0) {
                return clean;
            }
        } catch (Exception ignored) {}

        return "Unknown";
    }

    private boolean isReadable(String s) {
        for (char c : s.toCharArray()) {
            if (c < 32 || c == 127) return false;
        }
        return true;
    }
}