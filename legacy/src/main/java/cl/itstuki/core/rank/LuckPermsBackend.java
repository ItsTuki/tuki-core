package cl.itstuki.core.rank;

import cl.itstuki.core.TukiCore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Rangos sobre LuckPerms: cada rango de ranks.yml es un grupo de LuckPerms. TukiCore crea los que faltan
 * y, al editarlos con /rank, actualiza prefijo, sufijo, peso y los permisos/herencias que él mismo puso
 * (no toca lo que el dueño agregó directamente en LuckPerms).
 */
final class LuckPermsBackend implements RankBackend {
    private final TukiCore plugin;
    private final RankManager ranks;
    private final LuckPerms api;
    /** Permisos y herencias que TukiCore escribió en cada grupo (para quitar solo esos). */
    private final Map<String, Set<String>> managedPerms = new HashMap<>();
    private final Map<String, Set<String>> managedParents = new HashMap<>();
    private EventSubscription<UserDataRecalculateEvent> subscription;

    LuckPermsBackend(TukiCore plugin, RankManager ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
        this.api = LuckPermsProvider.get();
    }

    @Override
    public String name() {
        return "LuckPerms";
    }

    @Override
    public void enable() {
        for (Rank rank : ranks.all()) {
            managedPerms.put(rank.name(), new HashSet<>(rank.permissions()));
            managedParents.put(rank.name(), new HashSet<>(rank.inherits()));
        }
        if (plugin.getConfig().getBoolean("ranks.sync-to-luckperms", true)) {
            api.getGroupManager().loadAllGroups().thenRun(() -> {
                for (Rank rank : ranks.all()) {
                    if (api.getGroupManager().getGroup(rank.name()) == null) {
                        api.getGroupManager().createAndLoadGroup(rank.name()).thenCompose(group -> {
                            apply(group, rank, java.util.Collections.emptySet(), java.util.Collections.emptySet());
                            return api.getGroupManager().saveGroup(group);
                        }).thenRun(() -> plugin.getLogger().info("Grupo de LuckPerms creado: " + rank.name()));
                    }
                }
            });
        }
        // Cuando LuckPerms recalcula a alguien (cambio de grupo, de prefijo…) se actualiza su nombre en el tab.
        subscription = api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID id = event.getUser().getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    plugin.tab().refresh(player);
                }
            });
        });
    }

    @Override
    public void disable() {
        if (subscription != null) {
            subscription.close();
        }
    }

    private void apply(Group group, Rank rank, Set<String> oldPerms, Set<String> oldParents) {
        group.data().clear(NodeType.PREFIX::matches);
        group.data().clear(NodeType.SUFFIX::matches);
        group.data().clear(NodeType.WEIGHT::matches);
        group.data().add(WeightNode.builder(rank.weight()).build());
        if (!rank.prefix().isEmpty()) {
            group.data().add(PrefixNode.builder(rank.prefix(), rank.weight()).build());
        }
        if (!rank.suffix().isEmpty()) {
            group.data().add(SuffixNode.builder(rank.suffix(), rank.weight()).build());
        }
        for (String perm : oldPerms) {
            String key = perm.startsWith("-") ? perm.substring(1) : perm;
            group.data().clear(node -> node instanceof PermissionNode && ((PermissionNode) node).getPermission().equals(key));
        }
        for (String perm : rank.permissions()) {
            boolean negated = perm.startsWith("-");
            String key = negated ? perm.substring(1) : perm;
            group.data().clear(node -> node instanceof PermissionNode && ((PermissionNode) node).getPermission().equals(key));
            group.data().add(PermissionNode.builder(key).value(!negated).build());
        }
        for (String parent : oldParents) {
            group.data().clear(node -> node instanceof InheritanceNode && ((InheritanceNode) node).getGroupName().equals(parent));
        }
        for (String parent : rank.inherits()) {
            if (!parent.equals(rank.name())) {
                group.data().add(InheritanceNode.builder(parent).build());
            }
        }
    }

    @Override
    public void rankChanged(Rank rank) {
        Set<String> oldPerms = managedPerms.getOrDefault(rank.name(), java.util.Collections.emptySet());
        Set<String> oldParents = managedParents.getOrDefault(rank.name(), java.util.Collections.emptySet());
        managedPerms.put(rank.name(), new HashSet<>(rank.permissions()));
        managedParents.put(rank.name(), new HashSet<>(rank.inherits()));
        api.getGroupManager().createAndLoadGroup(rank.name()).thenCompose(group -> {
            apply(group, rank, oldPerms, oldParents);
            return api.getGroupManager().saveGroup(group);
        }).thenRun(() -> api.getMessagingService().ifPresent(m -> m.pushUpdate()));
    }

    @Override
    public void rankDeleted(String rank) {
        managedPerms.remove(rank);
        managedParents.remove(rank);
        api.getGroupManager().loadGroup(rank).thenAccept(group -> group.ifPresent(g -> api.getGroupManager().deleteGroup(g)));
    }

    private CachedMetaData meta(Player player) {
        return api.getPlayerAdapter(Player.class).getMetaData(player);
    }

    @Override
    public String rankOf(Player player) {
        return api.getPlayerAdapter(Player.class).getUser(player).getPrimaryGroup();
    }

    @Override
    public String prefix(Player player) {
        String prefix = meta(player).getPrefix();
        return prefix != null ? prefix : "";
    }

    @Override
    public String suffix(Player player) {
        String suffix = meta(player).getSuffix();
        return suffix != null ? suffix : "";
    }

    @Override
    public CompletableFuture<Assignment> lookup(UUID player) {
        return api.getUserManager().loadUser(player).thenApply(user -> {
            String primary = user.getPrimaryGroup();
            long expires = user.getNodes(NodeType.INHERITANCE).stream()
                    .filter(n -> n.getGroupName().equals(primary) && n.hasExpiry())
                    .mapToLong(n -> n.getExpiry().getEpochSecond()).findFirst().orElse(0);
            return new Assignment(primary, expires);
        });
    }

    @Override
    public CompletableFuture<Void> setRank(UUID id, String playerName, String rank, long seconds) {
        Set<String> ours = new HashSet<>();
        ranks.all().forEach(r -> ours.add(r.name()));
        String defaultRank = ranks.defaultRank();
        return api.getUserManager().modifyUser(id, user -> {
            user.data().clear(node -> node instanceof InheritanceNode && ours.contains(((InheritanceNode) node).getGroupName())
                    && !((InheritanceNode) node).getGroupName().equals("default"));
            if (!rank.equals(defaultRank) || seconds > 0) {
                InheritanceNode.Builder node = InheritanceNode.builder(rank);
                if (seconds > 0) {
                    node.expiry(seconds, TimeUnit.SECONDS);
                }
                user.data().add(node.build());
            }
            if (!rank.equals("default")) {
                user.setPrimaryGroup(rank);
            }
        });
    }

    @Override
    public void refresh(Player player) {
        // LuckPerms aplica los permisos por su cuenta.
    }
}
