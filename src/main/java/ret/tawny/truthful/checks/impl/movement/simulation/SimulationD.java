package ret.tawny.truthful.checks.impl.movement.simulation;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.WorldUtils;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'D', type = CheckType.SIMULATION)
public final class SimulationD extends Check {

    private static final double BOAT_MAX_XZ          = 1.25D;
    private static final double BOAT_ICE_MAX_XZ      = 5.0D;
    private static final double BOAT_BLUE_ICE_MAX_XZ = 9.5D;
    private static final double BOAT_MAX_Y            = 0.7D;

    private static final double HORSE_MAX_XZ          = 1.15D;
    private static final double HORSE_MAX_Y           = 1.05D;
    private static final double CAMEL_MAX_XZ          = 1.40D;
    private static final double CAMEL_MAX_Y           = 1.05D;

    private static final double LLAMA_MAX_XZ          = 0.60D;
    private static final double LLAMA_MAX_Y           = 0.65D;

    private static final double PIG_MAX_XZ            = 0.50D;
    private static final double PIG_MAX_Y             = 0.52D;

    private static final double STRIDER_MAX_XZ        = 0.65D;
    private static final double STRIDER_MAX_Y         = 0.50D;

    private static final double MINECART_MAX_XZ       = 8.50D;
    private static final double MINECART_MAX_Y        = 1.00D;

    private static final double UNKNOWN_MAX_XZ        = 1.50D;
    private static final double UNKNOWN_MAX_Y         = 1.05D;

    private static final double ENTITY_PUSH_MAX_XZ    = 1.5D;
    private static final double ENTITY_PUSH_MAX_Y     = 0.95D;

    private static final int MOUNT_GRACE_TICKS        = 15;
    private static final int DISMOUNT_GRACE_TICKS     = 20;

    private static final double BUFFER_FLAG_THRESHOLD = 6.0D;
    private static final double BUFFER_RESET_VALUE    = 3.0D;

    private enum Tag {
        BOAT, HORSE, DONKEY, MULE, LLAMA, PIG, STRIDER, CAMEL, MINECART, UNKNOWN_MOUNT,
        ON_ICE, ON_BLUE_ICE, IN_LIQUID, BOUNCY_BLOCK, NEAR_ENTITY_PUSH,
        MOUNT_GRACE, DISMOUNT_GRACE, TELEPORT_GRACE, VELOCITY_RECEIVED, SERVER_FROZEN,
        SOURCE_PACKET, SOURCE_EVENT,
        V_SPEED, V_FLY, V_PUSH_XZ, V_PUSH_Y,
    }

    private static final class State {
        int  mountTick   = -1000;
        int  dismountTick = -1000;
        String vehicleTypeName = "";
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();
    private final CheckBuffer buffer = new CheckBuffer(BUFFER_FLAG_THRESHOLD);

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;
        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        final Player player = data.getPlayer();
        if (player == null) return;

        final int ticksNow = data.getTicksTracked();
        final State st = states.computeIfAbsent(player.getUniqueId(), k -> new State());

        boolean currentlyMounted = data.isInsideVehicle();
        boolean wasMounted = !st.vehicleTypeName.isEmpty();

        if (currentlyMounted && !wasMounted) {
            st.mountTick = ticksNow;
            Entity vehicle = player.getVehicle();
            st.vehicleTypeName = vehicle != null ? vehicle.getType().name() : "UNKNOWN";
        } else if (!currentlyMounted && wasMounted) {
            st.dismountTick = ticksNow;
            st.vehicleTypeName = "";
        }

        if (currentlyMounted) {
            Entity vehicle = player.getVehicle();
            if (vehicle == null) return;

            final EnumSet<Tag> tags = buildVehicleTags(data, vehicle, st, ticksNow, Tag.SOURCE_PACKET);

            double distXZ = data.getDeltaXZ();
            double deltaY = data.getDeltaY();
            Vector vehicleVel = vehicle.getVelocity();

            double[] limits = getLimits(tags);
            double maxXZ = limits[0];
            double maxY  = limits[1];

            if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.DISMOUNT_GRACE)) {
                if (deltaY > 1.5 || distXZ > 3.0) {
                    tags.remove(Tag.MOUNT_GRACE);
                    tags.remove(Tag.DISMOUNT_GRACE);
                }
            }

