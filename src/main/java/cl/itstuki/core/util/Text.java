package cl.itstuki.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Conversión de textos con códigos & (y &#RRGGBB), como los prefijos de rango. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private Text() {
    }

    public static Component legacy(String text) {
        return text == null || text.isEmpty() ? Component.empty() : LEGACY.deserialize(text.replace('§', '&'));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Quita los códigos de color & de un texto. */
    public static String stripLegacy(String text) {
        return plain(legacy(text));
    }

    private static final java.util.Map<Character, String> MINI = new java.util.HashMap<>();

    static {
        String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
                "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        String codes = "0123456789abcdef";
        for (int i = 0; i < codes.length(); i++) {
            MINI.put(codes.charAt(i), "<" + names[i] + ">");
        }
        MINI.put('l', "<bold>");
        MINI.put('o', "<italic>");
        MINI.put('n', "<underlined>");
        MINI.put('m', "<strikethrough>");
        MINI.put('k', "<obfuscated>");
        MINI.put('r', "<reset>");
    }

    /**
     * Pasa los códigos &amp; (y &amp;#RRGGBB) a etiquetas MiniMessage, para poder mezclar ambos en anuncios:
     * "&amp;l&amp;6Hola" queda en negrita y dorado.
     */
    public static String toMini(String text) {
        if (text == null || text.indexOf('&') < 0 && text.indexOf('§') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if (next == '#' && i + 7 < text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    out.append("<#").append(text, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = MINI.get(next);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
