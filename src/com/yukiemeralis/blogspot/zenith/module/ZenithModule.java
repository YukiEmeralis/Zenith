package com.yukiemeralis.blogspot.zenith.module;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import com.yukiemeralis.blogspot.zenith.command.ZenithCommand;
import com.yukiemeralis.blogspot.zenith.module.java.annotations.DefaultConfig;
import com.yukiemeralis.blogspot.zenith.module.java.enums.PreventUnload;
import com.yukiemeralis.blogspot.zenith.utils.ItemUtils;
import com.yukiemeralis.blogspot.zenith.utils.JsonUtils;
import com.yukiemeralis.blogspot.zenith.utils.PrintUtils;
import com.yukiemeralis.blogspot.zenith.utils.PrintUtils.InfoType;

@SuppressWarnings("unused")
public abstract class ZenithModule
{
    protected String modName, version, description, maintainer;
    protected Material modIcon;
    private boolean isEnabled = false;

    private List<Listener> listeners = new ArrayList<>();
    private List<ZenithCommand> commands = new ArrayList<>();

    protected Map<String, String> config;

    /**
     * Runs once when the module is first loaded.
     */
    public abstract void onEnable();
    /**
     * Runs once when the module is being unloaded.
     */
    public abstract void onDisable();

    public void addListener(Listener... listener)
    {
        for (Listener l : listener)
            listeners.add(l);
    }

    public void addCommand(ZenithCommand... command)
    {
        for (ZenithCommand c : command)
            commands.add(c);
    }

    public boolean getIsEnabled()
    {
        return this.isEnabled;
    }

    public void setEnabled()
    {
        this.isEnabled = true;
    }

    public void setDisabled()
    {
        this.isEnabled = false;
    }

    public String getName()
    {
        return this.modName;
    }

    public String getVersion()
    {
        return this.version;
    }

    public Map<String, String> getConfig()
    {
        return config;
    }

    public List<Listener> getListeners()
    {
        return this.listeners;
    }

    public List<ZenithCommand> getCommands()
    {
        return this.commands;
    }

    //
    // Module info generation
    //

    public ItemStack toIcon()
    {
        String status;
        if (isEnabled) {
            status = "§aenabled";
        } else {
            status = "§cdisabled";
        }

        String rulesHeader = "§r§6§lCan be disabled:";
        String byPlayer = "§r§7- From here? [§a§l✔§r§7]", byConsole = "§r§7- By console? [§a§l✔§r§7]", byZenith = "§r§7- By Zenith? [§a§l✔§r§7]";

        if (this.getClass().isAnnotationPresent(PreventUnload.class))
        {
            switch (this.getClass().getAnnotation(PreventUnload.class).value())
            {
                case CONSOLE:
                    byPlayer = "§r§7- From here? [§c§l✖§r§7]";
                    break;
                case ZENITH:
                    byPlayer = "§r§7- From here? [§c§l✖§r§7]";
                    byConsole = "§r§7- By console? [§c§l✖§r§7]";
                    break;
                default: break;
            }
        }

        ItemStack icon = new ItemStack(this.modIcon);
        ItemUtils.applyName(icon, "§r§b§l" + this.modName);
        ItemUtils.applyLore(icon, 
            "§r§3" + this.description,
            "§r§3Version: " + this.version,
            "§r§3Maintainer: " + this.maintainer,
            "",
            "§r§3Status: " + status,
            "",
            rulesHeader,
            byPlayer,
            byConsole,
            byZenith
        );

        ItemUtils.saveToNamespacedKey(icon, "modName", this.modName);

        return icon;
    }

    public void setInfo(ModInfo info)
    {
        this.modName = info.modName();
        this.version = info.version();
        this.description = info.description();
        this.maintainer = info.maintainer();
        this.modIcon = info.modIcon();
    }

    public void setBlankInfo(String name)
    {
        this.modName = name;
        this.version = "1.0";
        this.description = "This module has no information.";
        this.maintainer = "Unknown";
        this.modIcon = Material.BARRIER;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ZenConfig 
    {
        
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ModInfo
    {
        String modName();
        String version();
        Material modIcon();
        String description();
        String maintainer();
    }

    @SuppressWarnings("unchecked")
    public void loadConfig()
    {
        File file = new File("./plugins/Zenith/configs/" + this.modName + ".json");

        if (!file.exists())
        {
            if (!this.getClass().isAnnotationPresent(DefaultConfig.class))
            {
                PrintUtils.log("Module \"" + this.modName + "\" specifies a configuration file, but one doesn't exist nor is a default config specified!", InfoType.ERROR);
                return;
            }

            DefaultConfig defaultconfig = this.getClass().getAnnotation(DefaultConfig.class);
            Map<String, String> dc = new HashMap<>();

            for (int i = 0; i < defaultconfig.keys().length; i++)
                dc.put(defaultconfig.keys()[i], defaultconfig.values()[i]);

            JsonUtils.toJsonFile("./plugins/Zenith/configs/" + this.modName + ".json", dc);
        }

        this.config = (HashMap<String, String>) JsonUtils.fromJsonFile("./plugins/Zenith/configs/" + this.modName + ".json", HashMap.class);

        // Then do a quick double check to make sure that the config has all the expected values, specified in @DefaultConfig
        if (this.getClass().isAnnotationPresent(DefaultConfig.class))
        {
            if (this.config.size() < this.getClass().getAnnotation(DefaultConfig.class).keys().length)
            {
                DefaultConfig defaultconfig = this.getClass().getAnnotation(DefaultConfig.class);
                PrintUtils.log("Local config file is missing configuration values. Filling in from default config, please review these new values.", InfoType.WARN);

                for (int i = 0; i < defaultconfig.keys().length; i++)
                    if (!this.config.containsKey(defaultconfig.keys()[i]))
                        config.put(defaultconfig.keys()[i], defaultconfig.values()[i]);

                this.saveConfig();
            }
        }
    }

    public void saveConfig()
    {
        JsonUtils.toJsonFile("./plugins/Zenith/configs/" + this.modName + ".json", config);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface LoadBefore
    {
        String[] loadBefore();
    }
}
