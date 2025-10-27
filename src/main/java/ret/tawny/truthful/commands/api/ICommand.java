package ret.tawny.truthful.commands.api;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public interface ICommand {
    public boolean onCommand(final CommandSender sender, final Command command, final String head, final String[] args);
}
