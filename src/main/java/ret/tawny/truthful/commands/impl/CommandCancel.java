package ret.tawny.truthful.commands.impl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class CommandCancel implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String head, String[] args) {
        return false;
    }
}
