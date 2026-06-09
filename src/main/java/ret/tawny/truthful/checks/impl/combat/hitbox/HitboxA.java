package ret.tawny.truthful.checks.impl.combat.hitbox;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.compensation.CompensationTracker;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.hitbox.SimpleHitbox;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'A', type = CheckType.HITBOX)
public final class HitboxA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);
    private final Map<UUID, Integer> pendingAttacks = new ConcurrentHashMap<>();

    private static final int MAX_BACKTRACK = 8;
    // Vanilla: Player hitbox is 0.6×1.8 (standing) / 0.6×1.5 (sneaking) / 0.6×0.6 (swimming)
    private static final double PLAYER_EXPANSION = 0.08;
    private static final double NON_PLAYER_EXPANSION = 0.25;

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        final WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            final Player player = (Player) event.getPlayer();
            pendingAttacks.put(player.getUniqueId(), wrapper.getEntityId());
        }
    }

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        final Player player = wrapper.getPlayer();
        Integer entityId = pendingAttacks.remove(player.getUniqueId());
        if (entityId == null) return;

        final PlayerData data = wrapper.getPlayerData();
        if (data == null || data.isExempt() || player.getGameMode() == GameMode.CREATIVE || Truthful.getInstance().isBedrockPlayer(player)) return;

        final CompensationTracker tracker = Truthful.getInstance().getCompensationTracker();
        if (tracker == null) return;

        final CompensationTracker.CompensatedEntity comp = tracker.getEntityData(entityId);
        if (comp == null) return;

        int tickDelay = (int) Math.floor(data.getPing() / 50.0);
        if (tickDelay > MAX_BACKTRACK) tickDelay = MAX_BACKTRACK;
        if (tickDelay < 0) tickDelay = 0;

        // Full backtrack coverage (same as ReachA fix)
        final int startTick = Math.max(0, tickDelay - MAX_BACKTRACK);
        final int currentTick = org.bukkit.Bukkit.getCurrentTick();

        // Eye positions: current tick
        final double eyeHeightCurrent = data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        final Vector eyeCurrent = new Vector(data.getX(), data.getY() + eyeHeightCurrent, data.getZ());

        // Eye positions: last tick (use last tick's eye height, not current)
        final double lastX = data.getX() - data.getDeltaX();
        final double lastY = data.getY() - data.getDeltaY();
        final double lastZ = data.getZ() - data.getDeltaZ();
        final double eyeHeightLast = data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        final Vector eyeLast = new Vector(lastX, lastY + eyeHeightLast, lastZ);

        // Look vectors for current and last tick
        final Vector[] lookVectors = {
                direction(data.getYaw(), data.getPitch()),
                direction(data.getLastYaw(), data.getLastPitch())
        };

        // Ping compensation: expand hitbox based on latency
        double pingExpansion = Math.min(0.15, data.getPing() * 0.001);
        double sprintExpansion = data.isSprinting() ? 0.05 : 0.0;
        double velocityExpansion = data.hasVelocity() ? 0.10 : 0.0;

        double baseExpansion = (comp.isPlayer ? PLAYER_EXPANSION : NON_PLAYER_EXPANSION)
                + pingExpansion + sprintExpansion + velocityExpansion;

        double minDist = Double.MAX_VALUE;

        // Full backtrack loop (0 to tickDelay, up to MAX_BACKTRACK ticks)
        for (int t = startTick; t <= tickDelay; t++) {
            final SimpleHitbox box = comp.getHitboxAt(t, currentTick);
            if (box == null) continue;

            box.expand(baseExpansion);

            // Use closest-point distance (matches vanilla behavior, same as ReachA)
            double d1 = box.distance(eyeCurrent);
            double d2 = box.distance(eyeLast);

            if (d1 >= 0) minDist = Math.min(minDist, d1);
            if (d2 >= 0) minDist = Math.min(minDist, d2);

            // Also check with look vectors (catches edge cases)
            for (Vector look : lookVectors) {
                double d3 = rayEntryDistance(box, eyeCurrent, look);
                double d4 = rayEntryDistance(box, eyeLast, look);
                if (d3 >= 0) minDist = Math.min(minDist, d3);
                if (d4 >= 0) minDist = Math.min(minDist, d4);
            }
        }

        if (minDist == Double.MAX_VALUE) return;

        // Vanilla reach: 3.0 blocks base
        double maxReach = Truthful.getInstance().getVersionManager().getAdapter().getEntityInteractionRange(player);
        if (data.isSprinting()) maxReach += 0.05;
        if (data.hasVelocity()) maxReach += 0.10;
        if (data.getPing() > 50) maxReach += (data.getPing() * 0.001);

        // Non-player entities get slightly larger hitbox (matches vanilla mob reach changes 1.20.2)
        if (!comp.isPlayer) maxReach += 0.25;

        double trueReach = minDist;
        double over = trueReach - maxReach;

        if (over > 0.02) {
            // Check for massive desync (e.g., entity died/teleported)
            if (minDist > 10.0) return;

            // Determine severity based on how far over
            double severity = 1.0 + (over * 2.0);
            if (severity > 5.0) severity = 5.0;

            if (buffer.increase(player, severity) > 10.0) {
                flag(data, String.format("Hitbox Miss. Reach=%.3f Max=%.3f Over=%.3f", trueReach, maxReach, over));
                buffer.reset(player, 4.0);
            }
        } else {
            buffer.decrease(player, 0.5);
        }
    }

    private Vector direction(final float yaw, final float pitch) {
        final double ry = Math.toRadians(yaw);
        final double rp = Math.toRadians(pitch);
        final double cosP = Math.cos(rp);
        return new Vector(-cosP * Math.sin(ry), -Math.sin(rp), cosP * Math.cos(ry));
    }

    // Same rayEntryDistance as ReachA - closest point on box to ray origin
    private double rayEntryDistance(final SimpleHitbox box, final Vector origin, final Vector dir) {
        final double invX = 1.0 / dir.getX();
        final double invY = 1.0 / dir.getY();
        final double invZ = 1.0 / dir.getZ();

        double tMin = Math.min((box.minX - origin.getX()) * invX, (box.maxX - origin.getX()) * invX);
        double tMax = Math.max((box.minX - origin.getX()) * invX, (box.maxX - origin.getX()) * invX);

        final double tyMin = Math.min((box.minY - origin.getY()) * invY, (box.maxY - origin.getY()) * invY);
        final double tyMax = Math.max((box.minY - origin.getY()) * invY, (box.maxY - origin.getY()) * invY);

        if (tMin > tyMax || tyMin > tMax) return -1;
        tMin = Math.max(tMin, tyMin);
        tMax = Math.min(tMax, tyMax);

        final double tzMin = Math.min((box.minZ - origin.getZ()) * invZ, (box.maxZ - origin.getZ()) * invZ);
        final double tzMax = Math.max((box.minZ - origin.getZ()) * invZ, (box.maxZ - origin.getZ()) * invZ);

        if (tMin > tzMax || tzMin > tMax) return -1;
        tMin = Math.max(tMin, tzMin);
        tMax = Math.min(tMax, tzMax);

        if (Double.isNaN(tMin)) tMin = Double.NEGATIVE_INFINITY;
        if (Double.isNaN(tMax)) tMax = Double.POSITIVE_INFINITY;

        if (tMax < 0) return -1;
        if (tMin > 6.0) return -1;

        return Math.max(0.0, tMin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        pendingAttacks.remove(event.getPlayer().getUniqueId());
    }
}
