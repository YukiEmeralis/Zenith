package coffee.khyonieheart.eden.flock;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import coffee.khyonieheart.eden.command.EdenCommand;
import coffee.khyonieheart.eden.command.annotations.EdenCommandHandler;
import coffee.khyonieheart.eden.command.annotations.EdenCommandRedirect;
import coffee.khyonieheart.eden.flock.enums.JsonDownloadStatus;
import coffee.khyonieheart.eden.flock.gui.GlobalRepositoryGui;
import coffee.khyonieheart.eden.flock.repository.ModuleRepository;
import coffee.khyonieheart.eden.module.EdenModule;
import coffee.khyonieheart.eden.utils.PrintUtils;
import coffee.khyonieheart.eden.utils.option.Option;
import coffee.khyonieheart.eden.utils.result.Result;

/**
 * Flock command class
 */
public class FlockCommand extends EdenCommand 
{
    public FlockCommand(EdenModule parent_module) 
    {
        super("flock", parent_module);

        this.addBranch("^sync", "^forcesync", "^add", "^upgrade", "^open"); // TODO Add these
        this.getMultiBranch("^sync", "^forcesync", "^add", "^open").addBranch("<URL>");
        this.getBranch("^upgrade").addBranch("<ALL_MODULES>");
    }

    @EdenCommandHandler(usage = "flock", description = "Opens the module repository GUI.", argsCount = 0)
    public void edencommand_nosubcmd(CommandSender sender, String label, String[] args)
    {
        if (!(sender instanceof Player))
        {
            PrintUtils.sendMessage(sender, "§cOnly players may use this command.");
            return;
        }

        new GlobalRepositoryGui((Player) sender).display((Player) sender);
    }

    @EdenCommandHandler(usage = "flock sync <URL>", description = "Synchronizes an upstream repository to a local repository.", argsCount = 2)
    @EdenCommandRedirect(labels = {"add"}, command = "flock sync args")
    public void edencommand_sync(CommandSender sender, String label, String[] args)
    {
        ModuleRepository upstream;
        Result syncResult = DownloadUtils.downloadJson(args[1], ModuleRepository.class);

        if (syncResult.isErr())
        {
            PrintUtils.sendMessage(sender, "§cFailed to synchronize with upstream URL. Reason: " + syncResult.unwrapErr(JsonDownloadStatus.class));
            return;
        }

        upstream = syncResult.unwrapOk(ModuleRepository.class);
        upstream.updateReferences();
        
        ModuleRepository local;

        Option opt = Flock.getRepository(upstream.getName()); 
        if (opt.isNone())
        {
            Flock.addRepository(upstream);
            PrintUtils.sendMessage(sender, "§aSuccessfully synchronized with new upstream repository §e" + upstream.getName() + "§a!");
            return;
        }    
        
        local = opt.unwrap(ModuleRepository.class);

        // Check timestamps to see if we should attempt to synchronize
        if (upstream.getTimestamp() <= local.getTimestamp())
        {
            PrintUtils.sendMessage(sender, "Local repository \"" + args[1] + "\" is already up to date. To sync anyways, run §e\"/flock forcesync " + args[1] + "\"§7.");
            return;
        }

        local.sync(upstream);
        local.updateTimestamp(upstream.getTimestamp());
    }
}