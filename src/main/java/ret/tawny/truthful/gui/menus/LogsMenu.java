package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.database.LogManager;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiHolder;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class LogsMenu {

    public static String getTitle(String playerName) {
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();
        return GuiConstants.PRIMARY + pluginName + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + "Logs: " + playerName;
    }

    public static void open(Player admin, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage(GuiConstants.ERROR + "Player not found.");
            PlayerSelectMenu.open(admin, "Logs");
            return;
        }

        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.LOGS, targetName, null, "Logs", 0);
        Inventory inv = Bukkit.createInventory(holder, 54, getTitle(targetName));
        admin.openInventory(inv);

        final UUID targetUUID = target.getUniqueId();

        Truthful.getInstance().getServerScheduler().runAsync(() -> {
            final List<LogManager.LogEntry> logs = Truthful.getInstance().getLogManager().getLogs(targetUUID, 36);

            Truthful.getInstance().getServerScheduler().runRegion(admin, () -> {
                if (!admin.isOnline()) return;
                if (!(admin.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder h && targetName.equalsIgnoreCase(h.getTargetName()))) return;

                GuiItemFactory.fillGradientBorder(inv);

                PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);

                List<String> headerLore = new ArrayList<>();
                headerLore.add("");
                headerLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " + GuiConstants.MUTED + "Client  " + GuiConstants.HIGHLIGHT + (data != null ? data.getClientBrand() : "Unknown"));
                headerLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " + GuiConstants.MUTED + "Ping    " + GuiConstants.HIGHLIGHT + (data != null ? data.getPing() + "ms" : "N/A"));
                headerLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " + GuiConstants.MUTED + "VL      " + GuiConstants.HIGHLIGHT + (data != null ? String.valueOf(data.getVl()) : "N/A"));

                inv.setItem(4, GuiItemFactory.createPlayerHead(target, GuiConstants.SECONDARY + GuiConstants.BOLD + target.getName(), headerLore));

                int[] contentSlots = { 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };
                int slotIndex = 0;

                SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
                for (LogManager.LogEntry log : logs) {
                    if (slotIndex >= contentSlots.length) break;

                    CheckType type = getCheckTypeFromString(log.check);
                    Material icon = type != null ? GuiConstants.getIcon(type) : GuiConstants.getMat("PAPER");
                    String date = dateFormat.format(new Date(log.timestamp));

                    List<String> entryLore = new ArrayList<>();
                    entryLore.add(GuiConstants.DARK + log.data);
                    entryLore.add("");
                    entryLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " + GuiConstants.MUTED + "VL    " + GuiConstants.ERROR + log.vl);
                    entryLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " + GuiConstants.MUTED + "Ping  " + GuiConstants.HIGHLIGHT + log.ping + "ms");
                    entryLore.add(GuiConstants.DARK + GuiConstants.SYM_LINE + " " + GuiConstants.MUTED + "Time  " + GuiConstants.DARK + date);

                    inv.setItem(contentSlots[slotIndex++], GuiItemFactory.create(icon, GuiConstants.WARNING + log.check, entryLore));
                }

                if (slotIndex == 0) {
                    inv.setItem(22, GuiItemFactory.create(GuiConstants.getMat("BARRIER"), GuiConstants.MUTED + "No logs found"));
                }

                GuiItemFactory.fillEmpty(inv, contentSlots, slotIndex);
                inv.setItem(49, GuiItemFactory.createBackButton("Player Selection"));
            });
        });
    }

    private static CheckType getCheckTypeFromString(String checkName) {
        if (checkName.startsWith("B ")) return CheckType.BEDROCK;
        int parenIndex = checkName.indexOf('(');
        if (parenIndex > 0) {
            String typeName = checkName.substring(0, parenIndex).toUpperCase();
            for (CheckType type : CheckType.values()) {
                if (type.getName().equalsIgnoreCase(typeName) || type.name().equalsIgnoreCase(typeName)) {
                    return type;
                }
            }
        }
        return null;
    }
}