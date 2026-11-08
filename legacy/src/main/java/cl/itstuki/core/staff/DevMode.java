package cl.itstuki.core.staff;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import cl.itstuki.core.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

/**
 * Modo dev: solo para las cuentas premium de {@code dev-mode.players} (por UUID de Mojang, no por nombre:
 * en un servidor no premium cualquiera puede ponerse el mismo nombre, pero no un UUID premium).
 * Desactivado por defecto: cada servidor decide si lo usa. No viene con OP y nadie más lo puede activar.
 *
 * Al activarlo: permisos de desarrollo (tukiuhc.dev, /uhcdev), barra con TPS/MSPT/RAM y los avisos y
 * errores de la consola en el chat.
 */
public final class DevMode implements Listener {
    private static final List<String> PERMISSIONS = java.util.Arrays.asList("tukicore.dev", "tukiuhc.dev");
    private final TukiCore plugin;
    private final Map<UUID, PermissionAttachment> active = new HashMap<>();
    private final Handler consoleRelay = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() < Level.WARNING.intValue() || active.isEmpty()) {
                return;
            }
            String source = record.getLoggerName() == null || record.getLoggerName().isEmpty() ? "Servidor" : record.getLoggerName();
            String message = record.getMessage() == null ? "" : record.getMessage();
            if (record.getThrown() != null) {
                message += " — " + record.getThrown();
            }
            String text = message.length() > 300 ? message.substring(0, 297) + "…" : message;
            String key = record.getLevel().intValue() >= Level.SEVERE.intValue() ? "dev.console-error" : "dev.console-warning";
            Runnable send = () -> {
                for (UUID id : active.keySet()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        plugin.lang().send(player, key, Lang.p("source", source), Lang.p("message", text));
                    }
                }
            };
            if (Bukkit.isPrimaryThread()) {
                send.run();
            } else if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, send);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public DevMode(TukiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("dev-mode.enabled", false)) {
            return;
        }
        Logger.getLogger("").addHandler(consoleRelay);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active.isEmpty()) {
                return;
            }
            double tps = Compat.tps()[0];
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
            net.kyori.adventure.text.Component bar = plugin.lang().get("dev.actionbar", Lang.p("tps", String.format(Locale.ROOT, "%.1f", Math.min(20, tps))),
                    Lang.p("mspt", mspt("%.1f")),
                    Lang.p("ram", used), Lang.p("max", rt.maxMemory() / 1048576),
                    Lang.p("players", Bukkit.getOnlinePlayers().size()));
            for (UUID id : active.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    plugin.actionBar(player, bar);
                }
            }
        }, 20L, 20L);
        if (!Bukkit.getOnlineMode()) {
            plugin.getLogger().warning("Modo dev activo con online-mode=false: asegúrate de que el servidor solo sea accesible "
                    + "por tu proxy (Velocity/BungeeCord con online-mode) para que los UUID premium sean reales.");
        }
    }

    public void stop() {
        Logger.getLogger("").removeHandler(consoleRelay);
        for (UUID id : new java.util.ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                disable(player);
            }
        }
        active.clear();
    }

    /** ¿Esta cuenta puede usar el modo dev? Premium (UUID v4) y en la lista de config.yml. */
    public boolean allowed(CommandSender sender) {
        if (!(sender instanceof Player) || !plugin.getConfig().getBoolean("dev-mode.enabled", false)) {
            return false;
        }
        UUID id = ((Player) sender).getUniqueId();
        if (id.version() != 4) {
            return false; // Cuenta no premium (UUID generado a partir del nombre).
        }
        return plugin.getConfig().getStringList("dev-mode.players").stream()
                .anyMatch(entry -> entry.trim().equalsIgnoreCase(id.toString()));
    }

    public boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    public void toggle(Player player) {
        if (isActive(player)) {
            disable(player);
            plugin.lang().send(player, "dev.disabled");
        } else {
            PermissionAttachment attachment = player.addAttachment(plugin);
            PERMISSIONS.forEach(perm -> attachment.setPermission(perm, true));
            active.put(player.getUniqueId(), attachment);
            Compat.updateCommands(player);
            plugin.lang().sendList(player, "dev.enabled");
        }
        plugin.getLogger().info("Modo dev " + (isActive(player) ? "activado" : "desactivado") + " por " + player.getName()
                + " (" + player.getUniqueId() + ")");
    }

    private void disable(Player player) {
        PermissionAttachment attachment = active.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
            Compat.updateCommands(player);
        }
        plugin.actionBar(player, net.kyori.adventure.text.Component.empty());
    }

    public void info(CommandSender to) {
        Runtime rt = Runtime.getRuntime();
        StringBuilder plugins = new StringBuilder();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (plugins.length() > 0) {
                plugins.append(", ");
            }
            plugins.append(p.isEnabled() ? "" : "✗").append(p.getName()).append(' ').append(p.getDescription().getVersion());
        }
        plugin.lang().sendList(to, "dev.info",
                Lang.p("server", Bukkit.getName() + " " + Bukkit.getVersion()),
                Lang.p("mc", Compat.versionName()),
                Lang.p("java", System.getProperty("java.version")),
                Lang.p("online_mode", Bukkit.getOnlineMode()),
                Lang.p("tps", String.format(Locale.ROOT, "%.2f / %.2f / %.2f", Compat.tps()[0], Compat.tps()[1], Compat.tps()[2])),
                Lang.p("mspt", mspt("%.2f")),
                Lang.p("ram", (rt.totalMemory() - rt.freeMemory()) / 1048576), Lang.p("max", rt.maxMemory() / 1048576),
                Lang.p("players", Bukkit.getOnlinePlayers().size()),
                Lang.p("worlds", Bukkit.getWorlds().size()),
                Lang.p("backend", plugin.ranks().backend().name()),
                Lang.p("plugins", plugins.toString()));
    }

    private static String mspt(String format) {
        double mspt = Compat.mspt();
        return mspt < 0 ? "-" : String.format(Locale.ROOT, format, mspt);
    }

    /** ¿Tiene {@code target} el permiso? Útil para probar rangos. */
    public void checkPermission(CommandSender to, Player target, String permission) {
        plugin.lang().send(to, target.hasPermission(permission) ? "dev.perm-yes" : "dev.perm-no",
                Lang.p("player", target.getName()), Lang.p("permission", permission),
                Lang.p("set", target.isPermissionSet(permission)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disable(event.getPlayer());
    }
}
