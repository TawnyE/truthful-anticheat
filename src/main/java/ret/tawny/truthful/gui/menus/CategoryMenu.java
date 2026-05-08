package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Premium Category Menu
 * Browse check categories with live stats and progress bars.
 */
public final class CategoryMenu {

    public static String getTitle() {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " §8» §7Categories";
    }

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle());
        GuiItemFactory.fillGradientBorder(inv);

        Map<String, int[]> catStats = getCategoryStats();

        // ── ROW 2: Movement / Combat / World ──
        inv.setItem(20, buildCategoryItem("Movement", catStats,
                GuiConstants.getMat("FEATHER"),
                GuiConstants.ACCENT, "Simulation, Velocity, Timer"));

        inv.setItem(22, buildCategoryItem("Combat", catStats,
                GuiConstants.getMat("IRON_SWORD"),
                GuiConstants.ERROR, "KillAura, Reach, Aim, Clicks"));

        inv.setItem(24, buildCategoryItem("World", catStats,
                GuiConstants.getMat("GRASS_BLOCK", "GRASS"),
                GuiConstants.SUCCESS, "Scaffold, FastBreak, Raycast"));

        // ── ROW 3: Packet / Bot / Bedrock ──
        inv.setItem(29, buildCategoryItem("Packet", catStats,
                GuiConstants.getMat("REPEATER", "DIODE"),
                GuiConstants.LIGHT_PURPLE, "BadPacket, Invalid, Crasher"));

        inv.setItem(31, buildCategoryItem("Bot", catStats,
                GuiConstants.getMat("COMPASS"),
                GuiConstants.WARNING, "Baritone, Automation"));

        inv.setItem(33, buildCategoryItem("Bedrock", catStats,
                GuiConstants.getMat("BEDROCK"),
                GuiConstants.BLUE, "Geyser/Floodgate profiles"));

        // ── BOTTOM: Toggle All + Back ──
        int totalEnabled = 0, totalChecks = 0;
        for (int[] s : catStats.values()) {
            totalEnabled += s[0];
            totalChecks += s[1];
        }
        inv.setItem(49, GuiItemFactory.createToggleAll(totalEnabled == totalChecks, "All categories"));
        inv.setItem(45, GuiItemFactory.createBackButton("Dashboard"));

        player.openInventory(inv);
    }

    private static ItemStack buildCategoryItem(String name, Map<String, int[]> stats,
            org.bukkit.Material icon, String color, String desc) {
        int[] s = stats.getOrDefault(name, new int[] { 0, 0 });
        int enabled = s[0], total = s[1];

        List<String> lore = new ArrayList<>();
        lore.add(GuiConstants.DARK + desc);
        lore.add("");
        lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                GuiConstants.MUTED + "Enabled  " +
                (enabled == total ? GuiConstants.SUCCESS : enabled > 0 ? GuiConstants.SECONDARY : GuiConstants.ERROR) +
                enabled + GuiConstants.DARK + "/" + GuiConstants.MUTED + total);
        lore.add("  " + GuiConstants.buildProgressBar(enabled, total, 10));
        lore.add("");
        lore.add(GuiConstants.SECONDARY + GuiConstants.SYM_ARROW + " Click to configure");

        return GuiItemFactory.createGlowing(icon,
                color + GuiConstants.BOLD + name, lore);
    }

    // ═══════════════════════════════════════════════
    // TOGGLE ALL
    // ═══════════════════════════════════════════════

    public static void toggleAllChecks(Player player) {
        boolean anyDisabled = false;
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (!check.isEnabled()) {
                anyDisabled = true;
                break;
            }
        }
        boolean newState = anyDisabled; // enable all if any disabled, disable all if all enabled

        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            check.setEnabled(newState);
            Truthful.getInstance().getConfiguration()
                    .setCheckEnabled(check.getType().name(), String.valueOf(check.getOrder()), newState);
        }

        String msg = newState ? "§aEnabled all checks" : "§cDisabled all checks";
        player.sendMessage(GuiConstants.PRIMARY + "Truthful §8» " + msg);
    }

    // ═══════════════════════════════════════════════
    // STATS & CLASSIFICATION
    // ═══════════════════════════════════════════════

    private static String getStatLine(int enabled, int total) {
        String color = enabled == total ? GuiConstants.SUCCESS
                : enabled > 0 ? GuiConstants.SECONDARY : GuiConstants.ERROR;
        return GuiConstants.DARK + "Enabled: " + color + enabled + "/" + total;
    }

    private static Map<String, int[]> getCategoryStats() {
        Map<String, int[]> stats = new HashMap<>();
        stats.put("Movement", new int[] { 0, 0 });
        stats.put("Combat", new int[] { 0, 0 });
        stats.put("World", new int[] { 0, 0 });
        stats.put("Packet", new int[] { 0, 0 });
        stats.put("Bot", new int[] { 0, 0 });
        stats.put("Bedrock", new int[] { 0, 0 });

        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            String cat = getCategoryForType(check.getType());
            if (cat != null && stats.containsKey(cat)) {
                int[] arr = stats.get(cat);
                arr[1]++;
                if (check.isEnabled())
                    arr[0]++;
            }
        }
        return stats;
    }

    public static String getCategoryForType(CheckType type) {
        switch (type) {
            case SIMULATION:
            case VELOCITY:
            case SPOOF:
            case PHASE:
            case TIMER:
                return "Movement";
            case KILLAURA:
            case AIM:
            case HITBOX:
            case REACH:
            case AUTOCLICKER:
            case CRYSTAL:
            case ANCHOR:
                return "Combat";
            case SCAFFOLD:
            case FAST_BREAK:
            case RAYCAST:
                return "World";
            case BAD_PACKET:
            case CRASHER:
            case INVALID:
            case SPRINT:
            case INVENTORY:
            case PACKET_ORDER:
                return "Packet";
            case BARITONE:
                return "Bot";
            case BEDROCK:
                return "Bedrock";
            default:
                return null;
        }
    }

    public static List<CheckType> getTypesByCategory(String category) {
        List<CheckType> types = new ArrayList<>();
        for (CheckType type : CheckType.values()) {
            if (category.equals(getCategoryForType(type))) {
                types.add(type);
            }
        }
        return types;
    }
}
