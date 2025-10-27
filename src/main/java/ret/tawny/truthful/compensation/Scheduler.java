package ret.tawny.truthful.compensation;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

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
            for(final PacketType packetType : new ArrayList<>(dispatchers.keySet())) {
                if(packetType == null)
                    continue;

                final List<PacketEvent> packetEvents = this.events.get(packetType);
                if(packetEvents == null || packetEvents.isEmpty())
                    continue;

                final List<PacketEvent> matchingEvents = new ArrayList<>();
                for(final PacketEvent queued : packetEvents) {
                    if(queued.getPlayer() == packetEvent.getPlayer()) {
                        matchingEvents.add(queued);
                    }
                }

                if(matchingEvents.isEmpty())
                    continue;

                final List<Consumer<PacketEvent>> consumers = this.dispatchers.get(packetType);
                if(consumers == null || consumers.isEmpty())
                    continue;

                for(final Consumer<PacketEvent> consumer : consumers) {
                    for(final PacketEvent queued : matchingEvents) {
                        consumer.accept(queued);
                    }
                }

                packetEvents.removeAll(matchingEvents);
                if(packetEvents.isEmpty()) {
                    this.events.remove(packetType);
                }
            }

            return;
        }
        this.events.computeIfAbsent(packetEvent.getPacketType(), key -> new ArrayList<>()).add(packetEvent);
    }

    public void registerDispatcher(final Consumer<PacketEvent> consumer, final PacketType packetType) {
        this.dispatchers.putIfAbsent(packetType, new ArrayList<>());
        this.dispatchers.get(packetType).add(consumer);
    }
}
