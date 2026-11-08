package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /msg, /r y espía de mensajes privados. */
public final class PrivateMessages {
    private final TukiCore plugin;
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    public PrivateMessages(TukiCore plugin) {
        this.plugin = plugin;
    }

    public boolean toggleSpy(UUID id) {
        if (!spies.remove(id)) {
            spies.add(id);
            return true;
        }
        return false;
    }

    public void send(Player from, Player to, String message) {
        if (!plugin.vanish().canSee(from, to)) {
            plugin.lang().send(from, "core.unknown-player", Lang.p("player", to.getName()));
            return;
        }
        if (from.equals(to)) {
            plugin.lang().send(from, "msg.self");
            return;
        }
        if (!from.hasPermission("tukicore.mute.bypass") && plugin.mutes().mute(from.getUniqueId()) != null) {
            plugin.lang().send(from, "msg.muted");
            return;
        }
        plugin.lang().send(from, "msg.to", Lang.p("player", to.getName()), Lang.p("message", message));
        plugin.lang().send(to, "msg.from", Lang.p("player", from.getName()), Lang.p("message", message));
        lastPartner.put(from.getUniqueId(), to.getUniqueId());
        lastPartner.put(to.getUniqueId(), from.getUniqueId());
        for (UUID spy : spies) {
            Player p = Bukkit.getPlayer(spy);
            if (p != null && !p.equals(from) && !p.equals(to)) {
                plugin.lang().send(p, "msg.spy", Lang.p("from", from.getName()), Lang.p("to", to.getName()), Lang.p("message", message));
            }
        }
    }

    public void reply(Player from, String message) {
        UUID partner = lastPartner.get(from.getUniqueId());
        Player to = partner == null ? null : Bukkit.getPlayer(partner);
        if (to == null) {
            plugin.lang().send(from, "msg.no-reply");
            return;
        }
        send(from, to, message);
    }

    public void forget(CommandSender sender) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            lastPartner.remove(p.getUniqueId());
            spies.remove(p.getUniqueId());
        }
    }
}
