package coffee.khyonieheart.eden.flock.gui;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryAction;

import coffee.khyonieheart.eden.flock.repository.ModuleRepository;
import coffee.khyonieheart.eden.surface.SimpleComponentBuilder;
import coffee.khyonieheart.eden.surface.component.GuiItemStack;
import coffee.khyonieheart.eden.surface.enums.DefaultClickAction;
import coffee.khyonieheart.eden.surface.special.PagedSurfaceGui;

public class RepositoryGui extends PagedSurfaceGui 
{
    private static GuiItemStack QUIT_BUTTON = SimpleComponentBuilder.build(Material.BARRIER, "§r§c§lClose", (event) -> event.getWhoClicked().closeInventory(), "§7§oExits this menu.");
    private static GuiItemStack BACK_BUTTON = SimpleComponentBuilder.build(Material.RED_CONCRETE, "§r§c§lBack", (event) -> new GlobalRepositoryGui(event.getWhoClicked()).display(event.getWhoClicked()), "§7§oReturns to the main menu.");

    public RepositoryGui(ModuleRepository repo, HumanEntity target) 
    {
        super(36, repo.getName(), target, 0, repo.getEntryList(), List.of(QUIT_BUTTON, BACK_BUTTON, generateEditGuiButton(repo)), DefaultClickAction.CANCEL, InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF, InventoryAction.MOVE_TO_OTHER_INVENTORY);
    }

    private static GuiItemStack generateEditGuiButton(ModuleRepository repo)
    {
        return SimpleComponentBuilder.build(
            Material.EMERALD, 
            "§r§e§lEdit repository", 
            (event) -> new RepositoryEditGui(repo, event.getWhoClicked()).display(event.getWhoClicked()),
            "§7§oOpens this repository in edit mode.",
            "",
            "§7§oThis tool is for repository authors,",
            "§7§oand can be safely ignored."
        );
    }
}
