package cl.itstuki.core.staff;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.util.Compat;
import java.util.Iterator;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * /vanish: el staff se vuelve invisible para quien no tenga tukicore.vanish.see (también en el tab y en
 * la lista de servidores). Pone el metadata "vanished", el estándar que leen otros plugins (TukiUHC lo usa
 * para no meter en la partida a quien está en vanish).
 */
public final class VanishManager implements Listener {
    public static final String SEE = "tukicore.vanish.see";
    private final TukiCore plugin;
    private final File file;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(TukiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vanished.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        for (String id : data.getStringList("vanished")) {
            try {
                vanished.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // Línea inválida en vanished.yml.
            }
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isVanished(player)) {
                    plugin.actionBar(player, plugin.lang().get("vanish.actionbar"));
                }
            }
        }, 40L, 40L);
        // Tras un /reload o al activar el plugin con gente conectada.
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    public boolean isVanished(Player player) {
        return vanished.contains(player.getUniqueId());
    }

    public int count() {
        return (int) Bukkit.getOnlinePlayers().stream().filter(this::isVanished).count();
    }

    public List<Player> online() {
        return Bukkit.getOnlinePlayers().stream().filter(this::isVanished).map(p -> (Player) p).collect(java.util.stream.Collectors.toList());
    }

    /** Puede {@code viewer} ver a {@code target}? */
    public boolean canSee(org.bukkit.command.CommandSender viewer, Player target) {
        return !isVanished(target) || viewer.hasPermission(SEE) || viewer.equals(target);
    }

    public void set(Player player, boolean vanish) {
        if (vanish == isVanished(player)) {
            return;
        }
        if (vanish) {
            vanished.add(player.getUniqueId());
        } else {
            vanished.remove(player.getUniqueId());
        }
        save();
        apply(player);
        plugin.lang().send(player, vanish ? "vanish.enabled" : "vanish.disabled");
        if (!vanish) {
            plugin.actionBar(player, net.kyori.adventure.text.Component.empty());
        }
        // Aviso al staff y mensaje falso de salida/entrada para el resto.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && other.hasPermission(SEE)) {
                plugin.lang().send(other, vanish ? "vanish.staff-on" : "vanish.staff-off", Lang.p("player", player.getName()));
            }
        }
        if (plugin.getConfig().getBoolean("vanish.fake-messages", true) && plugin.getConfig().getBoolean("join-quit.enabled", true)) {
            net.kyori.adventure.text.Component message = plugin.lang().format(plugin.lang().raw(vanish ? "join.quit" : "join.message"), player,
                    Lang.c("rank_prefix", cl.itstuki.core.util.Text.legacy(plugin.ranks().prefix(player))),
                    Lang.p("player", player.getName()));
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission(SEE)) {
                    plugin.msg(other, message);
                }
            }
        }
    }

    /** Aplica el estado de vanish de {@code player} frente a todos los demás. */
    @SuppressWarnings("deprecation") // El metadata "vanished" es lo que leen los demás plugins.
    private void apply(Player player) {
        boolean vanish = isVanished(player);
        player.setMetadata("vanished", new FixedMetadataValue(plugin, vanish));
        Compat.setCollidable(player, !vanish);
        player.setSleepingIgnored(vanish);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (vanish && !other.hasPermission(SEE)) {
                Compat.hide(other, plugin, player);
            } else {
                Compat.show(other, plugin, player);
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("vanished", vanished.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList()));
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar vanished.yml: " + ex.getMessage());
        }
    }

    // ── Eventos ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Quien estaba en vanish vuelve en vanish (si aún tiene el permiso).
        if (isVanished(player) && !player.hasPermission("tukicore.vanish")) {
            vanished.remove(player.getUniqueId());
            save();
        }
        apply(player);
        if (!player.hasPermission(SEE)) {
            for (Player other : online()) {
                if (!other.equals(player)) {
                    Compat.hide(player, plugin, other);
                }
            }
        }
        if (isVanished(player)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.lang().send(player, "vanish.still"), 20L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player && isVanished((Player) event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** Recoger ítems: EntityPickupItemEvent desde 1.12, PlayerPickupItemEvent antes. */
    public void registerPickup() {
        Class<? extends Event> type = eventClass("org.bukkit.event.entity.EntityPickupItemEvent");
        if (type == null) {
            type = eventClass("org.bukkit.event.player.PlayerPickupItemEvent");
        }
        if (type == null) {
            return;
        }
        EventExecutor executor = (listener, event) -> {
            Object entity = event instanceof EntityEvent ? ((EntityEvent) event).getEntity()
                    : event instanceof org.bukkit.event.player.PlayerEvent ? ((org.bukkit.event.player.PlayerEvent) event).getPlayer() : null;
            if (entity instanceof Player && isVanished((Player) entity) && event instanceof Cancellable
                    && !plugin.getConfig().getBoolean("vanish.pickup-items", false)) {
                ((Cancellable) event).setCancelled(true);
            }
        };
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.NORMAL, executor, plugin, true);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventClass(String name) {
        try {
            return (Class<? extends Event>) Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent event) {
        // Sin activar placas de presión, cuerdas de trampa ni pisar cultivos.
        if (event.getAction() == Action.PHYSICAL && isVanished(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && isVanished((Player) event.getEntity())
                && plugin.getConfig().getBoolean("vanish.god-mode", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (count() == 0) {
            return;
        }
        // Quitar de la lista (y del contador) a quienes están en vanish.
        try {
            Iterator<Player> it = event.iterator();
            while (it.hasNext()) {
                if (isVanished(it.next())) {
                    it.remove();
                }
            }
        } catch (UnsupportedOperationException ignored) {
            // Este servidor no permite ocultar jugadores en la lista.
        }
    }
}
