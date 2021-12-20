package com.yukiemeralis.blogspot.modules.zenithgui;

import java.util.HashMap;

import com.yukiemeralis.blogspot.modules.zenithgui.special.GuiTab;
import com.yukiemeralis.blogspot.modules.zenithgui.special.PagedDynamicGui;
import com.yukiemeralis.blogspot.modules.zenithgui.special.TabbedDynamicGui;
import com.yukiemeralis.blogspot.zenith.module.ZenithModule;
import com.yukiemeralis.blogspot.zenith.module.ZenithModule.ModInfo;
import com.yukiemeralis.blogspot.zenith.module.java.enums.CallerToken;
import com.yukiemeralis.blogspot.zenith.module.java.enums.PreventUnload;

import org.bukkit.Material;

@ModInfo(
    modName = "ZenithGui",
    description = "Centralized system for static and dynamic inventory GUIs.",
    modFamily = "Zenith base modules",
    version = "1.0",
    maintainer = "Yuki_emeralis",
    modIcon = Material.CHEST,
    supportedApiVersions = {"v1_16_R3", "v1_17_R1", "v1_18_R1"}
)
@PreventUnload(CallerToken.ZENITH)
public class GuiModule extends ZenithModule
{
    public GuiModule() 
    {
        addListener(
            new PagedDynamicGui(0, "Invalid GUI", null, 0, null),
            new TabbedDynamicGui(0, "Invalid GUI", null, new HashMap<String, GuiTab>(), null)
        );
    }

    @Override
    public void onEnable() 
    {
        
    }

    @Override
    public void onDisable() 
    {
        
    }
}