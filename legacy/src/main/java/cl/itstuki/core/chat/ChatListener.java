package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.rank.Rank;
import cl.itstuki.core.util.Durations;
import cl.itstuki.core.util.Text;
import cl.itstuki.core.util.Compat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Chat del servidor: silencios, anti-spam, filtro, staff chat, colores por permiso, menciones y
 * formato por rango. Corre en prioridad LOW: un plugin de minijuego (TukiUHC) puede poner su propio
 * formato encima, pero los silencios y el anti-spam se aplican igual.
 *
 * Edición Legacy: el chat de 1.8 no admite componentes en el evento, así que el formato se convierte a
 * texto con "§" (sin hover ni clic sobre el nombre).
 */
public final class ChatListener implements Listener {
    private static final class Last {
        private final String message;
        private final long time;

        Last(String message, long time) {
            this.message = message;
            this.time = time;
        }

        String message() {
            return message;
        }

        long time() {
            return time;
        }
    }

    /** Marca temporal del mensaje dentro del formato (se reemplaza por %2$s). */
    private static final String MESSAGE_MARK = "\uE000tcmsg\uE000";

    private final TukiCore plugin;
    private final Map<UUID, Last> last = new ConcurrentHashMap<>();

    public ChatListener(TukiCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String plain = event.getMessage();

        // Staff chat: modo activado o mensaje que empieza con el prefijo.
        String staffPrefix = plugin.getConfig().getString("staff-chat.prefix", "$");
        boolean staffToggle = plugin.staffChat().toggled(sender.getUniqueId());
        if (sender.hasPermission("tukicore.staffchat")
                && (staffToggle || !staffPrefix.isEmpty() && plain.startsWith(staffPrefix) && plain.length() > staffPrefix.length())) {
            event.setCancelled(true);
            String text = !staffPrefix.isEmpty() && plain.startsWith(staffPrefix) ? plain.substring(staffPrefix.length()).trim() : plain;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.staffChat().send(sender, text));
            return;
        }

        // En vanish hablar en el chat normal te delataría.
        if (plugin.vanish().isVanished(sender) && plugin.getConfig().getBoolean("vanish.block-chat", true)) {
            if (plain.startsWith("!")) {
                plain = plain.substring(1).trim();
                event.setMessage(plain);
            } else {
                event.setCancelled(true);
                plugin.lang().send(sender, "vanish.chat-blocked");
                return;
            }
        }

        if (!sender.hasPermission("tukicore.mute.bypass")) {
            MuteManager.Mute mute = plugin.mutes().mute(sender.getUniqueId());
            if (mute != null) {
                event.setCancelled(true);
                plugin.lang().send(sender, mute.permanent() ? "mute.you-are-muted-perm" : "mute.you-are-muted",
                        Lang.p("time", Durations.format(mute.secondsLeft())), Lang.p("reason", mute.reason()));
                return;
            }
            if (plugin.mutes().chatMuted() && !sender.hasPermission("tukicore.mutechat.bypass")) {
                event.setCancelled(true);
                plugin.lang().send(sender, "mutechat.blocked");
                return;
            }
        }

        if (!sender.hasPermission("tukicore.chat.bypass")) {
            long now = System.currentTimeMillis();
            Last previous = last.get(sender.getUniqueId());
            long cooldown = plugin.getConfig().getLong("chat.cooldown-ms", 1500);
            if (previous != null && now - previous.time() < cooldown) {
                event.setCancelled(true);
                plugin.lang().send(sender, "chat.slow-down",
                        Lang.p("seconds", String.format(Locale.ROOT, "%.1f", (cooldown - (now - previous.time())) / 1000.0)));
                return;
            }
            if (previous != null && plugin.getConfig().getBoolean("chat.block-repeated", true)
                    && previous.message().equalsIgnoreCase(plain) && now - previous.time() < 30_000) {
                event.setCancelled(true);
                plugin.lang().send(sender, "chat.repeated");
                return;
            }
            last.put(sender.getUniqueId(), new Last(plain, now));
        }

