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
import java.util.Comparator;
import java.util.List;

/**
 * Premium Check Type Menu
 * Shows all check types within a category with progress indicators.
 */
public final class CheckTypeMenu {

    public static String getTitle(String category) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + category;
    }

    public static void open(Player player, String category) {
        Inventory inv = Bukkit.createInventory(null, 45, getTitle(category));
        GuiItemFactory.fillGradientBorder(inv);

        List<CheckType> types = CategoryMenu.getTypesByCategory(category);
        types.sort(Comparator.comparing(CheckType::getName));

        int[] slots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34 };
        int slotIndex = 0;

        for (CheckType type : types) {
            if (slotIndex >= slots.length)
                break;

            int[] stats = countChecksForType(type);
            int enabled = stats[0], total = stats[1];

            String description = GuiConstants.getTypeDescription(type);
            boolean allOn = enabled == total && total > 0;

            List<String> lore = new ArrayList<>();
            lore.add(GuiConstants.DARK + description);
            lore.add("");
            lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                    GuiConstants.MUTED + "Checks  " +
                    (allOn ? GuiConstants.SUCCESS : enabled > 0 ? GuiConstants.SECONDARY : GuiConstants.ERROR) +
                    enabled + GuiConstants.DARK + "/" + GuiConstants.MUTED + total);
            lore.add("  " + GuiConstants.buildProgressBar(enabled, total, 10));
            lore.add("");
            lore.add(GuiConstants.SECONDARY + GuiConstants.SYM_ARROW + " Click to configure");

            ItemStack item = allOn
                    ? GuiItemFactory.createGlowing(GuiConstants.getIcon(type),
                            GuiConstants.HIGHLIGHT + GuiConstants.BOLD + type.getName(), lore)
                    : GuiItemFactory.create(GuiConstants.getIcon(type),
                            GuiConstants.MUTED + GuiConstants.BOLD + type.getName(), lore);

            inv.setItem(slots[slotIndex++], item);
        }

        // Fill remaining slots
        GuiItemFactory.fillEmpty(inv, slots, slotIndex);

        inv.setItem(40, GuiItemFactory.createBackButton("Categories"));
        player.openInventory(inv);
    }

    private static int[] countChecksForType(CheckType type) {
        int enabled = 0, total = 0;
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (check.getType() == type) {
                total++;
                if (check.isEnabled())
                    enabled++;
            }
        }
        return new int[] { enabled, total };
    }

    public static CheckType getCheckTypeFromTitle(String title) {
        for (CheckType type : CheckType.values()) {
            if (title.contains(type.getName()))
                return type;
        }
        return null;
    }
}
