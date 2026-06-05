package ret.tawny.truthful.checks.impl.movement.spoof;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

@CheckData(order = 'F', type = CheckType.SPOOF)
public final class GroundSpoofF extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);

        if (data == null || data.isServerFrozen() || data.isTeleportTick()) return;

        if (!data.isChunkLoaded()) {
            buffer.decrease(player, 0.5);
            return;
        }

        if (data.isAllowFlight() || data.isInsideVehicle() || data.isGliding()) return;
        if (data.getAirTicks() < 4) return;

        if (wrapper.isGround()) {

            boolean physicalGround = WorldUtils.safeGround(data.getLocation(), data.getWorldCache());

            if (!physicalGround && (data.isNearVehicle() || data.isNearEntity())) {
                physicalGround = true;
            }

            if (!physicalGround) {

                int ticksSincePlace = data.getTicksTracked() - data.getLastBlockPlaceTick();
                int ticksSinceGhost = data.getTicksTracked() - data.getLastGhostBlockTick();

                if (ticksSincePlace < 10 || ticksSinceGhost < 10) {
                    buffer.decrease(player, 0.5);
                    return;
                }

                if (data.getDeltaY() < -0.07 && WorldUtils.isGroundBelow(data, 1.2)) {
                    buffer.decrease(player, 0.2);
                    return;
                }

                // FIXED: Main-thread block verification for Tile Entities (Spawners, Chests)
                Location loc = new Location(player.getWorld(), wrapper.getX(), wrapper.getY() - 0.2, wrapper.getZ());

                Truthful.getInstance().getServerScheduler().runRegion(player, () -> {
                    if (!player.isOnline()) return;
                    org.bukkit.block.Block block = loc.getBlock();

                    if (block.getType() != org.bukkit.Material.AIR && block.getType().isSolid()) {
                        return;
                    }

                    if (buffer.increase(player, 0.85) > 5.0) {
                        flag(data, "Physical Ground Spoof (No Collision)");
                        if (Truthful.getInstance().getConfiguration().isLagbacks()) {
                            data.executeLagback();
                        }
                        buffer.reset(player, 2.0);
                    }
                });

            } else {
                buffer.decrease(player, 0.5);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}