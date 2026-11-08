package cl.itstuki.core.rank;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/** Dónde viven los permisos y el rango de cada jugador: TukiCore (archivos) o LuckPerms. */
public interface RankBackend {
    String name();

    void enable();

    void disable();

    /** Nombre del rango del jugador conectado. */
    String rankOf(Player player);

    /** Rango (y hasta cuándo, en segundos epoch, 0 = permanente) de cualquier jugador. */
    CompletableFuture<Assignment> lookup(UUID player);

    final class Assignment {
        private final String rank;
        private final long expires;

        public Assignment(String rank, long expires) {
            this.rank = rank;
            this.expires = expires;
        }

        public String rank() {
            return rank;
        }

        public long expires() {
            return expires;
        }
    }

    /** Asigna el rango principal. {@code seconds} = 0 para permanente. */
    CompletableFuture<Void> setRank(UUID player, String playerName, String rank, long seconds);

    /** El rango cambió en ranks.yml: actualizar permisos (y LuckPerms, si es el backend). */
    void rankChanged(Rank rank);

    void rankDeleted(String rank);

    /** Recalcula permisos y prefijo de un jugador conectado. */
    void refresh(Player player);

    /** Prefijo efectivo, o null para usar el del rango de ranks.yml. */
    default String prefix(Player player) {
        return null;
    }

    default String suffix(Player player) {
        return null;
    }
}
