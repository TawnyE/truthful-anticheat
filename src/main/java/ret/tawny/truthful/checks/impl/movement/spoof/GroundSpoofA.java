package ret.tawny.truthful.checks.impl.movement.spoof;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'A', type = CheckType.SPOOF)
public final class GroundSpoofA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isServerFrozen() || data.isTeleportTick() || data.isJoinExempt()) return;

        if (!data.isChunkLoaded()) {
            buffer.decrease(player, 0.5);
            return;
        }

        if (data.isAllowFlight() || data.isFlying() || data.isInsideVehicle() || data.isGliding()) {
            return;
        }

        boolean clientGround = wrapper.isGround();
        boolean serverGround = WorldUtils.safeGround(data.getLocation(), data.getWorldCache());

        if (clientGround && !serverGround) {
            if (data.getAirTicks() < 3) {
                buffer.decrease(player, 0.2);
                return;
            }

            if (data.getTicksTracked() - data.getLastBlockPlaceTick() < 10) return;
            if (data.isNearVehicle() || data.isNearEntity()) return;

            // FIXED: Main-thread block verification for Tile Entities (Spawners, Chests)
            // Sometimes the async packet cache drops block entity IDs. We safely check the Bukkit
            // thread before punishing.
            Location loc = new Location(player.getWorld(), wrapper.getX(), wrapper.getY() - 0.2, wrapper.getZ());

            Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), () -> {
                if (!player.isOnline()) return;
                org.bukkit.block.Block block = loc.getBlock();

                // If Bukkit sees a solid block here, the async cache just missed a spawner/chest.
                if (block.getType() != org.bukkit.Material.AIR && block.getType().isSolid()) {
                    return;
                }

                if (buffer.increase(player, 1.0) > 6.0) {
                    flag(data, "Ground Spoof (No Block Below)");
                    if (Truthful.getInstance().getConfiguration().isLagbacks()) {
                        data.executeLagback();
                    }
                    buffer.reset(player, 2.0);
                }
            });

        } else {
            buffer.decrease(player, 0.25);
        }
    }
}