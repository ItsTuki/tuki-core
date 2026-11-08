package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.util.Compat;
import cl.itstuki.core.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Prefijo y sufijo del rango en el tab. Solo pisa el nombre si sigue siendo el que puso TukiCore
 * (o el normal): si otro plugin lo cambió (TukiUHC en partida, "???"), se respeta.
 */
public final class TabManager {
    private final TukiCore plugin;
    private final Map<UUID, String> applied = new HashMap<>();

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
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
        if (plugin.getConfig().getBoolean("tab.header-footer.enabled", false)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.adventure().player(player).sendPlayerListHeaderAndFooter(
                        plugin.lang().format(String.join("<newline>", plugin.getConfig().getStringList("tab.header-footer.header")), player),
                        plugin.lang().format(String.join("<newline>", plugin.getConfig().getStringList("tab.header-footer.footer")), player));
            }
        }
    }

    private static boolean plainName(Player player, String listed) {
        return listed == null || listed.equals(player.getName()) || listed.equals("§f" + player.getName());
    }

    public void refresh(Player player) {
        nametag(player);
        if (!enabled()) {
            return;
        }
        String current = player.getPlayerListName();
        String last = applied.get(player.getUniqueId());
        boolean ours = last == null ? plainName(player, current) : last.equals(current);
        if (!ours && !plainName(player, current)) {
            return; // Otro plugin maneja el nombre ahora mismo.
        }
        String wanted = Text.section(plugin.ranks().prefix(player)) + "§f" + player.getName()
                + Text.section(plugin.ranks().suffix(player));
        if (!wanted.equals(current)) {
            try {
                player.setPlayerListName(wanted);
            } catch (IllegalArgumentException tooLong) {
                // Versiones con límite de largo en el tab: sin el sufijo.
                player.setPlayerListName(Text.cut(Text.section(plugin.ranks().prefix(player)) + "§f" + player.getName(), 16));
            }
        }
        applied.put(player.getUniqueId(), player.getPlayerListName());
    }

    public void forget(Player player) {
        applied.remove(player.getUniqueId());
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
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
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team current = main.getEntryTeam(player.getName());
        if (current != null && !current.getName().startsWith("tc_")) {
            return; // Está en un equipo de otro plugin o de /team.
        }
        String prefix = plugin.ranks().prefix(player);
        int weight = Math.max(0, Math.min(9999, plugin.ranks().rankOf(player).weight()));
        // Antes de 1.13 los nombres de equipo tienen máximo 16 caracteres.
        String id = "tc_" + String.format("%04d", 9999 - weight) + "_" + Integer.toHexString(prefix.hashCode());
        id = id.substring(0, Math.min(16, id.length()));
        Team team = main.getTeam(id);
        if (team == null) {
            team = main.registerNewTeam(id);
            String legacy = Text.section(prefix);
            team.setPrefix(Compat.atLeast(13) ? Text.cut(legacy, 64) : Text.cut(legacy, 14) + "§f");
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
        for (Team team : new ArrayList<>(Bukkit.getScoreboardManager().getMainScoreboard().getTeams())) {
            if (team.getName().startsWith("tc_")) {
                team.unregister();
            }
        }
    }
}
