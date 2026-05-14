package ret.tawny.truthful.commands.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;

import java.util.*;

/**
 * /truthful tools <player> <tool>
 *
 * Testing tools for anti-cheat validation. Each tool sends a specific test
 * packet or challenge to a player and reports PASS/FAIL.
 *
 * Tools:
 * - kb         — Sends knockback and measures if the player processes correct velocity
 * - rotation   — Analyzes the player's rotation GCD to validate Minecraft sensitivity math
 * - sensitivity — Reports the detected mouse sensitivity percentage
 * - timer      — Measures packet rate over 5 seconds to detect timer manipulation
 * - reach      — Reports the player's current average hit distance
 */
public final class CommandTools {

    private static final String PREFIX = "§8[§eTruthful§8] ";
    private static final String DIVIDER = "§8§m─────────────────────────────────";

    // Active tool sessions per player UUID
    private static final Map<UUID, String> activeSessions = new HashMap<>();

    public static void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(PREFIX + "§cOnly players can use testing tools.");
            return;
        }

        if (args.length < 3) {
            sendToolHelp(staff);
            return;
        }

        String targetName = args[1];
        String tool = args[2].toLowerCase();

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            staff.sendMessage(PREFIX + "§cPlayer not found: §f" + targetName);
            return;
        }

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        if (data == null) {
            staff.sendMessage(PREFIX + "§cNo data for player: §f" + targetName);
            return;
        }

        switch (tool) {
            case "kb" -> runKBTest(staff, target, data);
            case "rotation" -> runRotationTest(staff, target, data);
            case "sensitivity" -> runSensitivityTest(staff, target, data);
            case "timer" -> runTimerTest(staff, target, data);
            case "reach" -> runReachTest(staff, target, data);
            default -> sendToolHelp(staff);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // KB TEST: Send knockback velocity and measure response
    // ─────────────────────────────────────────────────────────────────────────────
    private static void runKBTest(Player staff, Player target, PlayerData data) {
        staff.sendMessage(DIVIDER);
        staff.sendMessage(PREFIX + "§eKB Test §7→ §f" + target.getName());
        staff.sendMessage(PREFIX + "§7Sending directional knockback...");

        // Calculate a directional KB vector based on the staff's look direction
        // This launches the player sideways + upward so you can visually see the response
        Vector staffDir = staff.getLocation().getDirection().normalize();
        Vector kb = new Vector(
                staffDir.getX() * 0.6,
                0.45,
                staffDir.getZ() * 0.6
        );

        final double kbMagnitude = kb.length();

        Bukkit.getScheduler().runTask(Truthful.getInstance().getPlugin(), () -> {
            target.setVelocity(kb);
        });

        // Track the player's response over the next 20 ticks
        new BukkitRunnable() {
            int ticks = 0;
            double maxDeltaY = 0;
            double maxDeltaXZ = 0;
            boolean processedVertical = false;
            boolean processedHorizontal = false;

            @Override
            public void run() {
                ticks++;
                double currentDeltaY = data.getDeltaY();
                double currentDeltaXZ = data.getDeltaXZ();

                if (currentDeltaY > maxDeltaY) maxDeltaY = currentDeltaY;
                if (currentDeltaXZ > maxDeltaXZ) maxDeltaXZ = currentDeltaXZ;

                if (currentDeltaY > 0.08) processedVertical = true;
                if (currentDeltaXZ > 0.15) processedHorizontal = true;

                if (ticks >= 20) {
                    cancel();

                    double verticalPct = (maxDeltaY / 0.45) * 100.0;
                    double horizontalPct = (maxDeltaXZ / (0.6 * Math.sqrt(2))) * 100.0;
                    boolean pass = processedVertical && processedHorizontal && verticalPct > 40.0;

                    staff.sendMessage(PREFIX + "§7KB Direction: §f" + String.format("(%.2f, %.2f, %.2f)", kb.getX(), kb.getY(), kb.getZ()));
                    staff.sendMessage(PREFIX + "§7Max ΔY: §f" + String.format("%.4f", maxDeltaY) + " §7(" + String.format("%.1f%%", verticalPct) + " of expected)");
                    staff.sendMessage(PREFIX + "§7Max ΔXZ: §f" + String.format("%.4f", maxDeltaXZ) + " §7(" + String.format("%.1f%%", horizontalPct) + " of expected)");
                    staff.sendMessage(PREFIX + "§7Vertical Response: " + (processedVertical ? "§a✔" : "§c✘"));
                    staff.sendMessage(PREFIX + "§7Horizontal Response: " + (processedHorizontal ? "§a✔" : "§c✘"));
                    staff.sendMessage(PREFIX + "§7Result: " + (pass ? "§a✔ PASS" : "§c✘ FAIL") +
                            (pass ? " §7(velocity processed correctly)" : " §7(possible velocity modification)"));
                    staff.sendMessage(DIVIDER);
                }
            }
        }.runTaskTimer(Truthful.getInstance().getPlugin(), 5L, 1L);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ROTATION TEST: Analyze rotation GCD to validate Minecraft sensitivity math
    // ─────────────────────────────────────────────────────────────────────────────
    private static void runRotationTest(Player staff, Player target, PlayerData data) {
        staff.sendMessage(DIVIDER);
        staff.sendMessage(PREFIX + "§eRotation Test §7→ §f" + target.getName());
        staff.sendMessage(PREFIX + "§7Sampling rotation data over 3 seconds...");

        final List<Float> pitchDeltas = new ArrayList<>();
        final List<Float> yawDeltas = new ArrayList<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                float dp = data.getDeltaPitch();
                float dy = data.getDeltaYaw();

                if (dp > 0.001f && dp < 20.0f) pitchDeltas.add(dp);
                if (dy > 0.001f && dy < 180.0f) yawDeltas.add(dy);

                if (ticks >= 60) { // 3 seconds
                    cancel();

                    if (pitchDeltas.size() < 3) {
                        staff.sendMessage(PREFIX + "§cInsufficient rotation data. Player may be AFK.");
                        staff.sendMessage(DIVIDER);
                        return;
                    }

                    // Calculate GCD of pitch deltas
                    double gcd = 0;
                    for (float d : pitchDeltas) {
                        if (gcd == 0) {
                            gcd = d;
                        } else {
                            gcd = gcd(gcd, d);
                        }
                    }

                    // Validate against Minecraft sensitivity formula:
                    // f = sens * 0.6 + 0.2; step = f^3 * 8 * 0.15
                    double f = Math.cbrt(gcd / 1.2);
                    double sensitivity = (f - 0.2) / 0.6;
                    int sensPercent = (int) Math.round(sensitivity * 200.0);

                    boolean validGcd = gcd > 0.0001 && gcd < 2.0;
                    boolean validSens = sensPercent >= 0 && sensPercent <= 200;
                    boolean pass = validGcd && validSens;

                    staff.sendMessage(PREFIX + "§7Samples: §f" + pitchDeltas.size() + " pitch, " + yawDeltas.size() + " yaw");
                    staff.sendMessage(PREFIX + "§7Rotation GCD: §f" + String.format("%.6f", gcd));
                    staff.sendMessage(PREFIX + "§7Derived Sensitivity: §f" + sensPercent + "%");
                    staff.sendMessage(PREFIX + "§7GCD Valid: " + (validGcd ? "§a✔" : "§c✘"));
                    staff.sendMessage(PREFIX + "§7Result: " + (pass ? "§a✔ PASS" : "§c✘ FAIL") +
                            (pass ? " §7(rotations match valid sensitivity)" : " §7(abnormal rotation pattern)"));
                    staff.sendMessage(DIVIDER);
                }
            }
        }.runTaskTimerAsynchronously(Truthful.getInstance().getPlugin(), 1L, 1L);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SENSITIVITY TEST: Report detected mouse sensitivity
    // ─────────────────────────────────────────────────────────────────────────────
    private static void runSensitivityTest(Player staff, Player target, PlayerData data) {
        staff.sendMessage(DIVIDER);
        staff.sendMessage(PREFIX + "§eSensitivity Test §7→ §f" + target.getName());

        int detected = data.getSensitivityPercent();

        if (detected < 0) {
            staff.sendMessage(PREFIX + "§cSensitivity has not yet been resolved.");
            staff.sendMessage(PREFIX + "§7The player needs to rotate their camera first.");
            staff.sendMessage(PREFIX + "§7Result: §e⚠ PENDING");
        } else {
            boolean valid = detected >= 0 && detected <= 200;
            staff.sendMessage(PREFIX + "§7Detected Sensitivity: §f" + detected + "%");
            staff.sendMessage(PREFIX + "§7Valid Range: §f0-200%");
            staff.sendMessage(PREFIX + "§7Result: " + (valid ? "§a✔ PASS" : "§c✘ FAIL") +
                    (valid ? " §7(valid Minecraft sensitivity)" : " §7(out of range — possible rotation mod)"));
        }
        staff.sendMessage(DIVIDER);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TIMER TEST: Measure packet rate over 5 seconds
    // ─────────────────────────────────────────────────────────────────────────────
    private static void runTimerTest(Player staff, Player target, PlayerData data) {
        staff.sendMessage(DIVIDER);
        staff.sendMessage(PREFIX + "§eTimer Test §7→ §f" + target.getName());
        staff.sendMessage(PREFIX + "§7Measuring packet rate over 5 seconds...");

        final int startTick = data.getTicksTracked();
        final long startTime = System.nanoTime();

        new BukkitRunnable() {
            @Override
            public void run() {
                int endTick = data.getTicksTracked();
                long endTime = System.nanoTime();

                int ticksCounted = endTick - startTick;
                double secondsElapsed = (endTime - startTime) / 1_000_000_000.0;
                double ticksPerSecond = ticksCounted / secondsElapsed;
                double timerBalance = data.getTransactionTimerBalance();

                // Normal is ~20 tps; anything significantly above indicates Timer
                boolean pass = ticksPerSecond < 21.5 && timerBalance < 500.0;

                staff.sendMessage(PREFIX + "§7Ticks Counted: §f" + ticksCounted);
                staff.sendMessage(PREFIX + "§7Time Elapsed: §f" + String.format("%.2fs", secondsElapsed));
                staff.sendMessage(PREFIX + "§7Effective TPS: §f" + String.format("%.2f", ticksPerSecond));
                staff.sendMessage(PREFIX + "§7Timer Balance: §f" + String.format("%.1fms", timerBalance));
                staff.sendMessage(PREFIX + "§7Result: " + (pass ? "§a✔ PASS" : "§c✘ FAIL") +
                        (pass ? " §7(normal packet rate)" : " §7(abnormal packet rate — possible timer)"));
                staff.sendMessage(DIVIDER);
            }
        }.runTaskLater(Truthful.getInstance().getPlugin(), 100L); // 5 seconds
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // REACH TEST: Report average hit distance and validate
    // ─────────────────────────────────────────────────────────────────────────────
    private static void runReachTest(Player staff, Player target, PlayerData data) {
        staff.sendMessage(DIVIDER);
        staff.sendMessage(PREFIX + "§eReach Test §7→ §f" + target.getName());
        staff.sendMessage(PREFIX + "§7Monitoring hit distance for 10 seconds...");
        staff.sendMessage(PREFIX + "§7Player must attack an entity during this window.");

        final List<Double> hitDistances = new ArrayList<>();
        final int startAttackTick = data.getLastAttackPacketTick();

        new BukkitRunnable() {
            int ticks = 0;
            int lastRecordedAttackTick = startAttackTick;

            @Override
            public void run() {
                ticks++;

                // Check if a new attack happened since last check
                int currentAttackTick = data.getLastAttackPacketTick();
                if (currentAttackTick > lastRecordedAttackTick) {
                    lastRecordedAttackTick = currentAttackTick;

                    org.bukkit.entity.Entity lastTarget = data.getLastTarget();
                    if (lastTarget != null && lastTarget.isValid()) {
                        // Use eye position to target's bounding box center (more accurate than feet)
                        Location eyeLoc = new Location(
                                target.getWorld(),
                                data.getX(), data.getY() + PlayerData.EYE_HEIGHT_STANDING, data.getZ()
                        );

                        // Calculate distance to the closest point on the entity's hitbox
                        // Entity.getLocation() returns feet position. The hitbox center is ~half the height up.
                        Location entityLoc = lastTarget.getLocation();
                        double entityHeight = lastTarget.getHeight();
                        double entityWidth = lastTarget.getWidth();
                        double halfWidth = entityWidth / 2.0;

                        // Closest point on the entity's AABB to the player's eye
                        double closestX = clamp(eyeLoc.getX(), entityLoc.getX() - halfWidth, entityLoc.getX() + halfWidth);
                        double closestY = clamp(eyeLoc.getY(), entityLoc.getY(), entityLoc.getY() + entityHeight);
                        double closestZ = clamp(eyeLoc.getZ(), entityLoc.getZ() - halfWidth, entityLoc.getZ() + halfWidth);

                        double dist = Math.sqrt(
                                Math.pow(eyeLoc.getX() - closestX, 2) +
                                Math.pow(eyeLoc.getY() - closestY, 2) +
                                Math.pow(eyeLoc.getZ() - closestZ, 2)
                        );

                        if (dist > 0.0 && dist < 10.0) {
                            hitDistances.add(dist);
                        }
                    }
                }

                if (ticks >= 200) { // 10 seconds
                    cancel();

                    if (hitDistances.isEmpty()) {
                        staff.sendMessage(PREFIX + "§cNo attacks detected. Player did not attack anything.");
                        staff.sendMessage(PREFIX + "§7Result: §e⚠ INCONCLUSIVE");
                        staff.sendMessage(DIVIDER);
                        return;
                    }

                    double avg = hitDistances.stream().mapToDouble(d -> d).average().orElse(0);
                    double max = hitDistances.stream().mapToDouble(d -> d).max().orElse(0);
                    double min = hitDistances.stream().mapToDouble(d -> d).min().orElse(0);

                    // Vanilla creative reach is 5.0, survival is 3.0.
                    // With latency compensation + hitbox expansion, 3.5 is a fair PASS threshold.
                    boolean pass = max < 3.5;

                    staff.sendMessage(PREFIX + "§7Hits Recorded: §f" + hitDistances.size());
                    staff.sendMessage(PREFIX + "§7Avg Distance: §f" + String.format("%.3f", avg) + " blocks");
                    staff.sendMessage(PREFIX + "§7Min Distance: §f" + String.format("%.3f", min) + " blocks");
                    staff.sendMessage(PREFIX + "§7Max Distance: §f" + String.format("%.3f", max) + " blocks");
                    staff.sendMessage(PREFIX + "§7Threshold: §f3.5 blocks §7(vanilla 3.0 + latency tolerance)");
                    staff.sendMessage(PREFIX + "§7Result: " + (pass ? "§a✔ PASS" : "§c✘ FAIL") +
                            (pass ? " §7(within vanilla reach)" : " §7(exceeds vanilla reach — possible reach hack)"));
                    staff.sendMessage(DIVIDER);
                }
            }
        }.runTaskTimer(Truthful.getInstance().getPlugin(), 1L, 1L);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELP
    // ─────────────────────────────────────────────────────────────────────────────
    private static void sendToolHelp(Player staff) {
        staff.sendMessage(DIVIDER);
        staff.sendMessage(PREFIX + "§eTesting Tools");
        staff.sendMessage("§7/truthful tools <player> kb §8- §fKnockback processing test");
        staff.sendMessage("§7/truthful tools <player> rotation §8- §fRotation GCD validation");
        staff.sendMessage("§7/truthful tools <player> sensitivity §8- §fMouse sensitivity detection");
        staff.sendMessage("§7/truthful tools <player> timer §8- §fPacket rate measurement");
        staff.sendMessage("§7/truthful tools <player> reach §8- §fHit distance monitoring");
        staff.sendMessage(DIVIDER);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // UTIL
    // ─────────────────────────────────────────────────────────────────────────────
    private static double gcd(double a, double b) {
        if (a < b) return gcd(b, a);
        if (Math.abs(b) < 0.001) return a;
        return gcd(b, a - Math.floor(a / b) * b);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .toList();
        }
        if (args.length == 3) {
            String partial = args[2].toLowerCase();
            return List.of("kb", "rotation", "sensitivity", "timer", "reach").stream()
                    .filter(s -> s.startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }
}