        Component message = sender.hasPermission("tukicore.chat.color") ? Text.legacy(plain) : Component.text(plain);
        message = filter(message, sender);
        Rank rank = plugin.ranks().rankOf(sender);
        TextColor color = color(rank.chatColor());
        if (color != null) {
            message = message.colorIfAbsent(color);
        }
        message = mentions(message, sender);
        event.setMessage(Text.section(message));

        if (!plugin.getConfig().getBoolean("chat.format-enabled", true)) {
            return;
        }
        String format = rank.chatFormat().isEmpty() ? plugin.getConfig().getString("chat.format",
                "<prefix><name><suffix> <dark_gray>»</dark_gray> <message>") : rank.chatFormat();
        Component name = Component.text(sender.getName(), NamedTextColor.WHITE);
        TagResolver tags = TagResolver.resolver(
                Lang.c("prefix", Text.legacy(plugin.ranks().prefix(sender))),
                Lang.c("suffix", Text.legacy(plugin.ranks().suffix(sender))),
                Lang.c("name", name),
                Lang.c("rank", Text.legacy(rank.display())));
        String rendered = Text.section(plugin.lang().format(format, sender, tags, Lang.c("message", Component.text(MESSAGE_MARK))));
        int mark = rendered.indexOf(MESSAGE_MARK);
        if (mark < 0) {
            return;
        }
        // Lo que no es el mensaje va escapado; el color que trae el formato antes del mensaje se repite al inicio.
        String before = rendered.substring(0, mark).replace("%", "%%");
        String after = rendered.substring(mark + MESSAGE_MARK.length()).replace("%", "%%");
        event.setFormat(before + org.bukkit.ChatColor.getLastColors(before) + "%2$s" + after);
    }

    private Component filter(Component message, Player sender) {
        List<String> words = plugin.getConfig().getStringList("chat.filter.words");
        if (words.isEmpty() || sender.hasPermission("tukicore.chat.bypass")) {
            return message;
        }
        String replacement = plugin.getConfig().getString("chat.filter.replacement", "***");
        for (String word : words) {
            if (word.trim().isEmpty()) {
                continue;
            }
            message = message.replaceText(TextReplacementConfig.builder()
                    .match("(?i)" + Pattern.quote(word)).replacement(replacement).build());
        }
        return message;
    }

    /** Resalta los nombres de jugadores conectados (con o sin @) y les avisa con un sonido. */
    private Component mentions(Component message, Player sender) {
        if (!plugin.getConfig().getBoolean("chat.mentions.enabled", true)) {
            return message;
        }
        List<Player> mentioned = new ArrayList<>();
        String plain = Text.plain(message).toLowerCase(Locale.ROOT);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(sender) && Pattern.compile("(?<![A-Za-z0-9_])@?" + Pattern.quote(target.getName().toLowerCase(Locale.ROOT))
                    + "(?![A-Za-z0-9_])").matcher(plain).find()) {
                mentioned.add(target);
            }
        }
        if (mentioned.isEmpty()) {
            return message;
        }
        TextColor highlight = color(plugin.getConfig().getString("chat.mentions.color", "yellow"));
        for (Player target : mentioned) {
            message = message.replaceText(TextReplacementConfig.builder()
                    .match("(?i)(?<![A-Za-z0-9_])@?" + Pattern.quote(target.getName()) + "(?![A-Za-z0-9_])")
                    .replacement(match -> match.color(highlight != null ? highlight : NamedTextColor.YELLOW)).build());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player target : mentioned) {
                if (target.isOnline()) {
                    Compat.ping(target);
                    plugin.actionBar(target, plugin.lang().get("chat.mentioned", Lang.p("player", sender.getName())));
                }
            }
        });
        return message;
    }

    static TextColor color(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.startsWith("#")) {
            return TextColor.fromHexString(value);
        }
        return NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
        plugin.tab().forget(event.getPlayer());
    }
}
