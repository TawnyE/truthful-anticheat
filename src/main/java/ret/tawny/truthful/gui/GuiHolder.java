package ret.tawny.truthful.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ret.tawny.truthful.checks.api.data.CheckType;

public final class GuiHolder implements InventoryHolder {

    public enum MenuType {
        MAIN, CATEGORIES, CHECK_TYPES, CHECK_CONFIG, CHECK_DETAILS,
        PLAYER_SELECT, PLAYER_INFO, LOGS, PLUGIN_INFO
    }

    private final MenuType menuType;
    private final String targetName;
    private final CheckType checkType;
    private final String category;
    private final int page;

    public GuiHolder(MenuType menuType, String targetName, CheckType checkType, String category, int page) {
        this.menuType = menuType;
        this.targetName = targetName;
        this.checkType = checkType;
        this.category = category;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public MenuType getMenuType() { return menuType; }
    public String getTargetName() { return targetName; }
    public CheckType getCheckType() { return checkType; }
    public String getCategory() { return category; }
    public int getPage() { return page; }
}