package ret.tawny.truthful.gui.menus;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Premium Player Info Menu (Live Inspector)
 * Real-time player data with velocity tracking and threat assessment.
 */
public final class PlayerInfoMenu {

    private static final DecimalFormat COORD = new DecimalFormat("0.00");
    private static final DecimalFormat SPEED = new DecimalFormat("0.0000");

    public static String getTitle(String playerName) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " §8» §7Info: " + playerName;
    }

    public static void open(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(target.getName()));
        update(inv, target);
        admin.openInventory(inv);
    }

    public static void update(Inventory inv, Player target) {
        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        if (data == null)
            return;

        inv.clear();
        GuiItemFactory.fillGradientBorder(inv);

        // ── PLAYER HEAD (slot 4) ──
        int vl = data.getVl();
        String vlColor = vl == 0 ? GuiConstants.SUCCESS
                : vl < 10 ? GuiConstants.SECONDARY
                        : vl < 50 ? GuiConstants.WARNING : GuiConstants.ERROR;

        List<String> headLore = new ArrayList<>();
        headLore.add(GuiConstants.DARK + "UUID: " + GuiConstants.MUTED +
                target.getUniqueId().toString().substring(0, 8) + "...");
        headLore.add("");
        headLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Gamemode  " + GuiConstants.HIGHLIGHT + target.getGameMode().name());
        headLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Health    " + GuiConstants.HIGHLIGHT +
                COORD.format(target.getHealth()) + GuiConstants.ERROR + " " + GuiConstants.SYM_HEART);
        headLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Total VL  " + vlColor + vl);

        inv.setItem(4, GuiItemFactory.createPlayerHead(target,
                GuiConstants.ACCENT + GuiConstants.BOLD + target.getName(), headLore));

        // ── ROW 2: Core Data Panels ──

        // Position
        List<String> posLore = new ArrayList<>();
        posLore.add(GuiConstants.DARK + "World Position");
        posLore.add("");
        posLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "X  " + GuiConstants.HIGHLIGHT + COORD.format(data.getX()));
        posLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Y  " + GuiConstants.HIGHLIGHT + COORD.format(data.getY()));
        posLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Z  " + GuiConstants.HIGHLIGHT + COORD.format(data.getZ()));
        posLore.add("");
        posLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Yaw    " + GuiConstants.HIGHLIGHT + COORD.format(data.getYaw()));
        posLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Pitch  " + GuiConstants.HIGHLIGHT + COORD.format(data.getPitch()));

        inv.setItem(19, GuiItemFactory.createGlowing(
                GuiConstants.getMat("COMPASS"),
                GuiConstants.SECONDARY + GuiConstants.BOLD + "Position", posLore));

        // Velocity / Movement
        List<String> velLore = new ArrayList<>();
        velLore.add(GuiConstants.DARK + "Movement Analysis");
        velLore.add("");
        velLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Speed XZ  " + GuiConstants.HIGHLIGHT + SPEED.format(data.getDeltaXZ()));
        velLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Delta Y   " + GuiConstants.HIGHLIGHT + SPEED.format(data.getDeltaY()));
        velLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "ΔYaw      " + GuiConstants.HIGHLIGHT + COORD.format(data.getDeltaYaw()) + "°");
        velLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "ΔPitch    " + GuiConstants.HIGHLIGHT + COORD.format(data.getDeltaPitch()) + "°");

        inv.setItem(20, GuiItemFactory.createGlowing(
                GuiConstants.getMat("ARROW"),
                GuiConstants.ACCENT + GuiConstants.BOLD + "Movement", velLore));

        // Network
        int ping = (int) data.getPing();
        String pingColor = getPingColor(ping);

        List<String> netLore = new ArrayList<>();
        netLore.add(GuiConstants.DARK + "Connection Info");
        netLore.add("");
        netLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Ping          " + pingColor + ping + "ms");
        netLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Timer         " + GuiConstants.HIGHLIGHT +
                String.format("%.4f", data.getTransactionTimerBalance()));
        netLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Transactions  " + GuiConstants.HIGHLIGHT + data.getNextTransactionId());

        inv.setItem(21, GuiItemFactory.createGlowing(
                GuiConstants.getMat("CLOCK", "WATCH"),
                GuiConstants.WARNING + GuiConstants.BOLD + "Network", netLore));

        // Ground State
        String clientGround = data.isClientGround() ? GuiConstants.SUCCESS + "TRUE" : GuiConstants.ERROR + "FALSE";
        String serverGround = data.isServerGround() ? GuiConstants.SUCCESS + "TRUE" : GuiConstants.ERROR + "FALSE";
        String mismatch = (data.isClientGround() != data.isServerGround())
                ? " " + GuiConstants.ERROR + "⚠"
                : "";

        List<String> groundLore = new ArrayList<>();
        groundLore.add(GuiConstants.DARK + "Physics Analysis");
        groundLore.add("");
        groundLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Client  " + clientGround);
        groundLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Server  " + serverGround + mismatch);
        groundLore.add("");
        groundLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Air Ticks     " + GuiConstants.HIGHLIGHT + data.getAirTicks());
        groundLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Ground Ticks  " + GuiConstants.HIGHLIGHT + data.getGroundTicks());

        inv.setItem(22, GuiItemFactory.createGlowing(
                GuiConstants.getMat("GRASS_BLOCK", "GRASS"),
                GuiConstants.SUCCESS + GuiConstants.BOLD + "Ground State", groundLore));

        // Sensitivity
        int sens = data.getSensitivityPercent();
        String sensDisplay = (sens == -1)
                ? GuiConstants.MUTED + "Calculating..."
                : GuiConstants.ACCENT + sens + "%";

        List<String> sensLore = new ArrayList<>();
        sensLore.add(GuiConstants.DARK + "Input Analysis");
        sensLore.add("");
        sensLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Estimated  " + sensDisplay);
        sensLore.add("");
        sensLore.add(GuiConstants.DARK + GuiConstants.ITALIC + "Refines over time");

        inv.setItem(23, GuiItemFactory.createGlowing(
                GuiConstants.getMat("ENDER_EYE", "EYE_OF_ENDER"),
                GuiConstants.AQUA + GuiConstants.BOLD + "Sensitivity", sensLore));

        // Client Info
        String clientBrand = data.getClientBrand();
        String clientVersion = getClientVersionString(target);

        List<String> brandLore = new ArrayList<>();
        brandLore.add(GuiConstants.DARK + "Identity Analysis");
        brandLore.add("");
        brandLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Brand     " + GuiConstants.HIGHLIGHT + clientBrand);
        brandLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Version   " + GuiConstants.ACCENT + clientVersion);
        brandLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Protocol  " + GuiConstants.HIGHLIGHT +
                Truthful.getInstance().getVersionManager().getAdapter().getServerVersion());

        inv.setItem(24, GuiItemFactory.createGlowing(
                GuiConstants.getMat("NAME_TAG"),
                GuiConstants.LIGHT_PURPLE + GuiConstants.BOLD + "Client Info", brandLore));

        // Potion Effects
        List<String> potionLore = new ArrayList<>();
        potionLore.add(GuiConstants.DARK + "Active Effects");
        potionLore.add("");

        Collection<PotionEffect> effects = target.getActivePotionEffects();
        if (effects.isEmpty()) {
            potionLore.add(GuiConstants.MUTED + "None");
        } else {
            for (PotionEffect effect : effects) {
                String name = formatEnum(effect.getType().getName());
                String amp = numToRoman(effect.getAmplifier() + 1);
                String dur = formatTime(effect.getDuration() / 20);
                potionLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                        GuiConstants.HIGHLIGHT + name + " " + amp + " " +
                        GuiConstants.MUTED + "(" + dur + ")");
            }
        }

        inv.setItem(25, GuiItemFactory.createGlowing(
                GuiConstants.getMat("POTION"),
                GuiConstants.PURPLE + GuiConstants.BOLD + "Effects", potionLore));

        // ── ROW 4: Server Status ──
        double tps = Truthful.getInstance().getTps();
        String tpsColor = tps >= 18 ? GuiConstants.SUCCESS
                : tps >= 15 ? GuiConstants.SECONDARY : GuiConstants.ERROR;

        List<String> serverLore = new ArrayList<>();
        serverLore.add(GuiConstants.DARK + "Performance");
        serverLore.add("");
        serverLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "TPS     " + tpsColor + String.format("%.2f", tps));
        serverLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Online  " + GuiConstants.HIGHLIGHT +
                Bukkit.getOnlinePlayers().size() + GuiConstants.DARK + "/" +
                GuiConstants.MUTED + Bukkit.getMaxPlayers());

        inv.setItem(31, GuiItemFactory.createGlowing(
                GuiConstants.getMat("REDSTONE_BLOCK"),
                GuiConstants.ERROR + GuiConstants.BOLD + "Server", serverLore));

        // Back button
        inv.setItem(49, GuiItemFactory.createBackButton("Player Selection"));
    }

    // ═══════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════

    private static String getClientVersionString(Player player) {
        try {
            ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            if (version != null) {
                String name = version.name();
                if (name.startsWith("V_"))
                    return name.substring(2).replace("_", ".");
                return name;
            }
        } catch (Throwable ignored) {
        }
        return "Unknown";
    }

    private static String getPingColor(int ping) {
        if (ping < 50)
            return GuiConstants.SUCCESS;
        if (ping < 100)
            return GuiConstants.SECONDARY;
        if (ping < 200)
            return GuiConstants.WARNING;
        return GuiConstants.ERROR;
    }

    private static String formatEnum(String name) {
        String[] parts = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1)
                    sb.append(part.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static String numToRoman(int num) {
        String[] romans = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return num > 0 && num < romans.length ? romans[num] : String.valueOf(num);
    }

    private static String formatTime(int seconds) {
        if (seconds < 60)
            return seconds + "s";
        int mins = seconds / 60;
        int secs = seconds % 60;
        return mins + "m " + secs + "s";
    }
}
