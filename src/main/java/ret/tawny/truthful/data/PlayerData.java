package ret.tawny.truthful.data;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.handler.SetbackHandler;
import ret.tawny.truthful.data.tracker.ActionTracker;
import ret.tawny.truthful.data.tracker.PositionTracker;
import ret.tawny.truthful.data.world.CompensatedWorld;
import ret.tawny.truthful.sync.TeleportQueue;
import ret.tawny.truthful.sync.VelocityQueue;
import ret.tawny.truthful.utils.elytra.FireworkBoostTracker;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;
import ret.tawny.truthful.utils.Threading;
import ret.tawny.truthful.wrapper.impl.server.position.SetPositionPacketWrapper;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.UUID;

public final class PlayerData {

    public static final double EYE_HEIGHT_STANDING = 1.62;

    private final Player player;
    private final UUID playerId;

    // Systems
    private final MovementProcessor movementProcessor;
    private final VelocityQueue velocityQueue;
    private final TeleportQueue teleportQueue;
    private final FireworkBoostTracker fireworkTracker;
    private final CompensatedWorld worldCache;
    private final MovementState movementState;
    private final MovementContext movementContext;

    private final SetbackHandler setbackHandler;

    // Trackers
    private final PositionTracker positionTracker;
    private final ActionTracker actionTracker;

    // Ticks & Timing
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

    // State & Flags
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
    private double walkSpeedCache = 0.1;
    private int foodLevelCache = 20;
    private boolean allowFlightCache = false;
    private boolean flyingCache = false;
    private boolean glidingCache = false;
    private boolean insideVehicleCache = false;
    private int worldMinHeightCache = -64;
    private int worldMaxHeightCache = 320;
    private boolean holdingBlockCache = false;
    private boolean wearingElytraCache = false;
    private boolean holdingCrystalCache = false;
    private boolean slowItemCache = false;
    private boolean tridentCache = false;
    private int lastCacheUpdate = -100;

    // Teleport & Setback state
    private boolean nextMoveIsTeleport = false;
    private boolean nextMoveIsLagbackTeleport = false;
    private boolean isNextTeleportLagback = false;

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

        this.setExemption(ExemptionType.JOIN, 40);

