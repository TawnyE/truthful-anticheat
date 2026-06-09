package ret.tawny.truthful.data.handler;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.PlayerData;

public final class SetbackHandler {

    private final PlayerData data;
    private final Player player;

    private Location lastSafeLocation;
    private boolean awaitingTeleport = false;
    private short pendingTransactionId = -1;
    private long lastSetbackTime = 0;

    /** How many consecutive setbacks have occurred without the player reaching a safe state. */
    private int consecutiveSetbacks = 0;

    /** SUPER_STRICT freezes movement processing until the client confirms the teleport. */
    private boolean frozen = false;

    public SetbackHandler(PlayerData data) {
        this.data = data;
        this.player = data.getPlayer();
        this.lastSafeLocation = player.getLocation();
    }

    /**
     * Updates the safe location if conditions allow it.
     * All gating logic lives here — PlayerData calls this unconditionally
     * every position tick and the handler decides whether to accept the update.
     *
     * Gates (applied before mode-specific checks):
     * - No active teleport/frozen state
     * - No queued velocity
     * - No pending teleport confirmations
     * - Player must be on server ground
     * - Must be more than 12 ticks since last flag
     * - Must not have excessive consecutive setbacks
     */
    public void updateSafeLocation(Location location) {
        if (awaitingTeleport || frozen) return;
        if (data.hasVelocity()) return;
        if (data.getTeleportQueue().getPendingCount() > 0) return;
        if (data.isTeleportPending()) return;
        if (!data.isServerGround()) return;
        if (data.getTicksTracked() - data.getLastFlagTick() <= 12) return;
        if (consecutiveSetbacks >= 2) return;

        Configuration.LagbackMode mode = Truthful.getInstance().getConfiguration().getLagbackMode();

        switch (mode) {
            case SUPER_STRICT:
            case STRICT:
                if (data.getAirTicks() == 0
                        && data.getTicksTracked() - data.getLastFlagTick() > 10) {
                    this.lastSafeLocation = location.clone();
                    this.consecutiveSetbacks = 0;
                }
                break;
            case MODERATE:
                if (data.getAirTicks() == 0
                        && data.getTicksTracked() - data.getLastFlagTick() > 8) {
                    this.lastSafeLocation = location.clone();
                    this.consecutiveSetbacks = 0;
                }
                break;
            case BARELY:
            default:
                this.lastSafeLocation = location.clone();
                this.consecutiveSetbacks = 0;
                break;
        }
    }

    public Location getLastSafeLocation() {
        return lastSafeLocation;
    }


    private Location resolveSetbackTarget() {
        Configuration config = Truthful.getInstance().getConfiguration();
        double snapDistance = config.getLagbackGroundSnapDistance();

        // Current reported player position is the primary target
        double curX = data.getX();
        double curY = data.getY();
        double curZ = data.getZ();
        Location currentPos = new Location(player.getWorld(), curX, curY, curZ, data.getYaw(), data.getPitch());

        // 1. If the player is on server ground, use current position directly
        if (data.isServerGround()) {
            return currentPos;
        }

        // 2. If airborne, find the nearest solid ground beneath current XZ
        Location groundSnap = findGroundBelow(curX, curY, curZ);
        if (groundSnap != null) {
            double distToSnap = currentPos.distanceSquared(groundSnap);
            if (distToSnap <= snapDistance * snapDistance) {
                return groundSnap;
            }
            // Ground is too far below — fall through to safe location check
        }

        // 3. Fall back to lastSafeLocation, but apply distance threshold
        Location safe = lastSafeLocation;
        if (safe == null) {
            return currentPos;
        }

        if (safe.getWorld() == null || !safe.getWorld().equals(player.getWorld())) {
            return currentPos;
        }

        double distToSafe = currentPos.distanceSquared(safe);
        if (distToSafe > snapDistance * snapDistance) {
            // Safe location is too far — prefer current-position ground-snap or current pos
            if (groundSnap != null) {
                return groundSnap;
            }
            return currentPos;
        }

        return safe;
    }

    /**
     * Scans downward from the given position to find the nearest solid block surface.
     * Returns a Location with yaw/pitch from the player's current rotation, or null
     * if no ground is found within 10 blocks.
     */
    private Location findGroundBelow(double x, double y, double z) {
        int startY = (int) Math.floor(y);
        int minY = data.getWorldMinHeight();
        int maxScan = 10;

        for (int dy = 0; dy <= maxScan; dy++) {
            int checkY = startY - dy;
            if (checkY < minY) break;

            if (ret.tawny.truthful.utils.world.BlockPropertyRegistry.isSolid(
                    data.getWorldCache().getBlockState((int) Math.floor(x), checkY, (int) Math.floor(z)))) {
                return new Location(player.getWorld(), x, checkY + 1.0, z, data.getYaw(), data.getPitch());
            }
        }
        return null;
    }

