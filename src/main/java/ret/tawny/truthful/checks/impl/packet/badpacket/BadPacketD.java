package ret.tawny.truthful.checks.impl.packet.badpacket;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.compensation.CompensationTracker;
import ret.tawny.truthful.data.PlayerData;

/**
 * BadPacketD - Entity Interaction Integrity
 *
 * Verifies that a player only interacts with entities that currently exist
 * in the async compensation tracker.
 */
@CheckData(order = 'D', type = CheckType.BAD_PACKET)
public final class BadPacketD extends Check {

    private final CheckBuffer buffer = new CheckBuffer(5.0);

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY)
            return;

        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isServerFrozen())
            return;

        // Skip this strict packet integrity check during unstable simulation windows
        // (teleports/setback freeze), where packet ordering can legitimately jitter.
        if (data.shouldSkipChecks())
            return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        int entityId = wrapper.getEntityId();

        if (entityId < 0) {
            flag(data, "Invalid Entity ID (Negative): " + entityId);
            return;
        }

        CompensationTracker tracker = Truthful.getInstance().getCompensationTracker();
        boolean exists = tracker != null && tracker.hasEntityData(entityId);

        if (!exists) {
            if (buffer.increase(player, 2.0) > 4.0) {
                flag(data, "Non-existent Entity Interaction. ID: " + entityId);
                buffer.reset(player, 2.0);
            }
        } else {
            buffer.decrease(player, 0.5);
        }
    }
}
