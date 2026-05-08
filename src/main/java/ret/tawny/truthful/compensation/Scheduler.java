package ret.tawny.truthful.compensation;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import org.bukkit.entity.Player;
import ret.tawny.truthful.wrapper.api.PacketWrapper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Scheduler {

    private final Map<PacketTypeCommon, List<PacketWrapper>> events;

    private final Map<PacketTypeCommon, List<Consumer<PacketWrapper>>> dispatchers;

    /**
     * The Scheduler allows you to read a packet's data on post position update.
     */
    public Scheduler() {
        this.dispatchers = new HashMap<>();
        this.events = new HashMap<>();
    }

    public void register(final PacketTypeCommon type, final Consumer<PacketWrapper> consumer) {
        this.dispatchers.computeIfAbsent(type, k -> new ArrayList<>()).add(consumer);
        this.events.computeIfAbsent(type, k -> new ArrayList<>());
    }

    public void onPacketReceive(final PacketWrapper wrapper) {
        PacketTypeCommon type = wrapper.getType();

        if (RelMovePacketWrapper.isRelMove(type)) {
            for (final PacketTypeCommon packetType : new ArrayList<>(dispatchers.keySet())) {
                if (packetType == null)
                    continue;

                final List<PacketWrapper> packetWrappers = this.events.get(packetType);
                if (packetWrappers == null || packetWrappers.isEmpty())
                    continue;

                final List<PacketWrapper> matchingWrappers = new ArrayList<>();
                Player player = wrapper.getPlayer();
                for (final PacketWrapper queued : packetWrappers) {
                    if (queued.getPlayer() == player) {
                        matchingWrappers.add(queued);
                    }
                }

                if (matchingWrappers.isEmpty())
                    continue;

                final List<Consumer<PacketWrapper>> consumers = this.dispatchers.get(packetType);
                if (consumers != null) {
                    for (final Consumer<PacketWrapper> consumer : consumers) {
                        for (final PacketWrapper queued : matchingWrappers) {
                            consumer.accept(queued);
                        }
                    }
                }

                packetWrappers.removeAll(matchingWrappers);
            }
            return;
        }

        this.events.computeIfAbsent(type, key -> new ArrayList<>()).add(wrapper);
    }

    public void registerDispatcher(final Consumer<PacketWrapper> consumer,
            final PacketTypeCommon packetType) {
        this.dispatchers.computeIfAbsent(packetType, k -> new ArrayList<>()).add(consumer);
    }
}