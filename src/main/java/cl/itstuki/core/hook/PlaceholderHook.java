package cl.itstuki.core.hook;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** PlaceholderAPI opcional: registra %tukicore_...% y permite <papi:nombre> en los textos. */
public final class PlaceholderHook {
    private @Nullable CoreExpansion expansion;

    public void enable(TukiCore plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            expansion = new CoreExpansion(plugin);
            expansion.register();
            plugin.getLogger().info("PlaceholderAPI detectado: placeholders %tukicore_...% registrados.");
        } catch (Throwable ex) {
            expansion = null;
            plugin.getLogger().warning("No se pudo conectar con PlaceholderAPI: " + ex.getMessage());
        }
    }

    public void disable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    /** <papi:nombre> en los textos de lang/*.yml y en los formatos de chat. */
    public TagResolver tags(@Nullable Player player) {
        return TagResolver.resolver("papi", (args, ctx) -> {
            String name = args.hasNext() ? args.pop().value() : "";
            if (expansion == null || player == null || name.isEmpty()) {
                return Tag.selfClosingInserting(Component.empty());
            }
            return Tag.selfClosingInserting(Text.legacy(
                    me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%" + name + "%")));
        });
    }
}
