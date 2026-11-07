package cl.itstuki.core.staff;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.jspecify.annotations.Nullable;

/**
 * Con qué cliente entra cada jugador: la "marca" que envía (vanilla, fabric, forge, lunarclient…) y los
 * canales de red que registran sus mods (voicechat, xaeroworldmap…). Un servidor Paper no recibe la lista
 * de mods: los canales son la mejor pista disponible. Un cliente de hacks puede disfrazarse de vanilla, así
 * que esto ayuda a sospechar, no prueba nada.
 */
public final class ClientCheck implements Listener {
    public record Info(String brand, String loader, Set<String> mods, List<String> flags) {
        public boolean suspicious() {
            return !flags.isEmpty();
        }
    }

    private static final Set<String> IGNORED = Set.of("minecraft", "bungeecord", "velocity", "c", "brigadier", "vv");
    private final TukiCore plugin;
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> alerted = new ConcurrentHashMap<>();

    public ClientCheck(TukiCore plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("client-check.enabled", true);
    }

    public Info inspect(Player player) {
        String brand = player.getClientBrandName();
        String rawBrand = brand == null ? "" : brand;
        Set<String> mods = new TreeSet<>();
        boolean fabricChannels = false;
        boolean forgeChannels = false;
        for (String channel : player.getListeningPluginChannels()) {
            String namespace = channel.contains(":") ? channel.substring(0, channel.indexOf(':')) : channel;
            namespace = namespace.toLowerCase(Locale.ROOT);
            if (namespace.startsWith("fabric")) {
                fabricChannels = true;
                namespace = "fabric-api";
            } else if (namespace.equals("forge") || namespace.equals("fml") || namespace.equals("neoforge")) {
                forgeChannels = true;
                continue;
            }
            if (!IGNORED.contains(namespace)) {
                mods.add(namespace);
            }
        }
        String loader = loader(rawBrand);
        List<String> flags = new ArrayList<>();
        String lowerBrand = rawBrand.toLowerCase(Locale.ROOT);
        if (lowerBrand.equals("vanilla") && (fabricChannels || forgeChannels)) {
            // Dice ser vanilla pero tiene canales de un cargador de mods: está ocultando la marca.
            flags.add(plugin.lang().raw("client.flag-spoofed"));
        }
        if (rawBrand.isEmpty()) {
            flags.add(plugin.lang().raw("client.flag-no-brand"));
        }
        for (String keyword : plugin.getConfig().getStringList("client-check.suspicious")) {
            String key = keyword.toLowerCase(Locale.ROOT);
            if (key.isBlank()) {
                continue;
            }
            if (lowerBrand.contains(key)) {
                flags.add(plugin.lang().raw("client.flag-keyword") + " " + keyword);
            }
            for (String mod : mods) {
                if (mod.contains(key)) {
                    flags.add(plugin.lang().raw("client.flag-keyword") + " " + mod);
                }
            }
        }
        return new Info(rawBrand.isEmpty() ? "?" : rawBrand, loader, mods, flags);
    }

    private String loader(String brand) {
        String b = brand.toLowerCase(Locale.ROOT);
        if (b.isEmpty()) {
            return plugin.lang().raw("client.unknown");
        }
        if (b.contains("neoforge")) {
            return "NeoForge";
        }
        if (b.contains("forge") || b.contains("fml")) {
            return "Forge";
        }
        if (b.contains("quilt")) {
            return "Quilt";
        }
        if (b.contains("lunar")) {
            return "Lunar Client";
        }
        if (b.contains("badlion")) {
            return "Badlion";
        }
        if (b.contains("feather")) {
            return "Feather";
        }
        if (b.contains("labymod")) {
            return "LabyMod";
        }
        if (b.contains("fabric")) {
            return "Fabric";
        }
        if (b.equals("vanilla")) {
            return "Vanilla";
        }
        return brand;
    }

    /** Línea con el cliente del jugador; al pasar el mouse muestra los canales de mods. */
    public Component describe(Player player, Info info) {
        String mods = info.mods().isEmpty() ? plugin.lang().raw("client.no-mods") : String.join(", ", info.mods());
        Component hover = plugin.lang().get("client.hover", Lang.p("brand", info.brand()), Lang.p("mods", mods),
                Lang.p("flags", info.flags().isEmpty() ? "-" : String.join(", ", info.flags())));
        String key = info.suspicious() ? "client.alert-suspicious" : "client.alert";
        return plugin.lang().get(key, Lang.p("player", player.getName()), Lang.p("loader", info.loader()),
                Lang.p("brand", info.brand()), Lang.p("count", info.mods().size()), Lang.p("mods", shorten(mods)),
                Lang.p("flags", String.join(", ", info.flags()))).hoverEvent(HoverEvent.showText(hover));
    }

    private static String shorten(String text) {
        return text.length() <= 60 ? text : text.substring(0, 57) + "…";
    }

    public void show(CommandSender to, Player target) {
        Info info = inspect(target);
        to.sendMessage(describe(target, info));
        if (!info.mods().isEmpty()) {
            plugin.lang().send(to, "client.mods-line", Lang.p("mods", String.join(", ", info.mods())));
        }
    }

    private void alert(Player player, Info info, boolean onlySuspicious) {
        if (onlySuspicious && !info.suspicious()) {
            return;
        }
        boolean everyone = plugin.getConfig().getBoolean("client-check.alert-all-joins", true);
        if (!everyone && !info.suspicious()) {
            return;
        }
        Component line = describe(player, info);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("tukicore.clientcheck.alerts")) {
                staff.sendMessage(line);
            }
        }
        plugin.getLogger().info("Cliente de " + player.getName() + ": " + info.loader() + " (marca \"" + info.brand() + "\")"
                + (info.mods().isEmpty() ? "" : " | canales: " + String.join(", ", info.mods()))
                + (info.suspicious() ? " | SOSPECHOSO: " + String.join(", ", info.flags()) : ""));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled()) {
            return;
        }
        Player player = event.getPlayer();
        joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
        // La marca y los canales llegan un momento después de entrar.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Info info = inspect(player);
                alerted.put(player.getUniqueId(), ConcurrentHashMap.newKeySet());
                alerted.get(player.getUniqueId()).addAll(info.flags());
                alert(player, info, false);
            }
        }, 20L * Math.max(1, plugin.getConfig().getInt("client-check.delay-seconds", 3)));
    }

    /** Un mod que registra su canal más tarde (al abrir un menú, por ejemplo) también se revisa. */
    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        Player player = event.getPlayer();
        Long joined = joinedAt.get(player.getUniqueId());
        Set<String> already = alerted.get(player.getUniqueId());
        if (!enabled() || joined == null || already == null || System.currentTimeMillis() - joined < 5000) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Info info = inspect(player);
            List<String> fresh = info.flags().stream().filter(f -> !already.contains(f)).toList();
            if (!fresh.isEmpty()) {
                already.addAll(fresh);
                alert(player, info, true);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        joinedAt.remove(event.getPlayer().getUniqueId());
        alerted.remove(event.getPlayer().getUniqueId());
    }

    public @Nullable String loaderOf(Player player) {
        return inspect(player).loader();
    }
}
