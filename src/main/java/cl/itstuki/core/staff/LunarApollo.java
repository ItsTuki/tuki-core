package cl.itstuki.core.staff;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.Messenger;

/**
 * Integración con Lunar Client (Apollo, la API oficial de Lunar para servidores) por su canal JSON, sin
 * plugins extra. Permite al staff usar los mods de staff de Lunar (XRay, nametags a través de paredes y bhop)
 * y le manda notificaciones nativas de Lunar con las alertas de clientes.
 */
public final class LunarApollo implements Listener {
    /** Canal que registra Lunar Client al entrar: así se sabe que es Lunar con Apollo. */
    public static final String DETECT_CHANNEL = "lunar:apollo";
    /** Canal por el que van los mensajes JSON de Apollo. */
    public static final String JSON_CHANNEL = "apollo:json";
    private static final String TYPE = "type.googleapis.com/lunarclient.apollo.";

    /** Mods de staff de Lunar (el número es el que usa Apollo). */
    public enum StaffMod {
        XRAY(1), BHOP(2), NAMETAGS(3);

        private final int id;

        StaffMod(int id) {
            this.id = id;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final TukiCore plugin;
    private final Set<UUID> apollo = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffOn = ConcurrentHashMap.newKeySet();
    private boolean registered;

    public LunarApollo(TukiCore plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("lunar.enabled", true);
    }

    public void register() {
        if (!enabled() || registered) {
            return;
        }
        Messenger messenger = Bukkit.getMessenger();
        try {
            messenger.registerIncomingPluginChannel(plugin, DETECT_CHANNEL, (channel, player, data) -> { });
            messenger.registerOutgoingPluginChannel(plugin, JSON_CHANNEL);
            registered = true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("No se pudo activar la integración con Lunar Client: " + ex.getMessage());
        }
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        Messenger messenger = Bukkit.getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, DETECT_CHANNEL);
        messenger.unregisterOutgoingPluginChannel(plugin, JSON_CHANNEL);
        registered = false;
        apollo.clear();
        staffOn.clear();
    }

    public boolean isLunar(Player player) {
        return apollo.contains(player.getUniqueId());
    }

    public boolean staffModsOn(Player player) {
        return staffOn.contains(player.getUniqueId());
    }

