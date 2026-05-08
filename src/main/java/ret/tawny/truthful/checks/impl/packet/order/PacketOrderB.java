package ret.tawny.truthful.checks.impl.packet.order;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PacketOrderB — Attack While Blocking/Using Item Detection
 *
 * Detects players attacking entities while actively using an item (blocking,
 * eating, drawing a bow). In vanilla, the client sends RELEASE_USE_ITEM
 * before attacking. If an attack arrives while the player is confirmed
 * to be using a slow item, it indicates autoblock or NoSlow combat.
 *
 * FIX: Previously, PLAYER_BLOCK_PLACEMENT was treated as "using an item",
 * which caused false positives when rapidly switching between block
 * interaction (mining/placing blocks) and attacking nearby players.
 * Now only USE_ITEM (actual item use like eating/blocking/bow) sets
 * the interaction state, AND we require isSlowItem() confirmation.
 */
@CheckData(order = 'B', type = CheckType.PACKET_ORDER)
@SuppressWarnings("unused")
public final class PacketOrderB extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, ItemUseState> stateMap = new ConcurrentHashMap<>();

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        final Player player = (Player) event.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || Truthful.getInstance().isBedrockPlayer(player))
            return;

        PacketTypeCommon type = event.getPacketType();

        ItemUseState state = stateMap.computeIfAbsent(player.getUniqueId(), k -> new ItemUseState());

        // 1. Track ONLY genuine item use (eating, blocking, drawing bow)
        //    NOT block placement — block placement is NOT "using an item"
        if (type == PacketType.Play.Client.USE_ITEM) {
            // Only mark as using if the player is holding a slow item
            if (data.isSlowItem()) {
                state.usingItem = true;
                state.useStartTick = data.getTicksTracked();
            }
        }

        // 2. Track block digging to create a grace window
        //    Players legitimately right-click blocks then attack — the digging
        //    action creates a brief window where we should NOT flag.
        else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = wrapper.getAction();

            if (action == DiggingAction.RELEASE_USE_ITEM ||
                    action == DiggingAction.DROP_ITEM ||
                    action == DiggingAction.DROP_ITEM_STACK) {
                state.usingItem = false;
            }

            // Any digging action creates a 3-tick grace window
            state.lastDiggingTick = data.getTicksTracked();
        }

        // 3. Check Attack while using item
        else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();

            if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {

                // Must actually be using a slow item (sword block, food, bow, etc.)
                boolean confirmed = state.usingItem
                        && data.isUsingItem()
                        && data.isSlowItem();

                // Grace window: don't flag if player was digging recently
                // (avoids the block-interact → attack false positive)
                int ticksSinceDigging = data.getTicksTracked() - state.lastDiggingTick;
                boolean inDiggingGrace = ticksSinceDigging >= 0 && ticksSinceDigging <= 3;

                // Must have been using the item for at least 3 ticks to confirm
                int useAge = data.getTicksTracked() - state.useStartTick;
                boolean confirmedDuration = useAge >= 3;

                if (confirmed && confirmedDuration && !inDiggingGrace) {
                    if (buffer.increase(player, 1.0) > 8.0) {
                        flag(data, String.format("Attack while using item (useTicks=%d)", data.getUsingItemTicks()));
                        buffer.reset(player, 4.0);
                    }
                } else {
                    buffer.decrease(player, 0.2);
                }
            }
        }

        // 4. Reset on movement tick (flying packet)
        else if (isFlying(type)) {
            // Don't reset usingItem on flying — the server tracks this via use ticks.
            // Only reset if the server says they stopped using.
            if (!data.isUsingItem()) {
                state.usingItem = false;
            }
        }
    }

    private boolean isFlying(PacketTypeCommon type) {
        return type == PacketType.Play.Client.PLAYER_FLYING ||
                type == PacketType.Play.Client.PLAYER_POSITION ||
                type == PacketType.Play.Client.PLAYER_ROTATION ||
                type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    }

    @Override
    public void onQuit(final org.bukkit.event.player.PlayerQuitEvent event) {
        stateMap.remove(event.getPlayer().getUniqueId());
        buffer.remove(event.getPlayer());
    }

    private static class ItemUseState {
        boolean usingItem = false;
        int useStartTick = -100;
        int lastDiggingTick = -100;
    }
}