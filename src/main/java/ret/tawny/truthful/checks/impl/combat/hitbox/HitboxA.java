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

import java.util.ArrayList;
import java.util.List;

@CheckData(order = 'A', type = CheckType.HITBOX)
public final class HitboxA extends Check {

    private final CheckBuffer buffer = new CheckBuffer(10.0);

    @Override
    public void handlePacketPlayerReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
        if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isExempt()
                || player.getGameMode() == GameMode.CREATIVE
                || Truthful.getInstance().isBedrockPlayer(player)) return;

        int entityId = wrapper.getEntityId();
        CompensationTracker tracker = Truthful.getInstance().getCompensationTracker();
        if (tracker == null) return;

        CompensationTracker.CompensatedEntity comp = tracker.getEntityData(entityId);
        if (comp == null) return;

        CompensationTracker.EntitySnapshot latest = comp.getLatest();
        if (latest == null) return;

        SimpleHitbox box = latest.toHitbox();
        double distToBox = box.distance(new Vector(data.getX(), data.getY() + 1.62, data.getZ()));

        if (distToBox > 4.5D) {
            return;
        }

        long ping = data.getPing();
        double pingTicks = Math.min(CompensationTracker.MAX_BACKTRACK_TICKS, Math.max(0.0, ping / 50.0));
        int tickDelay = (int) Math.ceil(pingTicks);
        int currentTick = tracker.getCurrentTick();

        double currentEyeH = data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        double[] possibleEyeHeights = { currentEyeH, 1.62D, 1.27D, 0.40D };

        // Test actual recorded attacker locations rather than naive linear extrapolation
        List<Vector> attackerEyePositions = new ArrayList<>();
        attackerEyePositions.add(new Vector(data.getX(), data.getY(), data.getZ()));
        attackerEyePositions.add(new Vector(data.getPositionTracker().getLastX(), data.getPositionTracker().getLastY(), data.getPositionTracker().getLastZ()));

        List<Vector> lookDirs = new ArrayList<>();
        lookDirs.add(getLookVector(data.getYaw(), data.getPitch()));
        lookDirs.add(getLookVector(data.getLastYaw(), data.getPitch()));
        lookDirs.add(getLookVector(data.getLastYaw(), data.getLastPitch()));

        boolean cleanRayIntersection = false;
        double minMissDistance = Double.MAX_VALUE;

        for (int offset = -1; offset <= 1; offset++) {
            int targetTickDelay = Math.max(0, tickDelay + offset);
            SimpleHitbox box = comp.getHitboxAt(targetTickDelay, currentTick);
            if (box == null) continue;

            SimpleHitbox standardBox = new SimpleHitbox(box.minX, box.minY, box.minZ, 0.6, 1.8);
            standardBox.expand(0.12D);

            for (Vector basePos : attackerEyePositions) {
                for (Vector dir : lookDirs) {
                    for (double eyeH : possibleEyeHeights) {
                        Vector eyePos = new Vector(basePos.getX(), basePos.getY() + eyeH, basePos.getZ());
                        if (standardBox.intersectsRay(eyePos, dir, 6.0D)) {
                            cleanRayIntersection = true;
                            break;
                        }
                        double miss = calculateRayMissDistance(standardBox, eyePos, dir);
                        if (miss < minMissDistance) {
                            minMissDistance = miss;
                        }
                    }
                    if (cleanRayIntersection) break;
                }
                if (cleanRayIntersection) break;
            }
            if (cleanRayIntersection) break;
        }

        if (cleanRayIntersection) {
            buffer.decrease(player, 0.5);
            return;
        }

        double proximityGrace = Math.max(0.0D, 1.5D - distToBox) * 0.35D;
        double rotDelta = Math.abs(data.getDeltaYaw()) + Math.abs(data.getDeltaPitch());
        double allowedMiss = 0.15D + proximityGrace + (rotDelta > 8.0f ? 0.15D : 0.0D);

        if (minMissDistance > allowedMiss) {
            double expansion = minMissDistance - allowedMiss;
            if (buffer.increase(player, 1.0 + expansion * 3.0) > 6.0) {
                flag(data, String.format("HitboxExpansion miss=%.3f allowed=%.3f (dist=%.2fm, rotDelta=%.1f deg)",
                        minMissDistance, allowedMiss, distToBox, rotDelta));
                buffer.reset(player, 3.0);
            }
        } else {
            buffer.decrease(player, 0.25);
        }
    }

    private static double calculateRayMissDistance(SimpleHitbox box, Vector origin, Vector dir) {
        double closestDist = Double.MAX_VALUE;
        Vector boxCenter = new Vector(
                (box.minX + box.maxX) * 0.5D,
                (box.minY + box.maxY) * 0.5D,
                (box.minZ + box.maxZ) * 0.5D
        );
        Vector vecToCenter = boxCenter.clone().subtract(origin);
        double tCenter = vecToCenter.dot(dir);

        double startT = Math.max(0.0D, tCenter - 1.5D);
        double endT = Math.min(6.0D, tCenter + 1.5D);

        for (double t = startT; t <= endT; t += 0.05D) {
            Vector pointOnRay = origin.clone().add(dir.clone().multiply(t));
            double distToBox = box.distance(pointOnRay);
            if (distToBox < closestDist) {
                closestDist = distToBox;
            }
        }
        return closestDist == Double.MAX_VALUE ? 0.0D : closestDist;
    }

    private static Vector getLookVector(float yaw, float pitch) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double cosP = Math.cos(rp);
        return new Vector(-cosP * Math.sin(ry), -Math.sin(rp), cosP * Math.cos(ry));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
    }
}