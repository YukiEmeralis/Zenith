package coffee.khyonieheart.eden.flock.repository;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import com.google.gson.annotations.Expose;

import coffee.khyonieheart.eden.Eden;
import coffee.khyonieheart.eden.flock.DownloadFinishedThread;
import coffee.khyonieheart.eden.flock.DownloadUtils;
import coffee.khyonieheart.eden.flock.Flock;
import coffee.khyonieheart.eden.flock.TextUtils;
import coffee.khyonieheart.eden.flock.gui.EditRepositoryEntryGui;
import coffee.khyonieheart.eden.flock.gui.RepositoryGui;
import coffee.khyonieheart.eden.surface.GuiUtils;
import coffee.khyonieheart.eden.surface.SimpleComponentBuilder;
import coffee.khyonieheart.eden.surface.SurfaceGui;
import coffee.khyonieheart.eden.surface.component.GuiComponent;
import coffee.khyonieheart.eden.surface.component.GuiItemStack;
import coffee.khyonieheart.eden.utils.PrintUtils;
import net.md_5.bungee.api.ChatColor;

public class ModuleRepositoryEntry implements GuiComponent
{
    @Expose
    private String 
        name,
        description,
        url,
        version,
        author;

    @Expose
    private List<String> dependencies; // Dependency list, with each entry formatted as "repository:module"

    @Expose
    private double timestamp;

    private ModuleRepository host;

    @Override
    public GuiItemStack generate()
    {
        if (this.dependencies == null) 
            this.dependencies = new ArrayList<>();

        return SimpleComponentBuilder.build(Material.BOOK, "§9§l" + name, (event) -> {
            switch (event.getAction())
            {
                case PICKUP_ALL -> { // Update
                    if (!this.isInstalled())
                    { 
                        event.getWhoClicked().closeInventory();
                        // Fresh install
                        try {
                            DownloadUtils.downloadFile(this.url, "./plugins/Eden/mods/" + this.name + ".jar", new DownloadFinishedThread() {
                                @Override
                                public void run() 
                                {
                                    if (this.failed())
                                    {
                                        PrintUtils.sendMessage(event.getWhoClicked(), "§cDownload failed. Reason: " + this.getFailureException().getClass().getSimpleName() + ":" + this.getFailureException().getMessage());
                                        return;
                                    }    

                                    PrintUtils.sendMessage(event.getWhoClicked(), "§aDownload complete.");

                                    Bukkit.getScheduler().runTask(Eden.getInstance(), () -> new RepositoryGui(getHostRepository(), event.getWhoClicked()).display(event.getWhoClicked()));
                                }
                            });
                        } catch (MalformedURLException e) {
                            PrintUtils.sendMessage(event.getWhoClicked(), "§cThis module's URL is corrupt! Cannot update.");
                        }
                        return;
                    }

                    // Update
                    if (!this.canUpdate())
                    {
                        PrintUtils.sendMessage(event.getWhoClicked(), "This module is up to date.");
                        return;
                    }

                    boolean enabled = Eden.getModuleManager().getModuleByName(this.name).getIsEnabled();
                    try {
                        DownloadUtils.downloadFile(this.url, "./plugins/Eden/mods/" + this.name + ".jar", new DownloadFinishedThread() {
                            @Override
                            public void run() 
                            {
                                if (this.failed())
                                {
                                    PrintUtils.sendMessage(event.getWhoClicked(), "§cDownload failed. Reason: " + this.getFailureException().getClass().getSimpleName() + ":" + this.getFailureException().getMessage());
                                    return;
                                }    

                                PrintUtils.sendMessage(event.getWhoClicked(), "§aDownload complete.");
                                if (!Eden.getModuleManager().forceReload(name, enabled, true, false))
                                {
                                    PrintUtils.sendMessage(event.getWhoClicked(), "Failed to automatically reload " + getName() + ". See console for details.");
                                    return;
                                }

                                Flock.updateEntrySyncTime(getReference());
                                Bukkit.getScheduler().runTask(Eden.getInstance(), () -> new RepositoryGui(getHostRepository(), event.getWhoClicked()).display(event.getWhoClicked()));

                                if (Eden.getModuleManager().getModuleByName(getName()).getIsEnabled())
                                    PrintUtils.sendMessage(event.getWhoClicked(), "Successfully enabled " + getName() + "!");
                            }
                        });
                    } catch (MalformedURLException e) {
                        PrintUtils.sendMessage(event.getWhoClicked(), "§cThis module's URL is corrupt! Cannot update.");
                    }
                }
                case PICKUP_HALF -> { // Edit
                    new EditRepositoryEntryGui(this, event.getWhoClicked()).display(event.getWhoClicked());
                }
                case MOVE_TO_OTHER_INVENTORY -> { // Remove
                    this.host.removeEntry(this);
                    SurfaceGui.getOpenGui(event.getWhoClicked()).unwrap(SurfaceGui.class).updateSingleItem(event.getWhoClicked(), event.getSlot(), GuiUtils.BLACK_PANE, false);
                }
                default -> { return; }
            }
        },
            "§7§o" + TextUtils.pruneStringLength(this.url == null ? "§cNo URL set ⚠" : this.url, "...", 35),
            "§7§o" + TextUtils.pruneStringLength(this.description == null ? "No description set" : this.description, "...", 35),
            "§7§o" + TextUtils.pruneStringLength(this.author == null ? "No author set" : this.author, "...", 35),
            "§7§o" + TextUtils.pruneStringLength(this.version == null ? "No version set" : this.version, "...", 35),
            "§7§o" + dependencies.size() + PrintUtils.plural(dependencies.size(), " dependency", " dependencies"),
            "",
            "§7" + (this.isInstalled() ? (this.canUpdate() ? "§9Left-click to update module." : "§aThis module is up to date.") : "§aLeft-click to install module."),
            "§7Right-click to edit entry.",
            "§7Shift + Left-click to remove entry."
        );
    }

