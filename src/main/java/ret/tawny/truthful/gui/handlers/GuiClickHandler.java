package ret.tawny.truthful.gui.handlers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.menus.*;

/**
 * Premium GUI Click Handler
 * Routes all inventory clicks with sound feedback.
 */
public final class GuiClickHandler implements Listener {

    public GuiClickHandler() {
        Bukkit.getPluginManager().registerEvents(this, Truthful.getInstance().getPlugin());
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        String pluginName = Truthful.getInstance().getConfiguration().getPluginDisplayName();

        // Only handle our GUIs
        if (!title.startsWith(GuiConstants.PRIMARY + pluginName))
            return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta())
            return;

        Player player = (Player) e.getWhoClicked();
        ItemStack item = e.getCurrentItem();
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        Material mat = item.getType();
        String matName = mat.name();

        // Ignore glass pane fillers (but NOT stained glass toggles in check config)
        if (matName.contains("GLASS_PANE") && (name == null || name.trim().isEmpty()))
            return;

        // Handle back buttons
        if (matName.contains("ARROW") && name.contains("Back")) {
            playSound(player, Sound.UI_BUTTON_CLICK);
            handleBack(player, title);
            return;
        }

        // Handle pagination buttons
        if (matName.contains("ARROW") && (name.contains("Next Page") || name.contains("Previous Page"))) {
            playSound(player, Sound.UI_BUTTON_CLICK);
            handlePagination(player, title, name);
            return;
        }

        // ═════════ ROUTE BY MENU TYPE ═════════

