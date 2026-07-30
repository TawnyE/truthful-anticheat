package ret.tawny.truthful.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.attributes.AttributeEngine;
import ret.tawny.truthful.data.handler.SetbackHandler;
import ret.tawny.truthful.data.tracker.ActionTracker;
import ret.tawny.truthful.data.tracker.PositionTracker;
import ret.tawny.truthful.data.world.CompensatedWorld;
import ret.tawny.truthful.sync.TeleportQueue;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.utils.Threading;
import ret.tawny.truthful.utils.elytra.FireworkBoostTracker;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;
import ret.tawny.truthful.wrapper.impl.server.position.SetPositionPacketWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PlayerData {

    public static final double EYE_HEIGHT_STANDING = 1.62;

    private final Player player;
    private final UUID playerId;

    private final MovementProcessor movementProcessor;
    private final VelocityQueue velocityQueue;
    private final TeleportQueue teleportQueue;
    private final FireworkBoostTracker fireworkTracker;
    private final CompensatedWorld worldCache;
    private final MovementState movementState;
    private final MovementContext movementContext;

    private final SetbackHandler setbackHandler;

    private final PositionTracker positionTracker;
    private final ActionTracker actionTracker;
    private final AttributeEngine attributeEngine;

    private int ticksTracked;
    private int lastGlideTick = -100;
    private int lastBlockPlaceTick;
    private int lastGhostBlockTick;
    private int lastHitTick;
    private int lastAttackPacketTick = -100;
    private int lastDamageTick;
    private int lastRiptideTick = -100;
    private int lastFireworkTick = -100;
    private int lastVehicleExitTick = -100;
    private int lastVehicleTick = -100;
    private int lastVelocityTick = -100;
    private int lastFlagTick = -100;
    private boolean lastSprinting;

    private long lastBlockPlaceTime;
    private long lastSlotSwitchTime;

    private final Map<Short, TransactionEntry> transactionsSent = new ConcurrentHashMap<>();
    private final Queue<Short> transactionOrder = new ConcurrentLinkedQueue<>();
    private long playerClockAtLeast = System.nanoTime();
    private long timerBalanceRealTime = System.nanoTime();
    private final long clockDrift = 150_000_000L;

    private final Map<ExemptionType, Integer> exemptions = new ConcurrentHashMap<>();
    private boolean alertsEnabled = true;
    private boolean permanentlyExempt = false;
    private String clientBrand = "Unknown";
    private int totalViolations;

    private short transactionId = 0;
    private long lastTransactionTime;
    private long transactionPing;

    private long highPingStartTime = 0;
    private long lastExplosionPacketTime = 0;

    private Entity lastTarget;
    private int lastTargetId = -1;
    private double preExitVehicleSpeed;
    private PlayerBlockPlacePacketWrapper currentBlockPlacement;
    private double baritoneTrust = 100.0;

    private boolean banning = false;
    private long banStartTime = 0;

    private volatile Map<PotionEffectType, Integer> potionCache = new HashMap<>();
    private final Map<String, Integer> enchantmentCache = new ConcurrentHashMap<>();
    private volatile double walkSpeedCache = 0.1;
    private volatile double gravityCache = 0.08;
    private volatile double jumpStrengthCache = 0.42;
    private volatile double stepHeightCache = 0.6;
    private volatile double movementEfficiencyCache = 0.0;
    private volatile double waterMovementEfficiencyCache = 0.0;
    private volatile double sneakingSpeedCache = 0.3;
    private volatile int foodLevelCache = 20;
    private volatile boolean allowFlightCache = false;
    private volatile boolean flyingCache = false;
    private volatile boolean glidingCache = false;
    private volatile boolean insideVehicleCache = false;
    private volatile int worldMinHeightCache = -64;
    private volatile int worldMaxHeightCache = 320;
    private volatile boolean holdingBlockCache = false;
    private volatile boolean wearingElytraCache = false;
    private volatile boolean holdingCrystalCache = false;
    private volatile boolean slowItemCache = false;
    private volatile boolean tridentCache = false;
    private int lastCacheUpdate = -100;

    public enum TeleportTag { NONE, NORMAL, LAGBACK }
    private TeleportTag outgoingTeleportTag = TeleportTag.NONE;
    private TeleportTag incomingTeleportTag = TeleportTag.NONE;

    public PlayerData(final Player player, int queueMaxEntries, long queueTtlMillis) {
        this.player = player;
        this.playerId = player.getUniqueId();
        this.movementProcessor = new MovementProcessor(this);
        this.velocityQueue = new VelocityQueue(queueMaxEntries, queueTtlMillis);
        this.teleportQueue = new TeleportQueue(queueMaxEntries, queueTtlMillis);
        this.fireworkTracker = new FireworkBoostTracker();
        this.worldCache = new CompensatedWorld(player.getWorld().getUID());
        this.movementState = new MovementState();
        this.movementContext = new MovementContext();

        this.setbackHandler = new SetbackHandler(this);
        this.positionTracker = new PositionTracker(this, player);
        this.actionTracker = new ActionTracker();
        this.attributeEngine = new AttributeEngine(this);

        this.setExemption(ExemptionType.JOIN, 40);

        updateStateCache();
    }

    public void update(final RelMovePacketWrapper event) {
        this.ticksTracked++;

        if (ticksTracked - lastCacheUpdate > 20) {
            updateStateCache();
        }

        if (getTicksSinceTeleport() > 10 && !isTeleportPending()) {
            double tps = Truthful.getInstance().getTps();
            if (tps <= 0) tps = 20.0;
            long tickIntervalNs = (long) ((1000.0 / tps) * 1_000_000.0);
            this.timerBalanceRealTime += tickIntervalNs;
        } else {
            this.timerBalanceRealTime = System.nanoTime();
        }

        Player p = getPlayer();
        if (p == null) return;

        allowFlightCache = p.getAllowFlight();
        flyingCache = p.isFlying();
        glidingCache = p.isGliding();
        insideVehicleCache = p.isInsideVehicle();
        if (insideVehicleCache) {
            lastVehicleTick = ticksTracked;
        }

        this.positionTracker.handleUpdate(event);
        this.movementState.update(this);
        this.movementContext.update(this);

        if (event.isPositionUpdate()) {
            boolean isTeleport = this.incomingTeleportTag != TeleportTag.NONE;

            if (isTeleport) {
                this.incomingTeleportTag = TeleportTag.NONE;
            } else if (teleportQueue.getPendingCount() > 0) {
                TeleportQueue.Teleport matched = teleportQueue.match(event.getX(), event.getY(), event.getZ());
                if (matched != null) {
                    isTeleport = true;
                    this.incomingTeleportTag = matched.isLagback ? TeleportTag.LAGBACK : TeleportTag.NORMAL;
                }
            }

            if (isTeleport) {
                // This permanently patches the 40-tick (2s) Disabler exploit loop!
                this.setExemption(ExemptionType.TELEPORT, 2);
                this.movementProcessor.handleTeleport();
                this.positionTracker.reset(event.getX(), event.getY(), event.getZ(), event.getYaw(), event.getPitch());
                this.timerBalanceRealTime = System.nanoTime();
                this.incomingTeleportTag = TeleportTag.NONE;
            }

            setbackHandler.updateSafeLocation(getLocation());
            this.movementProcessor.predict();
        }

        if (p.isGliding()) {
            this.lastGlideTick = this.ticksTracked;
            double speed = getDeltaXZ();
            double accel = speed - getLastDeltaXZ();
            if (fireworkTracker.update(ticksTracked, speed, accel)) {
                this.setExemption(ExemptionType.ELYTRA_BOOST, 1);
            }
        } else {
            fireworkTracker.reset();
        }

        if (actionTracker.isUsingRiptide()) {
            actionTracker.incrementPendingRiptideTicks();
            double speed = getDeltaXZ() + Math.abs(getDeltaY());
            if (speed > 1.0 || actionTracker.getPendingRiptideTicks() > 5) {
                actionTracker.setUsingRiptide(false);
            }
        }

        actionTracker.handleTick();
        this.tickExemptions();
        this.velocityQueue.tick();
        this.teleportQueue.cleanup();
    }

    public void updateStateCache() {
        if (!Bukkit.isPrimaryThread()) {
            Threading.runOnMain(this::updateStateCache);
            return;
        }

        Player p = getPlayer();
        if (p == null || !p.isOnline()) return;

        Map<PotionEffectType, Integer> newPotionCache = new HashMap<>();
        for (PotionEffect effect : p.getActivePotionEffects()) {
            newPotionCache.put(effect.getType(), effect.getAmplifier() + 1);
        }
        this.potionCache = newPotionCache;

        enchantmentCache.clear();
        updateEnchantment(p.getInventory().getBoots(), "soul_speed", org.bukkit.enchantments.Enchantment.SOUL_SPEED);
        updateEnchantment(p.getInventory().getBoots(), "depth_strider", org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
        updateEnchantment(p.getInventory().getLeggings(), "swift_sneak", org.bukkit.enchantments.Enchantment.SWIFT_SNEAK);
        updateEnchantment(p.getInventory().getItemInMainHand(), "efficiency", org.bukkit.enchantments.Enchantment.EFFICIENCY);

        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            walkSpeedCache = attr.getValue();
        }

        gravityCache = getAttributeValue(p, "GENERIC_GRAVITY", 0.08D);
        jumpStrengthCache = getAttributeValue(p, "GENERIC_JUMP_STRENGTH", 0.42D);
        stepHeightCache = getAttributeValue(p, "GENERIC_STEP_HEIGHT", 0.6D);
        movementEfficiencyCache = getAttributeValue(p, "GENERIC_MOVEMENT_EFFICIENCY", 0.0D);
        waterMovementEfficiencyCache = getAttributeValue(p, "GENERIC_WATER_MOVEMENT_EFFICIENCY", 0.0D);
        sneakingSpeedCache = getAttributeValue(p, "PLAYER_SNEAKING_SPEED", 0.3D);

        if (attributeEngine != null) {
            attributeEngine.record(
                    walkSpeedCache, gravityCache, jumpStrengthCache, stepHeightCache,
                    movementEfficiencyCache, waterMovementEfficiencyCache, sneakingSpeedCache,
                    newPotionCache.getOrDefault(PotionEffectType.SPEED, 0),
                    newPotionCache.getOrDefault(PotionEffectType.SLOWNESS, 0),
                    newPotionCache.getOrDefault(PotionEffectType.JUMP_BOOST, 0)
            );
        }

        foodLevelCache = p.getFoodLevel();
        allowFlightCache = p.getAllowFlight();
        flyingCache = p.isFlying();
        glidingCache = p.isGliding();
        insideVehicleCache = p.isInsideVehicle();
        worldMinHeightCache = p.getWorld().getMinHeight();
        worldMaxHeightCache = p.getWorld().getMaxHeight();

        org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
        holdingBlockCache = (hand != null && hand.getType().isBlock() && hand.getType() != org.bukkit.Material.AIR);

        org.bukkit.inventory.ItemStack chest = p.getInventory().getChestplate();
        wearingElytraCache = (chest != null && chest.getType() == org.bukkit.Material.ELYTRA);

        holdingCrystalCache = (hand != null && hand.getType().name().contains("END_CRYSTAL"));

        org.bukkit.inventory.ItemStack off = p.getInventory().getItemInOffHand();
        slowItemCache = ret.tawny.truthful.utils.world.PhysicsUtils.shouldSlowDown(hand, p) ||
                ret.tawny.truthful.utils.world.PhysicsUtils.shouldSlowDown(off, p);

        tridentCache = (hand != null && hand.getType() == org.bukkit.Material.TRIDENT) ||
                (off != null && off.getType() == org.bukkit.Material.TRIDENT);

        this.lastSprinting = isSprinting();
        lastCacheUpdate = ticksTracked;
    }

    public void handleTeleportConfirm(int teleportId) {
        TeleportQueue.Teleport tp = teleportQueue.confirm(teleportId);
        if (tp != null) {
            this.incomingTeleportTag = tp.isLagback ? TeleportTag.LAGBACK : TeleportTag.NORMAL;
        }
    }

    public void acceptTeleport(SetPositionPacketWrapper wrapper) {
        TeleportTag tag = this.outgoingTeleportTag;
        boolean lagback = tag == TeleportTag.LAGBACK;
        teleportQueue.add(wrapper.getTeleportId(), new Location(player.getWorld(), wrapper.getX(), wrapper.getY(), wrapper.getZ()), lagback);

        this.setExemption(ExemptionType.TELEPORT, 2);
        this.outgoingTeleportTag = TeleportTag.NONE;
    }

    public void forceLagback() {
        if (this.velocityQueue.hasExplosionVelocity() || isInExplosionGraceWindow(1500L)) {
            return;
        }
        this.outgoingTeleportTag = TeleportTag.LAGBACK;
        this.setbackHandler.setback();
        if (!this.velocityQueue.hasExplosionVelocity()) {
            this.velocityQueue.clear();
        }
    }

    public void setLastFlagTick(int tick) { this.lastFlagTick = tick; }
    public int getLastFlagTick() { return lastFlagTick; }

    public boolean isSlowItem() { return slowItemCache; }
    public boolean isTrident() { return tridentCache; }
    public boolean isHoldingCrystal() { return holdingCrystalCache; }
    public boolean isHoldingBlock() { return holdingBlockCache; }
    public boolean isWearingElytra() { return wearingElytraCache; }

    public boolean isSwimming() {
        if (player == null) return false;
        try { return player.isSwimming(); } catch (Throwable ignored) { return false; }
    }

    public void resetTotalViolations() { this.totalViolations = 0; }
    public boolean isRiptiding() { return isUsingRiptide(); }
    public int getTickFreezeGraceTicks() { return 0; }
    public float getRotationDeviation(boolean pitch) { return positionTracker.getRotationDeviation(pitch); }
    public float getLastDeltaYaw() { return positionTracker.getLastDeltaYaw(); }
    public float getLastDeltaPitch() { return positionTracker.getLastDeltaPitch(); }
    public boolean hasAttributeTransition() { return attributeEngine != null && attributeEngine.hasAttributeTransition(); }
    public int getUsingItemTicks() { return actionTracker.getUsingItemTicks(); }
    public long getTimerBalanceRealTime() { return timerBalanceRealTime; }
    public void addTimerBalance(long nanos) { this.timerBalanceRealTime += nanos; }

    private void updateEnchantment(org.bukkit.inventory.ItemStack item, String key, org.bukkit.enchantments.Enchantment enchant) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            int level = item.getEnchantmentLevel(enchant);
            if (level > 0) enchantmentCache.put(key, level);
        }
    }

    private double getAttributeValue(Player p, String enumName, double fallback) {
        try {
            Attribute attribute = Attribute.valueOf(enumName);
            AttributeInstance instance = p.getAttribute(attribute);
            return instance != null ? instance.getValue() : fallback;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return fallback;
        }
    }

    public int getEnchantLevel(String key) { return enchantmentCache.getOrDefault(key, 0); }
    public int getFoodLevel() { return foodLevelCache; }
    public boolean isAllowFlight() { return allowFlightCache; }
    public boolean isFlying() { return flyingCache; }
    public boolean isGliding() { return glidingCache; }
    public boolean isInsideVehicle() { return insideVehicleCache || (ticksTracked - lastVehicleTick < 10); }
    public void setInsideVehicleCache(boolean b) { this.insideVehicleCache = b; }
    public int getWorldMinHeight() { return worldMinHeightCache; }
    public int getWorldMaxHeight() { return worldMaxHeightCache; }
    public int getPotionLevel(PotionEffectType type) {
        if (type == PotionEffectType.SPEED && attributeEngine != null) return attributeEngine.getMaxSpeedLevel();
        if (type == PotionEffectType.SLOWNESS && attributeEngine != null) return attributeEngine.getMinSlownessLevel();
        if (type == PotionEffectType.JUMP_BOOST && attributeEngine != null) return attributeEngine.getMaxJumpBoostLevel();
        return potionCache.getOrDefault(type, 0);
    }
    public boolean hasPotionEffect(PotionEffectType type) { return potionCache.containsKey(type); }
    public double getWalkSpeed() { return attributeEngine != null ? attributeEngine.getMaxWalkSpeed() : walkSpeedCache; }
    public double getWalkSpeedCacheRaw() { return walkSpeedCache; }
    public double getGravity() { return attributeEngine != null ? attributeEngine.getMinGravity() : gravityCache; }
    public double getJumpStrength() { return attributeEngine != null ? attributeEngine.getMaxJumpStrength() : jumpStrengthCache; }
    public double getStepHeight() { return attributeEngine != null ? attributeEngine.getMaxStepHeight() : stepHeightCache; }
    public double getMovementEfficiency() { return attributeEngine != null ? attributeEngine.getMaxMovementEfficiency() : movementEfficiencyCache; }
    public double getWaterMovementEfficiency() { return attributeEngine != null ? attributeEngine.getMaxWaterMovementEfficiency() : waterMovementEfficiencyCache; }
    public double getSneakingSpeed() { return attributeEngine != null ? attributeEngine.getMaxSneakingSpeed() : sneakingSpeedCache; }
    public String getAttributeDebugTags() { return attributeEngine != null ? attributeEngine.getDebugTags() : "ATTR_DISABLED"; }

    public boolean isChunkLoaded() {
        int chunkX = (int) Math.floor(getX()) >> 4;
        int chunkZ = (int) Math.floor(getZ()) >> 4;
        return worldCache.isChunkLoaded(chunkX, chunkZ);
    }

    public boolean shouldSkipChecks() {
        double tps = Truthful.getInstance().getTps();
        double minTps = Truthful.getInstance().getConfiguration().getMinTps();
        if (tps < minTps) return true;
        if (isServerFrozen()) return true;

        if (!isChunkLoaded()) {
            if (getTicksTracked() < 100 || getTicksSinceTeleport() < 100) {
                return true;
            }
        }
        return false;
    }

    public boolean isServerFrozen() { return setbackHandler.shouldBlockMovement(); }

    public boolean isExempt() {
        if (shouldSkipChecks()) return true;
        return permanentlyExempt || isExempt(ExemptionType.JOIN) || isAllowFlight();
    }

    public void setExemption(ExemptionType type, int ticks) { exemptions.put(type, ticks); }
    public void setVelocityExemption(int ticks) { setExemption(ExemptionType.VELOCITY, ticks); }
    public boolean isExempt(ExemptionType type) { return permanentlyExempt || exemptions.containsKey(type); }

    public void setExempt(boolean exempt) { this.permanentlyExempt = exempt; }
    public boolean isJoinExempt() { return isExempt(ExemptionType.JOIN); }
    public boolean isRotationExempt() { return isExempt(ExemptionType.JOIN) || isInsideVehicle() || (ticksTracked - lastVehicleExitTick < 10); }
    public boolean isMovementExempt() { return isExempt(ExemptionType.JOIN) || isAllowFlight() || isInsideVehicle() || (ticksTracked - lastVehicleExitTick < 10); }
    public boolean isDamageExempt() { return isExempt(ExemptionType.DAMAGE); }

    private void tickExemptions() {
        exemptions.keySet().forEach(k -> exemptions.computeIfPresent(k, (key, val) -> val - 1 <= 0 ? null : val - 1));
    }

    public Player getPlayer() { return player; }
    public UUID getPlayerId() { return playerId; }

    public void teardown() {
        velocityQueue.clear();
        teleportQueue.clear();
        transactionsSent.clear();
        transactionOrder.clear();
        exemptions.clear();
        actionTracker.reset();
    }

    public MovementProcessor getProcessor() { return movementProcessor; }
    public VelocityQueue getVelocities() { return velocityQueue; }
    public TeleportQueue getTeleportQueue() { return teleportQueue; }
    public PositionTracker getPositionTracker() { return positionTracker; }
    public ActionTracker getActionTracker() { return actionTracker; }
    public SetbackHandler getSetbackHandler() { return setbackHandler; }
    public MovementState getMovementState() { return movementState; }
    public MovementContext getMovementContext() { return movementContext; }

    public double getDeltaXZ() { return positionTracker.getDeltaXZ(); }
    public double getLastDeltaXZ() { return positionTracker.getLastDeltaXZ(); }
    public double getX() { return positionTracker.getX(); }
    public double getY() { return positionTracker.getY(); }
    public double getZ() { return positionTracker.getZ(); }
    public double getDeltaX() { return positionTracker.getDeltaX(); }
    public double getDeltaY() { return positionTracker.getDeltaY(); }
    public double getDeltaZ() { return positionTracker.getDeltaZ(); }
    public double getLastDeltaX() { return positionTracker.getLastDeltaX(); }
    public double getLastDeltaY() { return positionTracker.getLastDeltaY(); }
    public double getLastDeltaZ() { return positionTracker.getLastDeltaZ(); }
    public float getYaw() { return positionTracker.getYaw(); }
    public float getPitch() { return positionTracker.getPitch(); }
    public float getLastYaw() { return positionTracker.getLastYaw(); }
    public float getLastPitch() { return positionTracker.getLastPitch(); }
    public float getDeltaYaw() { return positionTracker.getDeltaYaw(); }
    public float getDeltaPitch() { return positionTracker.getDeltaPitch(); }
    public int getSensitivityPercent() { return positionTracker.getSensitivityPercent(); }

    public boolean isServerGround() { return positionTracker.isServerGround(); }
    public boolean isClientGround() { return positionTracker.isClientGround(); }
    public boolean isLastGround() { return positionTracker.isLastGround(); }
    public boolean isOnGround() { return positionTracker.isOnGround(); }

    public int getAirTicks() { return positionTracker.getAirTicks(); }
    public int getGroundTicks() { return positionTracker.getGroundTicks(); }
    public int getTicksTracked() { return ticksTracked; }
    public Location getLocation() { return positionTracker.getLocation(); }
    public Location getLastLocation() { return positionTracker.getLastLocation(); }

    public boolean isInventoryOpen() { return actionTracker.isInventoryOpen(); }
    public void setInventoryOpen(boolean b) { actionTracker.setInventoryOpen(b, ticksTracked); }
    public int getInventoryOpenTick() { return actionTracker.getInventoryOpenTick(); }
    public long getLastInventoryClose() { return actionTracker.getLastInventoryClose(); }
    public long getLastWindowClick() { return actionTracker.getLastWindowClick(); }
    public void setLastWindowClick(long t) { actionTracker.setLastWindowClick(t); }

    public boolean isInLiquid() { return positionTracker.isInLiquid(); }
    public boolean isOnClimbable() { return positionTracker.isOnClimbable(); }
    public boolean isInWeb() { return positionTracker.isInWeb(); }
    public boolean isNearVehicle() { return positionTracker.isNearVehicle(); }
    public void setNearVehicle(boolean b) { positionTracker.setNearVehicle(b); }
    public boolean isNearEntity() { return positionTracker.isNearEntity(); }
    public void setNearEntity(boolean b) { positionTracker.setNearEntity(b); }
    public boolean isUnderBlock() { return positionTracker.isUnderBlock(); }

    public boolean isSprinting() { return actionTracker.isSprinting(); }
    public void setSprinting(boolean b) { actionTracker.setSprinting(b); }
    public boolean isLastSprinting() { return lastSprinting; }
    public void finishMovementTick() { this.lastSprinting = isSprinting(); }

    public boolean isSneaking() { return actionTracker.isSneaking(); }
    public void setSneaking(boolean b) { actionTracker.setSneaking(b); }
    public boolean isDigging() { return actionTracker.isDigging(); }
    public void setDigging(boolean b) { actionTracker.setDigging(b); }
    public boolean isUsingItem() { return actionTracker.isUsingItem(); }
    public void setUsingItem(boolean b, int ticksTracked) { actionTracker.setUsingItem(b, ticksTracked); }
    public void setUsingItem(boolean b) { actionTracker.setUsingItem(b, ticksTracked); }
    public int getLastUseItemTick() { return actionTracker.getLastUseItemTick(); }

    public boolean isUsingRiptide() { return actionTracker.isUsingRiptide(); }
    public void setUsingRiptide(boolean b) { actionTracker.setUsingRiptide(b); }

    public int getCurrentSlot() { return actionTracker.getCurrentSlot(); }
    public void setCurrentSlot(int s) { actionTracker.setCurrentSlot(s); }
    public void setLastSlot(int s) { actionTracker.setLastSlot(s); }
    public int getLastSlot() { return actionTracker.getLastSlot(); }
    public void setLastSlotSwitchTime(long t) { this.lastSlotSwitchTime = t; }
    public long getLastSlotSwitchTime() { return lastSlotSwitchTime; }
    public void setLastBlockPlaceTime(long t) { this.lastBlockPlaceTime = t; }
    public long getLastBlockPlaceTime() { return lastBlockPlaceTime; }
    public void setLastBlockPlaceTick(int t) { this.lastBlockPlaceTick = t; }
    public int getLastBlockPlaceTick() { return lastBlockPlaceTick; }
    public void setLastGhostBlockTick(int t) { this.lastGhostBlockTick = t; }
    public int getLastGhostBlockTick() { return lastGhostBlockTick; }

    public int getLastHitTick() { return lastHitTick; }
    public void setLastHitTick(int t) { this.lastHitTick = t; }
    public int getLastAttackPacketTick() { return lastAttackPacketTick; }
    public void setLastAttackPacketTick(int t) { this.lastAttackPacketTick = t; }
    public int getLastDamageTick() { return lastDamageTick; }
    public void setLastDamageTick(int t) { this.lastDamageTick = t; }
    public int getLastRiptideTick() { return lastRiptideTick; }
    public void setLastRiptideTick(int t) { this.lastRiptideTick = t; }
    public int getLastFireworkTick() { return lastFireworkTick; }
    public void setLastFireworkTick(int t) { this.lastFireworkTick = t; }
    public int getLastGlideTick() { return lastGlideTick; }

    public int getLastWebTick() { return positionTracker.getLastWebTick(); }
    public int getLastIceTick() { return positionTracker.getLastIceTick(); }
    public int getLastSlimeTick() { return positionTracker.getLastSlimeTick(); }
    public int getLastSoulSandTick() { return positionTracker.getLastSoulSandTick(); }

    public void setLastVehicleExitTick(int t) { this.lastVehicleExitTick = t; }
    public int getLastVehicleExitTick() { return lastVehicleExitTick; }
    public void setLastVehicleTick(int t) { this.lastVehicleTick = t; }
    public int getLastVehicleTick() { return lastVehicleTick; }
    public int getLastUnderBlockTick() { return positionTracker.getLastUnderBlockTick(); }

    public PlayerBlockPlacePacketWrapper getCurrentBlockPlacement() { return currentBlockPlacement; }
    public void setCurrentBlockPlacement(PlayerBlockPlacePacketWrapper p) { this.currentBlockPlacement = p; }
    public void setLastTarget(Entity e) {
        this.lastTarget = e;
        if (e != null) this.lastTargetId = e.getEntityId();
        else this.lastTargetId = -1;
    }
    public Entity getLastTarget() { return lastTarget; }
    public int getLastTargetId() { return lastTargetId; }

    public void setPreExitVehicleSpeed(double s) { this.preExitVehicleSpeed = s; }
    public double getBaritoneTrust() { return baritoneTrust; }
    public void lowerBaritoneTrust(double amount) { this.baritoneTrust = Math.max(0, baritoneTrust - amount); }

    public void onFireworkInteraction() { fireworkTracker.onFireworkInteraction(ticksTracked, getDeltaXZ(), player.isGliding()); }

    public boolean hasVelocity() { return velocityQueue.hasActiveVelocity(); }
    public int getLastVelocityTick() { return lastVelocityTick; }
    public void setLastVelocityTick(int t) { this.lastVelocityTick = t; }

    public void resetTransientState() {
        this.movementProcessor.reset();
        this.velocityQueue.clear();
        this.exemptions.clear();
        this.transactionsSent.clear();
        this.timerBalanceRealTime = System.nanoTime();

        if (player.isOnline()) {
            Location loc = player.getLocation();
            this.positionTracker.reset(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }

        this.setExemption(ExemptionType.RESPAWN, 60);
        this.setExemption(ExemptionType.JOIN, 60);

        this.ticksTracked = 0;
        this.totalViolations = Math.max(0, this.totalViolations - 5);
        this.banning = false;
        this.highPingStartTime = 0;

        updateStateCache();
    }

    public void handleWorldChange() {
        this.worldCache.setWorldUid(player.getWorld().getUID());
        resetTransientState();
        this.setExemption(ExemptionType.JOIN, 100);
    }

    public double getEyeHeight(boolean legacy, boolean sneaking, boolean swimming) {
        return positionTracker.getEyeHeight(legacy, sneaking, swimming);
    }

    public int getTicksSinceTeleport() { return this.ticksTracked - this.movementState.getLastTeleportTick(); }
    public boolean isTeleportTick() { return isExempt(ExemptionType.TELEPORT); }

    public CompensatedWorld getWorldCache() { return worldCache; }
    public long getPing() { return transactionPing; }
    public void setPing(long ping) { this.transactionPing = ping; }
    public String getClientBrand() { return clientBrand; }
    public void setClientBrand(String brand) { this.clientBrand = brand; }
    public int getVl() { return totalViolations; }
    public void addVl(int amount) { this.totalViolations += amount; }
    public boolean isAlertsEnabled() { return alertsEnabled; }
    public void setAlertsEnabled(boolean enabled) { this.alertsEnabled = enabled; }

    public short getNextTransactionId() {
        if (transactionId++ > 32000) transactionId = 0;
        return transactionId;
    }

    public void handleTransaction(short id) {
        TransactionEntry entry = transactionsSent.remove(id);
        if (entry != null) {
            this.playerClockAtLeast = Math.max(this.playerClockAtLeast, entry.timestamp);
            long nowNanos = System.nanoTime();
            long diffNanos = nowNanos - entry.timestamp;
            this.transactionPing = (this.transactionPing * 3 + (diffNanos / 1_000_000L)) / 4;
            this.timerBalanceRealTime = Math.max(this.timerBalanceRealTime, this.playerClockAtLeast - clockDrift);
        }

        this.velocityQueue.confirm(id);

        if (this.setbackHandler.onTransaction(id)) {
            this.movementProcessor.reset();
            this.timerBalanceRealTime = System.nanoTime();
        }
    }

    public void recordTransactionSent(short id) {
        this.transactionsSent.put(id, new TransactionEntry(id, System.nanoTime()));
        this.transactionOrder.add(id);
        while (transactionOrder.size() > 100) {
            Short oldestId = transactionOrder.poll();
            if (oldestId != null) this.transactionsSent.remove(oldestId);
        }
    }

    public void markExplosionPacket() { this.lastExplosionPacketTime = System.currentTimeMillis(); }
    public boolean isInExplosionGraceWindow(long millis) { return System.currentTimeMillis() - this.lastExplosionPacketTime <= millis; }
    public boolean isTeleportPending() { return setbackHandler.shouldBlockMovement(); }
    public boolean isBanning() { return banning; }

    public void setBanning(boolean banning) {
        this.banning = banning;
        if (banning) this.banStartTime = System.currentTimeMillis();
    }

    public long getBanStartTime() { return banStartTime; }
    public double getTransactionTimerBalance() { return (double) (timerBalanceRealTime - System.nanoTime()) / 1_000_000.0; }

    private static class TransactionEntry {
        final short id;
        final long timestamp;
        TransactionEntry(short id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }
}