package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.util.Text;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Chat privado del staff (/sc o mensajes que empiezan con staff-chat.prefix). */
public final class StaffChat {
    private final TukiCore plugin;
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public StaffChat(TukiCore plugin) {
        this.plugin = plugin;
    }

    public boolean toggled(UUID id) {
        return toggled.contains(id);
    }

    public boolean toggle(UUID id) {
        if (!toggled.remove(id)) {
            toggled.add(id);
            return true;
        }
        return false;
    }

    public void send(CommandSender sender, String message) {
        var name = sender instanceof Player p
                ? Text.legacy(plugin.ranks().prefix(p)).append(net.kyori.adventure.text.Component.text(p.getName()))
                : net.kyori.adventure.text.Component.text("Consola");
        var line = plugin.lang().get("staffchat.format", Lang.c("player", name), Lang.c("message", Text.legacy(message)));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("tukicore.staffchat")) {
                staff.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
