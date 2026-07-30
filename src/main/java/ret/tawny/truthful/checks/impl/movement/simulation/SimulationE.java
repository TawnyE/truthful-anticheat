package ret.tawny.truthful.checks.impl.movement.simulation;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.checks.impl.movement.MovementCheckSupport;
import ret.tawny.truthful.data.ExemptionType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.world.BlockPropertyRegistry;
import ret.tawny.truthful.utils.world.PhysicsConstants;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CheckData(order = 'E', type = CheckType.SIMULATION)
public final class SimulationE extends Check {

    private static final double ITEM_USE_SPEED_MULT  = 0.20D;
    private static final double HONEY_SPEED_MULT     = 0.40D;
    private static final double WEB_SPEED_MULT       = 0.25D;
    private static final double SOUL_SAND_SPEED_MULT = 0.40D;
    private static final double POWDER_SNOW_SLOW_MULT= 0.30D;
    private static final double SLIME_LANDING_MULT   = 0.60D;

    private static final double SLOWNESS_PER_LEVEL   = 0.15D;

    private static final double MIN_SPEED_TO_CHECK   = 0.03D;
    private static final int VIOLATION_TICKS_REQUIRED = 3;

    private static final float BUFFER_DECAY         = 0.20F;
    private static final float BUFFER_INCREASE      = 1.0F;
    private static final float BUFFER_FLAG_THRESHOLD = 3.0F;

    private enum Tag {
        ITEM_USE, PRE_POST_NOSLOW, HONEY, WEB, SOUL_SAND, POWDER_SNOW,
        SLIME_LANDING, SLOWNESS, SNEAKING, VELOCITY, TELEPORT_GRACE,
        IN_LIQUID, ON_CLIMBABLE, WALL_SLIDE, GLIDING, RESPAWN_GRACE
    }

    private static final class State {
        float buffer;
        int violationTicks;
        int lastItemUseTick = -1000;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isPositionUpdate()) return;

        final PlayerData data = wrapper.getPlayerData();
        if (data == null) return;

        final State st = states.computeIfAbsent(data.getPlayer().getUniqueId(), k -> new State());

        final double deltaXZ = data.getDeltaXZ();
        if (deltaXZ < MIN_SPEED_TO_CHECK) {
            st.buffer = Math.max(0f, st.buffer - BUFFER_DECAY);
            st.violationTicks = 0;
            return;
        }

        if (data.isTeleportPending() || data.getTicksSinceTeleport() < 4) {
            st.buffer = Math.max(0f, st.buffer - 0.4f);
            st.violationTicks = 0;
            return;
        }

        if (data.isExempt(ExemptionType.RESPAWN) && data.getTicksTracked() < 60) {
            st.buffer = Math.max(0f, st.buffer - 0.4f);
            st.violationTicks = 0;
            return;
        }

        if (MovementCheckSupport.skipForPrediction(data)) {
            st.buffer = Math.max(0f, st.buffer - BUFFER_DECAY);
            st.violationTicks = 0;
            return;
        }

        final int ticksNow = data.getTicksTracked();
        final boolean onGround = data.isServerGround() || data.isClientGround();
        final boolean inLiquid = data.isInLiquid();
        final boolean onClimbable = data.isOnClimbable();

        final EnumSet<Tag> tags = buildTags(data, ticksNow, onGround, inLiquid, onClimbable, st);

        boolean hasActiveSlowdown = tags.contains(Tag.ITEM_USE) || tags.contains(Tag.PRE_POST_NOSLOW)
                || tags.contains(Tag.HONEY) || tags.contains(Tag.WEB) || tags.contains(Tag.SOUL_SAND)
                || tags.contains(Tag.POWDER_SNOW) || tags.contains(Tag.SLIME_LANDING);

        if (!hasActiveSlowdown) {
            st.buffer = Math.max(0f, st.buffer - BUFFER_DECAY);
            st.violationTicks = 0;
            return;
        }

        if (tags.contains(Tag.TELEPORT_GRACE) || tags.contains(Tag.VELOCITY)
                || tags.contains(Tag.GLIDING) || tags.contains(Tag.RESPAWN_GRACE)) {
            st.buffer = Math.max(0f, st.buffer - 0.5f);
            st.violationTicks = 0;
            return;
        }

        if (inLiquid || onClimbable) {
            st.buffer = Math.max(0f, st.buffer - BUFFER_DECAY);
            st.violationTicks = 0;
            return;
        }

        double expectedMax = computeExpectedMaxSpeed(data, tags, ticksNow);

        double friction = onGround ? MovementCheckSupport.computeGroundFriction(data) : PhysicsConstants.AIR_DRAG_XZ;
        double momentumDecay = data.getLastDeltaXZ() * friction + 0.02D;
        expectedMax = Math.max(expectedMax, momentumDecay);

        if (tags.contains(Tag.WALL_SLIDE)) {
            expectedMax += 0.06D;
        }

        double excess = deltaXZ - expectedMax;

