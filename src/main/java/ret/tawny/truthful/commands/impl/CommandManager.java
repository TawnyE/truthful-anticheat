package ret.tawny.truthful.commands.impl;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.debug.DebugManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    public static final Set<UUID> debuggers = ConcurrentHashMap.newKeySet();

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "menu", "gui", "exempt", "info", "debug", "logs", "history", "export", "reload", "record", "tools");

    private static final List<String> PLAYER_SUBCOMMANDS = Arrays.asList(
            "exempt", "info", "logs", "history", "record", "tools");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("truthful.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                Truthful.getInstance().reload();
                sender.sendMessage("§aTruthfulAC reloaded.");
                return true;

            case "debug":
                handleDebug(sender, args);
                return true;

            case "export":
                handleExport(sender);
                return true;

            case "logs":
            case "history":
                handleLogs(sender, args);
                return true;

            case "exempt":
                handleExempt(sender, args);
                return true;

            case "info":
                handleInfo(sender, args);
                return true;

            case "record":
                handleRecord(sender, args);
                return true;

            case "menu":
            case "gui":
                if (sender instanceof Player) {
                    Truthful.getInstance().getGuiManager().openMainMenu((Player) sender);
                } else {
                    sender.sendMessage("§cOnly players can use the GUI.");
                }
                return true;

            case "tools":
                CommandTools.handle(sender, args);
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m----------------------------------");
        sender.sendMessage("§bTruthfulAC §7Commands:");
        sender.sendMessage("§7/truthful reload §8- §fReload config");
        sender.sendMessage("§7/truthful debug <check> §8- §fToggle debug");
        sender.sendMessage("§7/truthful record <player> §8- §fStart/Stop debug logging");
        sender.sendMessage("§7/truthful export §8- §fExport debug logs folder path");
        sender.sendMessage("§7/truthful logs <player> [limit] §8- §fView recent logs");
        sender.sendMessage("§7/truthful exempt <player> §8- §fToggle exempt");
        sender.sendMessage("§7/truthful info <player> §8- §fShow live player info");
        sender.sendMessage("§7/truthful gui §8- §fOpen GUI");
        sender.sendMessage("§7/truthful tools <player> <tool> §8- §fRun testing tools (kb/rotation/sensitivity/timer/reach)");
        sender.sendMessage("§8§m----------------------------------");
    }

    private void handleRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can start debug logging.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /truthful record <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        Truthful.getInstance().getDebugLoggingManager().toggleLogging((Player) sender, target);
    }

    private void handleExempt(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /truthful exempt <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        if (data == null) {
            sender.sendMessage("§cNo data for player.");
            return;
        }

        data.setExempt(!data.isExempt());
        sender.sendMessage("§aExempt for " + target.getName() + ": §f" + data.isExempt());
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can view the info GUI.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /truthful info <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return;
        }

        PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(target);
        if (data == null) {
            sender.sendMessage("§cNo data for player.");
            return;
        }

        Truthful.getInstance().getGuiManager().openPlayerInfoGui((Player) sender, target);
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return;
        }
        Player debugger = (Player) sender;

        if (debuggers.contains(debugger.getUniqueId())) {
            debuggers.remove(debugger.getUniqueId());
            sender.sendMessage("§cDebug disabled.");
        } else {
            debuggers.add(debugger.getUniqueId());
            sender.sendMessage("§aDebug enabled.");
        }

        DebugManager dm = Truthful.getInstance().getDebugManager();
        if (args.length >= 2) {
            String checkName = args[1];
            dm.startDebuggingCheck(debugger, checkName);
            sender.sendMessage("§7Debugging check: §f" + checkName);
        } else {
            dm.stopDebuggingCheck(debugger);
        }
    }

    private void handleLogs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /truthful logs <player> [limit]");
            return;
        }

        if (sender instanceof Player) {
            Truthful.getInstance().getGuiManager().openLogs((Player) sender, args[1]);
        } else {
            sender.sendMessage("§cLogs only viewable via GUI currently.");
        }
    }

    private void handleExport(CommandSender sender) {
        File dataFolder = Truthful.getInstance().getPlugin().getDataFolder();
        File debugLogs = new File(dataFolder, "debug_logs");
        sender.sendMessage("§7Debug Logs folder: §f" + debugLogs.getAbsolutePath());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("truthful.admin")) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (PLAYER_SUBCOMMANDS.contains(sub)) {
                String partial = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        // Tools-specific tab completion for the 3rd argument
        if (args.length == 3 && args[0].equalsIgnoreCase("tools")) {
            return CommandTools.tabComplete(args);
        }

        return Collections.emptyList();
    }
}