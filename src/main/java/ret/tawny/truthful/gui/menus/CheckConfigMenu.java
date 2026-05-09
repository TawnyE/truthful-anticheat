package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * Premium Check Config Menu
 * Toggle individual checks with clean visual status indicators.
 * No descriptions per user request - icons + status only.
 */
public final class CheckConfigMenu {

    public static String getTitle(CheckType type) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + type.getName();
    }

    public static void open(Player player, CheckType type) {
        open(player, type, 0);
    }

    public static void open(Player player, CheckType type, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, getTitle(type) + " " + GuiConstants.DARK + "(Page " + (page + 1) + ")");
        GuiItemFactory.fillGradientBorder(inv);

        List<Check> checks = new ArrayList<>();
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (check.getType() == type)
                checks.add(check);
        }
        checks.sort(Comparator.comparingInt(Check::getOrder));

        int[] slots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };

        int start = page * slots.length;
        int end = Math.min(start + slots.length, checks.size());
        int slotIndex = 0;

        for (int i = start; i < end; i++) {
            Check check = checks.get(i);
            boolean enabled = check.isEnabled();
            boolean punish = Truthful.getInstance().getConfiguration()
                    .isPunishmentEnabled(check.getType().name(), String.valueOf(check.getOrder()));

            // Use glass panes for maximum visual pop
            Material mat = enabled
                    ? GuiConstants.getMat("LIME_STAINED_GLASS_PANE", "STAINED_GLASS_PANE")
                    : GuiConstants.getMat("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");

            String checkStatus = enabled
                    ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                    : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";

            String punishStatus = punish
                    ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " On"
                    : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Off";

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                    GuiConstants.MUTED + "Check   " + checkStatus);
            lore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                    GuiConstants.MUTED + "Punish  " + punishStatus);
            lore.add("");
            lore.add(GuiConstants.SECONDARY + "Left-click " + GuiConstants.DARK + "toggle check");
            lore.add(GuiConstants.SECONDARY + "Right-click " + GuiConstants.DARK + "toggle punish");

            String nameColor = enabled ? GuiConstants.SUCCESS : GuiConstants.MUTED;
            ItemStack item = GuiItemFactory.create(mat,
                    nameColor + check.getFormattedName(), lore);

            // Legacy stained glass data values
            if (mat.name().equals("STAINED_GLASS_PANE")) {
                item.setDurability((short) (enabled ? 5 : 14));
            }

            inv.setItem(slots[slotIndex++], item);
        }

        // Fill remaining content slots
        GuiItemFactory.fillEmpty(inv, slots, slotIndex);

        // Toggle All + Back
        boolean allEnabled = true;
        for (Check c : checks) {
            if (!c.isEnabled()) {
                allEnabled = false;
                break;
            }
        }
        inv.setItem(49, GuiItemFactory.createToggleAll(allEnabled, type.getName() + " checks"));
        inv.setItem(45, GuiItemFactory.createBackButton(CategoryMenu.getCategoryForType(type)));

        if (type == CheckType.AUTOCLICKER) {
            boolean countGroundPunches = Truthful.getInstance().getConfiguration().shouldCountGroundPunches();
            Material punchMat = countGroundPunches
                    ? GuiConstants.getMat("ORANGE_DYE", "INK_SACK")
                    : GuiConstants.getMat("GRAY_DYE", "INK_SACK");
            inv.setItem(51, GuiItemFactory.create(punchMat,
                    (countGroundPunches ? GuiConstants.WARNING : GuiConstants.MUTED) + "Ground Punches",
                    GuiConstants.DARK + "Animation packets without an attack",
                    "",
                    GuiConstants.metric("Counted", countGroundPunches ? "Yes" : "No"),
                    "",
                    GuiConstants.SECONDARY + "Click to toggle"));
        }

        if (page > 0) {
            inv.setItem(45, GuiItemFactory.create(Material.ARROW, GuiConstants.SECONDARY + "Previous Page", GuiConstants.MUTED + "Go to page " + page));
        }
        if (end < checks.size()) {
            inv.setItem(53, GuiItemFactory.create(Material.ARROW, GuiConstants.SECONDARY + "Next Page", GuiConstants.MUTED + "Go to page " + (page + 2)));
        }

        player.openInventory(inv);
    }

    // ═══════════════════════════════════════════════
    // TOGGLE ACTIONS
    // ═══════════════════════════════════════════════

    public static void toggleCheck(Player player, String formattedName) {
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (check.getFormattedName().equals(formattedName)) {
                boolean newState = !check.isEnabled();
                check.setEnabled(newState);
                Truthful.getInstance().getConfiguration()
                        .setCheckEnabled(check.getType().name(), String.valueOf(check.getOrder()), newState);

                String msg = newState
                        ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                        : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";
                player.sendMessage(GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " +
                        GuiConstants.MUTED + formattedName + " " + msg);
                return;
            }
        }
    }

    public static void togglePunishment(Player player, String formattedName) {
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (check.getFormattedName().equals(formattedName)) {
                boolean newState = !Truthful.getInstance().getConfiguration()
                        .isPunishmentEnabled(check.getType().name(), String.valueOf(check.getOrder()));

                Truthful.getInstance().getConfiguration()
                        .setPunishmentEnabled(check.getType().name(), String.valueOf(check.getOrder()), newState);

                String msg = newState
                        ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                        : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";
                player.sendMessage(GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " +
                        GuiConstants.MUTED + formattedName + " punishment " + msg);
                return;
            }
        }
    }

    public static void toggleAllForType(Player player, CheckType type) {
        boolean anyDisabled = false;
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (check.getType() == type && !check.isEnabled()) {
                anyDisabled = true;
                break;
            }
        }
        boolean newState = anyDisabled;

        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            if (check.getType() == type) {
                check.setEnabled(newState);
                Truthful.getInstance().getConfiguration()
                        .setCheckEnabled(check.getType().name(), String.valueOf(check.getOrder()), newState);
            }
        }

        String msg = newState ? GuiConstants.SUCCESS + "Enabled all " : GuiConstants.ERROR + "Disabled all ";
        player.sendMessage(
                GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " + msg + type.getName() + " checks");
    }

    public static void toggleGroundPunches(Player player) {
        boolean newState = !Truthful.getInstance().getConfiguration().shouldCountGroundPunches();
        Truthful.getInstance().getConfiguration().setCountGroundPunches(newState);
        String msg = newState ? GuiConstants.WARNING + "Ground punches counted" : GuiConstants.SUCCESS + "Ground punches ignored";
        player.sendMessage(GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " + msg);
    }
}