        if (excess > 0.005D) {
            st.violationTicks++;
            st.buffer += BUFFER_INCREASE * Math.min(3.0F, (float) (excess * 12.0));

            if (st.violationTicks >= VIOLATION_TICKS_REQUIRED && st.buffer >= BUFFER_FLAG_THRESHOLD) {
                String slowdownSources = describeSlowdowns(tags);
                flag(data, String.format("Speed (SlowdownBypass) XZ=%.4f expected=%.4f excess=%.4f srcs=%s",
                        deltaXZ, expectedMax, excess, slowdownSources));
                st.buffer = BUFFER_FLAG_THRESHOLD * 0.5F;
                st.violationTicks = 0;
            }
        } else {
            st.violationTicks = 0;
            st.buffer = Math.max(0f, st.buffer - BUFFER_DECAY);
        }
    }

    private double computeExpectedMaxSpeed(PlayerData data, EnumSet<Tag> tags, int ticksNow) {
        double baseInput = data.getWalkSpeed();

        int speedLevel = data.getPotionLevel(PotionEffectType.SPEED);
        if (speedLevel > 0) {
            baseInput *= (1.0D + 0.20D * speedLevel);
        }

        int slownessLevel = data.getPotionLevel(PotionEffectType.SLOWNESS);
        if (slownessLevel > 0) {
            baseInput *= Math.max(0.0D, 1.0D - SLOWNESS_PER_LEVEL * slownessLevel);
            tags.add(Tag.SLOWNESS);
        }

        double slowMult = 1.0D;

        if (tags.contains(Tag.ITEM_USE) || tags.contains(Tag.PRE_POST_NOSLOW)) {
            slowMult *= ITEM_USE_SPEED_MULT;
        }

        if (data.getMovementContext().isHoney()) {
            slowMult *= HONEY_SPEED_MULT;
            tags.add(Tag.HONEY);
        }

        if (data.isInWeb()) {
            slowMult *= WEB_SPEED_MULT;
            tags.add(Tag.WEB);
        }

        if (ticksNow - data.getLastSoulSandTick() < 8 && data.getEnchantLevel("soul_speed") == 0) {
            slowMult *= SOUL_SAND_SPEED_MULT;
            tags.add(Tag.SOUL_SAND);
        }

        if (data.isExempt(ExemptionType.POWDER_SNOW)) {
            ItemStack boots = data.getPlayer().getInventory().getBoots();
            boolean leatherBoots = boots != null && boots.getType() == Material.LEATHER_BOOTS;
            if (!leatherBoots) {
                slowMult *= POWDER_SNOW_SLOW_MULT;
                tags.add(Tag.POWDER_SNOW);
            }
        }

        if (ticksNow - data.getLastSlimeTick() <= 3 && data.getPositionTracker().getLastAirTicks() > 3) {
            slowMult *= SLIME_LANDING_MULT;
            tags.add(Tag.SLIME_LANDING);
        }

        if (data.isSneaking()) {
            tags.add(Tag.SNEAKING);
            int swiftSneakLevel = data.getEnchantLevel("swift_sneak");
            double maxSneakMultiplier = 0.30D;
            if (swiftSneakLevel > 0) {
                maxSneakMultiplier = Math.min(1.0D, 0.30D + (swiftSneakLevel * 0.15D));
            }
            slowMult = Math.min(slowMult, maxSneakMultiplier);
        }

        double sprintBonus = data.isSprinting() ? 1.30D : 1.0D;

        double friction = data.isServerGround()
                ? MovementCheckSupport.computeGroundFriction(data)
                : PhysicsConstants.AIR_DRAG_XZ;
        double f3 = Math.max(0.048D, friction * friction * friction);
        double accel = baseInput * sprintBonus * 0.16277136D / f3;
        double terminalSpeed = accel / Math.max(0.01D, 1.0D - friction);

        return Math.max(0, terminalSpeed * slowMult);
    }

    private EnumSet<Tag> buildTags(final PlayerData data, final int ticksNow,
                                   final boolean onGround, final boolean inLiquid, final boolean onClimbable, final State st) {

        final EnumSet<Tag> tags = EnumSet.noneOf(Tag.class);

        if (data.isGliding()) tags.add(Tag.GLIDING);
        if (data.getTicksSinceTeleport() < 5 || ticksNow - data.getLastVehicleExitTick() < 10) {
            tags.add(Tag.TELEPORT_GRACE);
        }
        if (data.hasVelocity() || ticksNow - data.getLastVelocityTick() < 5) {
            tags.add(Tag.VELOCITY);
        }
        if (inLiquid) tags.add(Tag.IN_LIQUID);
        if (onClimbable) tags.add(Tag.ON_CLIMBABLE);

        boolean currentlyUsing = data.isUsingItem() && data.isSlowItem();
        if (currentlyUsing) {
            st.lastItemUseTick = ticksNow;
            tags.add(Tag.ITEM_USE);
        } else if (ticksNow - st.lastItemUseTick <= 3 && data.isSlowItem()) {
            tags.add(Tag.PRE_POST_NOSLOW);
        }

        boolean moving = data.getDeltaXZ() > 0.02D;
        if (moving && onGround && !inLiquid && !onClimbable && isNearWall(data)) {
            tags.add(Tag.WALL_SLIDE);
        }

        return tags;
    }

    private boolean isNearWall(final PlayerData data) {
        final double x = data.getX(), y = data.getY(), z = data.getZ(), r = 0.35D;
        return hasSolid(data, x + r, y + 0.2D, z)
                || hasSolid(data, x - r, y + 0.2D, z)
                || hasSolid(data, x, y + 0.2D, z + r)
                || hasSolid(data, x, y + 0.2D, z - r);
    }

    private boolean hasSolid(final PlayerData data, final double x, final double y, final double z) {
        return BlockPropertyRegistry.isSolid(
                data.getWorldCache().getBlockState((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    private String describeSlowdowns(final EnumSet<Tag> tags) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Tag t : tags) {
            if (!first) sb.append(",");
            sb.append(t.name());
            first = false;
        }
        return sb.toString();
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}