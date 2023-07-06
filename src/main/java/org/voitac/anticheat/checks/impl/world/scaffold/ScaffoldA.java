package org.voitac.anticheat.checks.impl.world.scaffold;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.voitac.anticheat.AntiCheat;
import org.voitac.anticheat.checks.api.Check;
import org.voitac.anticheat.checks.api.data.CheckData;
import org.voitac.anticheat.checks.api.data.CheckType;
import org.voitac.anticheat.data.PlayerData;
import org.voitac.anticheat.utils.world.WorldUtils;
import org.voitac.anticheat.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;
import org.voitac.anticheat.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayList;
import java.util.List;

@CheckData(order = 'A', type = CheckType.SCAFFOLD)
public final class ScaffoldA extends Check {

    private final List<Player> scheduled;

    public ScaffoldA() {
        this.scheduled = new ArrayList<>();
    }

    @Override
    public void onPacketPlayerReceive(final PacketEvent event) {
        if(!event.getPacketType().equals(PacketType.Play.Client.BLOCK_PLACE))
            return;
        final PlayerBlockPlacePacketWrapper blockPlacePacketWrapper = new PlayerBlockPlacePacketWrapper(event);

        final Player player = event.getPlayer();
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);
        final BlockFace blockFace = blockPlacePacketWrapper.getBlockFace();

        if(WorldUtils.getBlockFace(event.getPlayer()).equals(blockFace))
            return;

        if(blockFace == null)
            return;

        // Sort of retarded check as there is technically a way it could false
        // This could happen as actions come before position update (fuck you mojang)
        // We can sort of help this by exemption a player who's is turning fast but this is a primitive approach
        if((blockFace.equals(BlockFace.UP) || blockFace.equals(BlockFace.DOWN)) && (playerData.getDeltaPitch() > 15 || playerData.getLastDeltaPitch() > 15)) {
            this.scheduled.add(player);
            return;
        }
        if(!(blockFace.equals(BlockFace.UP) || blockFace.equals(BlockFace.DOWN)) && (playerData.getDeltaYaw() > 20 || playerData.getLastDeltaYaw() > 20)) {
            this.scheduled.add(player);
        }
    }

    @Override
    public void onRelMove(final RelMovePacketWrapper relMovePacketWrapper) {
        final Player player = relMovePacketWrapper.getPlayer();
        final PlayerData playerData = AntiCheat.getInstance().getDataManager().getPlayerData(player);
        if(this.scheduled.contains(relMovePacketWrapper.getPlayer())) {
            this.scheduled.remove(player);

            formattedFlag(this, playerData,"Expected facing [" + WorldUtils.getBlockFace(player) + "]");
        }
    }

}
