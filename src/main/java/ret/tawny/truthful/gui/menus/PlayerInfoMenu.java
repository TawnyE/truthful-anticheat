package ret.tawny.truthful.gui.menus;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PlayerInfoMenu {

    private static final DecimalFormat COORD = new DecimalFormat("0.00");
    private static final DecimalFormat SPEED = new DecimalFormat("0.000");

    public static String getTitle(String playerName) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + "Info: " + playerName;
    }

    public static void open(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(target.getName()));
        GuiItemFactory.fillGradientBorder(inv);
        inv.setItem(49, GuiItemFactory.createBackButton("Player Selection"));
        update(inv, target);
        admin.openInventory(inv);
    }

    public static void update(Inventory inv, Player target) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        if (data == null) return;

        int vl = data.getVl();
        String vlColor = vl == 0 ? GuiConstants.SUCCESS : vl < 10 ? GuiConstants.SECONDARY : vl < 50 ? GuiConstants.WARNING : GuiConstants.ERROR;

        inv.setItem(4, GuiItemFactory.createPlayerHead(target,
                GuiConstants.ACCENT + GuiConstants.BOLD + target.getName(),
                List.of(
                        GuiConstants.metric("UUID", shortUuid(target.getUniqueId().toString())),
                        GuiConstants.metric("Mode", target.getGameMode().name()),
                        GuiConstants.metric("Health", COORD.format(target.getHealth())),
                        GuiConstants.DARK + GuiConstants.LINE + " " + GuiConstants.MUTED + "Total VL " + vlColor + vl)));

        inv.setItem(19, GuiItemFactory.createGlowing(
                GuiConstants.getMat("COMPASS"),
                GuiConstants.SECONDARY + GuiConstants.BOLD + "Position",
                List.of(
                        GuiConstants.metric("X", COORD.format(data.getX())),
                        GuiConstants.metric("Y", COORD.format(data.getY())),
                        GuiConstants.metric("Z", COORD.format(data.getZ())),
                        "",
                        GuiConstants.metric("Yaw", COORD.format(data.getYaw())),
                        GuiConstants.metric("Pitch", COORD.format(data.getPitch())))));

        inv.setItem(20, GuiItemFactory.createGlowing(
                GuiConstants.getMat("ARROW"),
                GuiConstants.ACCENT + GuiConstants.BOLD + "Movement",
                List.of(
                        GuiConstants.metric("Speed XZ", SPEED.format(data.getDeltaXZ())),
                        GuiConstants.metric("Delta Y", SPEED.format(data.getDeltaY())),
                        GuiConstants.metric("Yaw Delta", COORD.format(data.getDeltaYaw())),
                        GuiConstants.metric("Pitch Delta", COORD.format(data.getDeltaPitch())),
                        GuiConstants.metric("Sprinting", yesNo(data.isSprinting())))));

        int ping = (int) data.getPing();
        inv.setItem(21, GuiItemFactory.createGlowing(
                GuiConstants.getMat("CLOCK", "WATCH"),
                GuiConstants.WARNING + GuiConstants.BOLD + "Network",
                List.of(
                        GuiConstants.DARK + GuiConstants.LINE + " " + GuiConstants.MUTED + "Ping " + getPingColor(ping) + ping + "ms",
                        GuiConstants.metric("Timer", String.format("%.2fms", data.getTransactionTimerBalance())),
                        GuiConstants.metric("Last ping", data.getLastTransactionTime() == 0L ? "Pending" : "Tracked"))));

        boolean groundMismatch = data.isClientGround() != data.isServerGround();
        inv.setItem(22, GuiItemFactory.createGlowing(
                GuiConstants.getMat("GRASS_BLOCK", "GRASS"),
                (groundMismatch ? GuiConstants.WARNING : GuiConstants.SUCCESS) + GuiConstants.BOLD + "Ground",
                List.of(
                        GuiConstants.metric("Client", yesNo(data.isClientGround())),
                        GuiConstants.metric("Server", yesNo(data.isServerGround())),
                        GuiConstants.metric("Mismatch", yesNo(groundMismatch)),
                        GuiConstants.metric("Air ticks", String.valueOf(data.getAirTicks())),
                        GuiConstants.metric("Ground ticks", String.valueOf(data.getGroundTicks())))));

        int sensitivity = data.getSensitivityPercent();
        String sensitivityText = sensitivity < 0 ? GuiConstants.MUTED + "Learning" : GuiConstants.ACCENT + sensitivity + "%";
        inv.setItem(23, GuiItemFactory.createGlowing(
                GuiConstants.getMat("ENDER_EYE", "EYE_OF_ENDER"),
                GuiConstants.AQUA + GuiConstants.BOLD + "Sensitivity",
                List.of(
                        GuiConstants.DARK + "Rotation input estimate",
                        "",
                        GuiConstants.DARK + GuiConstants.LINE + " " + GuiConstants.MUTED + "Value " + sensitivityText,
                        GuiConstants.metric("Bounds", "0-200%"))));

        inv.setItem(24, GuiItemFactory.createGlowing(
                GuiConstants.getMat("NAME_TAG"),
                GuiConstants.LIGHT_PURPLE + GuiConstants.BOLD + "Client",
                List.of(
                        GuiConstants.metric("Brand", clean(data.getClientBrand())),
                        GuiConstants.metric("Version", getClientVersionString(target)),
                        GuiConstants.metric("Server", String.valueOf(Truthful.getInstance().getVersionManager().getAdapter().getServerVersion())))));

        inv.setItem(25, GuiItemFactory.createGlowing(
                GuiConstants.getMat("POTION"),
                GuiConstants.PURPLE + GuiConstants.BOLD + "Effects",
                buildPotionLore(target.getActivePotionEffects())));

        double tps = Truthful.getInstance().getTps();
        String tpsColor = tps >= 19.0D ? GuiConstants.SUCCESS : tps >= 17.5D ? GuiConstants.WARNING : GuiConstants.ERROR;
        inv.setItem(31, GuiItemFactory.createGlowing(
                GuiConstants.getMat("REDSTONE_BLOCK"),
                GuiConstants.ERROR + GuiConstants.BOLD + "Server",
                List.of(
                        GuiConstants.DARK + GuiConstants.LINE + " " + GuiConstants.MUTED + "TPS " + tpsColor + String.format("%.2f", tps),
                        GuiConstants.metric("Players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()),
                        GuiConstants.metric("Strict checks", data.shouldSkipChecks() ? "Paused" : "Active"))));
    }

    private static List<String> buildPotionLore(Collection<PotionEffect> effects) {
        List<String> lore = new ArrayList<>();
        if (effects.isEmpty()) {
            lore.add(GuiConstants.MUTED + "None");
            return lore;
        }
        int shown = 0;
        for (PotionEffect effect : effects) {
            if (shown++ >= 6) {
                lore.add(GuiConstants.MUTED + "+" + (effects.size() - 6) + " more");
                break;
            }
            lore.add(GuiConstants.DARK + GuiConstants.BULLET + " " + GuiConstants.HIGHLIGHT
                    + formatEnum(effect.getType().getName()) + " " + (effect.getAmplifier() + 1)
                    + GuiConstants.MUTED + " (" + formatTime(effect.getDuration() / 20) + ")");
        }
        return lore;
    }

    private static String getClientVersionString(Player player) {
        try {
            ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            if (version != null) {
                String name = version.name();
                return name.startsWith("V_") ? name.substring(2).replace("_", ".") : name;
            }
        } catch (Throwable ignored) {
        }
        return "Unknown";
    }

    private static String getPingColor(int ping) {
        if (ping < 50) return GuiConstants.SUCCESS;
        if (ping < 100) return GuiConstants.SECONDARY;
        if (ping < 200) return GuiConstants.WARNING;
        return GuiConstants.ERROR;
    }

    private static String yesNo(boolean value) {
        return value ? GuiConstants.SUCCESS + "Yes" : GuiConstants.ERROR + "No";
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private static String shortUuid(String uuid) {
        return uuid.length() > 8 ? uuid.substring(0, 8) + "..." : uuid;
    }

    private static String formatEnum(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }

    private static String formatTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private PlayerInfoMenu() {
    }
}
