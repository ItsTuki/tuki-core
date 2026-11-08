package cl.itstuki.core.rank;

import cl.itstuki.core.TukiCore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Rangos de ranks.yml y el backend que los aplica (TukiCore o LuckPerms). */
public final class RankManager {
    private final TukiCore plugin;
    private final File file;
    private final Map<String, Rank> ranks = new LinkedHashMap<>();
    private RankBackend backend;
    private String defaultRank = "default";

    public RankManager(TukiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ranks.clear();
        ConfigurationSection section = yaml.getConfigurationSection("ranks");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection rank = section.getConfigurationSection(name);
                if (rank != null) {
                    Rank loaded = Rank.load(name, rank);
                    ranks.put(loaded.name(), loaded);
                }
            }
        }
        defaultRank = plugin.getConfig().getString("ranks.default", "default").toLowerCase(Locale.ROOT);
        if (!ranks.containsKey(defaultRank)) {
            Rank fallback = new Rank(defaultRank, 0);
            fallback.prefix("&7");
            ranks.put(defaultRank, fallback);
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header(String.join("\n", java.util.Arrays.asList(
                "Rangos de TukiCore. Edítalos con /rank o aquí (luego /tukicore reload).",
                "prefix/suffix: códigos & y &#RRGGBB. chat-color: white, gray, #RRGGBB…",
                "permissions: un \"-\" delante niega el permiso. inherits: rangos de los que hereda permisos.")));
        for (Rank rank : sorted()) {
            rank.save(yaml.createSection("ranks." + rank.name()));
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar ranks.yml: " + ex.getMessage());
        }
    }

    /** Elige backend según config (AUTO = LuckPerms si está instalado). */
    public void startBackend() {
        String wanted = plugin.getConfig().getString("ranks.backend", "AUTO").toUpperCase(Locale.ROOT);
        boolean luckPerms = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        if (wanted.equals("LUCKPERMS") && !luckPerms) {
            plugin.getLogger().warning("ranks.backend es LUCKPERMS pero LuckPerms no está instalado: se usa TukiCore.");
        }
        backend = null;
        if (luckPerms && !wanted.equals("TUKICORE")) {
            try {
                backend = new LuckPermsBackend(plugin, this);
            } catch (Throwable ex) {
                plugin.getLogger().warning("No se pudo conectar con LuckPerms (" + ex.getMessage() + "): se usa TukiCore.");
            }
        }
        if (backend == null) {
            backend = new InternalBackend(plugin, this);
        }
        backend.enable();
        Bukkit.getOnlinePlayers().forEach(backend::refresh);
    }

    public void stopBackend() {
        if (backend != null) {
            backend.disable();
        }
    }

    public RankBackend backend() {
        return backend;
    }

    public String defaultRank() {
        return defaultRank;
    }

    public Rank rank(String name) {
        return ranks.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Rank> all() {
        return ranks.values();
    }

    /** Del más importante al menos. */
    public List<Rank> sorted() {
        List<Rank> list = new ArrayList<>(ranks.values());
        list.sort(Comparator.comparingInt((Rank r) -> r.weight()).reversed().thenComparing(r -> r.name()));
        return list;
    }

    public Rank rankOf(Player player) {
        Rank rank = rank(backend.rankOf(player));
        return rank != null ? rank : ranks.getOrDefault(defaultRank, new Rank(defaultRank, 0));
    }

    public String prefix(Player player) {
        String fromBackend = backend.prefix(player);
        return fromBackend != null ? fromBackend : rankOf(player).prefix();
    }

    public String suffix(Player player) {
        String fromBackend = backend.suffix(player);
        return fromBackend != null ? fromBackend : rankOf(player).suffix();
    }

    // ── Edición ──────────────────────────────────────────────

    public Rank create(String name, int weight) {
        String key = name.toLowerCase(Locale.ROOT);
        if (ranks.containsKey(key) || !key.matches("[a-z0-9_\\-]{1,32}")) {
            return null;
        }
        Rank rank = new Rank(key, weight);
        rank.display(name);
        if (!key.equals(defaultRank)) {
            rank.inherits().add(defaultRank);
        }
        ranks.put(key, rank);
        changed(rank);
        return rank;
    }

    public boolean delete(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.equals(defaultRank) || ranks.remove(key) == null) {
            return false;
        }
        for (Rank other : ranks.values()) {
            other.inherits().remove(key);
        }
        save();
        backend.rankDeleted(key);
        Bukkit.getOnlinePlayers().forEach(backend::refresh);
        return true;
    }

    public void edit(Rank rank, Consumer<Rank> change) {
        change.accept(rank);
        changed(rank);
    }

    private void changed(Rank rank) {
        save();
        backend.rankChanged(rank);
        Bukkit.getOnlinePlayers().forEach(backend::refresh);
        plugin.tab().refreshAll();
    }

    /** Permisos efectivos del rango con su herencia ("-x" = negado). Los de más peso mandan. */
    public Map<String, Boolean> permissions(Rank rank) {
        List<Rank> chain = new ArrayList<>();
        collect(rank, chain, new HashSet<>());
        chain.sort(Comparator.comparingInt((Rank r) -> r.weight()));
        Map<String, Boolean> perms = new LinkedHashMap<>();
        for (Rank r : chain) {
            for (String perm : r.permissions()) {
                boolean negated = perm.startsWith("-");
                perms.put(negated ? perm.substring(1) : perm, !negated);
            }
        }
        return perms;
    }

    private void collect(Rank rank, List<Rank> out, Set<String> seen) {
        if (!seen.add(rank.name())) {
            return;
        }
        out.add(rank);
        for (String parent : rank.inherits()) {
            Rank p = rank(parent);
            if (p != null) {
                collect(p, out, seen);
            }
        }
    }
}