        if (title.endsWith("Dashboard")) {
            playSound(player, Sound.UI_BUTTON_CLICK);
            handleDashboard(player, matName, name);
        } else if (title.endsWith("About") || title.endsWith("Credits")) {
            handleAbout(player, matName, name);
        } else if (title.endsWith("Categories")) {
            handleCategories(player, matName, name);
        } else if (title.contains("Select (Logs)")) {
            if (matName.contains("HEAD") || matName.contains("SKULL")) {
                playSound(player, Sound.UI_BUTTON_CLICK);
                LogsMenu.open(player, name);
            }
        } else if (title.contains("Select (Info)")) {
            if (matName.contains("HEAD") || matName.contains("SKULL")) {
                playSound(player, Sound.UI_BUTTON_CLICK);
                Player target = Bukkit.getPlayer(name);
                if (target != null) {
                    PlayerInfoMenu.open(player, target);
                } else {
                    player.sendMessage(GuiConstants.ERROR + "Player offline.");
                }
            }
        } else if (isCheckTypeMenu(title)) {
            // Click on check type -> open individual checks
            CheckType type = getCheckTypeByName(name);
            if (type != null) {
                playSound(player, Sound.UI_BUTTON_CLICK);
                CheckConfigMenu.open(player, type);
            }
        } else if (isCheckConfigMenu(title)) {
            handleCheckConfig(player, title, item, matName, name);
        } else if (isCheckDetailsMenu(title)) {
            handleCheckDetails(player, title, item, matName, name);
        } else if (title.contains("Logs:")) {
            // Log entries - no action
        } else if (title.contains("Info: ")) {
            // Live inspector - no action
        }
    }

    // ═══════════════════════════════════════════════
    // DASHBOARD
    // ═══════════════════════════════════════════════

    private void handleDashboard(Player player, String matName, String name) {
        if (matName.contains("COMPARATOR")) {
            CategoryMenu.open(player);
        } else if (matName.contains("ENDER_EYE") || matName.contains("EYE_OF_ENDER") || name.contains("Inspector")) {
            PlayerSelectMenu.open(player, "Info");
        } else if ((matName.contains("BOOK") && matName.contains("WRITABLE")) || name.contains("Logs")) {
            PlayerSelectMenu.open(player, "Logs");
        } else if (matName.contains("BOOK") && (name.contains("About") || name.contains("Credits"))) {
            PluginInfoMenu.open(player);
        }
    }

    // ═══════════════════════════════════════════════
    // ABOUT
    // ═══════════════════════════════════════════════

    private void handleAbout(Player player, String matName, String name) {
        if ((matName.contains("KNOWLEDGE") || matName.contains("BOOK")) && name.contains("Discord")) {
            PluginInfoMenu.sendDiscordLink(player);
        }
    }

    // ═══════════════════════════════════════════════
    // CATEGORIES
    // ═══════════════════════════════════════════════

    private void handleCategories(Player player, String matName, String name) {
        // Toggle All button
        if (matName.contains("EMERALD") || matName.contains("REDSTONE_BLOCK")) {
            if (name.contains("Toggle All")) {
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                CategoryMenu.toggleAllChecks(player);
                CategoryMenu.open(player);
                return;
            }
        }

        playSound(player, Sound.UI_BUTTON_CLICK);
        if (matName.contains("FEATHER")) {
            CheckTypeMenu.open(player, "Movement");
        } else if (matName.contains("SWORD")) {
            CheckTypeMenu.open(player, "Combat");
        } else if (matName.contains("GRASS")) {
            CheckTypeMenu.open(player, "World");
        } else if (matName.contains("REPEATER") || matName.contains("DIODE")) {
            CheckTypeMenu.open(player, "Packet");
        } else if (matName.contains("COMPASS")) {
            CheckTypeMenu.open(player, "Bot");
        } else if (matName.contains("BEDROCK")) {
            CheckTypeMenu.open(player, "Bedrock");
        }
    }

    // ═══════════════════════════════════════════════
    // CHECK CONFIG
    // ═══════════════════════════════════════════════

    private void handleCheckConfig(Player player, String title, ItemStack item, String matName, String name) {
        // Toggle All button
        if (matName.contains("EMERALD") || matName.contains("REDSTONE_BLOCK")) {
            if (name.contains("Toggle All")) {
                CheckType type = getCheckTypeFromTitle(title);
                if (type != null) {
                    playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                    CheckConfigMenu.toggleAllForType(player, type);
                    CheckConfigMenu.open(player, type);
                }
                return;
            }
        }

        if (name.contains("Ground Punches")) {
            CheckType type = getCheckTypeFromTitle(title);
            if (type == CheckType.AUTOCLICKER) {
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                CheckConfigMenu.toggleGroundPunches(player);
                CheckConfigMenu.open(player, type);
            }
            return;
        }

        // Individual check clicked -> open CheckDetailsMenu
        if (matName.contains("GLASS_PANE") || matName.contains("DYE") || matName.contains("INK_SACK")) {
            Check check = null;
            for (Check c : Truthful.getInstance().getCheckManager().getCollection()) {
                if (ChatColor.stripColor(c.getFormattedName()).equalsIgnoreCase(name)) {
                    check = c;
                    break;
                }
            }
            if (check != null) {
                playSound(player, Sound.UI_BUTTON_CLICK);
                new CheckDetailsMenu(check).open(player);
            }
        }
    }

    // ═══════════════════════════════════════════════
    // CHECK DETAILS SUB-MENU
    // ═══════════════════════════════════════════════

    private void handleCheckDetails(Player player, String title, ItemStack item, String matName, String name) {
        Check check = CheckDetailsMenu.getCheckFromTitle(title);
        if (check == null) return;

        Configuration config = Truthful.getInstance().getConfiguration();
        String typeName = check.getType().name();
        String orderStr = String.valueOf(check.getOrder());

        if (name.contains("Check Status")) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
            boolean newState = !check.isEnabled();
            check.setEnabled(newState);
            config.setCheckEnabled(typeName, orderStr, newState);
            
            String statusMsg = newState
                    ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                    : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";
            player.sendMessage(GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " +
                    GuiConstants.MUTED + check.getFormattedName() + " " + statusMsg);
            
            // Refresh
            new CheckDetailsMenu(check).open(player);
            
        } else if (name.contains("Lagback Status")) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
            boolean newState = !config.isCheckLagbackEnabled(typeName, orderStr);
            config.setCheckLagbackEnabled(typeName, orderStr, newState);
            
            String statusMsg = newState
                    ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                    : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";
            player.sendMessage(GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " +
                    GuiConstants.MUTED + check.getFormattedName() + " lagback " + statusMsg);
            
            // Refresh
            new CheckDetailsMenu(check).open(player);
            
        } else if (name.contains("Punishment Status")) {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
            boolean newState = !config.isPunishmentEnabled(typeName, orderStr);
            config.setPunishmentEnabled(typeName, orderStr, newState);
            
            String statusMsg = newState
                    ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " Enabled"
                    : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Disabled";
            player.sendMessage(GuiConstants.PRIMARY + "Truthful " + GuiConstants.DARK + GuiConstants.SYM_ARROW + " " +
                    GuiConstants.MUTED + check.getFormattedName() + " punishment " + statusMsg);
            
            // Refresh
            new CheckDetailsMenu(check).open(player);
        }
    }

    // ═══════════════════════════════════════════════
    // BACK NAVIGATION
    // ═══════════════════════════════════════════════

    private void handlePagination(Player player, String title, String itemName) {
        int currentPage = 0;
        try {
            // Title format: ... (Page X)
            String pageStr = title.substring(title.lastIndexOf("Page ") + 5, title.lastIndexOf(")"));
            currentPage = Integer.parseInt(pageStr) - 1;
        } catch (Exception ignored) {}

        int nextPage = itemName.contains("Next") ? currentPage + 1 : currentPage - 1;

        if (isCheckConfigMenu(title)) {
            String baseTitle = title.contains(" (Page ") ? title.substring(0, title.lastIndexOf(" (Page ")) : title;
            CheckType type = getCheckTypeFromTitle(baseTitle);
            if (type != null) {
                CheckConfigMenu.open(player, type, nextPage);
            }
        } else if (title.contains("Select (Logs)")) {
            PlayerSelectMenu.open(player, "Logs", nextPage);
        } else if (title.contains("Select (Info)")) {
            PlayerSelectMenu.open(player, "Info", nextPage);
        }
    }

    private void handleBack(Player player, String title) {
        if (title.endsWith("About") || title.endsWith("Credits") || title.endsWith("Categories") ||
                title.contains("Select (") || title.contains("Info: ") || title.contains("Logs: ")) {
            MainMenu.open(player);
        } else if (isCheckTypeMenu(title)) {
            CategoryMenu.open(player);
        } else if (isCheckConfigMenu(title)) {
            CheckType type = getCheckTypeFromTitle(title);
            if (type != null) {
                String category = CategoryMenu.getCategoryForType(type);
                if (category != null) {
                    CheckTypeMenu.open(player, category);
                } else {
                    CategoryMenu.open(player);
                }
            } else {
                CategoryMenu.open(player);
            }
        } else if (isCheckDetailsMenu(title)) {
            Check check = CheckDetailsMenu.getCheckFromTitle(title);
            if (check != null) {
                CheckConfigMenu.open(player, check.getType());
            } else {
                CategoryMenu.open(player);
            }
        } else {
            MainMenu.open(player);
        }
    }

    // ═══════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════

    private void playSound(Player player, Sound sound) {
        try {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (Throwable ignored) {
        }
    }

    private boolean isCheckTypeMenu(String title) {
        String[] categories = { "Movement", "Combat", "World", "Packet", "Bot", "Bedrock" };
        for (String cat : categories) {
            if (title.endsWith(cat))
                return true;
        }
        return false;
    }

    private boolean isCheckConfigMenu(String title) {
        String baseTitle = title;
        if (title.contains(" (Page ")) {
            baseTitle = title.substring(0, title.lastIndexOf(" (Page "));
        }
        for (CheckType type : CheckType.values()) {
            if (baseTitle.endsWith(type.getName()))
                return true;
        }
        return false;
    }

    private boolean isCheckDetailsMenu(String title) {
        return CheckDetailsMenu.getCheckFromTitle(title) != null;
    }

    private CheckType getCheckTypeFromTitle(String title) {
        String baseTitle = title;
        if (title.contains(" (Page ")) {
            baseTitle = title.substring(0, title.lastIndexOf(" (Page "));
        }
        for (CheckType type : CheckType.values()) {
            if (baseTitle.endsWith(type.getName()))
                return type;
        }
        return null;
    }

    private CheckType getCheckTypeByName(String name) {
        for (CheckType type : CheckType.values()) {
            if (type.getName().equalsIgnoreCase(name))
                return type;
        }
        return null;
    }
}
