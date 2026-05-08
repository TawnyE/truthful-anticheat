package ret.tawny.truthful.commands.impl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.config.api.Configuration;
import ret.tawny.truthful.data.DataManager;
import ret.tawny.truthful.data.PlayerData;

import java.util.Set;
import java.util.UUID;

public class CommandAlerts implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Configuration config = Truthful.getInstance().getConfiguration();

        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayersMessage());
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("truthful.alerts")) {
            player.sendMessage(config.getNoPermissionMessage());
            return true;
        }

        DataManager dataManager = Truthful.getInstance().getDataManager();
        PlayerData data = dataManager.getPlayerData(player);
        if (data == null) {
            player.sendMessage(config.getNoDataMessage());
            return true;
        }

        UUID uuid = player.getUniqueId();
        Set<UUID> subscribers = dataManager.getAlertSubscribers();
        boolean isSubscribed = subscribers.contains(uuid);

        // Toggle the state
        if (isSubscribed) {
            subscribers.remove(uuid);
            data.setAlertsEnabled(false);
            player.sendMessage(config.getAlertsDisabledMessage());
        } else {
            subscribers.add(uuid);
            data.setAlertsEnabled(true);
            player.sendMessage(config.getAlertsEnabledMessage());
        }

        return true;
    }
}