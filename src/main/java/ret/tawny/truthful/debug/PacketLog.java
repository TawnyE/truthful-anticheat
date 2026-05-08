package ret.tawny.truthful.debug;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;

public class PacketLog {
    private final long timestamp;
    private final String packetName;
    private final String packetDetails;
    private final boolean incoming;

    public PacketLog(PacketReceiveEvent event) {
        this.timestamp = System.currentTimeMillis();
        this.packetName = event.getPacketType().toString();
        this.packetDetails = event.getPacketName();
        this.incoming = true;
    }

    public PacketLog(PacketSendEvent event) {
        this.timestamp = System.currentTimeMillis();
        this.packetName = event.getPacketType().toString();
        this.packetDetails = event.getPacketName();
        this.incoming = false;
    }

    @Override
    public String toString() {
        return String.format("%d,%s,%s,%s", timestamp, incoming ? "IN" : "OUT", packetName, packetDetails);
    }
}
