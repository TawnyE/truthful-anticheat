package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.List;

public final class CheckDetailsMenu {

    private final Check check;

    public CheckDetailsMenu(Check check) {
        this.check = check;
    }

    public static String getTitle(Check check) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + check.getFormattedName();
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, getTitle(this.check));
        GuiItemFactory.fillGradientBorder(inv);

        Configuration config = Truthful.getInstance().getConfiguration();
        boolean checkEnabled = this.check.isEnabled();
        boolean lagbackEnabled = config.isCheckLagbackEnabled(this.check.getType().name(), String.valueOf(this.check.getOrder()));
        boolean punishmentEnabled = config.isPunishmentEnabled(this.check.getType().name(), String.valueOf(this.check.getOrder()));

        // 1. Check Status Button (Green Wool / Red Wool)
        Material woolMat = checkEnabled
                ? GuiConstants.getMat("GREEN_WOOL", "WOOL")
                : GuiConstants.getMat("RED_WOOL", "WOOL");
        String checkStatusStr = checkEnabled
                ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";
        
        List<String> checkLore = new ArrayList<>();
        checkLore.add("");
        checkLore.add(GuiConstants.metric("Status", checkStatusStr));
        checkLore.add("");
        checkLore.add(GuiConstants.SECONDARY + "Click to toggle");

        ItemStack checkItem = GuiItemFactory.create(woolMat,
                (checkEnabled ? GuiConstants.SUCCESS : GuiConstants.ERROR) + GuiConstants.BOLD + "Check Status", checkLore);
        if (woolMat.name().equals("WOOL")) {
            checkItem.setDurability((short) (checkEnabled ? 5 : 14));
        }

        // 2. Lagback Status Button (Slime Block / Magma Block)
        Material lagbackMat = lagbackEnabled
                ? GuiConstants.getMat("SLIME_BLOCK")
                : GuiConstants.getMat("MAGMA_BLOCK");
        String lagbackStatusStr = lagbackEnabled
                ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " On"
                : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Off";

        List<String> lagbackLore = new ArrayList<>();
        lagbackLore.add("");
        lagbackLore.add(GuiConstants.metric("Status", lagbackStatusStr));
        lagbackLore.add("");
        lagbackLore.add(GuiConstants.SECONDARY + "Click to toggle");

        ItemStack lagbackItem = GuiItemFactory.create(lagbackMat,
                (lagbackEnabled ? GuiConstants.SUCCESS : GuiConstants.ERROR) + GuiConstants.BOLD + "Lagback Status", lagbackLore);

        // 3. Punishment Status Button (Diamond Sword / Wooden Sword)
        Material punishmentMat = punishmentEnabled
                ? GuiConstants.getMat("DIAMOND_SWORD")
                : GuiConstants.getMat("WOODEN_SWORD", "WOOD_SWORD");
        String punishmentStatusStr = punishmentEnabled
                ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " On"
                : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Off";

        List<String> punishmentLore = new ArrayList<>();
        punishmentLore.add("");
        punishmentLore.add(GuiConstants.metric("Status", punishmentStatusStr));
        punishmentLore.add("");
        punishmentLore.add(GuiConstants.SECONDARY + "Click to toggle");

        ItemStack punishmentItem = GuiItemFactory.create(punishmentMat,
                (punishmentEnabled ? GuiConstants.SUCCESS : GuiConstants.ERROR) + GuiConstants.BOLD + "Punishment Status", punishmentLore);

        // 4. Back Button (Arrow)
        ItemStack backItem = GuiItemFactory.createBackButton(this.check.getType().getName() + " checks");

        // Set items in inventory
        inv.setItem(10, checkItem);
        inv.setItem(12, lagbackItem);
        inv.setItem(14, punishmentItem);
        inv.setItem(16, backItem);

        // Fill other center slots with black panes
        ItemStack filler = GuiItemFactory.createPane("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        inv.setItem(11, filler);
        inv.setItem(13, filler);
        inv.setItem(15, filler);

        player.openInventory(inv);
    }

    public static Check getCheckFromTitle(String title) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        String prefix = GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED;
        
        String strippedTitle = ChatColor.stripColor(title);
        String strippedPrefix = ChatColor.stripColor(prefix);
        
        if (strippedTitle.startsWith(strippedPrefix)) {
            String checkName = strippedTitle.substring(strippedPrefix.length()).trim();
            for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
                if (ChatColor.stripColor(check.getFormattedName()).equalsIgnoreCase(checkName)) {
                    return check;
                }
            }
        }
        return null;
    }
}
