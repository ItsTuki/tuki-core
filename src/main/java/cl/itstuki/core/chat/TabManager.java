package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.util.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Prefijo y sufijo del rango en el tab. Solo pisa el nombre si sigue siendo el que puso TukiCore
 * (o el normal): si otro plugin lo cambió (TukiUHC en partida, "???"), se respeta.
 */
public final class TabManager {
    private final TukiCore plugin;
    private final Map<UUID, Component> applied = new HashMap<>();

    public TabManager(TukiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 60L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("tab.rank-prefix", true);
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
        if (plugin.getConfig().getBoolean("tab.header-footer.enabled", false)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(
                        plugin.lang().format(String.join("<newline>", plugin.getConfig().getStringList("tab.header-footer.header")), player),
                        plugin.lang().format(String.join("<newline>", plugin.getConfig().getStringList("tab.header-footer.footer")), player));
            }
        }
    }

    public void refresh(Player player) {
        if (!enabled()) {
            return;
        }
        Component current = player.playerListName();
        Component last = applied.get(player.getUniqueId());
        boolean ours = last == null ? current.equals(Component.text(player.getName())) : current.equals(last);
        if (!ours && !current.equals(Component.text(player.getName()))) {
            return; // Otro plugin maneja el nombre ahora mismo.
        }
        Component wanted = Text.legacy(plugin.ranks().prefix(player))
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Text.legacy(plugin.ranks().suffix(player)));
        if (!wanted.equals(current)) {
            player.playerListName(wanted);
        }
        applied.put(player.getUniqueId(), player.playerListName());
    }

    public void forget(Player player) {
        applied.remove(player.getUniqueId());
    }
}
