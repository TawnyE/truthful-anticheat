package ret.tawny.truthful.checks.impl.movement.simulation;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'D', type = CheckType.SIMULATION)
public final class SimulationD extends Check {

    private static final double BOAT_MAX_XZ          = 1.25D;
    private static final double BOAT_ICE_MAX_XZ      = 5.0D;
    private static final double BOAT_BLUE_ICE_MAX_XZ = 9.5D;
    private static final double BOAT_MAX_Y            = 0.7D;

    private static final double HORSE_MAX_XZ          = 1.45D;
    private static final double HORSE_MAX_Y           = 1.05D;
    private static final double CAMEL_MAX_XZ          = 1.85D;
    private static final double CAMEL_MAX_Y           = 1.05D;

    private static final double LLAMA_MAX_XZ          = 0.60D;
    private static final double PIG_MAX_XZ            = 0.50D;
    private static final double STRIDER_MAX_XZ        = 0.65D;
    private static final double MINECART_MAX_XZ       = 8.50D;
    private static final double UNKNOWN_MAX_XZ        = 1.50D;

    private static final int MOUNT_GRACE_TICKS        = 15;
    private static final int DISMOUNT_GRACE_TICKS     = 20;

    private static final double BUFFER_FLAG_THRESHOLD = 6.0D;
    private static final double BUFFER_RESET_VALUE    = 3.0D;

    private enum Tag {
        BOAT, HORSE, DONKEY, MULE, LLAMA, PIG, STRIDER, CAMEL, MINECART, UNKNOWN_MOUNT,
        ON_ICE, ON_BLUE_ICE, IN_LIQUID, BOUNCY_BLOCK, NEAR_ENTITY_PUSH,
        MOUNT_GRACE, DISMOUNT_GRACE, TELEPORT_GRACE, VELOCITY_RECEIVED, SERVER_FROZEN,
        UNAUTHORIZED_SEAT, UNSADDLED_MOUNT, NO_INPUT_ACCEL,
        V_SPEED, V_FLY, V_PUSH_XZ, V_PUSH_Y
    }

    private static final class State {
        int mountTick = -1000;
        int dismountTick = -1000;
        String vehicleTypeName = "";

        float steerForward = 0f;
        float steerSideways = 0f;
        int lastSteerTick = -1000;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final CheckBuffer buffer = new CheckBuffer(BUFFER_FLAG_THRESHOLD);

    @Override
    public void handlePacketPlayerReceive(final PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.STEER_VEHICLE) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null) return;

        WrapperPlayClientSteerVehicle steer = new WrapperPlayClientSteerVehicle(event);
        State st = states.computeIfAbsent(player.getUniqueId(), k -> new State());

