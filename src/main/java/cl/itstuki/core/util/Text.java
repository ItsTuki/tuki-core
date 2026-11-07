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
}
