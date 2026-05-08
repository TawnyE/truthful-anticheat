package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Premium Main Menu — Dashboard
 * Central hub with live system stats and navigation.
 */
public final class MainMenu {

        public static String getTitle() {
                String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
                return GuiConstants.PRIMARY + pluginName + " §8» §7Dashboard";
        }

        public static void open(Player player) {
                String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
                String version = Truthful.getInstance().getPlugin().getDescription().getVersion();

                Inventory inv = Bukkit.createInventory(null, 45, getTitle());
                GuiItemFactory.fillGradientBorder(inv);

                // ── LIVE STATS ──
                int totalChecks = 0;
                int enabledChecks = 0;
                for (Check check : Truthful.getInstance().getCheckManager().getCollection()) {
                        totalChecks++;
                        if (check.isEnabled())
                                enabledChecks++;
                }
                double tps = Truthful.getInstance().getTps();
                String tpsColor = tps >= 18 ? GuiConstants.SUCCESS
                                : tps >= 15 ? GuiConstants.SECONDARY : GuiConstants.ERROR;

                // ── BRANDING (slot 4) ──
                List<String> logoLore = new ArrayList<>();
                logoLore.add(GuiConstants.DARK + "Enterprise Anti-Cheat");
                logoLore.add("");
                logoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Version  " + GuiConstants.HIGHLIGHT + version);
                logoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Checks   " + GuiConstants.HIGHLIGHT + enabledChecks +
                                GuiConstants.DARK + "/" + GuiConstants.MUTED + totalChecks);
                logoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "TPS      " + tpsColor + String.format("%.1f", tps));
                logoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Online   " + GuiConstants.HIGHLIGHT +
                                Bukkit.getOnlinePlayers().size() + GuiConstants.DARK + "/" +
                                GuiConstants.MUTED + Bukkit.getMaxPlayers());
                logoLore.add("");
                logoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Status   " + GuiConstants.SUCCESS + GuiConstants.SYM_CIRCLE
                                + " Active");

                ItemStack logo = GuiItemFactory.createGlowing(
                                GuiConstants.getMat("NETHER_STAR"),
                                GuiConstants.PRIMARY + GuiConstants.BOLD + pluginName, logoLore);
                inv.setItem(4, logo);

                // ── CHECK MANAGER (slot 20) ──
                List<String> checksLore = new ArrayList<>();
                checksLore.add(GuiConstants.DARK + "Configure detection modules");
                checksLore.add("");
                checksLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Movement, Combat, World");
                checksLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Packet, Bot, Bedrock");
                checksLore.add("");
                checksLore.add("  " + GuiConstants.buildProgressBar(enabledChecks, totalChecks, 12));
                checksLore.add("");
                checksLore.add(GuiConstants.SECONDARY + GuiConstants.SYM_ARROW + " Click to manage");

                inv.setItem(20, GuiItemFactory.createGlowing(
                                GuiConstants.getMat("COMPARATOR", "REDSTONE_COMPARATOR"),
                                GuiConstants.SECONDARY + GuiConstants.BOLD + "Check Manager", checksLore));

                // ── LIVE INSPECTOR (slot 22) ──
                List<String> inspectorLore = new ArrayList<>();
                inspectorLore.add(GuiConstants.DARK + "Real-time player analysis");
                inspectorLore.add("");
                inspectorLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Position & Movement");
                inspectorLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Client & Network Info");
                inspectorLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Violation History");
                inspectorLore.add("");
                inspectorLore.add(GuiConstants.ACCENT + GuiConstants.SYM_ARROW + " Select a player");

                inv.setItem(22, GuiItemFactory.createGlowing(
                                GuiConstants.getMat("ENDER_EYE", "EYE_OF_ENDER"),
                                GuiConstants.ACCENT + GuiConstants.BOLD + "Live Inspector", inspectorLore));

                // ── DETECTION LOGS (slot 24) ──
                List<String> logsLore = new ArrayList<>();
                logsLore.add(GuiConstants.DARK + "Browse flag history");
                logsLore.add("");
                logsLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Per-player violations");
                logsLore.add(GuiConstants.DARK + GuiConstants.SYM_BULLET + " " +
                                GuiConstants.MUTED + "Check details & timing");
                logsLore.add("");
                logsLore.add(GuiConstants.WARNING + GuiConstants.SYM_ARROW + " Select a player");

                inv.setItem(24, GuiItemFactory.createGlowing(
                                GuiConstants.getMat("WRITABLE_BOOK", "BOOK_AND_QUILL"),
                                GuiConstants.WARNING + GuiConstants.BOLD + "Detection Logs", logsLore));

                // ── ABOUT & CREDITS (slot 40) ──
                inv.setItem(40, GuiItemFactory.create(
                                GuiConstants.getMat("BOOK"),
                                GuiConstants.MUTED + "About & Credits",
                                GuiConstants.DARK + "Developer information",
                                "",
                                GuiConstants.SECONDARY + GuiConstants.SYM_ARROW + " Click to view"));

                player.openInventory(inv);
        }
}
