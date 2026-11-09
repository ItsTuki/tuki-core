package cl.itstuki.core.staff;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * /invsee: abre el inventario real de un jugador (o su cofre de ender). Con tukicore.invsee.edit los
 * cambios son en vivo; sin él, solo se puede mirar.
 */
public final class InventoryViewer implements Listener {
    private final TukiCore plugin;
    /** Quién está mirando el inventario de quién, y si puede editarlo. */
    private final Map<UUID, Boolean> viewing = new HashMap<>();

    public InventoryViewer(TukiCore plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Player target, boolean ender) {
        if (viewer.equals(target) && !ender) {
            plugin.lang().send(viewer, "invsee.self");
            return;
        }
        boolean edit = viewer.hasPermission("tukicore.invsee.edit")
                && !(target.hasPermission("tukicore.invsee.exempt") && !viewer.hasPermission("tukicore.invsee.exempt"));
        Inventory inventory = ender ? target.getEnderChest() : target.getInventory();
        viewer.openInventory(inventory);
        viewing.put(viewer.getUniqueId(), edit);
        plugin.lang().send(viewer, edit ? "invsee.opened-edit" : "invsee.opened-view",
                Lang.p("player", target.getName()), Lang.c("type", plugin.lang().get(ender ? "invsee.ender" : "invsee.inventory")));
    }

    private boolean readOnly(HumanEntity who) {
        Boolean edit = viewing.get(who.getUniqueId());
        return edit != null && !edit;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (readOnly(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (readOnly(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        viewing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        viewing.remove(id);
        // Si se va el jugador mirado, se cierra la ventana de quienes lo estaban viendo.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewing.containsKey(viewer.getUniqueId())
                    && (viewer.getOpenInventory().getTopInventory().getHolder() == event.getPlayer())) {
                viewer.closeInventory();
            }
        }
    }
}
