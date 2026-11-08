package cl.itstuki.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Conversión de textos con códigos & (y &#RRGGBB), como los prefijos de rango. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    /** Para chat, tab y equipos en "§": hexadecimal en 1.16+, el color más parecido antes. */
    private static final LegacyComponentSerializer SECTION = Compat.atLeast(16)
            ? LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build()
            : LegacyComponentSerializer.legacySection();

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

    /** Componente a texto con "§" (lo que entienden el chat, el tab y los equipos en 1.8). */
    public static String section(Component component) {
        return component == null ? "" : SECTION.serialize(component);
    }

    /** Prefijo &-codes a "§". */
    public static String section(String ampersand) {
        return section(legacy(ampersand));
    }

    public static String cut(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        String cut = text.substring(0, max);
        return cut.endsWith("§") ? cut.substring(0, max - 1) : cut;
    }
}
