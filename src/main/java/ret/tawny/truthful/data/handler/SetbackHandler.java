package ret.tawny.truthful.data.handler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;

public final class SetbackHandler {

    private final PlayerData data;
    private final Player player;

    private Location lastSafeLocation;
    private boolean awaitingTeleport = false;
    private short pendingTransactionId = -1;
    private long lastSetbackTime = 0;
    private static final long TELEPORT_TIMEOUT = 500L;

    public SetbackHandler(PlayerData data) {
        this.data = data;
        this.player = data.getPlayer();
        this.lastSafeLocation = player.getLocation();
    }

    public void updateSafeLocation(Location location) {
        if (!awaitingTeleport) {
            this.lastSafeLocation = location.clone();
        }
    }

    public Location getLastSafeLocation() {
        return lastSafeLocation;
    }

    public void setback() {
        // FIX: Increased to 250ms to prevent spamming teleport packets during lag
        if (System.currentTimeMillis() - lastSetbackTime < 250) return;
        if (awaitingTeleport) return;

        boolean explosionActive = data.getVelocities().hasExplosionVelocity() ||
                data.isInExplosionGraceWindow(1500L);

        if (explosionActive) {
            return;
        }

        this.awaitingTeleport = true;
        this.lastSetbackTime = System.currentTimeMillis();

        Location target = lastSafeLocation;
        if (target == null) target = player.getLocation();

        WrapperPlayServerPlayerPositionAndLook tpPacket = new WrapperPlayServerPlayerPositionAndLook(
                target.getX(), target.getY(), target.getZ(),
                target.getYaw(), target.getPitch(),
                (byte) 0, data.getNextTransactionId(), false
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, tpPacket);

        short uid = data.getNextTransactionId();
        this.pendingTransactionId = uid;

        data.recordTransactionSent(uid);

        if (Truthful.USE_MODERN_PING) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(uid));
        } else {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerWindowConfirmation(0, uid, false));
        }

        if (data.isInventoryOpen()) {
            Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            });
        }
    }

    public boolean onTransaction(short id) {
        if (awaitingTeleport) {
            if (id == pendingTransactionId) {
                awaitingTeleport = false;
                return true;
            }
        }
        return false;
    }

    public boolean shouldBlockMovement() {
        if (awaitingTeleport && System.currentTimeMillis() - lastSetbackTime > TELEPORT_TIMEOUT) {
            awaitingTeleport = false;
        }
        return awaitingTeleport;
    }
}