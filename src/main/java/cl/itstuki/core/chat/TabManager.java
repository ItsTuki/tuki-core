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
        nametag(player);
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
        org.bukkit.scoreboard.Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
        if (team != null && team.getName().startsWith("tc_")) {
            team.removeEntry(player.getName());
        }
    }

    /**
     * Prefijo del rango sobre la cabeza (equipos del scoreboard principal, ordenados por peso para el tab).
     * Con TukiUHC no se hace: él usa scoreboards por jugador y pone el rango en sus propios equipos.
     */
    private void nametag(Player player) {
        if (!plugin.getConfig().getBoolean("tab.nametag-prefix", true)
                || Bukkit.getPluginManager().isPluginEnabled("TukiUHC")) {
            return;
        }
        org.bukkit.scoreboard.Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team current = main.getEntryTeam(player.getName());
        if (current != null && !current.getName().startsWith("tc_")) {
            return; // Está en un equipo de otro plugin o de /team.
        }
        String prefix = plugin.ranks().prefix(player);
        int weight = Math.max(0, Math.min(9999, plugin.ranks().rankOf(player).weight()));
        String id = "tc_" + String.format("%04d", 9999 - weight) + "_" + Integer.toHexString(prefix.hashCode());
        org.bukkit.scoreboard.Team team = main.getTeam(id);
        if (team == null) {
            team = main.registerNewTeam(id);
            team.prefix(Text.legacy(prefix));
            team.color(NamedTextColor.WHITE);
        }
        if (current != team) {
            team.addEntry(player.getName());
            if (current != null && current.getEntries().isEmpty()) {
                current.unregister();
            }
        }
    }

    /** Al apagar: quitar los equipos de rango del scoreboard principal. */
    public void cleanup() {
        for (org.bukkit.scoreboard.Team team : java.util.Set.copyOf(Bukkit.getScoreboardManager().getMainScoreboard().getTeams())) {
            if (team.getName().startsWith("tc_")) {
                team.unregister();
            }
        }
    }
}
