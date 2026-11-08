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
        net.kyori.adventure.text.Component name = sender instanceof Player
                ? Text.legacy(plugin.ranks().prefix((Player) sender)).append(net.kyori.adventure.text.Component.text(sender.getName()))
                : net.kyori.adventure.text.Component.text("Consola");
        net.kyori.adventure.text.Component line = plugin.lang().get("staffchat.format", Lang.c("player", name), Lang.p("message", message));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("tukicore.staffchat")) {
                plugin.msg(staff, line);
            }
        }
        plugin.msg(Bukkit.getConsoleSender(), line);
    }
}
