package cl.itstuki.core.command;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.rank.Rank;
import cl.itstuki.core.util.Credits;
import cl.itstuki.core.util.Durations;
import cl.itstuki.core.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Comandos de TukiCore (edición Legacy: declarados en plugin.yml). */
public final class CoreCommands implements CommandExecutor, TabCompleter {
    private static final List<String> COMMANDS = Arrays.asList("rank", "tukicore", "msg", "r", "socialspy", "staffchat",
            "broadcast", "clearchat", "mutechat", "mute", "unmute", "vanish", "clientinfo", "dev", "gm", "fly");
    private static final List<String> RANK_SUBS = Arrays.asList("list", "info", "create", "delete", "prefix", "suffix",
            "display", "weight", "color", "perm", "inherit", "set", "user");
    private final TukiCore plugin;

    public CoreCommands(TukiCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String name : COMMANDS) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) {
                plugin.getLogger().warning("Falta el comando /" + name + " en plugin.yml");
                continue;
            }
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private Lang lang() {
        return plugin.lang();
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        lang().send(sender, "core.no-permission");
        return false;
    }

    private Player self(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        lang().send(sender, "core.player-only");
        return null;
    }

    private void usage(CommandSender sender, String usage) {
        lang().send(sender, "core.usage", Lang.p("usage", usage));
    }

    private static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length)).trim();
    }

    /** Un nombre, "@a" (todos) o "@s"/"@p" (uno mismo). Respeta el vanish: no se encuentra a quien no ves. */
    private List<Player> targets(CommandSender sender, String arg) {
        List<Player> list = new ArrayList<>();
        if (arg.equalsIgnoreCase("@a")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.vanish().canSee(sender, p)) {
                    list.add(p);
                }
            }
        } else if ((arg.equalsIgnoreCase("@s") || arg.equalsIgnoreCase("@p")) && sender instanceof Player) {
            list.add((Player) sender);
        } else {
            Player exact = Bukkit.getPlayerExact(arg);
            Player found = exact != null ? exact : Bukkit.getPlayer(arg);
            if (found != null && plugin.vanish().canSee(sender, found)) {
                list.add(found);
            }
        }
        if (list.isEmpty()) {
            lang().send(sender, "core.unknown-player", Lang.p("player", arg));
        }
        return list;
    }

    private Player target(CommandSender sender, String arg) {
        List<Player> list = targets(sender, arg);
        return list.isEmpty() ? null : list.get(0);
    }

    @SuppressWarnings("deprecation")
    private static OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(player.getName())) {
                return player;
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "rank":
                if (allowed(sender, "tukicore.rank")) {
                    rank(sender, args);
                }
                return true;
            case "tukicore":
                core(sender, args);
                return true;
            case "msg":
                if (allowed(sender, "tukicore.msg")) {
                    msg(sender, args);
                }
                return true;
            case "r":
                if (allowed(sender, "tukicore.msg")) {
                    Player from = self(sender);
                    if (from != null && args.length > 0) {
                        plugin.messages().reply(from, join(args, 0));
                    } else if (from != null) {
                        usage(sender, "/r <mensaje>");
                    }
                }
                return true;
            case "socialspy":
                if (allowed(sender, "tukicore.socialspy")) {
                    Player p = self(sender);
                    if (p != null) {
                        lang().send(p, plugin.messages().toggleSpy(p.getUniqueId()) ? "msg.spy-on" : "msg.spy-off");
                    }
                }
                return true;
            case "staffchat":
                if (allowed(sender, "tukicore.staffchat")) {
                    if (args.length > 0) {
                        plugin.staffChat().send(sender, join(args, 0));
                    } else {
                        Player p = self(sender);
                        if (p != null) {
                            lang().send(p, plugin.staffChat().toggle(p.getUniqueId()) ? "staffchat.enabled" : "staffchat.disabled");
                        }
                    }
                }
                return true;
            case "broadcast":
                if (allowed(sender, "tukicore.broadcast")) {
                    if (args.length == 0) {
                        usage(sender, "/broadcast <mensaje>");
                    } else {
                        Component text = lang().format(join(args, 0), null);
                        plugin.adventure().all().sendMessage(lang().get("broadcast.format", Lang.c("message", text)));
                    }
                }
                return true;
            case "clearchat":
                if (allowed(sender, "tukicore.clearchat")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.hasPermission("tukicore.clearchat.bypass")) {
                            for (int i = 0; i < 100; i++) {
                                p.sendMessage(" ");
                            }
                        }
                    }
                    lang().broadcast("clearchat.done", Lang.p("player", sender.getName()));
                }
                return true;
            case "mutechat":
                if (allowed(sender, "tukicore.mutechat")) {
                    boolean muted = !plugin.mutes().chatMuted();
                    plugin.mutes().chatMuted(muted);
                    lang().broadcast(muted ? "mutechat.enabled" : "mutechat.disabled", Lang.p("player", sender.getName()));
                }
                return true;
            case "mute":
                if (allowed(sender, "tukicore.mute")) {
                    if (args.length == 0) {
                        usage(sender, "/mute <jugador> [tiempo] [razón]");
                    } else {
                        mute(sender, args[0], args.length > 1 ? args[1] : "perm", join(args, 2));
                    }
                }
                return true;
            case "unmute":
                if (allowed(sender, "tukicore.mute")) {
                    unmute(sender, args);
                }
                return true;
            case "vanish":
                if (allowed(sender, "tukicore.vanish")) {
                    vanish(sender, args);
                }
                return true;
            case "clientinfo":
                if (allowed(sender, "tukicore.clientcheck")) {
                    if (args.length == 0 || args[0].equalsIgnoreCase("all")) {
                        showAllClients(sender);
                    } else {
                        Player target = target(sender, args[0]);
                        if (target != null) {
                            plugin.clientCheck().show(sender, target);
                        }
                    }
                }
                return true;
            case "dev":
                dev(sender, args);
                return true;
            case "gm":
                if (allowed(sender, "tukicore.gamemode")) {
                    gamemode(sender, label, args);
                }
                return true;
            case "fly":
                if (allowed(sender, "tukicore.fly")) {
                    if (args.length == 0) {
                        Player p = self(sender);
                        if (p != null) {
                            toggleFly(sender, p);
                        }
                    } else if (allowed(sender, "tukicore.fly.others")) {
                        for (Player p : targets(sender, args[0])) {
                            toggleFly(sender, p);
                        }
                    }
                }
                return true;
            default:
                return false;
        }
    }

    // ── General ──────────────────────────────────────────────

    private void core(CommandSender s, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (allowed(s, "tukicore.reload")) {
                plugin.reloadAll();
                lang().send(s, "core.reloaded");
            }
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            lang().sendList(s, "core.help");
            return;
        }
        lang().send(s, "core.version", Lang.p("version", plugin.getDescription().getVersion()),
                Lang.p("backend", plugin.ranks().backend().name()), Lang.p("ranks", plugin.ranks().all().size()));
        plugin.msg(s, Credits.helpLine());
    }

    private void msg(CommandSender sender, String[] args) {
        Player from = self(sender);
        if (from == null) {
            return;
        }
        if (args.length < 2) {
            usage(sender, "/msg <jugador> <mensaje>");
            return;
        }
        Player to = target(sender, args[0]);
        if (to != null) {
            plugin.messages().send(from, to, join(args, 1));
        }
    }

    private void showAllClients(CommandSender to) {
        lang().send(to, "client.list-header", Lang.p("count", Bukkit.getOnlinePlayers().size()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.msg(to, plugin.clientCheck().describe(p, plugin.clientCheck().inspect(p)));
        }
    }

    private void vanish(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Player p = self(sender);
            if (p != null) {
                plugin.vanish().set(p, !plugin.vanish().isVanished(p));
            }
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            List<String> list = plugin.vanish().online().stream().map(Player::getName).collect(Collectors.toList());
            lang().send(sender, "vanish.list", Lang.p("count", list.size()),
                    Lang.p("players", list.isEmpty() ? "-" : String.join(", ", list)));
            return;
        }
        if (!allowed(sender, "tukicore.vanish.others")) {
            return;
        }
        Player target = target(sender, args[0]);
        if (target != null) {
            plugin.vanish().set(target, !plugin.vanish().isVanished(target));
            lang().send(sender, plugin.vanish().isVanished(target) ? "vanish.other-on" : "vanish.other-off",
                    Lang.p("player", target.getName()));
        }
    }

    /** Modo dev: solo las cuentas premium de dev-mode.players. Para el resto el comando no existe. */
    private void dev(CommandSender sender, String[] args) {
        if (!plugin.devMode().allowed(sender)) {
            lang().send(sender, "core.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.devMode().toggle((Player) sender);
        } else if (args[0].equalsIgnoreCase("info")) {
            plugin.devMode().info(sender);
        } else if (args[0].equalsIgnoreCase("perm") && args.length >= 3) {
            Player target = target(sender, args[1]);
            if (target != null) {
                plugin.devMode().checkPermission(sender, target, join(args, 2));
            }
        } else {
            usage(sender, "/dev [info | perm <jugador> <permiso>]");
        }
    }

    // ── Utilidades ───────────────────────────────────────────

    private void toggleFly(CommandSender by, Player target) {
        boolean fly = !target.getAllowFlight();
        target.setAllowFlight(fly);
        if (!fly) {
            target.setFlying(false);
        }
        lang().send(target, fly ? "fly.enabled" : "fly.disabled");
        if (!by.equals(target)) {
            lang().send(by, fly ? "fly.enabled-other" : "fly.disabled-other", Lang.p("player", target.getName()));
        }
    }

    private static GameMode mode(String key) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "survival":
            case "0":
            case "s":
            case "gms":
                return GameMode.SURVIVAL;
            case "creative":
            case "1":
            case "c":
            case "gmc":
                return GameMode.CREATIVE;
            case "adventure":
            case "2":
            case "a":
            case "gma":
                return GameMode.ADVENTURE;
            case "spectator":
            case "3":
            case "sp":
            case "gmsp":
                return GameMode.SPECTATOR;
            default:
                return null;
        }
    }

    private void gamemode(CommandSender sender, String label, String[] args) {
        // /gmc, /gms… usan el alias como modo.
        GameMode fromLabel = mode(label);
        GameMode mode = fromLabel != null ? fromLabel : args.length > 0 ? mode(args[0]) : null;
        int playerArg = fromLabel != null ? 0 : 1;
        if (mode == null) {
            usage(sender, "/gm <survival|creative|adventure|spectator|0-3> [jugador]");
            return;
        }
        if (args.length > playerArg) {
            if (!allowed(sender, "tukicore.gamemode.others")) {
                return;
            }
            for (Player p : targets(sender, args[playerArg])) {
                setMode(sender, p, mode);
            }
            return;
        }
        Player p = self(sender);
        if (p != null) {
            setMode(sender, p, mode);
        }
    }

    private void setMode(CommandSender by, Player target, GameMode mode) {
        target.setGameMode(mode);
        String name = mode.name().toLowerCase(Locale.ROOT);
        lang().send(target, "gamemode.changed", Lang.c("mode", lang().get("gamemode.names." + name)));
        if (!by.equals(target)) {
            lang().send(by, "gamemode.changed-other", Lang.p("player", target.getName()),
                    Lang.c("mode", lang().get("gamemode.names." + name)));
        }
    }

    // ── /mute ────────────────────────────────────────────────

    private void mute(CommandSender s, String name, String time, String reason) {
        OfflinePlayer target = offline(name);
        if (target == null) {
            lang().send(s, "core.unknown-player", Lang.p("player", name));
            return;
        }
        long seconds = Durations.parse(time);
        if (seconds < 0) {
            lang().send(s, "core.bad-duration", Lang.p("value", time));
            return;
        }
        String why = reason.isEmpty() ? lang().raw("mute.no-reason") : reason;
        plugin.mutes().mute(target.getUniqueId(), name, seconds, why, s.getName());
        lang().broadcast(seconds == 0 ? "mute.broadcast-perm" : "mute.broadcast", Lang.p("player", name),
                Lang.p("by", s.getName()), Lang.p("time", Durations.format(seconds)), Lang.p("reason", why));
    }

    private void unmute(CommandSender s, String[] args) {
        if (args.length == 0) {
            usage(s, "/unmute <jugador>");
            return;
        }
        OfflinePlayer target = offline(args[0]);
        if (target == null) {
            lang().send(s, "core.unknown-player", Lang.p("player", args[0]));
            return;
        }
        if (plugin.mutes().unmute(target.getUniqueId())) {
            lang().send(s, "mute.unmuted", Lang.p("player", args[0]));
            if (target.getPlayer() != null) {
                lang().send(target.getPlayer(), "mute.you-were-unmuted");
            }
        } else {
            lang().send(s, "mute.not-muted", Lang.p("player", args[0]));
        }
    }

    // ── /rank ────────────────────────────────────────────────

    private void withRank(CommandSender s, String name, Consumer<Rank> action) {
        Rank rank = plugin.ranks().rank(name);
        if (rank == null) {
            lang().send(s, "rank.unknown", Lang.p("rank", name));
            return;
        }
        action.accept(rank);
    }

    private static String spaced(String text) {
        return text.isEmpty() || text.endsWith(" ") ? text : text + " ";
    }

    private boolean edit(CommandSender s) {
        return allowed(s, "tukicore.rank.edit");
    }

    private void rank(CommandSender s, String[] args) {
        if (args.length == 0) {
            lang().sendList(s, "rank.help");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String rankName = args.length > 1 ? args[1] : "";
        String rest = join(args, 2);
        switch (sub) {
            case "list":
                lang().send(s, "rank.list-header", Lang.p("count", plugin.ranks().all().size()),
                        Lang.p("backend", plugin.ranks().backend().name()));
                for (Rank rank : plugin.ranks().sorted()) {
                    lang().send(s, "rank.list-line", Lang.p("rank", rank.name()), Lang.p("weight", rank.weight()),
                            Lang.c("rank_prefix", Text.legacy(rank.prefix())), Lang.c("display", Text.legacy(rank.display())));
                }
                return;
            case "info":
                if (args.length < 2) {
                    usage(s, "/rank info <rango>");
                    return;
                }
                withRank(s, rankName, rank -> lang().sendList(s, "rank.info", Lang.p("rank", rank.name()),
                        Lang.c("display", Text.legacy(rank.display())),
                        Lang.p("weight", rank.weight()), Lang.c("rank_prefix", Text.legacy(rank.prefix())),
                        Lang.c("suffix", Text.legacy(rank.suffix())), Lang.p("prefix_raw", rank.prefix()),
                        Lang.p("color", rank.chatColor().isEmpty() ? "-" : rank.chatColor()),
                        Lang.p("inherits", rank.inherits().isEmpty() ? "-" : String.join(", ", rank.inherits())),
                        Lang.p("permissions", rank.permissions().isEmpty() ? "-" : String.join(", ", rank.permissions()))));
                return;
            case "create": {
                if (!edit(s)) {
                    return;
                }
                if (args.length < 2) {
                    usage(s, "/rank create <rango> [peso]");
                    return;
                }
                int weight = 10;
                if (args.length > 2) {
                    try {
                        weight = Math.max(0, Math.min(10000, Integer.parseInt(args[2])));
                    } catch (NumberFormatException ex) {
                        usage(s, "/rank create <rango> [peso]");
                        return;
                    }
                }
                Rank rank = plugin.ranks().create(rankName, weight);
                lang().send(s, rank != null ? "rank.created" : "rank.cannot-create", Lang.p("rank", rankName), Lang.p("weight", weight));
                return;
            }
            case "delete":
                if (edit(s)) {
                    if (args.length < 2) {
                        usage(s, "/rank delete <rango>");
                        return;
                    }
                    lang().send(s, plugin.ranks().delete(rankName) ? "rank.deleted" : "rank.cannot-delete", Lang.p("rank", rankName));
                }
                return;
            case "prefix":
            case "suffix":
            case "display":
                if (!edit(s)) {
                    return;
                }
                if (args.length < 3) {
                    usage(s, "/rank " + sub + " <rango> <texto|none>");
                    return;
                }
                withRank(s, rankName, rank -> {
                    if (sub.equals("prefix")) {
                        String prefix = rest.equalsIgnoreCase("none") ? "" : spaced(rest);
                        plugin.ranks().edit(rank, r -> r.prefix(prefix));
                        lang().send(s, "rank.prefix-set", Lang.p("rank", rank.name()), Lang.c("rank_prefix", Text.legacy(prefix)));
                    } else if (sub.equals("suffix")) {
                        String suffix = rest.equalsIgnoreCase("none") ? "" : " " + rest;
                        plugin.ranks().edit(rank, r -> r.suffix(suffix));
                        lang().send(s, "rank.suffix-set", Lang.p("rank", rank.name()), Lang.c("suffix", Text.legacy(suffix)));
                    } else {
                        plugin.ranks().edit(rank, r -> r.display(rest));
                        lang().send(s, "rank.display-set", Lang.p("rank", rank.name()), Lang.c("display", Text.legacy(rank.display())));
                    }
                });
                return;
            case "weight":
                if (!edit(s)) {
                    return;
                }
                withRank(s, rankName, rank -> {
                    int weight;
                    try {
                        weight = Math.max(0, Math.min(10000, Integer.parseInt(rest)));
                    } catch (NumberFormatException ex) {
                        usage(s, "/rank weight <rango> <peso>");
                        return;
                    }
                    plugin.ranks().edit(rank, r -> r.weight(weight));
                    lang().send(s, "rank.weight-set", Lang.p("rank", rank.name()), Lang.p("weight", rank.weight()));
                });
                return;
            case "color":
                if (!edit(s)) {
                    return;
                }
                if (args.length < 3) {
                    usage(s, "/rank color <rango> <color|none>");
                    return;
                }
                withRank(s, rankName, rank -> {
                    String value = args[2];
                    plugin.ranks().edit(rank, r -> r.chatColor(value.equalsIgnoreCase("none") ? "" : value));
                    lang().send(s, "rank.color-set", Lang.p("rank", rank.name()), Lang.p("color", value));
                });
                return;
            case "perm":
            case "inherit":
                if (!edit(s)) {
                    return;
                }
                if (args.length < 4 || !(args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
                    usage(s, "/rank " + sub + " <rango> <add|remove> <" + (sub.equals("perm") ? "permiso" : "rango") + ">");
                    return;
                }
                boolean add = args[2].equalsIgnoreCase("add");
                String value = join(args, 3);
                withRank(s, rankName, rank -> {
                    if (sub.equals("perm")) {
                        plugin.ranks().edit(rank, r -> {
                            r.permissions().remove(value);
                            if (add) {
                                r.permissions().add(value);
                            }
                        });
                        lang().send(s, add ? "rank.perm-added" : "rank.perm-removed", Lang.p("rank", rank.name()),
                                Lang.p("permission", value));
                        return;
                    }
                    Rank parent = plugin.ranks().rank(value);
                    if (parent == null || parent == rank) {
                        lang().send(s, "rank.unknown", Lang.p("rank", value));
                        return;
                    }
                    plugin.ranks().edit(rank, r -> {
                        r.inherits().remove(parent.name());
                        if (add) {
                            r.inherits().add(parent.name());
                        }
                    });
                    lang().send(s, add ? "rank.inherit-added" : "rank.inherit-removed", Lang.p("rank", rank.name()),
                            Lang.p("parent", parent.name()));
                });
                return;
            case "set":
                if (allowed(s, "tukicore.rank.set")) {
                    if (args.length < 3) {
                        usage(s, "/rank set <jugador> <rango> [tiempo]");
                        return;
                    }
                    setRank(s, args[1], args[2], args.length > 3 ? args[3] : "perm");
                }
                return;
            case "user":
                if (args.length < 2) {
                    usage(s, "/rank user <jugador>");
                    return;
                }
                user(s, args[1]);
                return;
            default:
                lang().sendList(s, "rank.help");
        }
    }

    private void user(CommandSender s, String name) {
        OfflinePlayer target = offline(name);
        if (target == null) {
            lang().send(s, "core.unknown-player", Lang.p("player", name));
            return;
        }
        plugin.ranks().backend().lookup(target.getUniqueId()).thenAccept(a -> Bukkit.getScheduler().runTask(plugin, () ->
                lang().send(s, "rank.user", Lang.p("player", name), Lang.p("rank", a.rank()),
                        Lang.p("time", a.expires() == 0 ? "∞" : Durations.format(a.expires() - System.currentTimeMillis() / 1000)))));
    }

    private void setRank(CommandSender s, String name, String rankName, String time) {
        OfflinePlayer target = offline(name);
        Rank rank = plugin.ranks().rank(rankName);
        if (target == null) {
            lang().send(s, "core.unknown-player", Lang.p("player", name));
            return;
        }
        if (rank == null) {
            lang().send(s, "rank.unknown", Lang.p("rank", rankName));
            return;
        }
        long seconds = Durations.parse(time);
        if (seconds < 0) {
            lang().send(s, "core.bad-duration", Lang.p("value", time));
            return;
        }
        String playerName = target.getName() != null ? target.getName() : name;
        plugin.ranks().backend().setRank(target.getUniqueId(), playerName, rank.name(), seconds).whenComplete((v, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        lang().send(s, "rank.error");
                        plugin.getLogger().warning("No se pudo asignar el rango: " + error.getMessage());
                        return;
                    }
                    lang().send(s, "rank.set", Lang.p("player", playerName), Lang.c("display", Text.legacy(rank.display())),
                            Lang.p("time", Durations.format(seconds)));
                    Player online = target.getPlayer();
                    if (online != null) {
                        lang().send(online, "rank.you-got", Lang.c("display", Text.legacy(rank.display())),
                                Lang.p("time", Durations.format(seconds)));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.tab().refresh(online), 10L);
                    }
                }));
    }

    // ── Autocompletado ───────────────────────────────────────

    private List<String> visibleNames(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.vanish().canSee(sender, p)) {
                names.add(p.getName());
            }
        }
        return names;
    }

    private List<String> rankNames() {
        return plugin.ranks().all().stream().map(Rank::name).collect(Collectors.toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        int n = args.length;
        List<String> options = new ArrayList<>();
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "rank":
                if (!sender.hasPermission("tukicore.rank")) {
                    break;
                }
                if (n == 1) {
                    options.addAll(RANK_SUBS);
                } else {
                    String sub = args[0].toLowerCase(Locale.ROOT);
                    if (sub.equals("set") || sub.equals("user")) {
                        if (n == 2) {
                            options.addAll(visibleNames(sender));
                        } else if (n == 3 && sub.equals("set")) {
                            options.addAll(rankNames());
                        } else if (n == 4 && sub.equals("set")) {
                            options.addAll(Arrays.asList("1d", "7d", "30d", "perm"));
                        }
                    } else if (n == 2 && !sub.equals("list") && !sub.equals("create")) {
                        options.addAll(rankNames());
                    } else if (n == 3 && (sub.equals("perm") || sub.equals("inherit"))) {
                        options.addAll(Arrays.asList("add", "remove"));
                    } else if (n == 4 && sub.equals("inherit")) {
                        options.addAll(rankNames());
                    } else if (n == 3 && sub.equals("color")) {
                        options.addAll(Arrays.asList("none", "white", "gray", "yellow", "aqua", "green", "gold", "#FFAA00"));
                    } else if (n == 3 && (sub.equals("prefix") || sub.equals("suffix"))) {
                        options.add("none");
                    }
                }
                break;
            case "tukicore":
                if (n == 1) {
                    options.addAll(Arrays.asList("help", "reload"));
                }
                break;
            case "msg":
            case "clientinfo":
            case "unmute":
            case "mute":
                if (n == 1) {
                    options.addAll(visibleNames(sender));
                    if (command.getName().equalsIgnoreCase("clientinfo")) {
                        options.add("all");
                    }
                } else if (n == 2 && command.getName().equalsIgnoreCase("mute")) {
                    options.addAll(Arrays.asList("10m", "1h", "1d", "7d", "perm"));
                }
                break;
            case "vanish":
                if (n == 1) {
                    options.add("list");
                    if (sender.hasPermission("tukicore.vanish.others")) {
                        options.addAll(visibleNames(sender));
                    }
                }
                break;
            case "gm":
                if (n == 1 && mode(alias) == null) {
                    options.addAll(Arrays.asList("survival", "creative", "adventure", "spectator"));
                } else if (sender.hasPermission("tukicore.gamemode.others")) {
                    options.addAll(visibleNames(sender));
                    options.add("@a");
                }
                break;
            case "fly":
                if (n == 1 && sender.hasPermission("tukicore.fly.others")) {
                    options.addAll(visibleNames(sender));
                }
                break;
            case "dev":
                if (n == 1 && plugin.devMode().allowed(sender)) {
                    options.addAll(Arrays.asList("info", "perm"));
                } else if (n == 2 && plugin.devMode().allowed(sender)) {
                    options.addAll(visibleNames(sender));
                }
                break;
            default:
                return Collections.emptyList();
        }
        String last = n == 0 ? "" : args[n - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }
}
