package cl.itstuki.core.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import cl.itstuki.core.TukiCore;

/** Banner de consola y créditos. */
public final class Credits {
    public static final String AUTHOR = "ItsTuki";
    public static final String REPOSITORY = "https://github.com/ItsTuki/tuki-core";
    private static final String TITLE = "<gradient:#55FF55:#00AAAA><bold>TukiCore</bold></gradient>";
    private static final String AUTHOR_TAG = "<gradient:#55FFFF:#5555FF><bold>" + AUTHOR + "</bold></gradient>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Credits() {
    }

    public static Component helpLine() {
        return MM.deserialize("<dark_gray>» </dark_gray><gray>Creado por </gray><click:open_url:'" + REPOSITORY
                + "'><hover:show_text:'<gray>Ver en GitHub'>" + AUTHOR_TAG + "</hover></click>");
    }

    public static void enableBanner(TukiCore plugin, int ranks, String backend) {
        send(plugin, TagResolver.resolver(Placeholder.unparsed("ranks", String.valueOf(ranks)), Placeholder.unparsed("backend", backend)), java.util.Arrays.asList(
                "",
                "<dark_gray>  ======================================",
                "  " + TITLE + " <gray>v<version>",
                "  <gray>Creado por " + AUTHOR_TAG,
                "  <gray>Servidor: <white><server>",
                "  <aqua>Edición Legacy (1.8.8 – 26.3)",
                "  <green>Plugin activado. Rangos: <white><ranks></white> <dark_gray>|</dark_gray> Backend: <white><backend>",
                "<dark_gray>  ======================================",
                ""));
    }

    public static void disableBanner(TukiCore plugin) {
        send(plugin, TagResolver.empty(), java.util.Arrays.asList("", "  " + TITLE + " <gray>v<version> <red>desactivado.",
                "  <gray>Creado por " + AUTHOR_TAG, ""));
    }

    private static void send(TukiCore plugin, TagResolver extra, List<String> lines) {
        TagResolver resolver = TagResolver.resolver(extra,
                Placeholder.unparsed("version", plugin.getDescription().getVersion()),
                Placeholder.unparsed("server", plugin.getServer().getName() + " " + Compat.versionName()));
        for (String line : lines) {
            plugin.msg(plugin.getServer().getConsoleSender(), MM.deserialize(line, resolver));
        }
    }
}
