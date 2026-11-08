package cl.itstuki.core.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** Textos del plugin (lang/es.yml, lang/en.yml) en formato MiniMessage. */
public final class Lang {
    public static final List<String> LANGUAGES = List.of("es", "en");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private TagResolver base = TagResolver.empty();
    private Function<@Nullable Player, TagResolver> playerTags = p -> TagResolver.empty();

    public Lang(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        String code = LANGUAGES.contains(language) ? language : "es";
        for (String lang : LANGUAGES) {
            File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (file.exists()) {
                // Si el archivo es de una versión anterior se respalda y se reemplaza por el nuevo.
                YamlConfiguration bundled = resource(lang);
                int bundledVersion = bundled == null ? 0 : bundled.getInt("lang-version", 0);
                if (YamlConfiguration.loadConfiguration(file).getInt("lang-version", 0) < bundledVersion) {
                    File backup = new File(file.getParentFile(), lang + ".yml.old");
                    backup.delete();
                    if (file.renameTo(backup)) {
                        plugin.getLogger().info("lang/" + lang + ".yml actualizado (copia anterior en " + backup.getName() + ")");
                    }
                }
            }
            if (!file.exists()) {
                plugin.saveResource("lang/" + lang + ".yml", false);
            }
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + code + ".yml"));
        YamlConfiguration defaults = resource(code);
        YamlConfiguration spanish = resource("es");
        if (defaults != null && spanish != null && defaults != spanish) {
            defaults.setDefaults(spanish);
        }
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        messages = loaded;
        base = TagResolver.resolver(Placeholder.parsed("prefix", loaded.getString("prefix", "")));
    }

    private @Nullable YamlConfiguration resource(String lang) {
        try (InputStream in = plugin.getResource("lang/" + lang + ".yml")) {
            return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    /** Etiquetas de jugador (<rank>, <papi:...>) para los textos. */
    public void playerTags(Function<@Nullable Player, TagResolver> tags) {
        this.playerTags = tags;
    }

    public String raw(String key) {
        // Sin valor por defecto explícito: así, si falta en el archivo del servidor, se usa el texto del jar.
        String value = messages.getString(key);
        return value != null ? value : key;
    }

    public List<String> rawList(String key) {
        return messages.getStringList(key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return format(raw(key), null, resolvers);
    }

    public Component format(String text, @Nullable Player player, TagResolver... resolvers) {
        // Los últimos mandan: las etiquetas propias del mensaje pisan a <prefix>, <rank>, <papi:...>.
        return MM.deserialize(text, TagResolver.resolver(base, playerTags.apply(player), TagResolver.resolver(resolvers)));
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (!raw.isEmpty()) {
            audience.sendMessage(format(raw, audience instanceof Player p ? p : null, resolvers));
        }
    }

    public void sendList(Audience audience, String key, TagResolver... resolvers) {
        for (String line : rawList(key)) {
            audience.sendMessage(format(line, audience instanceof Player p ? p : null, resolvers));
        }
    }

    public void broadcast(String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        Component message = get(key, resolvers);
        Bukkit.getServer().sendMessage(message);
    }

    /** Texto sin interpretar (lo que escriben los jugadores, nombres). */
    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver c(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