        st.steerForward = steer.getForward();
        st.steerSideways = steer.getSideways();
        st.lastSteerTick = data.getTicksTracked();
    }

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

            final EnumSet<Tag> tags = buildVehicleTags(data, vehicle, st, ticksNow);

            List<Entity> passengers = vehicle.getPassengers();
            if (!passengers.isEmpty() && passengers.get(0).getEntityId() != player.getEntityId()) {
                tags.add(Tag.UNAUTHORIZED_SEAT);
                if (data.getDeltaXZ() > 0.05D) {
                    flag(data, String.format("Area Fail (VehicleSeat) Passenger in seat %d moving vehicle XZ=%.3f",
                            passengers.indexOf(player), data.getDeltaXZ()));
                    ejectAndSetback(data, player);
                    return;
                }
            }

            if (!isLegallyControllable(player, vehicle)) {
                tags.add(Tag.UNSADDLED_MOUNT);
                if (data.getDeltaXZ() > 0.15D && !data.hasVelocity()) {
                    flag(data, String.format("Area Fail (UnsaddledSteer) Moving unsaddled mount type=%s XZ=%.3f",
                            vehicle.getType().name(), data.getDeltaXZ()));
                    ejectAndSetback(data, player);
                    return;
                }
            }

            boolean hasSteerInput = (ticksNow - st.lastSteerTick <= 3) && (Math.abs(st.steerForward) > 0.01f || Math.abs(st.steerSideways) > 0.01f);
            boolean isMountingTransition = (ticksNow - st.mountTick <= 5);
            double friction = tags.contains(Tag.ON_BLUE_ICE) ? 0.989D : tags.contains(Tag.ON_ICE) ? 0.98D : 0.6D;
            double unmotivatedAccel = data.getDeltaXZ() - (data.getLastDeltaXZ() * friction);

            if (!hasSteerInput && !isMountingTransition && unmotivatedAccel > 0.08D && !tags.contains(Tag.MOUNT_GRACE) && !data.hasVelocity() && !tags.contains(Tag.IN_LIQUID)) {
                tags.add(Tag.NO_INPUT_ACCEL);
                if (buffer.increase(player, 1.5) > BUFFER_FLAG_THRESHOLD) {
                    flag(data, String.format("Area Fail (NoInputAccel) Vehicle accel without key input XZ=%.3f accel=%.3f",
                            data.getDeltaXZ(), unmotivatedAccel));
                    buffer.reset(player, BUFFER_RESET_VALUE);
                    ejectAndSetback(data, player);
                    return;
                }
            }

            if (tags.contains(Tag.MOUNT_GRACE) || tags.contains(Tag.DISMOUNT_GRACE)
                    || tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.SERVER_FROZEN)) {
                buffer.decrease(player, 0.25D);
                return;
            }

            double distXZ = data.getDeltaXZ();
            double deltaY = data.getDeltaY();
            Vector vehicleVel = vehicle.getVelocity();

            double[] limits = getLimits(tags);
            double maxXZ = limits[0];
            double maxY  = limits[1];

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
            if (data.hasVelocity() || data.isExempt(ExemptionType.VELOCITY) || data.isTeleportTick() || data.isServerFrozen()) {
                buffer.decrease(player, 0.25D);
                return;
            }
            if (ticksNow - st.dismountTick < DISMOUNT_GRACE_TICKS) {
                buffer.decrease(player, 0.25D);
                return;
            }

            double distXZ = data.getDeltaXZ();
            double pushMaxXZ = WorldUtils.isNearIceWide(player) ? 3.5D : 1.5D;

            if (distXZ > pushMaxXZ) {
                if (buffer.increase(player, 1.0) > BUFFER_FLAG_THRESHOLD) {
                    flag(data, String.format("Area Fail (V_PushXZ) XZ=%.4f max=%.4f", distXZ, pushMaxXZ));
                    buffer.reset(player, BUFFER_RESET_VALUE);
                }
                return;
            }
        }

        buffer.decrease(player, 0.1D);
    }

    private boolean isLegallyControllable(Player player, Entity vehicle) {
        if (vehicle instanceof Horse horse) {
            return horse.getInventory().getSaddle() != null;
        }
        if (vehicle instanceof Steerable steerable) {
            if (!steerable.hasSaddle()) return false;
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand  = player.getInventory().getItemInOffHand();
            if (vehicle.getType().name().contains("PIG")) {
                return mainHand.getType() == Material.CARROT_ON_A_STICK || offHand.getType() == Material.CARROT_ON_A_STICK;
            }
            if (vehicle.getType().name().contains("STRIDER")) {
                return mainHand.getType() == Material.WARPED_FUNGUS_ON_A_STICK || offHand.getType() == Material.WARPED_FUNGUS_ON_A_STICK;
            }
            return true;
        }
        return true;
    }

    private EnumSet<Tag> buildVehicleTags(PlayerData data, Entity vehicle, State st, int ticksNow) {
        EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

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
        if (WorldUtils.isNearIceWide(player)) {
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
        double maxXZ, maxY;

        if (tags.contains(Tag.BOAT)) {
            if (tags.contains(Tag.ON_BLUE_ICE)) maxXZ = BOAT_BLUE_ICE_MAX_XZ;
            else if (tags.contains(Tag.ON_ICE)) maxXZ = BOAT_ICE_MAX_XZ;
            else maxXZ = BOAT_MAX_XZ;
            maxY = BOAT_MAX_Y;
        } else if (tags.contains(Tag.CAMEL)) {
            maxXZ = CAMEL_MAX_XZ;
            maxY = CAMEL_MAX_Y;
        } else if (tags.contains(Tag.HORSE) || tags.contains(Tag.DONKEY) || tags.contains(Tag.MULE)) {
            maxXZ = HORSE_MAX_XZ;
            maxY = HORSE_MAX_Y;
        } else if (tags.contains(Tag.LLAMA)) {
            maxXZ = LLAMA_MAX_XZ;
            maxY = 0.65D;
        } else if (tags.contains(Tag.PIG)) {
            maxXZ = PIG_MAX_XZ;
            maxY = 0.52D;
        } else if (tags.contains(Tag.STRIDER)) {
            maxXZ = STRIDER_MAX_XZ;
            maxY = 0.50D;
        } else if (tags.contains(Tag.MINECART)) {
            maxXZ = MINECART_MAX_XZ;
            maxY = 1.00D;
        } else {
            maxXZ = UNKNOWN_MAX_XZ;
            maxY = 1.05D;
        }

        return new double[]{ maxXZ, maxY };
    }

    private void ejectAndSetback(PlayerData data, Player player) {
        Truthful.getInstance().getServerScheduler().runRegion(player, () -> {
            if (player.isInsideVehicle() && player.getVehicle() != null) {
                player.getVehicle().eject();
            }
            player.teleport(data.getLastLocation());
        });
    }

    private void applyFlag(PlayerData data, Player player, String flagReason,
                           double severity, EnumSet<Tag> tags, boolean canEject) {
        if (flagReason != null) {
            if (buffer.increase(player, severity) > BUFFER_FLAG_THRESHOLD) {
                flag(data, flagReason + String.format(" buffer=%.2f", buffer.get(player)));
                if (canEject) ejectAndSetback(data, player);
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