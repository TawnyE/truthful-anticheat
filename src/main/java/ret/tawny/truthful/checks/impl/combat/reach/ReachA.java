package ret.tawny.truthful.checks.impl.combat.reach;

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

@CheckData(order = 'A', type = CheckType.REACH)
public final class ReachA extends Check {

    private static final double HARD_OVER_REACH = 0.35D;
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

        long ping = data.getPing();
        double pingTicks = Math.min(CompensationTracker.MAX_BACKTRACK_TICKS, Math.max(0.0, ping / 50.0));
        int tickDelay = (int) Math.ceil(pingTicks);
        int currentTick = tracker.getCurrentTick();

        SimpleHitbox box = comp.getHitboxAt(tickDelay, currentTick);
        if (box == null) return;

        double currentEyeH = data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        double[] possibleEyeHeights = { currentEyeH, 1.62D, 1.27D, 0.40D };

        // Test actual recorded attacker locations (current and last tick) rather than naive linear extrapolation
        List<Vector> attackerEyePositions = new ArrayList<>();
        attackerEyePositions.add(new Vector(data.getX(), data.getY(), data.getZ()));
        attackerEyePositions.add(new Vector(data.getPositionTracker().getLastX(), data.getPositionTracker().getLastY(), data.getPositionTracker().getLastZ()));

        List<Vector> lookDirs = new ArrayList<>();
        lookDirs.add(getLookVector(data.getYaw(), data.getPitch()));
        lookDirs.add(getLookVector(data.getLastYaw(), data.getPitch()));
        lookDirs.add(getLookVector(data.getLastYaw(), data.getLastPitch()));

        // Search target entity bounding box history across latency window [tickDelay - 1, tickDelay + 1]
        double minReachDistance = Double.MAX_VALUE;

        for (int offset = -1; offset <= 1; offset++) {
            int targetTickDelay = Math.max(0, tickDelay + offset);
            SimpleHitbox box = comp.getHitboxAt(targetTickDelay, currentTick);
            if (box == null) continue;

            SimpleHitbox testBox = new SimpleHitbox(box.minX, box.minY, box.minZ, 0.6, 1.8);
            double hitboxMargin = 0.08D + (ping > 50 ? Math.min(0.15D, pingTicks * 0.02D) : 0.0D);
            testBox.expand(hitboxMargin);

            for (Vector basePos : attackerEyePositions) {
                for (Vector dir : lookDirs) {
                    for (double eyeH : possibleEyeHeights) {
                        Vector eyePos = new Vector(basePos.getX(), basePos.getY() + eyeH, basePos.getZ());

                        if (isInside(testBox, eyePos)) {
                            minReachDistance = 0.0D;
                            break;
                        }

                        Vector intercept = calculateRayIntercept(testBox, eyePos, dir, 6.0D);
                        if (intercept != null) {
                            double dist = eyePos.distance(intercept);
                            if (dist < minReachDistance) {
                                minReachDistance = dist;
                            }
                        } else {
                            double dist = testBox.distance(eyePos);
                            if (dist < minReachDistance) {
                                minReachDistance = dist;
                            }
                        }
                    }
                    if (minReachDistance == 0.0D) break;
                }
                if (minReachDistance == 0.0D) break;
            }
            if (minReachDistance == 0.0D) break;
        }

        double maxReach = comp.baseReach + (data.isSprinting() ? 0.05D : 0.0D);

        if (minReachDistance > maxReach + HARD_OVER_REACH) {
            if (buffer.increase(player, 2.5) > 4.5) {
                flag(data, String.format("ReachHard dist=%.3f max=%.3f (base=%.2f rtt=%dms)",
                        minReachDistance, maxReach, comp.baseReach, ping));
                buffer.reset(player, 4.0);
            }
            return;
        }

        if (minReachDistance > maxReach + 0.02D) {
            double over = minReachDistance - maxReach;
            if (buffer.increase(player, 1.0 + over * 2.0) > 10.0) {
                flag(data, String.format("Reach dist=%.3f max=%.3f over=%.3f (rtt=%dms)",
                        minReachDistance, maxReach, over, ping));
                buffer.reset(player, 4.5);
            }
        } else {
            buffer.decrease(player, 0.5);
        }
    }

    private static Vector calculateRayIntercept(SimpleHitbox box, Vector origin, Vector dir, double maxDist) {
        double invX = 1.0 / (Math.abs(dir.getX()) < 1e-6 ? 1e-6 : dir.getX());
        double invY = 1.0 / (Math.abs(dir.getY()) < 1e-6 ? 1e-6 : dir.getY());
        double invZ = 1.0 / (Math.abs(dir.getZ()) < 1e-6 ? 1e-6 : dir.getZ());

        double t1 = (box.minX - origin.getX()) * invX;
        double t2 = (box.maxX - origin.getX()) * invX;
        double tMin = Math.min(t1, t2);
        double tMax = Math.max(t1, t2);

        double ty1 = (box.minY - origin.getY()) * invY;
        double ty2 = (box.maxY - origin.getY()) * invY;
        tMin = Math.max(tMin, Math.min(ty1, ty2));
        tMax = Math.min(tMax, Math.max(ty1, ty2));

        double tz1 = (box.minZ - origin.getZ()) * invZ;
        double tz2 = (box.maxZ - origin.getZ()) * invZ;
        tMin = Math.max(tMin, Math.min(tz1, tz2));
        tMax = Math.min(tMax, Math.max(tz1, tz2));

        if (tMax >= Math.max(0.0, tMin) && tMin <= maxDist) {
            double t = Math.max(0.0, tMin);
            return origin.clone().add(dir.clone().multiply(t));
        }
        return null;
    }

    private static boolean isInside(SimpleHitbox box, Vector vec) {
        return vec.getX() >= box.minX && vec.getX() <= box.maxX &&
                vec.getY() >= box.minY && vec.getY() <= box.maxY &&
                vec.getZ() >= box.minZ && vec.getZ() <= box.maxZ;
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