            if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.DISMOUNT_GRACE)
                    || tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.SERVER_FROZEN)) {
                buffer.decrease(player, 0.25D);
                return;
            }
            if (tags.contains(Tag.VELOCITY_RECEIVED)) {
                buffer.decrease(player, 0.3D);
                return;
            }

            String flagReason = null;
            double severity   = 0.0D;

            if (deltaY > maxY && !tags.contains(Tag.BOUNCY_BLOCK) && vehicleVel.getY() < deltaY - 0.1D) {
                tags.add(Tag.V_FLY);
                double dev = deltaY - maxY;
                flagReason = String.format("Area Fail (V_Fly) Y=%.4f max=%.4f vVelY=%.4f tags=%s", deltaY, maxY, vehicleVel.getY(), tags);
                severity = dev * 10.0D;
            }

            if (flagReason == null) {
                double vehicleSpeedXZ = Math.hypot(vehicleVel.getX(), vehicleVel.getZ());
                double minVehicleSpeed = tags.contains(Tag.ON_ICE) || tags.contains(Tag.ON_BLUE_ICE) ? 0.5D : 0.1D;
                if (distXZ > maxXZ && vehicleSpeedXZ < minVehicleSpeed) {
                    tags.add(Tag.V_SPEED);
                    double dev = distXZ - maxXZ;
                    flagReason = String.format("Area Fail (V_Speed) XZ=%.4f max=%.4f vSpd=%.4f tags=%s", distXZ, maxXZ, vehicleSpeedXZ, tags);
                    severity = dev * 8.0D;
                }
            }

            applyFlag(data, player, flagReason, severity, tags, true);
            return;
        }

        if (data.isNearEntity()) {
            if (data.hasVelocity() || data.isExempt(ExemptionType.VELOCITY)) {
                buffer.decrease(player, 0.25D);
                return;
            }
            if (data.isTeleportTick() || data.isServerFrozen()) {
                buffer.decrease(player, 0.25D);
                return;
            }
            if (ticksNow - st.dismountTick < DISMOUNT_GRACE_TICKS) {
                buffer.decrease(player, 0.25D);
                return;
            }

            final EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
            tags.add(Tag.NEAR_ENTITY_PUSH);
            tags.add(Tag.SOURCE_PACKET);
            if (WorldUtils.isNearIceWide(player)) tags.add(Tag.ON_ICE);
            if (WorldUtils.isBouncy(player))  tags.add(Tag.BOUNCY_BLOCK);

            double distXZ = data.getDeltaXZ();
            double deltaY = data.getDeltaY();

            String flagReason = null;
            double severity   = 0.0D;

            double pushMaxXZ = tags.contains(Tag.ON_ICE) ? ENTITY_PUSH_MAX_XZ * 2.5D : ENTITY_PUSH_MAX_XZ;
            if (distXZ > pushMaxXZ) {
                tags.add(Tag.V_PUSH_XZ);
                double dev = distXZ - pushMaxXZ;
                flagReason = String.format("Area Fail (V_PushXZ) XZ=%.4f max=%.4f tags=%s", distXZ, pushMaxXZ, tags);
                severity = dev * 6.0D;
            }

            if (deltaY > ENTITY_PUSH_MAX_Y && !tags.contains(Tag.BOUNCY_BLOCK)) {
                tags.add(Tag.V_PUSH_Y);
                double dev = deltaY - ENTITY_PUSH_MAX_Y;
                String extra = String.format("Area Fail (V_PushY) Y=%.4f max=%.4f tags=%s", deltaY, ENTITY_PUSH_MAX_Y, tags);
                if (flagReason == null || dev * 5.0D > severity) {
                    flagReason = extra;
                    severity   = dev * 5.0D;
                }
            }

            applyFlag(data, player, flagReason, severity, tags, false);
            return;
        }

        buffer.decrease(player, 0.1D);
    }

    @Override
    public void onVehicleMove(final VehicleMoveEvent event) {
        if (event.getVehicle().getPassengers().isEmpty()) return;
        if (!(event.getVehicle().getPassengers().get(0) instanceof Player p)) return;

        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(p);
        if (data == null) return;

        final int ticksNow = data.getTicksTracked();
        final State st = states.computeIfAbsent(p.getUniqueId(), k -> new State());

        final EnumSet<Tag> tags = buildVehicleTags(data, event.getVehicle(), st, ticksNow, Tag.SOURCE_EVENT);

        double vehicleDeltaX = event.getTo().getX() - event.getFrom().getX();
        double vehicleDeltaY = event.getTo().getY() - event.getFrom().getY();
        double vehicleDeltaZ = event.getTo().getZ() - event.getFrom().getZ();
        double distXZ = Math.hypot(vehicleDeltaX, vehicleDeltaZ);

        if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.DISMOUNT_GRACE)) {
            if (vehicleDeltaY > 1.5 || distXZ > 3.0) {
                tags.remove(Tag.MOUNT_GRACE);
                tags.remove(Tag.DISMOUNT_GRACE);
            }
        }

        if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.TELEPORT_GRACE)
                || tags.contains(Tag.SERVER_FROZEN) || tags.contains(Tag.DISMOUNT_GRACE)) {
            buffer.decrease(p, 0.25D);
            return;
        }
        if (tags.contains(Tag.VELOCITY_RECEIVED)) {
            buffer.decrease(p, 0.25D);
            return;
        }

        double[] limits = getLimits(tags);
        double maxXZ = limits[0];
        double maxY  = limits[1];

        String flagReason = null;
        double severity   = 0.0D;

        if (distXZ > maxXZ) {
            tags.add(Tag.V_SPEED);
            double dev = distXZ - maxXZ;
            flagReason = String.format("Area Fail (V_Speed/Evt) XZ=%.4f max=%.4f tags=%s", distXZ, maxXZ, tags);
            severity = dev * 5.0D;
        }

        Vector vehicleVel = event.getVehicle().getVelocity();
        if (vehicleDeltaY > maxY && !tags.contains(Tag.BOUNCY_BLOCK) && vehicleVel.getY() < vehicleDeltaY - 0.1D) {
            tags.add(Tag.V_FLY);
            double dev = vehicleDeltaY - maxY;
            String extra = String.format("Area Fail (V_Fly/Evt) Y=%.4f max=%.4f tags=%s", vehicleDeltaY, maxY, tags);
            if (flagReason == null || dev * 5.0D > severity) {
                flagReason = extra;
                severity   = dev * 5.0D;
            }
        }

        applyFlag(data, p, flagReason, severity, tags, true);
    }

    private EnumSet<Tag> buildVehicleTags(PlayerData data, Entity vehicle, State st, int ticksNow, Tag source) {
        final EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);
        tags.add(source);

        String vName = vehicle.getType().name().toUpperCase();
        if      (vName.contains("BOAT"))     tags.add(Tag.BOAT);
        else if (vName.contains("CAMEL"))    tags.add(Tag.CAMEL);
        else if (vName.contains("HORSE"))    tags.add(Tag.HORSE);
        else if (vName.contains("DONKEY"))   tags.add(Tag.DONKEY);
        else if (vName.contains("MULE"))     tags.add(Tag.MULE);
        else if (vName.contains("LLAMA"))    tags.add(Tag.LLAMA);
        else if (vName.contains("PIG"))      tags.add(Tag.PIG);
        else if (vName.contains("STRIDER"))  tags.add(Tag.STRIDER);
        else if (vName.contains("MINECART")) tags.add(Tag.MINECART);
        else                                 tags.add(Tag.UNKNOWN_MOUNT);

        Player player = data.getPlayer();
        if (WorldUtils.isNearIceWide(player))                    {
            tags.add(Tag.ON_ICE);
            if (WorldUtils.isNearBlueIce(player)) tags.add(Tag.ON_BLUE_ICE);
        }
        if (WorldUtils.isBouncy(player))                     tags.add(Tag.BOUNCY_BLOCK);
        if (data.isInLiquid() || WorldUtils.isNearLiquid(player)) tags.add(Tag.IN_LIQUID);

        if (ticksNow - st.mountTick < MOUNT_GRACE_TICKS) tags.add(Tag.MOUNT_GRACE);
        if (data.isExempt(ExemptionType.VEHICLE_EXIT) || ticksNow - st.dismountTick < DISMOUNT_GRACE_TICKS) tags.add(Tag.DISMOUNT_GRACE);
        if (data.isTeleportTick() || data.getTicksSinceTeleport() < 5 || ticksNow - data.getLastVehicleExitTick() < 10) tags.add(Tag.TELEPORT_GRACE);
        if (data.hasVelocity() || ticksNow - data.getLastVelocityTick() <= 5) tags.add(Tag.VELOCITY_RECEIVED);
        if (data.isServerFrozen()) tags.add(Tag.SERVER_FROZEN);

        return tags;
    }

    private double[] getLimits(EnumSet<Tag> tags) {
        double maxXZ;
        double maxY;

        if (tags.contains(Tag.BOAT)) {
            if (tags.contains(Tag.ON_BLUE_ICE)) {
                maxXZ = BOAT_BLUE_ICE_MAX_XZ;
            } else if (tags.contains(Tag.ON_ICE)) {
                maxXZ = BOAT_ICE_MAX_XZ;
            } else {
                maxXZ = BOAT_MAX_XZ;
            }
            maxY  = BOAT_MAX_Y;
        } else if (tags.contains(Tag.CAMEL)) {
            maxXZ = CAMEL_MAX_XZ;
            maxY  = CAMEL_MAX_Y;
        } else if (tags.contains(Tag.HORSE) || tags.contains(Tag.DONKEY) || tags.contains(Tag.MULE)) {
            maxXZ = HORSE_MAX_XZ;
            maxY  = HORSE_MAX_Y;
        } else if (tags.contains(Tag.LLAMA)) {
            maxXZ = LLAMA_MAX_XZ;
            maxY  = LLAMA_MAX_Y;
        } else if (tags.contains(Tag.PIG)) {
            maxXZ = PIG_MAX_XZ;
            maxY  = PIG_MAX_Y;
        } else if (tags.contains(Tag.STRIDER)) {
            maxXZ = STRIDER_MAX_XZ;
            maxY  = STRIDER_MAX_Y;
        } else if (tags.contains(Tag.MINECART)) {
            maxXZ = MINECART_MAX_XZ;
            maxY  = MINECART_MAX_Y;
        } else {
            maxXZ = UNKNOWN_MAX_XZ;
            maxY  = UNKNOWN_MAX_Y;
        }

        return new double[]{ maxXZ, maxY };
    }

    private void applyFlag(PlayerData data, Player player, String flagReason,
                           double severity, EnumSet<Tag> tags, boolean canEject) {
        if (flagReason != null) {
            if (buffer.increase(player, severity) > BUFFER_FLAG_THRESHOLD) {
                flag(data, flagReason + String.format(" buffer=%.2f", buffer.get(player)));
                if (canEject) {
                    Truthful.getInstance().getPlugin().getServer().getScheduler().runTask(
                            Truthful.getInstance().getPlugin(), () -> {
                                if (data.isInsideVehicle() && player.getVehicle() != null) {
                                    player.getVehicle().eject();
                                }
                                player.teleport(data.getLastLocation());
                            });
                }
                buffer.reset(player, BUFFER_RESET_VALUE);
            }
        } else {
            buffer.decrease(player, 0.1D);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
        buffer.remove(event.getPlayer());
    }
}
