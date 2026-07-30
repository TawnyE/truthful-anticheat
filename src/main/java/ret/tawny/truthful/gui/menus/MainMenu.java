package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiHolder;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.List;

public final class MainMenu {

    public static String getTitle() {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + "Dashboard";
    }

    public static void open(Player player) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();

        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.MAIN, null, null, "Dashboard", 0);
        Inventory inv = Bukkit.createInventory(holder, 45, getTitle());
        GuiItemFactory.fillGradientBorder(inv);

        int totalChecks = 0;
        int enabledChecks = 0;
        for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
            totalChecks++;
            if (check.isEnabled()) enabledChecks++;
        }

        double tps = Truthful.getInstance().getTps();
        String tpsColor = tps >= 19.0D ? GuiConstants.SUCCESS : tps >= 17.5D ? GuiConstants.WARNING : GuiConstants.ERROR;
        String version = Truthful.getInstance().getPlugin().getDescription().getVersion();

        List<String> hubLore = new ArrayList<>();
        hubLore.add(GuiConstants.DARK + "Anti-cheat control panel");
        hubLore.add("");
        hubLore.add(GuiConstants.metric("Version", version));
        hubLore.add(GuiConstants.metric("Checks", enabledChecks + "/" + totalChecks));
        hubLore.add(GuiConstants.DARK + GuiConstants.LINE + " " + GuiConstants.MUTED + "TPS " + tpsColor + String.format("%.1f", tps));
        hubLore.add(GuiConstants.metric("Players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()));
        hubLore.add("");
        hubLore.add(GuiConstants.buildProgressBar(enabledChecks, totalChecks, 14));

        inv.setItem(4, GuiItemFactory.createGlowing(
                GuiConstants.getMat("NETHER_STAR"),
                GuiConstants.PRIMARY + GuiConstants.BOLD + pluginName,
                hubLore));

        inv.setItem(20, GuiItemFactory.createGlowing(
                GuiConstants.getMat("COMPARATOR", "REDSTONE_COMPARATOR"),
                GuiConstants.SECONDARY + GuiConstants.BOLD + "Check Manager",
                List.of(
                        GuiConstants.DARK + "Enable, disable, and punish per check",
                        "",
                        GuiConstants.metric("Enabled", enabledChecks + "/" + totalChecks),
                        "",
                        GuiConstants.SECONDARY + GuiConstants.ARROW + " Click to manage")));

        inv.setItem(22, GuiItemFactory.createGlowing(
                GuiConstants.getMat("ENDER_EYE", "EYE_OF_ENDER"),
                GuiConstants.ACCENT + GuiConstants.BOLD + "Live Inspector",
                List.of(
                        GuiConstants.DARK + "Fast player snapshot & KeyPress tracker",
                        "",
                        GuiConstants.metric("View", "Keys, Sensitivity, DPI, Network"),
                        GuiConstants.metric("Updates", "Live 100ms"),
                        "",
                        GuiConstants.ACCENT + GuiConstants.ARROW + " Select a player")));

        inv.setItem(24, GuiItemFactory.createGlowing(
                GuiConstants.getMat("WRITABLE_BOOK", "BOOK_AND_QUILL"),
                GuiConstants.WARNING + GuiConstants.BOLD + "Detection Logs",
                List.of(
                        GuiConstants.DARK + "Recent flags by player",
                        "",
                        GuiConstants.metric("Format", "Check, VL, ping, time"),
                        "",
                        GuiConstants.WARNING + GuiConstants.ARROW + " Select a player")));

        inv.setItem(40, GuiItemFactory.create(
                GuiConstants.getMat("BOOK"),
                GuiConstants.MUTED + "Credits",
                GuiConstants.DARK + "Contributor book",
                "",
                GuiConstants.SECONDARY + GuiConstants.ARROW + " Click to view"));

        player.openInventory(inv);
    }

    private MainMenu() {}
}