    public void setback() {
        Configuration config = Truthful.getInstance().getConfiguration();
        Configuration.LagbackMode mode = config.getLagbackMode();

        long now = System.currentTimeMillis();

        // Cooldown enforcement — but in STRICT/SUPER_STRICT, allow re-setback
        // even while awaiting teleport if enough time passed (prevents fly-through)
        if (now - lastSetbackTime < mode.cooldownMs) return;

        // In STRICT/SUPER_STRICT, we DO NOT skip if awaitingTeleport.
        // We force a new setback to override the client's position.
        // FIX: MODERATE now also re-setbacks more aggressively when consecutive
        // setbacks indicate active cheating.
        if (awaitingTeleport) {
            long teleportTimeout;
            switch (mode) {
                case SUPER_STRICT: teleportTimeout = 80L; break;   // 80ms — extremely aggressive
                case STRICT:       teleportTimeout = 150L; break;  // 150ms
                // FIX: MODERATE timeout reduced from 350ms to 120ms.
                // 350ms gave fly cheats ~7 ticks of free movement between setbacks.
                // 120ms limits them to ~2 ticks, making flight effectively impossible.
                case MODERATE:     teleportTimeout = 120L; break;
                default:           teleportTimeout = 500L; break;
            }
            if (now - lastSetbackTime < teleportTimeout) return;
            // Timeout hit — force a new setback even though we didn't get confirmation
            awaitingTeleport = false;
        }

        boolean explosionActive = data.getVelocities().hasExplosionVelocity() ||
                data.isInExplosionGraceWindow(1500L);
        if (explosionActive) return;

        // Distance threshold check (BARELY mode only sets back if far enough)
        // FIX: MODERATE no longer has a distance threshold — it was 3.0 which
        // allowed players to fly 3 blocks between setbacks. Now always setbacks.
        if (mode.distanceThreshold > 0.0) {
            Location current = new Location(player.getWorld(), data.getX(), data.getY(), data.getZ());
            Location safe = lastSafeLocation != null ? lastSafeLocation : player.getLocation();
            double dist = current.distanceSquared(safe);
            if (dist < (mode.distanceThreshold * mode.distanceThreshold)) {
                return;
            }
        }

        // BARELY mode: skip airborne setbacks entirely
        if (!mode.setbackOnAirborne && data.getAirTicks() > 0) {
            return;
        }

        this.awaitingTeleport = true;
        this.lastSetbackTime = now;
        this.consecutiveSetbacks++;

        // SUPER_STRICT: freeze movement processing until teleport confirmed
        if (mode == Configuration.LagbackMode.SUPER_STRICT) {
            this.frozen = true;
        }

        // FIX: MODERATE/STRICT also freeze after repeated setbacks.
        // If we've set back 3+ times without the player reaching safety,
        // they're actively cheating. Freeze to prevent any further movement
        // until the client confirms the teleport.
        if (consecutiveSetbacks >= 3 && (mode == Configuration.LagbackMode.MODERATE
                || mode == Configuration.LagbackMode.STRICT)) {
            this.frozen = true;
        }

        Location target = resolveSetbackTarget();

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
            Truthful.getInstance().getServerScheduler().runRegion(player, () -> {
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
                frozen = false;
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if movement processing should be blocked.
     * In SUPER_STRICT, this stays true until the client confirms the teleport.
     * In other modes, times out after a short window.
     *
     * FIX: MODERATE timeout reduced from 400ms to 150ms, and frozen state
     * (from consecutive setbacks) uses a 2s safety valve like SUPER_STRICT.
     */
    public boolean shouldBlockMovement() {
        if (!awaitingTeleport && !frozen) return false;

        Configuration.LagbackMode mode = Truthful.getInstance().getConfiguration().getLagbackMode();

        // FIX: Any mode that is frozen (from consecutive setbacks or SUPER_STRICT)
        // stays frozen until the client confirms. Safety valve prevents softlock.
        if (frozen) {
            long safetyValveMs = (mode == Configuration.LagbackMode.SUPER_STRICT) ? 3000L : 2000L;
            if (System.currentTimeMillis() - lastSetbackTime > safetyValveMs) {
                awaitingTeleport = false;
                frozen = false;
                return false;
            }
            return true;
        }

        // Non-frozen modes: timeout-based
        long timeout;
        switch (mode) {
            case STRICT:   timeout = 200L; break;
            // FIX: MODERATE timeout reduced from 400ms to 150ms.
            // 400ms was too lenient — it let players move for 8 ticks after a setback
            // even though the teleport hadn't been confirmed yet.
            case MODERATE: timeout = 150L; break;
            default:       timeout = 500L; break;
        }

        if (System.currentTimeMillis() - lastSetbackTime > timeout) {
            awaitingTeleport = false;
            frozen = false;
            return false;
        }

        return awaitingTeleport;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public int getConsecutiveSetbacks() {
        return consecutiveSetbacks;
    }
}