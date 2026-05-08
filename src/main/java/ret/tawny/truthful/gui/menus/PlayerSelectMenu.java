package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium Player Selection Menu
 * Select a player for logs or live inspection with threat indicators.
 */
public final class PlayerSelectMenu {

    public static String getTitle(String type) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " §8» §7Select (" + type + ")";
    }

    public static void open(Player admin, String type) {
        open(admin, type, 0);
    }

    public static void open(Player admin, String type, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(type) + " §8(Page " + (page + 1) + ")");
        GuiItemFactory.fillGradientBorder(inv);

        int[] contentSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int start = page * contentSlots.length;
        int end = Math.min(start + contentSlots.length, players.size());
        int slotIndex = 0;

        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
            if (data == null)
                continue;

            int vl = data.getVl();
            long ping = data.getPing();
            String pingColor = getPingColor((int) ping);
            String vlColor = getVLColor(vl);

            String action = type.equals("Logs")
                    ? GuiConstants.WARNING + GuiConstants.SYM_ARROW + " View Logs"
                    : GuiConstants.ACCENT + GuiConstants.SYM_ARROW + " Inspect Live";

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                    GuiConstants.MUTED + "Ping    " + pingColor + ping + "ms");
            lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                    GuiConstants.MUTED + "VL      " + vlColor + vl);
            lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                    GuiConstants.MUTED + "Client  " + GuiConstants.HIGHLIGHT + data.getClientBrand());
            lore.add("");
            lore.add(action);

            inv.setItem(contentSlots[slotIndex++],
                    GuiItemFactory.createPlayerHead(target,
                            GuiConstants.SECONDARY + target.getName(), lore));
        }

        // Empty state
        if (slotIndex == 0 && page == 0) {
            inv.setItem(22, GuiItemFactory.create(
                    GuiConstants.getMat("BARRIER"),
                    GuiConstants.MUTED + "No players online",
                    GuiConstants.DARK + "Waiting for connections..."));
        }

        // Fill remaining content slots
        GuiItemFactory.fillEmpty(inv, contentSlots, slotIndex);

        inv.setItem(49, GuiItemFactory.createBackButton("Dashboard"));

        if (page > 0) {
            inv.setItem(45, GuiItemFactory.create(Material.ARROW, "§ePrevious Page", "§7Go to page " + page));
        }
        if (end < players.size()) {
            inv.setItem(53, GuiItemFactory.create(Material.ARROW, "§eNext Page", "§7Go to page " + (page + 2)));
        }

        admin.openInventory(inv);
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

    private static String getVLColor(int vl) {
        if (vl == 0)
            return GuiConstants.SUCCESS;
        if (vl < 10)
            return GuiConstants.SECONDARY;
        if (vl < 50)
            return GuiConstants.WARNING;
        return GuiConstants.ERROR;
    }
}