    /** Lista de jugadores con Lunar (Apollo) conectados. */
    public List<Player> lunarPlayers() {
        List<Player> list = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isLunar(player)) {
                list.add(player);
            }
        }
        return list;
    }

    private void send(Player player, JsonObject message) {
        if (!registered || !isLunar(player)) {
            return;
        }
        player.sendPluginMessage(plugin, JSON_CHANNEL, message.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ── Al entrar ────────────────────────────────────────────

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        if (!registered || !event.getChannel().equalsIgnoreCase(DETECT_CHANNEL)) {
            return;
        }
        final Player player = event.getPlayer();
        apollo.add(player.getUniqueId());
        // Un tick después: el cliente registra apollo:json en el mismo paquete y Bukkit descarta lo que llegue antes.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                enableModules(player);
                sendWorld(player);
            }
        });
        // Un momento después (ya con permisos y vanish cargados) se activan los mods de staff si corresponde.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            boolean auto = plugin.getConfig().getBoolean("lunar.staff-mods.auto-enable", false)
                    || (plugin.getConfig().getBoolean("lunar.staff-mods.with-vanish", true) && plugin.vanish().isVanished(player));
            if (auto && player.hasPermission("tukicore.lunar.staff")) {
                setStaffMods(player, true);
            }
        }, 40L);
    }

    /** Apollo exige activar los módulos antes de usarlos. */
    private void enableModules(Player player) {
        JsonArray settings = new JsonArray();
        for (String module : new String[] {"staff_mod", "notification"}) {
            JsonObject enable = new JsonObject();
            enable.addProperty("apollo_module", module);
            enable.addProperty("enable", true);
            settings.add(enable);
        }
        JsonObject message = new JsonObject();
        message.addProperty("@type", TYPE + "configurable.v1.OverrideConfigurableSettingsMessage");
        message.add("configurable_settings", settings);
        send(player, message);
    }

    private void sendWorld(Player player) {
        JsonObject message = new JsonObject();
        message.addProperty("@type", TYPE + "player.v1.UpdatePlayerWorldMessage");
        message.addProperty("world", player.getWorld().getName());
        send(player, message);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        if (isLunar(event.getPlayer())) {
            sendWorld(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        apollo.remove(event.getPlayer().getUniqueId());
        staffOn.remove(event.getPlayer().getUniqueId());
    }

    // ── Mods de staff ────────────────────────────────────────

    /** Mods de staff configurados (lunar.staff-mods.mods). */
    public Set<StaffMod> configuredMods() {
        Set<StaffMod> mods = EnumSet.noneOf(StaffMod.class);
        for (String name : plugin.getConfig().getStringList("lunar.staff-mods.mods")) {
            try {
                mods.add(StaffMod.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Mod de staff de Lunar desconocido en lunar.staff-mods.mods: " + name);
            }
        }
        if (mods.isEmpty()) {
            mods.add(StaffMod.XRAY);
        }
        return mods;
    }

    /**
     * Activa o desactiva los mods de staff de Lunar para un jugador. Devuelve false si no usa Lunar Client.
     * Con lunar.staff-mods.enabled-by-default se encienden solos; si no, quedan desbloqueados en el menú de mods.
     */
    public boolean setStaffMods(Player player, boolean on) {
        if (!isLunar(player)) {
            return false;
        }
        JsonArray ids = new JsonArray();
        Set<StaffMod> mods = on ? configuredMods() : EnumSet.allOf(StaffMod.class);
        for (StaffMod mod : mods) {
            ids.add(new JsonPrimitive(mod.id));
        }
        JsonObject message = new JsonObject();
        message.addProperty("@type", TYPE + "staffmod.v1." + (on ? "EnableStaffModsMessage" : "DisableStaffModsMessage"));
        message.add("staff_mods", ids);
        if (on) {
            message.addProperty("enabled_by_default", plugin.getConfig().getBoolean("lunar.staff-mods.enabled-by-default", true));
        }
        send(player, message);
        if (on) {
            staffOn.add(player.getUniqueId());
        } else {
            staffOn.remove(player.getUniqueId());
        }
        return true;
    }

    /** Al entrar o salir de vanish (si lunar.staff-mods.with-vanish está activo). */
    public void onVanish(Player player, boolean vanished) {
        if (!plugin.getConfig().getBoolean("lunar.staff-mods.with-vanish", true) || !isLunar(player)
                || !player.hasPermission("tukicore.lunar.staff")) {
            return;
        }
        if (vanished != staffModsOn(player)) {
            setStaffMods(player, vanished);
            plugin.lang().send(player, vanished ? "lunar.staff-on" : "lunar.staff-off", Lang.p("mods", modNames()));
        }
    }

    public String modNames() {
        List<String> names = new ArrayList<>();
        for (StaffMod mod : configuredMods()) {
            names.add(plugin.lang().raw("lunar.mods." + mod.key()));
        }
        return String.join(", ", names);
    }

    // ── Notificaciones ───────────────────────────────────────

    /** Notificación nativa de Lunar (arriba a la derecha). */
    public void notify(Player player, Component title, Component description, int seconds) {
        if (!isLunar(player) || !plugin.getConfig().getBoolean("lunar.notifications", true)) {
            return;
        }
        JsonObject message = new JsonObject();
        message.addProperty("@type", TYPE + "notification.v1.DisplayNotificationMessage");
        message.addProperty("title_adventure_json_lines", GsonComponentSerializer.gson().serialize(title));
        message.addProperty("description_adventure_json_lines", GsonComponentSerializer.gson().serialize(description));
        message.addProperty("display_time", Math.max(1, seconds) + "s");
        send(player, message);
    }

    // ── /lunar ───────────────────────────────────────────────

    public void status(CommandSender to) {
        List<Player> players = lunarPlayers();
        List<String> names = new ArrayList<>();
        for (Player player : players) {
            if (plugin.vanish().canSee(to, player)) {
                names.add(player.getName() + (staffModsOn(player) ? " ★" : ""));
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        plugin.lang().send(to, "lunar.status", Lang.p("count", names.size()),
                Lang.p("players", names.isEmpty() ? "-" : String.join(", ", names)));
        if (!registered) {
            plugin.lang().send(to, "lunar.disabled");
        }
    }

    /** /lunar staff [on|off] (para uno mismo) o /lunar staff <jugador> <on|off>. */
    public void toggle(CommandSender by, Player target, Boolean wanted) {
        boolean on = wanted != null ? wanted : !staffModsOn(target);
        if (!registered) {
            plugin.lang().send(by, "lunar.disabled");
            return;
        }
        if (!setStaffMods(target, on)) {
            plugin.lang().send(by, "lunar.not-lunar", Lang.p("player", target.getName()));
            return;
        }
        plugin.lang().send(target, on ? "lunar.staff-on" : "lunar.staff-off", Lang.p("mods", modNames()));
        if (!by.equals(target)) {
            plugin.lang().send(by, on ? "lunar.staff-on-other" : "lunar.staff-off-other", Lang.p("player", target.getName()));
        }
    }
}
