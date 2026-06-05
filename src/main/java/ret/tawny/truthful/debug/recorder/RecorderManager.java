package ret.tawny.truthful.debug.recorder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.debug.recorder.data.RecordingSession;
import ret.tawny.truthful.debug.recorder.data.TickSnapshot;
import ret.tawny.truthful.utils.world.PhysicsUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RecorderManager {

    private final Map<UUID, RecordingSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, WrappedTask> activeTimers = new ConcurrentHashMap<>();

    // Users who are listening to the debug output (Admins)
    // Key: Target UUID, Value: Admin UUID
    private final Map<UUID, UUID> debugSubscribers = new ConcurrentHashMap<>();

    public boolean isRecording(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void toggleRecording(Player admin, Player target) {
        if (isRecording(target)) {
            stopRecording(target);
            admin.sendMessage("§8[§bTruthful§8] §aStopped recording for " + target.getName());
        } else {
            startRecording(admin, target);
            admin.sendMessage("§8[§bTruthful§8] §aStarted recording " + target.getName() + " (5m Limit)");
        }
    }

    private void startRecording(Player admin, Player target) {
        UUID targetUUID = target.getUniqueId();

        // Initialize Session
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        String brand = (data != null) ? data.getClientBrand() : "Unknown";
        RecordingSession session = new RecordingSession(targetUUID, target.getName(), brand);

        activeSessions.put(targetUUID, session);
        debugSubscribers.put(targetUUID, admin.getUniqueId());

        // Schedule 5-minute auto-stop
        WrappedTask task = Truthful.getInstance().getServerScheduler().runGlobalLater(() -> {
            if (isRecording(target)) {
                stopRecording(target);
                if (admin.isOnline()) {
                    admin.sendMessage("§8[§bTruthful§8] §eRecording auto-stopped (5m limit).");
                }
            }
        }, 6000L); // 5 minutes * 60 seconds * 20 ticks

        activeTimers.put(targetUUID, task);
    }

    public void stopRecording(Player target) {
        UUID uuid = target.getUniqueId();

        // Cancel timer
        WrappedTask task = activeTimers.remove(uuid);
        if (task != null) task.cancel();

        // Save Data
        RecordingSession session = activeSessions.remove(uuid);
        if (session != null) {
            session.saveAndExport();
        }

        debugSubscribers.remove(uuid);
    }

    /**
     * Called every tick from PlayerData.update() to capture state.
     */
    public void captureTick(PlayerData data) {
        Player player = data.getPlayer();
        if (!isRecording(player)) return;

        // 1. Gather Data
        int tick = data.getTicksTracked();
        double x = data.getX();
        double y = data.getY();
        double z = data.getZ();
        float yaw = data.getYaw();
        float pitch = data.getPitch();

        double dX = data.getDeltaX();
        double dY = data.getDeltaY();
        double dZ = data.getDeltaZ();

        boolean cGround = data.isClientGround();
        boolean sGround = data.isServerGround();
        boolean lGround = data.isLastGround();

        // Environment
        boolean liquid = data.isInLiquid();
        boolean climb = data.isOnClimbable();
        boolean web = data.isInWeb();
        boolean roof = data.isUnderBlock();
        boolean vehicle = data.isNearVehicle();
        boolean slime = (data.getTicksTracked() - data.getLastSlimeTick() < 5);

        // Velocity & Prediction
        double vX = data.getProcessor().getVelocityX();
        double vY = data.getProcessor().getVelocityY();
        double vZ = data.getProcessor().getVelocityZ();
        boolean hasVel = data.hasVelocity();

        double pDX = data.getProcessor().getPredictedDragX();
        double pDZ = data.getProcessor().getPredictedDragZ();
        double pDY = data.getProcessor().getSimulatedY(); // Gravity engine result

        // Attributes
        boolean sprint = data.isSprinting();
        boolean sneak = data.isSneaking();
        boolean glide = player.isGliding();
        int jump = PhysicsUtils.getPotionLevel(player, PotionEffectType.JUMP_BOOST);
        int speed = PhysicsUtils.getPotionLevel(player, PotionEffectType.SPEED);
        float friction = PhysicsUtils.getEffectiveHorizontalFriction(player, data) > 0.61 ? 0.98f : 0.6f;

        // 2. Create Snapshot
        TickSnapshot snapshot = new TickSnapshot(
                tick, x, y, z, yaw, pitch,
                dX, dY, dZ,
                cGround, sGround, lGround,
                liquid, climb, web, roof, vehicle, slime,
                vX, vY, vZ, hasVel,
                pDX, pDZ, pDY,
                sprint, sneak, glide,
                jump, speed, friction
        );

        // 3. Add to Session
        RecordingSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.addSnapshot(snapshot);
        }

        // 4. Live Debug Output (Grim Style)
        UUID adminUUID = debugSubscribers.get(player.getUniqueId());
        if (adminUUID != null) {
            Player admin = Bukkit.getPlayer(adminUUID);
            if (admin != null) {
                sendLiveDebug(admin, data, dY, pDY, dX, dZ, pDX, pDZ);
            }
        }
    }

    private void sendLiveDebug(Player admin, PlayerData data,
                               double dY, double pDY,
                               double dX, double dZ,
                               double pDX, double pDZ) {

        // Color coding based on deviation
        String yColor = Math.abs(dY - pDY) > 0.0001 ? "§c" : "§a";
        String xzColor = Math.hypot(dX - pDX, dZ - pDZ) > 0.0001 ? "§c" : "§a";

        String groundState = (data.isClientGround() ? "§aC" : "§cC") + " " +
                (data.isServerGround() ? "§aS" : "§cS");

        // Format: [Tick] Y: Actual (Pred) | XZ: Actual (Pred) | G: C S
        String msg = String.format("§8[%d] §7Y: %s%.4f §7(%.4f) §8| §7XZ: %s%.4f §7(%.4f) §8| §7G: %s",
                data.getTicksTracked() % 1000,
                yColor, dY, pDY,
                xzColor, data.getDeltaXZ(), Math.hypot(pDX, pDZ),
                groundState
        );

        admin.sendMessage(msg);
    }
}