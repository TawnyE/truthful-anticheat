package ret.tawny.truthful.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.gui.handlers.GuiClickHandler;
import ret.tawny.truthful.gui.menus.*;
import ret.tawny.truthful.checks.api.data.CheckType;

/**
 * Premium GUI Manager
 * Slim coordinator with live-update task and modular menu delegation.
 */
public final class GuiManager {

    private final GuiClickHandler clickHandler;

    public GuiManager() {
        this.clickHandler = new GuiClickHandler();
        startUpdateTask();
    }

    // ═══════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════

    public void openMainMenu(Player player) {
        MainMenu.open(player);
    }

    public void openCategoryMenu(Player player) {
        CategoryMenu.open(player);
    }

    public void openCheckTypeMenu(Player player, String category) {
        CheckTypeMenu.open(player, category);
    }

    public void openIndividualChecksMenu(Player player, CheckType type) {
        CheckConfigMenu.open(player, type);
    }

    public void openPlayerSelectionMenu(Player admin, String type) {
        PlayerSelectMenu.open(admin, type);
    }

    public void openLogs(Player admin, String targetName) {
        LogsMenu.open(admin, targetName);
    }

    public void openPlayerInfoGui(Player admin, Player target) {
        PlayerInfoMenu.open(admin, target);
    }

    public void openPluginInfoMenu(Player player) {
        PluginInfoMenu.open(player);
    }

    // ═══════════════════════════════════════════════
    // LIVE UPDATE TASK
    // ═══════════════════════════════════════════════

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    Inventory top = admin.getOpenInventory().getTopInventory();
                    if (top == null)
                        continue;

                    String title = admin.getOpenInventory().getTitle();

                    // Update live inspector
                    if (title.contains("Info: ")) {
                        try {
                            String targetName = ChatColor.stripColor(title.split("Info: ")[1]);
                            Player target = Bukkit.getPlayer(targetName);

                            if (target != null && target.isOnline()) {
                                PlayerInfoMenu.update(top, target);
                            } else {
                                // Player disconnected - show red accent border
                                GuiItemFactory.fillRow(top, 0,
                                        GuiConstants.getMat("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }.runTaskTimer(Truthful.getInstance().getPlugin(), 20L, 20L);
    }
}