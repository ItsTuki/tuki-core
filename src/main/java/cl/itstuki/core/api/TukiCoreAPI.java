package cl.itstuki.core.api;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.rank.Rank;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * API pública de TukiCore para otros plugins (TukiUHC la usa por reflexión, sin depender de ella).
 * Los textos de prefijo y sufijo vienen con códigos & / &#RRGGBB.
 */
public final class TukiCoreAPI {
    private static @Nullable TukiCore plugin;

    private TukiCoreAPI() {
    }

    public static void init(@Nullable TukiCore instance) {
        plugin = instance;
    }

    public static boolean ready() {
        return plugin != null;
    }

    public static String prefix(Player player) {
        return plugin == null ? "" : plugin.ranks().prefix(player);
    }

    public static String suffix(Player player) {
        return plugin == null ? "" : plugin.ranks().suffix(player);
    }

    /** Nombre interno del rango (ej. "vip"). */
    public static String rank(Player player) {
        return plugin == null ? "" : plugin.ranks().rankOf(player).name();
    }

    /** Nombre para mostrar del rango (ej. "&aVIP"). */
    public static String rankDisplay(Player player) {
        return plugin == null ? "" : plugin.ranks().rankOf(player).display();
    }

    public static int weight(Player player) {
        return plugin == null ? 0 : plugin.ranks().rankOf(player).weight();
    }

    public static boolean isMuted(UUID player) {
        return plugin != null && plugin.mutes().mute(player) != null;
    }

    /** Está en /vanish (también se puede leer el metadata "vanished" del jugador). */
    public static boolean isVanished(Player player) {
        return plugin != null && plugin.vanish().isVanished(player);
    }

    /** Cliente con el que entró: Vanilla, Fabric, Forge, Lunar Client… */
    public static String client(Player player) {
        return plugin == null ? "" : plugin.clientCheck().inspect(player).loader();
    }

    public static @Nullable Rank rankInfo(String name) {
        return plugin == null ? null : plugin.ranks().rank(name);
    }
}
