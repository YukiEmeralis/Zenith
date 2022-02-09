/*
Copyright 2021 Yuki_emeralis https://yukiemeralis.blogspot.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
	or "LICENSE.txt" at the root of this project folder.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.yukiemeralis.blogspot.eden.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.yukiemeralis.blogspot.eden.Eden;
import com.yukiemeralis.blogspot.eden.auth.PermissionGroup;
import com.yukiemeralis.blogspot.eden.auth.SecurityCore;
import com.yukiemeralis.blogspot.eden.auth.EdenPermissionManager;
import com.yukiemeralis.blogspot.eden.command.CommandManager;
import com.yukiemeralis.blogspot.eden.command.EdenCommand;
import com.yukiemeralis.blogspot.eden.command.annotations.HideFromEdenHelpall;
import com.yukiemeralis.blogspot.eden.core.CoreModule.DisableRequest;
import com.yukiemeralis.blogspot.eden.core.modgui.ModuleGui;
import com.yukiemeralis.blogspot.eden.core.modgui.ModuleTracker;
import com.yukiemeralis.blogspot.eden.module.EdenModule;
import com.yukiemeralis.blogspot.eden.module.EdenModule.EdenConfig;
import com.yukiemeralis.blogspot.eden.module.java.annotations.DefaultConfig;
import com.yukiemeralis.blogspot.eden.module.java.enums.CallerToken;
import com.yukiemeralis.blogspot.eden.module.java.enums.PreventUnload;
import com.yukiemeralis.blogspot.eden.permissions.PlayerData;
import com.yukiemeralis.blogspot.eden.utils.ChatUtils;
import com.yukiemeralis.blogspot.eden.utils.DataUtils;
import com.yukiemeralis.blogspot.eden.utils.FileUtils;
import com.yukiemeralis.blogspot.eden.utils.HashUtils;
import com.yukiemeralis.blogspot.eden.utils.JsonUtils;
import com.yukiemeralis.blogspot.eden.utils.PrintUtils;
import com.yukiemeralis.blogspot.eden.utils.Result;
import com.yukiemeralis.blogspot.eden.utils.ChatUtils.ChatAction;
import com.yukiemeralis.blogspot.eden.utils.Result.UndefinedResultException;

@PreventUnload(CallerToken.EDEN)
public class CoreCommand extends EdenCommand
{
    public CoreCommand(EdenModule mod) 
    {
        super("eden", mod);

        this.addBranch("^mods", "data", "^mm", "^logging", "helpall", "^perms", "sudo");

        this.getBranch("^perms").addBranch("specific", "group");
        this.getBranch("^perms").getBranch("specific").addBranch("<ALL_PLAYERS>").addBranch("add", "remove");
        this.getBranch("^perms").getBranch("specific").getBranch("<ALL_PLAYERS>").getBranch("add").addBranch("<PERMISSION>").addBranch("<BOOLEAN>");
        this.getBranch("^perms").getBranch("specific").getBranch("<ALL_PLAYERS>").getBranch("remove").addBranch("<PERMISSION>").addBranch("<BOOLEAN>");
        this.getBranch("^perms").getBranch("group").addBranch("<GROUP>").addBranch("add", "remove", "create", "delete", "assign", "unassign");
        this.getBranch("^perms").getBranch("group").getBranch("<GROUP>").getBranch("add").addBranch("<PERMISSION>");
        this.getBranch("^perms").getBranch("group").getBranch("<GROUP>").getBranch("remove").addBranch("<PERMISSION>");
        this.getBranch("^perms").getBranch("group").getBranch("<GROUP>").getBranch("create").addBranch("<BASE>");
        this.getBranch("^perms").getBranch("group").getBranch("<GROUP>").getBranch("delete");
        this.getBranch("^perms").getBranch("group").getBranch("<GROUP>").getBranch("assign").addBranch("<ALL_PLAYERS>");
        this.getBranch("^perms").getBranch("group").getBranch("<GROUP>").getBranch("unassign").addBranch("<ALL_PLAYERS>");

        this.getBranch("^mm").addBranch("load", "unload", "enable", "disable", "download");
        this.getBranch("^mm").getBranch("load").addBranch("<FILENAME>");
        this.getBranch("^mm").getBranch("enable").addBranch("<DISABLED_MODULES>");
        this.getBranch("^mm").getBranch("unload").addBranch("<DISABLED_MODULES>");
        this.getBranch("^mm").getBranch("disable").addBranch("<ENABLED_MODULES>");
        this.getBranch("^mm").getBranch("download").addBranch("<URL>");

        this.getBranch("^mods").addBranch("<ALL_MODULES>").addBranch("config", "reloadconfig", "saveconfig", "readconfig", "clean");
        this.getBranch("^mods").getBranch("<ALL_MODULES>").getBranch("config").addBranch("<KEY>").addBranch("<VALUE>");

        // TODO /eden disengage, which unloads ALL modules

        this.getBranch("data").addBranch("<ALL_PLAYERS>").addBranch("^reset", "password", "^approve", "^clearpassword");

        this.getBranch("^logging").addBranch("verbose", "export");

        this.getBranch("sudo").addBranch("<ALL_PLAYERS>");
    }

    private static LocalDate time = LocalDate.now();
    private PrintWriter text_writer;
    private String fileName;
    private File logFile;
    private EdenModule module;
    
    @EdenCommandHandler(usage = "eden mods <Module name> <config | readconfig>", description = "View and configure Eden modules.", argsCount = 1)
    public void edencommand_mods(CommandSender sender, String commandLabel, String[] args)
    {
        if (args.length == 1)
        {
            if (sender instanceof ConsoleCommandSender)
            {
                PrintUtils.sendMessage(sender, "§6-----[ §eModules §6]-----");
                for (EdenModule m : Eden.getModuleManager().getEnabledModules())
                    PrintUtils.sendMessage(sender, "§a" + m.getName());

                for (EdenModule m : Eden.getModuleManager().getDisabledModules())
                    PrintUtils.sendMessage(sender, "§c" + m.getName());
                return;
            }

            ModuleTracker.update();
            ModuleGui gui = new ModuleGui();
            gui.display((Player) sender);
            
            return;
        }

        module = Eden.getModuleManager().getEnabledModuleByName(args[1]);

        if (module == null)
        {
            PrintUtils.sendMessage(sender, "No enabled module named \"" + args[1] + "\" was found.");
            return;
        }
            
        if (args.length <= 2)
        {
            PrintUtils.sendMessage(sender, "Expected a subcommand, received none.");
            return;
        }

        switch (args[2])
        {
            case "config":
                if (!ensureArgsCount(args, 4, 2, "config", sender))
                    break;

                String oldValue = module.getConfig().get(args[3]);

                module.getConfig().put(args[3], args[4]); // TODO args[4] is not required, causes ArrayIndexOutOfBoundsException

                // Call an event to notify anything that might want to listen for things like this
                Eden.getInstance().getServer().getPluginManager().callEvent(new EdenConfigChangeEvent(module, args[3], oldValue, args[4]));
                PrintUtils.sendMessage(sender, "Entered configuration value \"§a" + args[4] + "§7\" into key \"§b" + args[3] + "§7\" in module " + module.getName() + ".");
                break;
            case "readconfig":
                module.getConfig().forEach((key, value) -> {
                    String color = "e";
                    if (value.equals("true")) {
                        color = "a";
                    } else if (value.equals("false")) {
                        color = "c";
                    }

                    PrintUtils.sendMessage(sender, "Config: §b" + key + "§7 | §" + color + value);
                });
                break;
            case "reloadconfig":
                if (module.getClass().isAnnotationPresent(EdenConfig.class))
                {
                    module.loadConfig();
                    PrintUtils.sendMessage(sender, "Reloaded config from file. You should double-check the new values by using /eden mods " + args[1] + " readconfig.");
                    break;
                }
                    
                PrintUtils.sendMessage(sender, "This module does not have an associated configuration file.");
                break;
            case "saveconfig":
                if (module.getClass().isAnnotationPresent(EdenConfig.class))
                {
                    module.saveConfig();
                    PrintUtils.sendMessage(sender, "Saved configuration from memory to local file.");
                    break;
                }
                    
                PrintUtils.sendMessage(sender, "This module does not have an associated configuration file.");
                break;
            case "clean":
                if (module.getClass().isAnnotationPresent(EdenConfig.class) && module.getClass().isAnnotationPresent(DefaultConfig.class))
                {
                    DefaultConfig dc = module.getClass().getAnnotation(DefaultConfig.class);
                    Map<String, String> config = module.getConfig();
                    
                    Iterator<String> iter = config.keySet().iterator();
                    int removed = 0;
                    while (iter.hasNext())
                    {
                        String key = iter.next();

                        if (!Arrays.asList(dc.keys()).contains(key))
                        {
                            iter.remove();
                            removed++;
                        }
                    }

                    PrintUtils.sendMessage(sender, "Cleaned " + removed + " " + PrintUtils.plural(removed, "key", "keys") + " from configuration file.");
                    break;
                }

                PrintUtils.sendMessage(sender, "This module does not have an associated configuration file.");
                break;
            default:
                this.sendErrorMessage(sender, args[2], "<MODULE>");
                break;
        }
        return;
    }

    @EdenCommandHandler(usage = "eden perms <specific | group> <add | remove | assign | create>", description = "Change permissions for users and groups.", argsCount = 4)
    public void edencommand_perms(CommandSender sender, String commandLabel, String[] args)
    {
        Player player;
        PermissionGroup group;
        boolean bool;
        PlayerData account;

        switch (args[1])
        {
            case "group":
                // Generate group from argument 2
                if (!(Eden.getPermissionsManager() instanceof EdenPermissionManager))
                {
                    PrintUtils.sendMessage(sender, "§cThis command does not support editing groups for third-party permissions managers.");
                    return;
                }

                group = ((EdenPermissionManager) Eden.getPermissionsManager()).getAllGroups().get(args[2]);

                if (group == null && !args[3].equals("create"))
                {
                    PrintUtils.sendMessage(sender, "§cUnknown group \"" + args[2] + "\".");
                    return;
                }

                if (args[3].equals("create") && group != null)
                {
                    PrintUtils.sendMessage(sender, "§cA group with that name already exists.");
                }

                switch (args[3])
                {
                    case "add": // eden perms group <GROUP> add <PERMISSION>
                        if (!ensureArgsCount(args, 5, "add", sender))
                            return;

                        group.addPermission(args[4]);
                        PrintUtils.sendMessage(sender, "Added permission \"" + args[4] + "\" to group \"" + args[2] + "\".");
                        break;
                    case "remove": // eden perms group <GROUP> add <PERMISSION>
                        if (!ensureArgsCount(args, 5, "remove", sender))
                            return;

                        if (group.removePermission(args[4]))
                        {
                            PrintUtils.sendMessage(sender, "Removed permission \"" + args[4] + "\" from group \"" + args[2] + "\".");
                            break;
                        }

                        PrintUtils.sendMessage(sender, "§cGroup \"" + args[2] + "\" does not possess the permission \"" + args[4] + "\".");
                        break;
                    case "assign": // eden perms group <GROUP> assign <PlAYER>
                        if (!ensureArgsCount(args, 5, "assign", sender))
                            return;

                        player = Bukkit.getPlayer(args[4]);

                        // Player is not online, attempt to find them in the cache
                        if (player == null)
                        {
                            if (!Eden.getUuidCache().containsKey(args[4]))
                            {
                                PrintUtils.sendMessage(sender, "§cCould not find a player by that name.");
                                return;
                            }

                            account = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + Eden.getUuidCache().get(args[4]) + ".json", PlayerData.class);

                            if (account == null)
                            {
                                PrintUtils.sendMessage(sender, "§cPlayer data is corrupt! Cannot add group.");
                                return;
                            }

                            if (account.getPermissionGroups().contains(group.getName()))
                            {
                                PrintUtils.sendMessage(sender, "§cGroup \"" + group.getName() + "\" has already been assigned to offline player \"" + args[4] + "\".");
                                return;
                            }

                            account.addPermissionGroup(group.getName());
                            JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + Eden.getUuidCache().get(args[4]) + ".json", account);

                            PrintUtils.sendMessage(sender, "Assigned group \"" + group.getName() + "\" to offline player \"" + args[4] + "\".");
                            return;
                        }

                        account = Eden.getPermissionsManager().getPlayerData(player);

                        if (account.getPermissionGroups().contains(group.getName()))
                        {
                            PrintUtils.sendMessage(sender, "§cGroup \"" + group.getName() + "\" has already been assigned to player \"" + args[4] + "\".");
                            return;
                        }

                        account.addPermissionGroup(group.getName());

                        PrintUtils.sendMessage(sender, "Assigned group \"" + group.getName() + "\" to player \"" + args[4] + "\".");

                        break;
                    case "unassign":
                        if (!ensureArgsCount(args, 5, "unassign", sender))
                            return;

                        player = Bukkit.getPlayer(args[4]);

                        // Player is not online, attempt to find them in the cache
                        if (player == null)
                        {
                            if (!Eden.getUuidCache().containsKey(args[4]))
                            {
                                PrintUtils.sendMessage(sender, "§cCould not find a player by that name.");
                                return;
                            }

                            account = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + Eden.getUuidCache().get(args[4]) + ".json", PlayerData.class);

                            if (account == null)
                            {
                                PrintUtils.sendMessage(sender, "§cPlayer data is corrupt! Cannot remove group.");
                                return;
                            }

                            account.removePermissionGroup(group.getName());
                            JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + Eden.getUuidCache().get(args[4]) + ".json", account);

                            PrintUtils.sendMessage(sender, "Removed group \"" + group.getName() + "\" from offline player \"" + args[4] + "\".");
                            return;
                        }

                        account = Eden.getPermissionsManager().getPlayerData(player);
                        account.removePermissionGroup(group.getName());

                        PrintUtils.sendMessage(sender, "Removed group \"" + group.getName() + "\" from player \"" + args[4] + "\".");
                        break;
                    case "create": // eden perms group <GROUP>* create <BASE>** | *Where "GROUP" is an unused group label, **Optional
                        group = new PermissionGroup(args[2], new String[0]);

                        // TODO Handle bases
                        ((EdenPermissionManager) Eden.getPermissionsManager()).addGroup(group);
                        PrintUtils.sendMessage(sender, "Created new permissions group \"" + group.getName() + "\" successfully.");
                        
                        break;
                    case "delete":
                        ((EdenPermissionManager) Eden.getPermissionsManager()).deleteGroup(group);
                        PrintUtils.sendMessage(sender, "Deleted group \"" + group.getName() + "\" successfully.");
                        break;
                    default:
                        this.sendErrorMessage(sender, args[3], "group");
                        break;
                }

                break;
            case "specific":
                if (!ensureArgsCount(args, 6, "specific", sender))
                    return;

                // Generate player from argument 2
                player = Bukkit.getPlayer(args[2]);

                if (player != null)
                {
                    account = Eden.getPermissionsManager().getPlayerData(player);
                } else {
                    if (!Eden.getUuidCache().containsKey(args[2]))
                    {
                        PrintUtils.sendMessage(sender, "§cCould not find a player by that name.");
                        return;
                    }

                    account = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + Eden.getUuidCache().get(args[2]) + ".json", PlayerData.class);
                }

                if (!args[5].toLowerCase().equals("true") && !args[5].toLowerCase().equals("false"))
                {
                    PrintUtils.sendMessage(sender, "§cInvalid boolean value for \"revoked\". Expected \"true\" or \"false\", received \"" + args[5] + "\".");
                    return;
                }

                bool = Boolean.parseBoolean(args[5]);

                switch (args[3])
                {
                    case "add": // eden perms specific <PLAYER> add <PERMISSION> <REVOKED> 
                        if (bool)
                        {
                            account.addRevokedPermission(args[4]);
                            PrintUtils.sendMessage(sender, "Added specific revoked permission \"" + args[4] + "\".");
                            break;
                        }

                        account.addPermission(args[4]);
                        PrintUtils.sendMessage(sender, "Added specific permission \"" + args[4] + "\".");

                        break;
                    case "remove": // eden perms specific <PLAYER> remove <PERMISSION> <REVOKED> 
                        if (bool)
                        {
                            account.removeRevokedPermission(args[4]);
                            PrintUtils.sendMessage(sender, "Removed specific revoked permission \"" + args[4] + "\".");
                            break;
                        }

                        account.removePermission(args[4]);
                        PrintUtils.sendMessage(sender, "Removed specific permission \"" + args[4] + "\".");

                        break;
                    default:
                        this.sendErrorMessage(sender, args[3], "specific");
                        break;
                }

                if (player == null)
                    JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + Eden.getUuidCache().get(args[2]) + ".json", account);

                break;
            default:
                this.sendErrorMessage(sender, args[1], commandLabel);
                break;
        }
    }

    @EdenCommandHandler(usage = "eden logging <verbose | export>", description = "Toggle verbose logging or export the current log to a file.", argsCount = 2)
    @EdenCommandRedirect(labels = {"logs", "log"}, command = "eden logging args")
    public void edencommand_logging(CommandSender sender, String commandLabel, String[] args)
    {
        switch (args[1])
        {
            case "verbose":
                if (PrintUtils.isVerboseLoggingEnabled())
                {
                    PrintUtils.sendMessage(sender, "Disabled verbose logging.");
                    Eden.getModuleManager().getEnabledModuleByName("Eden").getConfig().put("verboseLogging", "false");
                    PrintUtils.disableVerboseLogging();
                    break;
                }
                
                PrintUtils.sendMessage(sender, "Enabled verbose logging.");
                PrintUtils.enableVerboseLogging();

                Eden.getModuleManager().getEnabledModuleByName("Eden").getConfig().put("verboseLogging", "true");
                break;
            case "export":
                time = LocalDate.now();
                fileName = "log-" + time.toString() + "-" + FileUtils.getFileNameCount("./plugins/Eden/", "log-" + time.toString() + "-") + ".txt";
                logFile = new File("./plugins/Eden/" + fileName);

                try {
                    text_writer = new PrintWriter(logFile);

                    for (String str : PrintUtils.getLog())
                        text_writer.println(str);
                } catch (FileNotFoundException e) {
                    break;
                }

                text_writer.flush();
                text_writer.close();
                
                PrintUtils.sendMessage(sender, "Done! Log exported to §a" + fileName + "§7.");
                break;
            default:
                sendErrorMessage(sender, args[1], "logging");
                break;
        }
    }

    @EdenCommandHandler(usage = "eden helpall", description = "Provides a list of all parent commands.", argsCount = 1)
    public void edencommand_helpall(CommandSender sender, String commandLabel, String[] args)
    {
        PrintUtils.sendMessage(sender, "§6----[ §eRegistered Eden Commands §6]----");

        for (EdenCommand parent : CommandManager.getKnownCommands())
        {
            if (parent.getClass().isAnnotationPresent(HideFromEdenHelpall.class))
                continue;

            try {
                PrintUtils.sendMessage(sender, "§7/§a" + parent.getName() + " §7| from module §b" + parent.getParentModule().getName());
            } catch (NullPointerException e) {
                PrintUtils.sendMessage(sender, "§7/§a" + parent.getName());
            }
        }

        PrintUtils.sendMessage(sender, "- Every Eden command has a \"help\" subcommand, which displays a list of subcommands.");
    }

    @EdenCommandHandler(usage = "eden mm <load | unload | enable | disable | download> <module name | URL>", description = "Command interface for the Eden module manager.", argsCount = 3)
    public void edencommand_mm(CommandSender sender, String commandLabel, String[] args)
    {
        EdenModule module;
        switch (args[1])
        {
            case "load":
            	Result<EdenModule, String> result = Eden.getModuleManager().loadSingleModule("./plugins/Eden/mods/" + args[2] + ".jar");
            	
            	switch (result.getState())
            	{
            		case OK:
						try {
							module = (EdenModule) result.unwrap();
						} catch (UndefinedResultException e1) { return; }
            			break;
            		case ERR:
            			try {
            				PrintUtils.sendMessage(sender, "Failed to load module! Error: \"" + result.unwrap() + "\"");
            				return;
            			} catch (UndefinedResultException e) { return; }
            		default: return;
            	}

                if (module == null)
                {
                    PrintUtils.sendMessage(sender, "File doesn't exist.");
                    return;
                }

                PrintUtils.sendMessage(sender, "Done! You may now enable this module by running \"/eden mm enable " + module.getName() + "\".");

                break;
            case "unload":
                module = Eden.getModuleManager().getDisabledModuleByName(args[2]);

                if (module == null)
                {
                    PrintUtils.sendMessage(sender, "Failed to find a disabled module by that name. Ensure that the module you wish to unload is disabled.");
                    return;
                }

                if (module.getClass().isAnnotationPresent(PreventUnload.class) && sender instanceof ConsoleCommandSender)
                {
                    
                    if (module.getClass().getAnnotation(PreventUnload.class).value().equals(CallerToken.EDEN))
                    {
                        if (CoreModule.EDEN_WARN_DISABLE_REQUESTS.contains(module.getName() + ":" + 1))
                        {
                            PrintUtils.sendMessage(sender, "§Already warned you about unloading this module, going ahead and unloading it...");
                            Eden.getModuleManager().removeModuleFromMemory(module.getName(), CallerToken.EDEN);

                            break;
                        }
                        
                        PrintUtils.sendMessage(sender, "§cThis module has an @PreventUnload tag with a caller token of EDEN. Unloading it is probably a REALLY BAD IDEA.");
                        PrintUtils.sendMessage(sender, "§cThis must be done very deliberately, and is not supported by the Eden devs. Do not report any bugs that arise.");
                        PrintUtils.sendMessage(sender, "§cIf you're absolutely CERTAIN you wish to continue, enter \"§4iknowwhatimdoingiswear§c\".");
                        PrintUtils.sendMessage(sender, "§cThis request will time out in 60 seconds.");

                        CoreModule.EDEN_DISABLE_REQUESTS.add(new DisableRequest(module, 1));

                        new BukkitRunnable() 
                        {
                            @Override
                            public void run() 
                            {
                                try {
                                    CoreModule.EDEN_DISABLE_REQUESTS.remove(new DisableRequest(module, 1));
                                } catch (Exception e) {
                                    // In case CORE is our unload target, silently handle
                                } 
                            }
                        }.runTaskLater(Eden.getInstance(), 60*20);

                        break;
                    }
                }

                Eden.getModuleManager().removeModuleFromMemory(module.getName(), CallerToken.fromCommandSender(sender));

                if (Eden.getModuleManager().getDisabledModules().contains(module))
                {
                    PrintUtils.sendMessage(sender, "Failed to unload module from memory!");
                    return;
                }

                PrintUtils.sendMessage(sender, "Successfully unloaded module from memory. To reload it, execute \"/eden mm load\" on the module file.");
                break;
            case "enable":
                module = Eden.getModuleManager().getDisabledModuleByName(args[2]);

                if (module == null)
                {
                    PrintUtils.sendMessage(sender, "Failed to find a gathered module by that name. Ensure that the module you wish to enable has been loaded into memory.");
                    return;
                }

                PrintUtils.sendMessage(sender, "Enabling module \"" + module.getName() + "\"...");

                try {
                    Eden.getModuleManager().enableModule(module);
                    module.setEnabled();
                } catch (Exception e) {
                    PrintUtils.printPrettyStacktrace(e);
                }

                if (Eden.getModuleManager().getEnabledModuleByName(module.getName()) != null)
                {
                    PrintUtils.sendMessage(sender, "Done! Module has been enabled. If this is a cold start for this module, then command suggestions may not work.");
                    return;
                }
                
                PrintUtils.sendMessage(sender, "Failed to enable module. Please check the console or export the log using /eden logging export for details.");
                break;
            case "disable":
                module = Eden.getModuleManager().getEnabledModuleByName(args[2]);

                if (module == null)
                {
                    PrintUtils.sendMessage(sender, "Failed to find a gathered module by that name. Ensure that the module you wish to enable has been loaded into memory.");
                    return;
                }

                if (module.getClass().isAnnotationPresent(PreventUnload.class) && sender instanceof ConsoleCommandSender)
                {
                    
                    if (module.getClass().getAnnotation(PreventUnload.class).value().equals(CallerToken.EDEN))
                    {
                        if (CoreModule.EDEN_WARN_DISABLE_REQUESTS.contains(module.getName() + ":" + 0))
                        {
                            PrintUtils.sendMessage(sender, "§6Already warned you about disabling this module, going ahead and disabling it...");
                            Eden.getModuleManager().disableModule(module.getName(), CallerToken.EDEN);
                            break;
                        }

                        PrintUtils.sendMessage(sender, "§6This module has an @PreventUnload tag with a caller token of EDEN. Disabling it is probably a bad idea!");
                        PrintUtils.sendMessage(sender, "§6If you're still sure you want to disable this module, enter \"§ciknowwhatimdoingiswear§6\".");
                        PrintUtils.sendMessage(sender, "§6This request will time out in 60 seconds.");

                        CoreModule.EDEN_DISABLE_REQUESTS.add(new DisableRequest(module, 0));

                        new BukkitRunnable() {
                            @Override
                            public void run()
                            {
                                try {
                                    CoreModule.EDEN_DISABLE_REQUESTS.remove(new DisableRequest(module, 0));
                                } catch (Exception e) {
                                    // In cose CORE is our disable target, handle error silently
                                }
                            }                        
                        }.runTaskLater(Eden.getInstance(), 60*20);
                        
                        break;
                    }
                }

                Eden.getModuleManager().disableModule(module.getName(), CallerToken.fromCommandSender(sender));

                if (Eden.getModuleManager().getDisabledModuleByName(module.getName()) != null)
                {
                    module.setDisabled();
                    PrintUtils.sendMessage(sender, "Disabled module.");
                    break;
                }
                
                PrintUtils.sendMessage(sender, "Failed to disable module. Perhaps it cannot be disabled?");

                break;
            case "download":
                if (!Eden.getModuleManager().isModulePresent("EdenNetworking"))
                {
                    PrintUtils.sendMessage(sender, "This functionality requires the EdenNetworking module.");
                    return;
                }

                try {
                    // We have to ask the module manager to grab this class from the cache, since it doesn't exist in this context
                    Class<?> nu_class = Eden.getModuleManager().getCachedClass("com.yukiemeralis.blogspot.eden.networking.NetworkingUtils");

                    String finalPortion = (String) nu_class.getMethod("getFinalURLPortion", String.class).invoke(null, args[2]);
                    nu_class.getMethod("downloadFileFromURLThreaded", String.class, String.class).invoke(null, args[2], "./plugins/Eden/dlcache/" + finalPortion);

                    PrintUtils.sendMessage(sender, "Done! File has been saved as " + finalPortion + ".");
                } catch (InvocationTargetException e) {
                    PrintUtils.sendMessage(sender, "Failed to download file. Ensure that the given URL is correct.");
                    PrintUtils.printPrettyStacktrace(e);
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    PrintUtils.printPrettyStacktrace(e);
                }
                break;
            default:
                sendErrorMessage(sender, args[1], "mm");
                break;
        }
    }

    @EdenCommandHandler(usage = "eden sudo [user]", description = "Elevate yourself, or elevate as another user.", argsCount = 1)
    public void edencommand_sudo(CommandSender sender, String commandLabel, String[] args)
    {
        if (!(sender instanceof Player))
        {
            PrintUtils.sendMessage(sender, "§cConsole is elevated by default, sudo is ineffective.");
            return;
        }

        PlayerData account;

        if (args.length == 1) // Elevate self
        {
            account = Eden.getPermissionsManager().getPlayerData((Player) sender);
            if (!account.hasPassword())
            {
                PrintUtils.sendMessage(sender, "§cYou do not have a password set up for this account. Please contact an administrator.");
                return;
            }

            ChatAction action = new ChatAction()
            {
                @Override
                public void run()
                {
                    String result = ChatUtils.receiveResult(sender);
                    ChatUtils.deleteResult(sender);   

                    if (result.toLowerCase().equals("cancel"))
                    {
                        PrintUtils.sendMessage(sender, "Exitted password-entry mode. It is no longer safe to enter your password.");
                        return;
                    }

                    if (!account.comparePassword(result))
                    {
                        PrintUtils.sendMessage(sender, "§cIncorrect password. It is no longer safe to enter your password.");
                        return;
                    }

                    Eden.getPermissionsManager().addElevatedUser((Player) sender);
                    PrintUtils.sendMessage(sender, "<#A9EED1>You are now elevated. With great power comes great responsibility.");
                }
            };

            PrintUtils.sendMessage(sender, "It is now safe to enter your password. Password for your account:");
            ChatUtils.expectChat(sender, action);

            return;
        }

        // Elevate as other account
        if (!Eden.getUuidCache().containsKey(args[1]))
        {
            PrintUtils.sendMessage(sender, "§cCannot find user \"" + args[1] + "\".");
            return;
        }

        if (args[1].equals(((Player) sender).getName()))
        {
            ((Player) sender).chat("/eden sudo"); // Resend command without player argument
            return;
        }

        account = Eden.getPermissionsManager().getOfflinePlayerData(Eden.getUuidCache().get(args[1]));
        
        if (!account.hasPassword())
        {
            PrintUtils.sendMessage(sender, "§cUser \"" + args[1] + "\" does not have a password associated with their account! Cannot elevate as them.");
            return;
        }

        ChatAction action = new ChatAction()
        {
            @Override
            public void run()
            {
                String result = ChatUtils.receiveResult(sender);
                ChatUtils.deleteResult(sender);   

                if (result.toLowerCase().equals("cancel"))
                {
                    PrintUtils.sendMessage(sender, "Exitted password-entry mode. It is no longer safe to enter a password.");
                    return;
                }

                if (!account.comparePassword(result))
                {
                    PrintUtils.sendMessage(sender, "§cIncorrect password for user \"" + args[1] + "\". It is no longer safe to enter a password.");
                    return;
                }

                Eden.getPermissionsManager().addElevatedUser((Player) sender);
                PrintUtils.sendMessage(sender, "<#A9EED1>You are now elevated. With great power comes great responsibility.");
            }
        };

        PrintUtils.sendMessage(sender, "It is now safe to enter password. Password for user \"" + args[1] + "\":");
        ChatUtils.expectChat(sender, action);
    }

    private final Map<String, String> cachedPasswords = new HashMap<>(); 

    @EdenCommandHandler(usage = "eden data <user> <subcommand>", description = "View and repair player data.", argsCount = 2)
    public void edencommand_data(CommandSender sender, String commandLabel, String[] args)
    {
        Player p = Eden.getInstance().getServer().getPlayerExact(args[1]);
        String uuid;

        if (p == null)
        {
            uuid = Eden.getUuidCache().get(args[1]);

            if (uuid == null)
            {
                PrintUtils.sendMessage(sender, "No data exists for a player named \"" + args[1] + "\".") ;
                return;
            }
        } else {
            uuid = p.getUniqueId().toString();
        }

        if (args.length == 2)
        {   
            if (p == null)
            {
                PrintUtils.sendMessage(sender, "No player named \"" + args[1] + "\" is online.");
                return;
            }

            PrintUtils.sendMessage(sender, "Player information about user \"§b" + args[1] + "§7\":");
            PrintUtils.sendMessage(sender, "UUID: §a" + p.getUniqueId().toString());
            PrintUtils.sendMessage(sender, "Current IP: §a" + p.getAddress().getAddress().getHostAddress());
            PrintUtils.sendMessage(sender, "Has data? §a" + (Eden.getPermissionsManager().getPlayerData(p) != null));
            return;
        }

        PlayerData account;

        switch (args[2])
        {
            case "reset":
                if (p != null)
                {
                    if (Eden.getPermissionsManager().getPlayerData(p).hasPassword())
                        SecurityCore.log("Potentially concerning action: player data containing a password, belonging to user \"" + p.getName() + "\" has been wiped and reset. See caller above.", true);

                    Eden.getPermissionsManager().getAllData().put(p, new PlayerData());
                    Eden.getPermissionsManager().getPlayerData(p).addPermissionGroup("default");
                    PrintUtils.sendMessage(sender, "Successfully reset data for player \"" + p.getName() + "\".");
                    return;
                }

                account = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", PlayerData.class);
                if (account.hasPassword())
                    SecurityCore.log("Potentially concerning action: player data containing a password, belonging to UUID \"" + uuid + "\" has been wiped and reset. See caller above.", true);
                
                account = new PlayerData();
                account.addPermissionGroup("default");
                JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", account);
                PrintUtils.sendMessage(sender, "Successfully reset data for UUID \"" + uuid + "\".");
                break;
            case "clearpassword":
                if (p != null)
                {
                    Eden.getPermissionsManager().getPlayerData(p).removePassword();
                    PrintUtils.sendMessage(sender, "Successfully removed password for player \"" + p.getName() + "\".");
                    return;
                }

                account = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", PlayerData.class);
                account.removePassword();
                JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", account);
                PrintUtils.sendMessage(sender, "Successfully removed password for UUID \"" + uuid + "\".");
                break;
            case "password":
                if (!(sender instanceof Player))
                {
                    PrintUtils.sendMessage(sender, "Only players can set an account password.");
                    return;
                }

                if (!((Player) sender).getName().equals(args[1]))
                {
                    PrintUtils.sendMessage(sender, "You may only set your own password.");
                    return;
                }

                ChatAction action = new ChatAction()
                {
                    @Override
                    public void run()
                    {
                        String result = ChatUtils.receiveResult(sender);
                        ChatUtils.deleteResult(sender);

                        if (result.equals("cancel"))
                        {
                            PrintUtils.sendMessage(sender, "Exitted password entry mode.");
                            return;
                        }

                        cachedPasswords.put(((Player) sender).getName(), result);
                        PrintUtils.log("Received new password request. Estimated strength: [" + DataUtils.estimatePasswordStrength(result) + "]");
                        PrintUtils.sendMessage(sender, "Sent password for approval.");
                    }
                };

                account = Eden.getPermissionsManager().getPlayerData((Player) sender);
                PlayerData faccount = account; // Java is a fun language.
                if (account.hasPassword())
                {
                    PrintUtils.sendMessage(sender, "This account already has a password. Enter your current password to confirm replacement request:");
                    
                    ChatUtils.expectChat(sender, new ChatAction()
                    {
                        @Override
                        public void run()
                        {
                            String result = ChatUtils.receiveResult(sender);
                            ChatUtils.deleteResult(sender);

                            if (result.equals("cancel"))
                            {
                                PrintUtils.sendMessage(sender, "Exitted password entry mode.");
                                return;
                            }

                            if (!faccount.comparePassword(result))
                            {
                                PrintUtils.sendMessage(sender, "§cIncorrect password. You may request a password reset from an administrator.");
                                return;
                            }

                            ChatUtils.expectChat(sender, action);
                            PrintUtils.sendMessage(sender, "Please enter a new password. Type \"cancel\" to leave password entry mode.");
                        }
                    });
                    return;
                }

                ChatUtils.expectChat(sender, action);
                PrintUtils.sendMessage(sender, "Please enter a new password. Type \"cancel\" to leave password entry mode.");

                break;
            case "approve":
                if (!cachedPasswords.containsKey(args[1]))
                {
                    PrintUtils.sendMessage(sender, "This player has not submitted a password.");
                    return;
                }

                if (sender instanceof ConsoleCommandSender)
                {
                    if (p == null)
                    {
                        // Offline player
                        account = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", PlayerData.class);

                        account.setPassword(HashUtils.hexToString(HashUtils.hashStringSHA256(cachedPasswords.get(args[1]))));
                        JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", account);

                        PrintUtils.sendMessage(sender, "Approved offline password request.");
                        cachedPasswords.remove(args[2]);

                        return;
                    }

                    account = Eden.getPermissionsManager().getPlayerData(p);
                    
                    account.setPassword(HashUtils.hexToString(HashUtils.hashStringSHA256(cachedPasswords.get(args[1]))));

                    PrintUtils.sendMessage(sender, "Approved password request.");
                    PrintUtils.sendMessage(p, "Your password has been approved. You may now access elevated resources with \"/eden sudo\".");
                    cachedPasswords.remove(args[1]);

                    return;
                }

                ChatAction approveAction = new ChatAction()
                {
                    @Override
                    public void run()
                    {
                        String result = ChatUtils.receiveResult(sender).toLowerCase();
                        ChatUtils.deleteResult(sender);

                        if (result.equals("cancel"))
                        {
                            PrintUtils.sendMessage(sender, "Ignored request. Please re-evaluate this request later.");
                            return;
                        }

                        PlayerData paccount;

                        switch (result)
                        {
                            case "yes":
                                if (p == null)
                                {
                                    // Offline player
                                    paccount = JsonUtils.fromJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", PlayerData.class);

                                    paccount.setPassword(HashUtils.hexToString(HashUtils.hashStringSHA256(cachedPasswords.get(args[1]))));
                                    JsonUtils.toJsonFile("./plugins/Eden/playerdata/" + uuid + ".json", paccount);

                                    PrintUtils.sendMessage(sender, "Approved offline password request.");
                                    cachedPasswords.remove(args[2]);

                                    return;
                                }

                                paccount = Eden.getPermissionsManager().getPlayerData(p);
                                
                                paccount.setPassword(HashUtils.hexToString(HashUtils.hashStringSHA256(cachedPasswords.get(args[1]))));

                                PrintUtils.sendMessage(sender, "Approved password request.");
                                PrintUtils.sendMessage(p, "Your password has been approved. You may now access elevated resources with \"/eden sudo\".");
                                cachedPasswords.remove(args[1]);

                                break;
                            case "no":
                                cachedPasswords.remove(args[1]);
                                PrintUtils.sendMessage(sender, "Rejected password request.");
                                break;
                            default:
                                PrintUtils.sendMessage(sender, "Unrecognized input. Please enter one of the following: [yes/no/cancel].");
                                ChatUtils.expectChat(sender, this);
                                break;
                        }
                    }
                };

                ChatUtils.expectChat(sender, approveAction);
                PrintUtils.sendMessage(sender, "Approve password for user \"" + args[2] + "\"? Estimated strength: " + DataUtils.estimatePasswordStrength(cachedPasswords.get(args[1])) + ", [yes|no|cancel].");

                break;
            default:
                sendErrorMessage(sender, args[2], "data");
                break;
        }
    }

    @EdenCommandHandler(usage = "eden", description = "Provides the user with Eden's version and a module count.", argsCount = 0)
    public void edencommand_nosubcmd(CommandSender sender, String commandLabel, String[] args)
    {
        String buffer = "┌§m";

        if (!(sender instanceof Entity))
        {
            PrintUtils.sendMessage(sender, "Version: Eden " + VersionCtrl.getVersion());
            PrintUtils.sendMessage(sender, 
                "Enabled modules: §a" + Eden.getModuleManager().getEnabledModules().size() + 
                "§7 (§c" + Eden.getModuleManager().getDisabledModules().size() + "§7)/" + 
                Eden.getModuleManager().getAllModules().size()
            );
            return;
        }

        // Line 1
        for (int i = 0; i < 11; i++)
            buffer = buffer + "─";
        buffer = buffer + "§r§7┐";
        PrintUtils.sendMessage(sender, buffer);

        // Line 2
        PrintUtils.sendMessage(sender, "│ " + " Eden " + VersionCtrl.getVersion() + "│");
        
        buffer = buffer.replace("┌", "└").replace("┐", "┘");
        PrintUtils.sendMessage(sender, buffer);

        PrintUtils.sendMessage(sender, "§oWith love from Yuki_emeralis §r§c:heart:");
        PrintUtils.sendMessage(sender, "Project is maintained here: §9https://github.com/YukiEmeralis/Zenith");

        PrintUtils.sendMessage(sender, 
            "Enabled modules: §a" + Eden.getModuleManager().getEnabledModules().size() + 
            "§7 (§c" + Eden.getModuleManager().getDisabledModules().size() + "§7)/" + 
            Eden.getModuleManager().getAllModules().size()
        );
    }
}