package cl.itstuki.core.rank;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

/** Rangos guardados por TukiCore (players.yml) y permisos con PermissionAttachment. */
final class InternalBackend implements RankBackend, Listener {
    private final TukiCore plugin;
    private final RankManager ranks;
    private final File file;
    private YamlConfiguration data;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private BukkitTask expiryTask;

    InternalBackend(TukiCore plugin, RankManager ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    @Override
    public String name() {
        return "TukiCore";
    }

    @Override
    public void enable() {
        data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiry, 200L, 200L);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (expiryTask != null) {
            expiryTask.cancel();
        }
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.removeAttachment(entry.getValue());
            }
        }
        attachments.clear();
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar players.yml: " + ex.getMessage());
        }
    }

    private String path(UUID id) {
        return "players." + id;
    }

    private Assignment assignment(UUID id) {
        String rank = data.getString(path(id) + ".rank", ranks.defaultRank());
        long expires = data.getLong(path(id) + ".expires", 0);
        if (ranks.rank(rank) == null) {
            return new Assignment(ranks.defaultRank(), 0);
        }
        return new Assignment(rank, expires);
    }

    @Override
    public String rankOf(Player player) {
        return assignment(player.getUniqueId()).rank();
    }

    @Override
    public CompletableFuture<Assignment> lookup(UUID player) {
        return CompletableFuture.completedFuture(assignment(player));
    }

    @Override
    public CompletableFuture<Void> setRank(UUID id, String playerName, String rank, long seconds) {
        if (rank.equals(ranks.defaultRank()) && seconds == 0) {
            data.set(path(id), null);
        } else {
            data.set(path(id) + ".name", playerName);
            data.set(path(id) + ".rank", rank);
            data.set(path(id) + ".expires", seconds > 0 ? System.currentTimeMillis() / 1000 + seconds : 0);
        }
        save();
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            refresh(online);
            plugin.tab().refresh(online);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void rankChanged(Rank rank) {
    }

    @Override
    public void rankDeleted(String rank) {
        var section = data.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (rank.equals(section.getString(key + ".rank"))) {
                section.set(key, null);
            }
        }
        save();
    }

    @Override
    public void refresh(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            player.removeAttachment(old);
        }
        Rank rank = ranks.rankOf(player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        ranks.permissions(rank).forEach(attachment::setPermission);
        attachments.put(player.getUniqueId(), attachment);
        player.updateCommands();
    }

    private void checkExpiry() {
        long now = System.currentTimeMillis() / 1000;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Assignment a = assignment(player.getUniqueId());
            if (a.expires() > 0 && a.expires() <= now) {
                String expired = a.rank();
                setRank(player.getUniqueId(), player.getName(), ranks.defaultRank(), 0);
                plugin.lang().send(player, "rank.expired", Lang.p("rank", expired));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Assignment a = assignment(player.getUniqueId());
        if (a.expires() > 0 && a.expires() <= System.currentTimeMillis() / 1000) {
            data.set(path(player.getUniqueId()), null);
            save();
        }
        refresh(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = attachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            event.getPlayer().removeAttachment(attachment);
        }
    }
}