        updateStateCache();
    }

    public void update(final RelMovePacketWrapper event) {
        if (setbackHandler.shouldBlockMovement()) {
            return;
        }

        this.ticksTracked++;

        if (ticksTracked - lastCacheUpdate > 20) {
            updateStateCache();
        }

        if (getTicksSinceTeleport() > 10 && !isTeleportPending()) {
            // Dynamic tick interval based on actual server TPS
            double tps = Truthful.getInstance().getTps();
            if (tps <= 0) tps = 20.0;
            long tickIntervalNs = (long) ((1000.0 / tps) * 1_000_000.0);
            this.timerBalanceRealTime += tickIntervalNs;
        } else {
            this.timerBalanceRealTime = System.nanoTime();
        }

        Player player = getPlayer();
        if (player == null) return;

        allowFlightCache = player.getAllowFlight();
        flyingCache = player.isFlying();
        glidingCache = player.isGliding();
        insideVehicleCache = player.isInsideVehicle();
        if (insideVehicleCache) {
            lastVehicleTick = ticksTracked;
        }

        this.positionTracker.handleUpdate(event);
        this.movementState.update(this);
        this.movementContext.update(this);

        if (event.isPositionUpdate()) {
            boolean isTeleport = this.nextMoveIsTeleport;

            if (isTeleport) {
                this.nextMoveIsTeleport = false;
            } else if (teleportQueue.getPendingCount() > 0) {
                TeleportQueue.Teleport matched = teleportQueue.match(event.getX(), event.getY(), event.getZ());
                if (matched != null) {
                    isTeleport = true;
                    this.nextMoveIsLagbackTeleport = matched.isLagback;
                }
            }

            if (isTeleport) {
                if (this.nextMoveIsLagbackTeleport) {
                    this.setExemption(ExemptionType.TELEPORT, 3);
                } else {
                    this.setExemption(ExemptionType.TELEPORT, 40);
                }
                this.movementProcessor.handleTeleport();
                this.positionTracker.reset(event.getX(), event.getY(), event.getZ(), event.getYaw(), event.getPitch());
                this.timerBalanceRealTime = System.nanoTime();
                this.nextMoveIsLagbackTeleport = false;
            }

            // FIX: Only update safe location when genuinely on the ground with no
            // recent flags. Previously updated after just 5 ticks without a flag,
            // even while airborne — this let fly cheats slowly drift the setback
            // position forward between flag bursts.
            // Now requires: server ground + no velocity + no pending teleport + 12
            // ticks without a flag. The SetbackHandler's updateSafeLocation() adds
            // its own mode-specific checks on top of this.
            if (!hasVelocity() && teleportQueue.getPendingCount() == 0 && !isTeleportPending()) {
                if (isServerGround() && this.ticksTracked - this.lastFlagTick > 12) {
                    setbackHandler.updateSafeLocation(getLocation());
                }
            }

            this.movementProcessor.predict();
        }

        if (player.isGliding()) {
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
            if (speed > 1.0) {
                actionTracker.setUsingRiptide(false);
            } else if (actionTracker.getPendingRiptideTicks() > 5) {
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

        Player player = getPlayer();
        if (player == null || !player.isOnline()) return;

        Map<PotionEffectType, Integer> newPotionCache = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            newPotionCache.put(effect.getType(), effect.getAmplifier() + 1);
        }
        this.potionCache = newPotionCache;

        enchantmentCache.clear();
        updateEnchantment(player.getInventory().getBoots(), "soul_speed", org.bukkit.enchantments.Enchantment.SOUL_SPEED);
        updateEnchantment(player.getInventory().getBoots(), "depth_strider", org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
        updateEnchantment(player.getInventory().getLeggings(), "swift_sneak", org.bukkit.enchantments.Enchantment.SWIFT_SNEAK);
        updateEnchantment(player.getInventory().getItemInMainHand(), "efficiency", org.bukkit.enchantments.Enchantment.EFFICIENCY);

        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            walkSpeedCache = attr.getValue();
        }

        foodLevelCache = player.getFoodLevel();
        allowFlightCache = player.getAllowFlight();
        flyingCache = player.isFlying();
        glidingCache = player.isGliding();
        insideVehicleCache = player.isInsideVehicle();
        worldMinHeightCache = player.getWorld().getMinHeight();
        worldMaxHeightCache = player.getWorld().getMaxHeight();

        org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
        holdingBlockCache = (hand != null && hand.getType().isBlock() && hand.getType() != org.bukkit.Material.AIR);

        org.bukkit.inventory.ItemStack chest = player.getInventory().getChestplate();
        wearingElytraCache = (chest != null && chest.getType() == org.bukkit.Material.ELYTRA);

        holdingCrystalCache = (hand != null && hand.getType().name().contains("END_CRYSTAL"));

        org.bukkit.inventory.ItemStack off = player.getInventory().getItemInOffHand();
        slowItemCache = ret.tawny.truthful.utils.world.PhysicsUtils.shouldSlowDown(hand, player) ||
                ret.tawny.truthful.utils.world.PhysicsUtils.shouldSlowDown(off, player);

        tridentCache = (hand != null && hand.getType() == org.bukkit.Material.TRIDENT) ||
                (off != null && off.getType() == org.bukkit.Material.TRIDENT);

        this.lastSprinting = isSprinting();
        lastCacheUpdate = ticksTracked;
    }

    public void handleTeleportConfirm(int teleportId) {
        TeleportQueue.Teleport tp = teleportQueue.confirm(teleportId);
        if (tp != null) {
            this.nextMoveIsTeleport = true;
            this.nextMoveIsLagbackTeleport = tp.isLagback;

        }
    }

    public void acceptTeleport(SetPositionPacketWrapper wrapper) {
        boolean lagback = this.isNextTeleportLagback;
        teleportQueue.add(wrapper.getTeleportId(), new Location(player.getWorld(), wrapper.getX(), wrapper.getY(), wrapper.getZ()), lagback);

        if (lagback) {
            this.setExemption(ExemptionType.TELEPORT, 20);
            this.isNextTeleportLagback = false;
        } else {
            this.setExemption(ExemptionType.TELEPORT, 60);
        }
    }

    @Deprecated
    public void executeLagback() {
        // Redirect legacy calls to the centralized lagback system
        forceLagback();
    }

    // Called by the core Check.java engine
    public void forceLagback() {
        if (this.velocityQueue.hasExplosionVelocity() || isInExplosionGraceWindow(1500L)) {
            return;
        }
        this.isNextTeleportLagback = true; // Tag the next outgoing TP as a lagback
        this.setbackHandler.setback();
        if (!this.velocityQueue.hasExplosionVelocity()) {
            this.velocityQueue.clear();
        }
    }

    public void setLastFlagTick(int tick) {
        this.lastFlagTick = tick;
    }

    public int getLastFlagTick() {
        return lastFlagTick;
    }

    public boolean isSlowItem() { return slowItemCache; }
    public boolean isTrident() { return tridentCache; }
    public boolean isHoldingCrystal() { return holdingCrystalCache; }
    public boolean isHoldingBlock() { return holdingBlockCache; }
    public boolean isWearingElytra() { return wearingElytraCache; }

    private void updateEnchantment(org.bukkit.inventory.ItemStack item, String key, org.bukkit.enchantments.Enchantment enchant) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            int level = item.getEnchantmentLevel(enchant);
            if (level > 0) enchantmentCache.put(key, level);
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
    public int getPotionLevel(PotionEffectType type) { return potionCache.getOrDefault(type, 0); }
    public boolean hasPotionEffect(PotionEffectType type) { return potionCache.containsKey(type); }
    public double getWalkSpeed() { return walkSpeedCache; }

    public boolean isChunkLoaded() {
        int chunkX = (int) Math.floor(getX()) >> 4;
        int chunkZ = (int) Math.floor(getZ()) >> 4;
        return worldCache.isChunkLoaded(chunkX, chunkZ);
    }

    public boolean shouldSkipChecks() {
        double tps = Truthful.getInstance().getTps();
        double minTps = Truthful.getInstance().getConfiguration().getMinTps();
        if (tps < minTps) return true;
        if (isServerFrozen() || isTeleportTick()) return true;

        if (!isChunkLoaded()) {
            if (getTicksTracked() < 100 || getTicksSinceTeleport() < 100) {
                return true;
            }
        }
        return false;
    }

    public boolean isServerFrozen() { return setbackHandler.shouldBlockMovement(); }
    public int getTickFreezeGraceTicks() { return 0; }
    public float getRotationDeviation(boolean pitch) { return positionTracker.getRotationDeviation(pitch); }
    public int getTicksInAir() { return positionTracker.getAirTicks(); }

    public boolean isSwimming() {
        try { return player.isSwimming(); } catch (NoSuchMethodError t) { return false; } // For older versions
    }

    public short getNextTransactionId() {
        if (transactionId++ > 32000) transactionId = 0;
        return transactionId;
    }

    public void sendTransaction() { this.lastTransactionTime = System.currentTimeMillis(); }

    public void handleTransaction(short id) {
        TransactionEntry entry = transactionsSent.remove(id);

        if (entry != null) {
            this.playerClockAtLeast = Math.max(this.playerClockAtLeast, entry.timestamp);
            long nowNanos = System.nanoTime();
            long diffNanos = nowNanos - entry.timestamp;
            this.transactionPing = (this.transactionPing * 3 + (diffNanos / 1_000_000L)) / 4;
            this.timerBalanceRealTime = Math.max(this.timerBalanceRealTime, this.playerClockAtLeast - clockDrift);
            checkPingKick(this.transactionPing);
        }

        this.velocityQueue.confirm(id);

        if (this.setbackHandler.onTransaction(id)) {
            this.movementProcessor.reset();
            this.timerBalanceRealTime = System.nanoTime();
        }
    }

    private void checkPingKick(long currentPing) {
        Configuration config = Truthful.getInstance().getConfiguration();
        if (!config.isPingKickEnabled()) {
            this.highPingStartTime = 0;
            return;
        }

        if (currentPing > config.getPingKickThreshold()) {
            if (this.highPingStartTime == 0) {
                this.highPingStartTime = System.currentTimeMillis();
            } else {
                long duration = System.currentTimeMillis() - this.highPingStartTime;
                long maxDuration = config.getPingKickDuration() * 1000L;

                if (duration > maxDuration) {
                    Threading.runOnMain(() -> {
                        if (player.isOnline()) player.kickPlayer(config.getPingKickMessage());
                    });
                    this.highPingStartTime = System.currentTimeMillis() + 10000;
                }
            }
        } else {
            this.highPingStartTime = 0;
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
    public void resetTotalViolations() { this.totalViolations = 0; }
    public long getLastTransactionTime() { return lastTransactionTime; }
    public double getTransactionTimerBalance() { return (double) (timerBalanceRealTime - System.nanoTime()) / 1_000_000.0; }
    public long getPlayerClockAtLeast() { return playerClockAtLeast; }
    public long getTimerBalanceRealTime() { return timerBalanceRealTime; }
    public void addTimerBalance(long nanos) { this.timerBalanceRealTime += nanos; }

    public void setExemption(ExemptionType type, int ticks) { exemptions.put(type, ticks); }
    public void setVelocityExemption(int ticks) { setExemption(ExemptionType.VELOCITY, ticks); }
    public boolean isExempt(ExemptionType type) { return permanentlyExempt || exemptions.containsKey(type); }

    public boolean isExempt() {
        if (shouldSkipChecks()) return true;
        return permanentlyExempt || isExempt(ExemptionType.JOIN) || isExempt(ExemptionType.TELEPORT) || isAllowFlight();
    }

    public void setExempt(boolean exempt) { this.permanentlyExempt = exempt; }
    public boolean isJoinExempt() { return isExempt(ExemptionType.JOIN); }
    public boolean isRotationExempt() { return isExempt(ExemptionType.TELEPORT) || isExempt(ExemptionType.JOIN) || isInsideVehicle() || (ticksTracked - lastVehicleExitTick < 10); }

    public boolean isMovementExempt() { return isExempt(ExemptionType.TELEPORT) || isExempt(ExemptionType.JOIN) || isAllowFlight() || isInsideVehicle() || (ticksTracked - lastVehicleExitTick < 10); }
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
    public float getLastDeltaYaw() { return positionTracker.getLastDeltaYaw(); }
    public float getLastDeltaPitch() { return positionTracker.getLastDeltaPitch(); }
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
    public List<Long> getClickDelays() { return actionTracker.getClickDelays(); }
    public List<Integer> getClickedSlots() { return actionTracker.getClickedSlots(); }

    public boolean isInLiquid() { return positionTracker.isInLiquid(); }
    public boolean isOnClimbable() { return positionTracker.isOnClimbable(); }
    public boolean isInWeb() { return positionTracker.isInWeb(); }
    public boolean isNearVehicle() { return positionTracker.isNearVehicle(); }
    public void setNearVehicle(boolean b) { positionTracker.setNearVehicle(b); }
    public boolean isNearEntity() { return positionTracker.isNearEntity(); }
    public void setNearEntity(boolean b) { positionTracker.setNearEntity(b); }
    public boolean isUnderBlock() { return positionTracker.isUnderBlock(); }
    public boolean isRiptiding() { return isUsingRiptide(); }

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
    public int getUsingItemTicks() { return actionTracker.getUsingItemTicks(); }

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
        if (e != null) {
            this.lastTargetId = e.getEntityId();
        } else {
            this.lastTargetId = -1;
        }
    }
    public Entity getLastTarget() { return lastTarget; }
    public int getLastTargetId() { return lastTargetId; }

    public void setPreExitVehicleSpeed(double s) { this.preExitVehicleSpeed = s; }
    public double getBaritoneTrust() { return baritoneTrust; }
    public void lowerBaritoneTrust(double amount) { this.baritoneTrust = Math.max(0, baritoneTrust - amount); }

    public void onFireworkInteraction() { fireworkTracker.onFireworkInteraction(ticksTracked, getDeltaXZ(), player.isGliding()); }
    public boolean processFireworkBoost(double speed, double acceleration) { return fireworkTracker.update(ticksTracked, speed, acceleration); }

    public boolean hasVelocity() { return velocityQueue.hasActiveVelocity(); }
    public int getLastVelocityTick() { return lastVelocityTick; }
    public void setLastVelocityTick(int t) { this.lastVelocityTick = t; }
    public double getMaxVelocity() { return velocityQueue.getActiveVelocity().length(); }

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
        this.setExemption(ExemptionType.TELEPORT, 60);

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
        this.setExemption(ExemptionType.TELEPORT, 100);
    }

    public double getEyeHeight(boolean legacy, boolean sneaking, boolean swimming) {
        return positionTracker.getEyeHeight(legacy, sneaking, swimming);
    }

    public int getTicksSinceTeleport() {
        return this.ticksTracked - this.movementState.getLastTeleportTick();
    }

    public boolean isTeleportTick() {
        return isExempt(ExemptionType.TELEPORT);
    }

    public CompensatedWorld getWorldCache() { return worldCache; }
    public long getPing() { return transactionPing; }
    public void setPing(long ping) { this.transactionPing = ping; }
    public String getClientBrand() { return clientBrand; }
    public void setClientBrand(String brand) { this.clientBrand = brand; }
    public int getVl() { return totalViolations; }
    public void addVl(int amount) { this.totalViolations += amount; }
    public boolean isAlertsEnabled() { return alertsEnabled; }
    public void setAlertsEnabled(boolean enabled) { this.alertsEnabled = enabled; }

    private static class TransactionEntry {
        final short id;
        final long timestamp;
        TransactionEntry(short id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }
    }
}
