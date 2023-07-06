package org.voitac.anticheat.compensation;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public final class Scheduler {

    private final HashMap<PacketType, List<PacketEvent>> events;

    private final HashMap<PacketType, List<Consumer<PacketEvent>>> dispatchers;

    /**
     * The Scheduler allows you to read a packet's data on post position update.
     * <p/>
     * This SHOULD not and CANNOT be used to read position updates
     * <p/>
     * It should be relied on to read data that is dependent on the real client state, ie rotation checks
     */
    public Scheduler() {
        this.events = new HashMap<>();
        this.dispatchers = new HashMap<>();
    }

    public void onPacket(final PacketEvent packetEvent) {
        if(RelMovePacketWrapper.isRelMove(packetEvent.getPacketType())) {
            for(final PacketType packetType : dispatchers.keySet()) {
                if(packetType == null)
                    continue;
                final List<PacketEvent> packetEvents = this.events.get(packetType);
                if(packetEvents == null)
                    continue;
                final List<Consumer<PacketEvent>> consumers = this.dispatchers.get(packetType);

                final int size = consumers.size();
                final int eventSize = packetEvents.size();
                for(int i = 0; i < size; ++i) {
                    final PacketEvent packet = packetEvents.get(i);
                    if(packet.getPlayer() != packetEvent.getPlayer())
                        continue;
                    final Consumer<PacketEvent> consumer = consumers.get(i);
                    for(int l = 0; l < eventSize; ++l)
                        consumer.accept(packet);
                }
            }

            return;
        }
        this.events.putIfAbsent(packetEvent.getPacketType(), new ArrayList<>());
        this.events.get(packetEvent.getPacketType()).add(packetEvent);
    }

    public void registerDispatcher(final Consumer<PacketEvent> consumer, final PacketType packetType) {
        this.dispatchers.putIfAbsent(packetType, new ArrayList<>());
        this.dispatchers.get(packetType).add(consumer);
    }
}