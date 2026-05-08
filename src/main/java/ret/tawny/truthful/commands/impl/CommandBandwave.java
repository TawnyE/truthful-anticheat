package ret.tawny.truthful.commands.impl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.managers.BandwaveManager;

import java.util.List;

public final class CommandBandwave implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Configuration config = Truthful.getInstance().getConfiguration();

        if (!sender.hasPermission("truthful.admin")) {
            sender.sendMessage(config.getNoPermissionMessage());
            return true;
        }

        if (!config.isBandwaveEnabled()) {
            sender.sendMessage(config.getBandwaveNotEnabledMessage());
            return true;
        }

        final BandwaveManager manager = Truthful.getInstance().getBandwaveManager();

        if (args.length == 0) {
            sender.sendMessage(config.getBandwaveUsageMessage());
            sender.sendMessage(config.getBandwaveScheduleMessage()
                    .replace("%enabled%", String.valueOf(config.isBandwaveAutoStartEnabled()))
                    .replace("%day%", config.getBandwaveAutoStartDay().name())
                    .replace("%time%", config.getBandwaveAutoStartTime().toString()));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(config.getBandwaveUsageMessage());
                    return true;
                }
                if (manager.addPlayer(args[1])) {
                    sender.sendMessage(config.getBandwaveQueuedMessage().replace("%player%", args[1]));
                } else {
                    sender.sendMessage(config.getBandwaveDuplicateMessage().replace("%player%", args[1]));
                }
                return true;

            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(config.getBandwaveUsageMessage());
                    return true;
                }
                if (manager.removePlayer(args[1])) {
                    sender.sendMessage(config.getBandwaveRemovedMessage().replace("%player%", args[1]));
                } else {
                    sender.sendMessage(config.getBandwaveDuplicateMessage().replace("%player%", args[1]));
                }
                return true;

            case "list":
                List<String> queued = manager.getQueuedPlayers();
                sender.sendMessage(config.getBandwaveListHeader().replace("%queued%", String.valueOf(queued.size())));
                for (int i = 0; i < queued.size(); i++) {
                    sender.sendMessage(config.getBandwaveListEntry()
                            .replace("%position%", String.valueOf(i + 1))
                            .replace("%player%", queued.get(i)));
                }
                return true;

            case "start":
                if (manager.getQueuedCount() == 0) {
                    sender.sendMessage(config.getBandwaveEmptyMessage());
                    return true;
                }
                if (manager.start()) sender.sendMessage(config.getBandwaveStartedMessage());
                else sender.sendMessage(config.getBandwaveStatusMessage()
                        .replace("%running%", "true")
                        .replace("%queued%", String.valueOf(manager.getQueuedCount())));
                return true;

            case "stop":
                if (manager.stop()) sender.sendMessage(config.getBandwaveStoppedMessage());
                else sender.sendMessage(config.getBandwaveStatusMessage()
                        .replace("%running%", "false")
                        .replace("%queued%", String.valueOf(manager.getQueuedCount())));
                return true;

            case "clear":
                manager.clearQueue();
                sender.sendMessage(config.getBandwaveClearedMessage());
                return true;

            case "status":
                sender.sendMessage(config.getBandwaveStatusMessage()
                        .replace("%running%", String.valueOf(manager.isRunning()))
                        .replace("%queued%", String.valueOf(manager.getQueuedCount())));
                sender.sendMessage(config.getBandwaveScheduleMessage()
                        .replace("%enabled%", String.valueOf(config.isBandwaveAutoStartEnabled()))
                        .replace("%day%", config.getBandwaveAutoStartDay().name())
                        .replace("%time%", config.getBandwaveAutoStartTime().toString()));
                return true;

            default:
                sender.sendMessage(config.getBandwaveUsageMessage());
                return true;
        }
    }
}
