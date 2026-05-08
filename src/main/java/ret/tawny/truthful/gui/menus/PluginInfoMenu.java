package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Premium Plugin Info Menu
 * Credits, system info, and support links.
 */
public final class PluginInfoMenu {

        private static final UUID OWNER_UUID = UUID.fromString("e8c0b212-974f-4df5-87e8-d418e2cf84b9");

        public static String getTitle() {
                String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
                return GuiConstants.PRIMARY + pluginName + " §8» §7About";
        }

        public static void open(Player player) {
                String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
                String version = Truthful.getInstance().getPlugin().getDescription().getVersion();

                Inventory inv = Bukkit.createInventory(null, 27, getTitle());
                GuiItemFactory.fillGradientBorder(inv);

                // Developer
                List<String> devLore = new ArrayList<>();
                devLore.add(GuiConstants.MUTED + "Lead Developer");
                devLore.add("");
                devLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Creator of " + GuiConstants.HIGHLIGHT + pluginName);

                inv.setItem(10, GuiItemFactory.createSkull("CodeControl",
                                GuiConstants.ERROR + GuiConstants.BOLD + "CodeControl",
                                devLore.toArray(new String[0])));

                // Owner
                Player ownerPlayer = Bukkit.getPlayer(OWNER_UUID);
                String onlineStatus = (ownerPlayer != null && ownerPlayer.isOnline())
                                ? GuiConstants.SUCCESS + GuiConstants.SYM_CIRCLE + " Online"
                                : GuiConstants.ERROR + GuiConstants.SYM_CIRCLE + " Offline";

                List<String> ownerLore = new ArrayList<>();
                ownerLore.add(GuiConstants.MUTED + "Plugin Owner");
                ownerLore.add("");
                ownerLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Status  " + onlineStatus);

                inv.setItem(12, GuiItemFactory.createSkull(OWNER_UUID,
                                GuiConstants.WARNING + GuiConstants.BOLD + "Tawny",
                                ownerLore.toArray(new String[0])));

                // System Info
                String javaVersion = System.getProperty("java.version", "Unknown");
                String serverSoftware = Bukkit.getName() + " " + Bukkit.getVersion();

                List<String> infoLore = new ArrayList<>();
                infoLore.add("");
                infoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Version  " + GuiConstants.HIGHLIGHT + version);
                infoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Core     " + GuiConstants.HIGHLIGHT + "PacketEvents");
                infoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Java     " + GuiConstants.HIGHLIGHT + javaVersion);
                infoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Server   " + GuiConstants.HIGHLIGHT + serverSoftware);
                infoLore.add("");
                infoLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " +
                                GuiConstants.MUTED + "Status   " + GuiConstants.SUCCESS + GuiConstants.SYM_CHECK
                                + " Stable");

                inv.setItem(14, GuiItemFactory.createGlowing(
                                GuiConstants.getMat("PAPER"),
                                GuiConstants.SECONDARY + GuiConstants.BOLD + "System Info", infoLore));

                // Discord
                List<String> discordLore = new ArrayList<>();
                discordLore.add(GuiConstants.MUTED + "Get support and updates");
                discordLore.add("");
                discordLore.add(GuiConstants.SECONDARY + GuiConstants.SYM_ARROW + " Click for invite");

                inv.setItem(16, GuiItemFactory.createGlowing(
                                GuiConstants.getMat("KNOWLEDGE_BOOK", "BOOK"),
                                GuiConstants.SUCCESS + GuiConstants.BOLD + "Discord", discordLore));

                // Back
                inv.setItem(22, GuiItemFactory.createBackButton("Dashboard"));

                player.openInventory(inv);
        }

        public static void sendDiscordLink(Player player) {
                player.closeInventory();
                player.sendMessage(
                                GuiConstants.DARK + GuiConstants.STRIKE + "                                        ");
                player.sendMessage("  " + GuiConstants.ACCENT + GuiConstants.BOLD + "Truthful Support");
                player.sendMessage("  " + GuiConstants.MUTED + "Join our Discord:");
                player.sendMessage("  " + GuiConstants.SECONDARY + "https://discord.gg/AnQvddTZDg");
                player.sendMessage(
                                GuiConstants.DARK + GuiConstants.STRIKE + "                                        ");
        }
}
