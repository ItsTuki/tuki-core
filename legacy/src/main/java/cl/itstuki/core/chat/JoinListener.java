package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Mensajes de entrada y salida con el rango, y nombre del tab al entrar. */
public final class JoinListener implements Listener {
    private final TukiCore plugin;

    public JoinListener(TukiCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.vanish().isVanished(player)) {
            event.setJoinMessage(null); // Entra en vanish sin avisar.
        } else if (plugin.getConfig().getBoolean("join-quit.enabled", true)) {
            String key = player.hasPlayedBefore() ? "join.message" : "join.first";
            event.setJoinMessage(plugin.lang().raw(key).isEmpty() ? null : Text.section(plugin.lang().format(plugin.lang().raw(key), player,
                    Lang.c("rank_prefix", Text.legacy(plugin.ranks().prefix(player))), Lang.p("player", player.getName()),
                    Lang.p("count", Bukkit.getOfflinePlayers().length))));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.tab().refresh(player);
                plugin.lang().sendList(player, "join.motd");
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.vanish().isVanished(player)) {
            event.setQuitMessage(null);
        } else if (plugin.getConfig().getBoolean("join-quit.enabled", true)) {
            String raw = plugin.lang().raw("join.quit");
            event.setQuitMessage(raw.isEmpty() ? null : Text.section(plugin.lang().format(raw, player,
                    Lang.c("rank_prefix", Text.legacy(plugin.ranks().prefix(player))), Lang.p("player", player.getName()))));
        }
        plugin.messages().forget(player);
    }
}
