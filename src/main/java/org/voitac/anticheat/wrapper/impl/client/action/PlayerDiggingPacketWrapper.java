package org.voitac.anticheat.wrapper.impl.client.action;

import com.comphenix.protocol.events.PacketEvent;
import org.voitac.anticheat.wrapper.api.PacketWrapper;

public final class PlayerDiggingPacketWrapper extends PacketWrapper {
    public PlayerDiggingPacketWrapper(final PacketEvent packetEvent) {
        super(packetEvent);
    }
}
