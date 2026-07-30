package ret.tawny.truthful.gui.menus;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.gui.GuiConstants;
import ret.tawny.truthful.gui.GuiHolder;
import ret.tawny.truthful.gui.GuiItemFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PluginInfoMenu {

    private static final List<Contributor> CONTRIBUTORS = List.of(
            new Contributor(
                    UUID.fromString("e8c0b212-974f-4df5-87e8-d418e2cf84b9"),
                    "CodeControl / Tawny",
                    "CodeControl / Tawny",
                    "Founder / Owner",
                    "Original maker")
    );

    public static String getTitle() {
        return GuiConstants.PRIMARY + Truthful.getInstance().getConfiguration().getPluginDisplayName()
                + " " + GuiConstants.DARK + "> " + GuiConstants.MUTED + "Credits";
    }

    public static void open(Player player) {
        GuiHolder holder = new GuiHolder(GuiHolder.MenuType.PLUGIN_INFO, null, null, "Credits", 0);
        Inventory inv = Bukkit.createInventory(holder, 45, getTitle());
        GuiItemFactory.fillGradientBorder(inv);

        String version = Truthful.getInstance().getPlugin().getDescription().getVersion();
        List<String> bookLore = new ArrayList<>();
        bookLore.add(GuiConstants.DARK + "Contributor book");
        bookLore.add("");
        bookLore.add(GuiConstants.metric("Version", version));
        bookLore.add(GuiConstants.metric("Core", "PacketEvents"));
        bookLore.add(GuiConstants.metric("License", "Open source ready"));

        inv.setItem(4, GuiItemFactory.createGlowing(GuiConstants.getMat("WRITABLE_BOOK", "BOOK_AND_QUILL"), GuiConstants.SECONDARY + GuiConstants.BOLD + "Credits Book", bookLore));

        int[] slots = { 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34 };
        int index = 0;
        for (Contributor contributor : CONTRIBUTORS) {
            if (index >= slots.length) break;
            List<String> lore = new ArrayList<>();
            lore.add(GuiConstants.DARK + contributor.role());
            lore.add("");
            lore.add(GuiConstants.metric("Credit", contributor.credit()));
            lore.add(GuiConstants.metric("Role", contributor.role()));
            lore.add(GuiConstants.metric("Work", contributor.note()));

            inv.setItem(slots[index++], GuiItemFactory.createSkull(contributor.uuid(), GuiConstants.ACCENT + GuiConstants.BOLD + contributor.displayName(), lore.toArray(new String[0])));
        }

        GuiItemFactory.fillEmpty(inv, slots, index);

        List<String> supportLore = new ArrayList<>();
        supportLore.add(GuiConstants.DARK + "Support and releases");
        supportLore.add("");
        supportLore.add(GuiConstants.SECONDARY + GuiConstants.ARROW + " Click for invite");
        inv.setItem(40, GuiItemFactory.createGlowing(GuiConstants.getMat("KNOWLEDGE_BOOK", "BOOK"), GuiConstants.SUCCESS + GuiConstants.BOLD + "Discord", supportLore));

        inv.setItem(36, GuiItemFactory.createBackButton("Dashboard"));
        player.openInventory(inv);
    }

    public static void sendDiscordLink(Player player) {
        player.closeInventory();
        player.sendMessage(GuiConstants.separator());
        player.sendMessage(GuiConstants.ACCENT + GuiConstants.BOLD + "Truthful Support");
        player.sendMessage(GuiConstants.MUTED + "https://discord.gg/AnQvddTZDg");
        player.sendMessage(GuiConstants.separator());
    }

    private record Contributor(UUID uuid, String displayName, String credit, String role, String note) {}

    private PluginInfoMenu() {}
}