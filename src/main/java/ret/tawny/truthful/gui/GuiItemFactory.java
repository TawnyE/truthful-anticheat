package ret.tawny.truthful.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise Item Factory
 * Creates premium GUI items with consistent styling and visual effects.
 */
public final class GuiItemFactory {

    // ═══════════════════════════════════════════════
    // CORE ITEM BUILDERS
    // ═══════════════════════════════════════════════

    /**
     * Create a standard GUI item with name and lore.
     */
    public static ItemStack create(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create a standard GUI item with List lore.
     */
    public static ItemStack create(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create an item with enchant glint (premium shimmer effect).
     */
    public static ItemStack createGlowing(Material mat, String name, String... lore) {
        ItemStack item = create(mat, name, lore);
        addGlow(item);
        return item;
    }

    /**
     * Create a glowing item with List lore.
     */
    public static ItemStack createGlowing(Material mat, String name, List<String> lore) {
        ItemStack item = create(mat, name, lore);
        addGlow(item);
        return item;
    }

    /**
     * Add enchant glow to an existing item.
     */
    public static void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                // Try modern API first
                Enchantment ench = Enchantment.getByName("UNBREAKING");
                if (ench == null)
                    ench = Enchantment.getByName("DURABILITY");
                if (ench != null) {
                    meta.addEnchant(ench, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            } catch (Throwable ignored) {
            }
            item.setItemMeta(meta);
        }
    }

    // ═══════════════════════════════════════════════
    // PLAYER HEADS
    // ═══════════════════════════════════════════════

    public static ItemStack createSkull(String owner, String name, String... lore) {
        ItemStack item = new ItemStack(GuiConstants.getMat("PLAYER_HEAD", "SKULL_ITEM"), 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwner(owner);
            meta.setDisplayName(name);
            if (lore.length > 0)
                meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createSkull(UUID uuid, String name, String... lore) {
        ItemStack item = new ItemStack(GuiConstants.getMat("PLAYER_HEAD", "SKULL_ITEM"), 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(offPlayer);
            meta.setDisplayName(name);
            if (lore.length > 0)
                meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createPlayerHead(Player player, String name, String... lore) {
        ItemStack item = new ItemStack(GuiConstants.getMat("PLAYER_HEAD", "SKULL_ITEM"), 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(name);
            if (lore.length > 0)
                meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createPlayerHead(Player player, String name, List<String> lore) {
        ItemStack item = new ItemStack(GuiConstants.getMat("PLAYER_HEAD", "SKULL_ITEM"), 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty())
                meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ═══════════════════════════════════════════════
    // GLASS PANES & BORDERS
    // ═══════════════════════════════════════════════

    /**
     * Create a glass pane filler.
     */
    public static ItemStack createPane(String... matNames) {
        Material mat = GuiConstants.getMat(matNames);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Premium gradient border with proper visual hierarchy.
     * Corners: Black | Edges: Gray | Center-top/bottom: Dark gray gradient
     */
    public static void fillGradientBorder(Inventory inv) {
        int size = inv.getSize();
        int rows = size / 9;

        ItemStack black = createPane("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        ItemStack darkGray = createPane("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        ItemStack lightGray = createPane("LIGHT_GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");

        // Top row: gradient from black corners to lighter center
        inv.setItem(0, black);
        inv.setItem(1, darkGray);
        inv.setItem(2, darkGray);
        inv.setItem(3, lightGray);
        inv.setItem(4, lightGray);
        inv.setItem(5, lightGray);
        inv.setItem(6, darkGray);
        inv.setItem(7, darkGray);
        inv.setItem(8, black);

        // Bottom row: same gradient
        int bot = size - 9;
        inv.setItem(bot, black);
        inv.setItem(bot + 1, darkGray);
        inv.setItem(bot + 2, darkGray);
        inv.setItem(bot + 3, lightGray);
        inv.setItem(bot + 4, lightGray);
        inv.setItem(bot + 5, lightGray);
        inv.setItem(bot + 6, darkGray);
        inv.setItem(bot + 7, darkGray);
        inv.setItem(bot + 8, black);

        // Side columns: fade from dark to lighter near center
        for (int row = 1; row < rows - 1; row++) {
            boolean nearCenter = row >= (rows / 2 - 1) && row <= (rows / 2 + 1);
            ItemStack side = nearCenter ? darkGray : black;
            inv.setItem(row * 9, side);
            inv.setItem(row * 9 + 8, side);
        }
    }

    /**
     * Red accent border for emphasis menus.
     */
    public static void fillAccentBorder(Inventory inv) {
        int size = inv.getSize();
        int rows = size / 9;

        ItemStack black = createPane("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        ItemStack red = createPane("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");

        // Top: red accent strip
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, (i >= 3 && i <= 5) ? red : black);
        }
        // Bottom: red accent strip
        int bot = size - 9;
        for (int i = 0; i < 9; i++) {
            inv.setItem(bot + i, (i >= 3 && i <= 5) ? red : black);
        }
        // Sides
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, black);
            inv.setItem(row * 9 + 8, black);
        }
    }

    /**
     * Fill a specific row with a material.
     */
    public static void fillRow(Inventory inv, int startSlot, Material mat) {
        for (int i = startSlot; i < startSlot + 9 && i < inv.getSize(); i++) {
            inv.setItem(i, createPane(mat.name()));
        }
    }

    /**
     * Fill remaining empty content slots with dark glass.
     */
    public static void fillEmpty(Inventory inv, int[] contentSlots, int usedCount) {
        ItemStack filler = createPane("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        for (int i = usedCount; i < contentSlots.length; i++) {
            inv.setItem(contentSlots[i], filler);
        }
    }

    // ═══════════════════════════════════════════════
    // NAVIGATION & ACTION BUTTONS
    // ═══════════════════════════════════════════════

    /**
     * Premium back button with consistent styling.
     */
    public static ItemStack createBackButton(String destination) {
        return create(GuiConstants.getMat("ARROW"),
                GuiConstants.ERROR + GuiConstants.SYM_ARROW + " Back",
                GuiConstants.DARK + "Return to " + destination);
    }

    /**
     * Toggle All button (emerald block).
     */
    public static ItemStack createToggleAll(boolean allEnabled, String context) {
        Material mat = allEnabled
                ? GuiConstants.getMat("EMERALD_BLOCK")
                : GuiConstants.getMat("REDSTONE_BLOCK");

        String status = allEnabled
                ? GuiConstants.SUCCESS + GuiConstants.SYM_CHECK + " All Enabled"
                : GuiConstants.ERROR + GuiConstants.SYM_CROSS + " Not All Enabled";

        String action = allEnabled
                ? GuiConstants.MUTED + "Click to " + GuiConstants.ERROR + "disable all"
                : GuiConstants.MUTED + "Click to " + GuiConstants.SUCCESS + "enable all";

        return createGlowing(mat,
                GuiConstants.SECONDARY + GuiConstants.BOLD + "Toggle All",
                GuiConstants.DARK + context,
                "",
                status,
                "",
                action);
    }

    /**
     * Create an info-only item (non-interactive display).
     */
    public static ItemStack createInfoItem(Material mat, String name, List<String> lore) {
        return createGlowing(mat, name, lore);
    }

    private GuiItemFactory() {
    }
}