    /**
     * Sets this entry's name.
     * @param name New name to be set
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * Sets this entry's description.
     * @param description New description to be set
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * Sets this entry's URL.
     * @param url New URL to be set
     */
    public void setUrl(String url)
    {
        this.url = url;
    }

    /**
     * Sets this entry's version.
     * @param version
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public void updateTimestamp()
    {
        this.timestamp = System.currentTimeMillis();
        Flock.updateEntrySyncTime(this);
    }

    public String getName()
    {
        return this.name;
    }

    public String getDescription()
    {
        return ChatColor.stripColor(this.description);
    }

    public String getUrl()
    {
        return this.url;
    }

    public String getVersion()
    {
        return this.version;
    }

    public String getAuthor()
    {
        return this.author;
    }

    public double getTimestamp()
    {
        return this.timestamp;
    }

    public ModuleRepository getHostRepository()
    {
        return this.host;
    }

    public void addDependency(String dependency)
    {
        if (this.dependencies == null)
            this.dependencies = new ArrayList<>();
        this.dependencies.add(dependency);
    }

    public List<String> getDependencies()
    {
        if (this.dependencies == null)
            this.dependencies = new ArrayList<>();
        return Collections.unmodifiableList(dependencies);
    }

    public String removeBottomDependency()
    {
        if (this.dependencies == null)
            this.dependencies = new ArrayList<>();

        if (dependencies.size() == 0)
            return null;
        return this.dependencies.remove(this.dependencies.size() - 1);
    }

    public void attachHost(ModuleRepository repo)
    {
        this.host = repo;
    }

    public void setProperty(RepositoryEntryProperty property, String value)
    {
        switch (property)
        {
            case AUTHOR -> setAuthor(value);
            case DESCRIPTION -> setDescription(value);
            case NAME -> setName(value);
            case URL -> setUrl(value);
            case VERSION -> setVersion(value);
        }
    }

    public boolean isInstalled()
    {
        return Eden.getModuleManager().isModulePresent(this.getName());
    }

    /**
     * Gets whether or not this entry's timestamp is greater than the last known sync timestamp.
     * An entry with a timestamp greater than the last sync time (if any) is assumed to be able to update.
     * @return Whether or not the associated module can update
     */
    public boolean canUpdate()
    {
        if (!Flock.hasEntrySyncTime(this))
            return true;

        return this.timestamp > Flock.getEntrySyncTime(this);
    }

    /**
     * Verifies a repository's validity. A valid repository must not contain any null fields.
     * @return Whether or not this repository is valid.
     */
    public boolean isValid()
    {
        for (Field f : this.getClass().getDeclaredFields())
        {
            f.setAccessible(true);
            try {
                if (f.get(this) == null)
                    return false;
            } catch (IllegalAccessException e) {
                continue;
            } finally {
                f.setAccessible(false);   
            }
        }

        return true;
    }

    private ModuleRepositoryEntry getReference()
    {
        return this;
    }

    public static enum RepositoryEntryProperty
    {
        NAME,
        DESCRIPTION,
        AUTHOR,
        VERSION,
        URL
    }
}