package cl.itstuki.core.command;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.rank.Rank;
import cl.itstuki.core.util.Credits;
import cl.itstuki.core.util.Durations;
import cl.itstuki.core.util.Text;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Comandos de TukiCore (Brigadier). */
public final class CoreCommands {
    private final TukiCore plugin;

    public CoreCommands(TukiCore plugin) {
        this.plugin = plugin;
    }

    private interface Run {
        void run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static Command<CommandSourceStack> ok(Run run) {
        return ctx -> {
            run.run(ctx);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Predicate<CommandSourceStack> perm(String permission) {
        return src -> src.getSender().hasPermission(permission);
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    private Lang lang() {
        return plugin.lang();
    }

    private static String str(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    private static List<Player> players(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        return ctx.getArgument(arg, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
    }

    private final SuggestionProvider<CommandSourceStack> rankNames = (ctx, builder) -> {
        TukiCore core = (TukiCore) Bukkit.getPluginManager().getPlugin("TukiCore");
        core.ranks().all().forEach(r -> builder.suggest(r.name()));
        return builder.buildFuture();
    };

    private final SuggestionProvider<CommandSourceStack> onlineNames = (ctx, builder) -> {
        Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
        return builder.buildFuture();
    };

    public void register(Commands registrar) {
        registrar.register(rankCommand().build(), "Gestiona los rangos", List.of("rango", "ranks"));

        registrar.register(Commands.literal("tukicore").executes(ok(ctx -> {
            CommandSender s = sender(ctx);
            lang().send(s, "core.version", Lang.p("version", plugin.getPluginMeta().getVersion()),
                    Lang.p("backend", plugin.ranks().backend().name()), Lang.p("ranks", plugin.ranks().all().size()));
            s.sendMessage(Credits.helpLine());
        })).then(Commands.literal("reload").requires(perm("tukicore.reload")).executes(ok(ctx -> {
            plugin.reloadAll();
            lang().send(sender(ctx), "core.reloaded");
        }))).then(Commands.literal("help").executes(ok(ctx -> lang().sendList(sender(ctx), "core.help")))).build(),
                "Información de TukiCore", List.of("core"));

        // ── Mensajes privados ──
        registrar.register(Commands.literal("msg").requires(perm("tukicore.msg"))
                .then(Commands.argument("jugador", ArgumentTypes.player())
                        .then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(ok(ctx -> {
                            if (!(sender(ctx) instanceof Player from)) {
                                lang().send(sender(ctx), "core.player-only");
                                return;
                            }
                            plugin.messages().send(from, players(ctx, "jugador").getFirst(), str(ctx, "mensaje"));
                        })))).build(), "Mensaje privado", List.of("tell", "w", "m", "whisper"));
        registrar.register(Commands.literal("r").requires(perm("tukicore.msg"))
                .then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(ok(ctx -> {
                    if (sender(ctx) instanceof Player from) {
                        plugin.messages().reply(from, str(ctx, "mensaje"));
                    }
                }))).build(), "Responde el último mensaje privado", List.of("reply"));
        registrar.register(Commands.literal("socialspy").requires(perm("tukicore.socialspy")).executes(ok(ctx -> {
            if (sender(ctx) instanceof Player p) {
                lang().send(p, plugin.messages().toggleSpy(p.getUniqueId()) ? "msg.spy-on" : "msg.spy-off");
            }
        })).build(), "Ver los mensajes privados", List.of("spy"));

        // ── Staff ──
        registrar.register(Commands.literal("staffchat").requires(perm("tukicore.staffchat"))
                .executes(ok(ctx -> {
                    if (sender(ctx) instanceof Player p) {
                        lang().send(p, plugin.staffChat().toggle(p.getUniqueId()) ? "staffchat.enabled" : "staffchat.disabled");
                    }
                }))
                .then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(ok(ctx ->
                        plugin.staffChat().send(sender(ctx), str(ctx, "mensaje"))))).build(),
                "Chat del staff", List.of("sc"));
        registrar.register(Commands.literal("broadcast").requires(perm("tukicore.broadcast"))
                .then(Commands.argument("mensaje", StringArgumentType.greedyString()).executes(ok(ctx -> {
                    Component text = lang().format(Text.toMini(str(ctx, "mensaje")), null);
                    Bukkit.getServer().sendMessage(lang().get("broadcast.format", Lang.c("message", text)));
                }))).build(), "Anuncio para todos (códigos & y MiniMessage)", List.of("bc", "anuncio"));
        registrar.register(Commands.literal("invsee").requires(perm("tukicore.invsee"))
                .then(Commands.argument("jugador", ArgumentTypes.player())
                        .executes(ok(ctx -> invsee(ctx, false)))
                        .then(Commands.literal("ender").executes(ok(ctx -> invsee(ctx, true))))).build(),
                "Ver (y con permiso editar) el inventario de un jugador", List.of("inv", "verinv"));
        registrar.register(Commands.literal("clearchat").requires(perm("tukicore.clearchat")).executes(ok(ctx -> {
            Component blank = Component.text(" ");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("tukicore.clearchat.bypass")) {
                    for (int i = 0; i < 100; i++) {
                        p.sendMessage(blank);
                    }
                }
            }
            lang().broadcast("clearchat.done", Lang.p("player", sender(ctx).getName()));
        })).build(), "Limpia el chat", List.of("cc"));
        registrar.register(Commands.literal("mutechat").requires(perm("tukicore.mutechat")).executes(ok(ctx -> {
            boolean muted = !plugin.mutes().chatMuted();
            plugin.mutes().chatMuted(muted);
            lang().broadcast(muted ? "mutechat.enabled" : "mutechat.disabled", Lang.p("player", sender(ctx).getName()));
        })).build(), "Silencia el chat para todos", List.of());
        registrar.register(muteCommand().build(), "Silencia a un jugador", List.of());
        registrar.register(Commands.literal("unmute").requires(perm("tukicore.mute"))
                .then(Commands.argument("jugador", StringArgumentType.word()).suggests(onlineNames).executes(ok(ctx -> {
                    OfflinePlayer target = offline(str(ctx, "jugador"));
                    if (target == null) {
                        lang().send(sender(ctx), "core.unknown-player", Lang.p("player", str(ctx, "jugador")));
                        return;
                    }
                    if (plugin.mutes().unmute(target.getUniqueId())) {
                        lang().send(sender(ctx), "mute.unmuted", Lang.p("player", str(ctx, "jugador")));
                        if (target.getPlayer() != null) {
                            lang().send(target.getPlayer(), "mute.you-were-unmuted");
                        }
                    } else {
                        lang().send(sender(ctx), "mute.not-muted", Lang.p("player", str(ctx, "jugador")));
                    }
                }))).build(), "Quita el silencio a un jugador", List.of());

        // ── Vanish y clientes ──
        registrar.register(Commands.literal("vanish").requires(perm("tukicore.vanish"))
                .executes(ok(ctx -> {
                    if (sender(ctx) instanceof Player p) {
                        plugin.vanish().set(p, !plugin.vanish().isVanished(p));
                    } else {
                        lang().send(sender(ctx), "core.player-only");
                    }
                }))
                .then(Commands.literal("list").executes(ok(ctx -> {
                    var list = plugin.vanish().online().stream().map(Player::getName).toList();
                    lang().send(sender(ctx), "vanish.list", Lang.p("count", list.size()),
                            Lang.p("players", list.isEmpty() ? "-" : String.join(", ", list)));
                })))
                .then(Commands.argument("jugador", ArgumentTypes.player()).requires(perm("tukicore.vanish.others"))
                        .executes(ok(ctx -> {
                            Player target = players(ctx, "jugador").getFirst();
                            plugin.vanish().set(target, !plugin.vanish().isVanished(target));
                            lang().send(sender(ctx), plugin.vanish().isVanished(target) ? "vanish.other-on" : "vanish.other-off",
                                    Lang.p("player", target.getName()));
                        }))).build(), "Te vuelve invisible para los jugadores", List.of("v"));
        registrar.register(Commands.literal("clientinfo").requires(perm("tukicore.clientcheck"))
                .executes(ok(ctx -> showAllClients(sender(ctx))))
                .then(Commands.literal("all").executes(ok(ctx -> showAllClients(sender(ctx)))))
                .then(Commands.argument("jugador", ArgumentTypes.player()).executes(ok(ctx ->
                        plugin.clientCheck().show(sender(ctx), players(ctx, "jugador").getFirst())))).build(),
                "Con qué cliente y mods entró un jugador", List.of("client", "mods", "brand"));

        // ── Modo dev (solo las cuentas premium de dev-mode.players) ──
        registrar.register(Commands.literal("dev").requires(src -> plugin.devMode().allowed(src.getSender()))
                .executes(ok(ctx -> plugin.devMode().toggle((Player) sender(ctx))))
                .then(Commands.literal("info").executes(ok(ctx -> plugin.devMode().info(sender(ctx)))))
                .then(Commands.literal("perm").then(Commands.argument("jugador", ArgumentTypes.player())
                        .then(Commands.argument("permiso", StringArgumentType.greedyString()).executes(ok(ctx ->
                                plugin.devMode().checkPermission(sender(ctx), players(ctx, "jugador").getFirst(),
                                        str(ctx, "permiso").trim())))))).build(),
                "Modo desarrollador", List.of("tukidev"));

        // ── Utilidades ──
        registrar.register(gamemodeCommand().build(), "Cambia el modo de juego", List.of("gamemode"));
        registrar.register(Commands.literal("fly").requires(perm("tukicore.fly"))
                .executes(ok(ctx -> {
                    if (sender(ctx) instanceof Player p) {
                        toggleFly(sender(ctx), p);
                    }
                }))
                .then(Commands.argument("jugadores", ArgumentTypes.players()).requires(perm("tukicore.fly.others"))
                        .executes(ok(ctx -> {
                            for (Player p : players(ctx, "jugadores")) {
                                toggleFly(sender(ctx), p);
                            }
                        }))).build(), "Activa o desactiva el vuelo", List.of());
    }

    private void showAllClients(CommandSender to) {
        lang().send(to, "client.list-header", Lang.p("count", Bukkit.getOnlinePlayers().size()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            to.sendMessage(plugin.clientCheck().describe(p, plugin.clientCheck().inspect(p)));
        }
    }

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

    private LiteralArgumentBuilder<CommandSourceStack> gamemodeCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gm").requires(perm("tukicore.gamemode"));
        for (String key : List.of("survival", "creative", "adventure", "spectator", "0", "1", "2", "3", "s", "c", "a", "sp")) {
            GameMode mode = switch (key) {
                case "survival", "0", "s" -> GameMode.SURVIVAL;
                case "creative", "1", "c" -> GameMode.CREATIVE;
                case "adventure", "2", "a" -> GameMode.ADVENTURE;
                default -> GameMode.SPECTATOR;
            };
            root.then(Commands.literal(key)
                    .executes(ok(ctx -> {
                        if (sender(ctx) instanceof Player p) {
                            setMode(sender(ctx), p, mode);
                        } else {
                            lang().send(sender(ctx), "core.player-only");
                        }
                    }))
                    .then(Commands.argument("jugadores", ArgumentTypes.players()).requires(perm("tukicore.gamemode.others"))
                            .executes(ok(ctx -> {
                                for (Player p : players(ctx, "jugadores")) {
                                    setMode(sender(ctx), p, mode);
                                }
                            }))));
        }
        return root;
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

    private LiteralArgumentBuilder<CommandSourceStack> muteCommand() {
        return Commands.literal("mute").requires(perm("tukicore.mute"))
                .then(Commands.argument("jugador", StringArgumentType.word()).suggests(onlineNames)
                        .executes(ok(ctx -> mute(ctx, "perm", "")))
                        .then(Commands.argument("tiempo", StringArgumentType.word()).suggests((c, b) -> {
                            List.of("10m", "1h", "1d", "7d", "perm").forEach(b::suggest);
                            return b.buildFuture();
                        }).executes(ok(ctx -> mute(ctx, str(ctx, "tiempo"), "")))
                                .then(Commands.argument("razón", StringArgumentType.greedyString())
                                        .executes(ok(ctx -> mute(ctx, str(ctx, "tiempo"), str(ctx, "razón")))))));
    }

    private void mute(CommandContext<CommandSourceStack> ctx, String time, String reason) {
        CommandSender s = sender(ctx);
        String name = str(ctx, "jugador");
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

    private static OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    // ── /rank ────────────────────────────────────────────────

    private RequiredArgumentBuilder<CommandSourceStack, String> rankArg() {
        return Commands.argument("rango", StringArgumentType.word()).suggests(rankNames);
    }

    /** Ejecuta {@code action} sobre el rango del argumento, o avisa si no existe. */
    private void withRank(CommandContext<CommandSourceStack> ctx, Consumer<Rank> action) {
        Rank rank = plugin.ranks().rank(str(ctx, "rango"));
        if (rank == null) {
            lang().send(sender(ctx), "rank.unknown", Lang.p("rank", str(ctx, "rango")));
            return;
        }
        action.accept(rank);
    }

    private static String spaced(String text) {
        return text.isEmpty() || text.endsWith(" ") ? text : text + " ";
    }

    private LiteralArgumentBuilder<CommandSourceStack> rankCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("rank").requires(perm("tukicore.rank"))
                .executes(ok(ctx -> lang().sendList(sender(ctx), "rank.help")));

        root.then(Commands.literal("list").executes(ok(ctx -> {
            CommandSender s = sender(ctx);
            lang().send(s, "rank.list-header", Lang.p("count", plugin.ranks().all().size()),
                    Lang.p("backend", plugin.ranks().backend().name()));
            for (Rank rank : plugin.ranks().sorted()) {
                lang().send(s, "rank.list-line", Lang.p("rank", rank.name()), Lang.p("weight", rank.weight()),
                        Lang.c("rank_prefix", Text.legacy(rank.prefix())), Lang.c("display", Text.legacy(rank.display())));
            }
        })));

        root.then(Commands.literal("info").then(rankArg().executes(ok(ctx -> withRank(ctx, rank -> {
            CommandSender s = sender(ctx);
            lang().sendList(s, "rank.info", Lang.p("rank", rank.name()), Lang.c("display", Text.legacy(rank.display())),
                    Lang.p("weight", rank.weight()), Lang.c("rank_prefix", Text.legacy(rank.prefix())),
                    Lang.c("suffix", Text.legacy(rank.suffix())), Lang.p("prefix_raw", rank.prefix()),
                    Lang.p("color", rank.chatColor().isEmpty() ? "-" : rank.chatColor()),
                    Lang.p("inherits", rank.inherits().isEmpty() ? "-" : String.join(", ", rank.inherits())),
                    Lang.p("permissions", rank.permissions().isEmpty() ? "-" : String.join(", ", rank.permissions())));
        })))));

        root.then(Commands.literal("create").requires(perm("tukicore.rank.edit"))
                .then(Commands.argument("rango", StringArgumentType.word())
                        .executes(ok(ctx -> create(ctx, 10)))
                        .then(Commands.argument("peso", IntegerArgumentType.integer(0, 10000))
                                .executes(ok(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "peso")))))));

        root.then(Commands.literal("delete").requires(perm("tukicore.rank.edit")).then(rankArg().executes(ok(ctx -> {
            String name = str(ctx, "rango");
            lang().send(sender(ctx), plugin.ranks().delete(name) ? "rank.deleted" : "rank.cannot-delete", Lang.p("rank", name));
        }))));

        root.then(Commands.literal("prefix").requires(perm("tukicore.rank.edit")).then(rankArg()
                .then(Commands.argument("texto", StringArgumentType.greedyString()).executes(ok(ctx -> withRank(ctx, rank -> {
                    String value = str(ctx, "texto");
                    String prefix = value.equalsIgnoreCase("none") ? "" : spaced(value);
                    plugin.ranks().edit(rank, r -> r.prefix(prefix));
                    lang().send(sender(ctx), "rank.prefix-set", Lang.p("rank", rank.name()), Lang.c("rank_prefix", Text.legacy(prefix)));
                }))))));

        root.then(Commands.literal("suffix").requires(perm("tukicore.rank.edit")).then(rankArg()
                .then(Commands.argument("texto", StringArgumentType.greedyString()).executes(ok(ctx -> withRank(ctx, rank -> {
                    String value = str(ctx, "texto");
                    String suffix = value.equalsIgnoreCase("none") ? "" : " " + value.stripLeading();
                    plugin.ranks().edit(rank, r -> r.suffix(suffix));
                    lang().send(sender(ctx), "rank.suffix-set", Lang.p("rank", rank.name()), Lang.c("suffix", Text.legacy(suffix)));
                }))))));

        root.then(Commands.literal("display").requires(perm("tukicore.rank.edit")).then(rankArg()
                .then(Commands.argument("texto", StringArgumentType.greedyString()).executes(ok(ctx -> withRank(ctx, rank -> {
                    plugin.ranks().edit(rank, r -> r.display(str(ctx, "texto")));
                    lang().send(sender(ctx), "rank.display-set", Lang.p("rank", rank.name()), Lang.c("display", Text.legacy(rank.display())));
                }))))));

        root.then(Commands.literal("weight").requires(perm("tukicore.rank.edit")).then(rankArg()
                .then(Commands.argument("peso", IntegerArgumentType.integer(0, 10000)).executes(ok(ctx -> withRank(ctx, rank -> {
                    plugin.ranks().edit(rank, r -> r.weight(IntegerArgumentType.getInteger(ctx, "peso")));
                    lang().send(sender(ctx), "rank.weight-set", Lang.p("rank", rank.name()), Lang.p("weight", rank.weight()));
                }))))));

        root.then(Commands.literal("color").requires(perm("tukicore.rank.edit")).then(rankArg()
                .then(Commands.argument("color", StringArgumentType.word()).suggests((c, b) -> {
                    List.of("none", "white", "gray", "yellow", "aqua", "green", "gold", "#FFAA00").forEach(b::suggest);
                    return b.buildFuture();
                }).executes(ok(ctx -> withRank(ctx, rank -> {
                    String value = str(ctx, "color");
                    plugin.ranks().edit(rank, r -> r.chatColor(value.equalsIgnoreCase("none") ? "" : value));
                    lang().send(sender(ctx), "rank.color-set", Lang.p("rank", rank.name()), Lang.p("color", value));
                }))))));

        for (String action : List.of("add", "remove")) {
            root.then(Commands.literal("perm").requires(perm("tukicore.rank.edit")).then(rankArg()
                    .then(Commands.literal(action).then(Commands.argument("permiso", StringArgumentType.greedyString())
                            .executes(ok(ctx -> withRank(ctx, rank -> {
                                String perm = str(ctx, "permiso").trim();
                                boolean add = action.equals("add");
                                plugin.ranks().edit(rank, r -> {
                                    r.permissions().remove(perm);
                                    if (add) {
                                        r.permissions().add(perm);
                                    }
                                });
                                lang().send(sender(ctx), add ? "rank.perm-added" : "rank.perm-removed",
                                        Lang.p("rank", rank.name()), Lang.p("permission", perm));
                            })))))));
            root.then(Commands.literal("inherit").requires(perm("tukicore.rank.edit")).then(rankArg()
                    .then(Commands.literal(action).then(Commands.argument("padre", StringArgumentType.word()).suggests(rankNames)
                            .executes(ok(ctx -> withRank(ctx, rank -> {
                                Rank parent = plugin.ranks().rank(str(ctx, "padre"));
                                if (parent == null || parent == rank) {
                                    lang().send(sender(ctx), "rank.unknown", Lang.p("rank", str(ctx, "padre")));
                                    return;
                                }
                                boolean add = action.equals("add");
                                plugin.ranks().edit(rank, r -> {
                                    r.inherits().remove(parent.name());
                                    if (add) {
                                        r.inherits().add(parent.name());
                                    }
                                });
                                lang().send(sender(ctx), add ? "rank.inherit-added" : "rank.inherit-removed",
                                        Lang.p("rank", rank.name()), Lang.p("parent", parent.name()));
                            })))))));
        }

        root.then(Commands.literal("set").requires(perm("tukicore.rank.set"))
                .then(Commands.argument("jugador", StringArgumentType.word()).suggests(onlineNames)
                        .then(rankArg().executes(ok(ctx -> setRank(ctx, "perm")))
                                .then(Commands.argument("tiempo", StringArgumentType.word()).suggests((c, b) -> {
                                    List.of("1d", "7d", "30d", "perm").forEach(b::suggest);
                                    return b.buildFuture();
                                }).executes(ok(ctx -> setRank(ctx, str(ctx, "tiempo"))))))));

        root.then(Commands.literal("user").then(Commands.argument("jugador", StringArgumentType.word()).suggests(onlineNames)
                .executes(ok(ctx -> {
                    CommandSender s = sender(ctx);
                    String name = str(ctx, "jugador");
                    OfflinePlayer target = offline(name);
                    if (target == null) {
                        lang().send(s, "core.unknown-player", Lang.p("player", name));
                        return;
                    }
                    plugin.ranks().backend().lookup(target.getUniqueId()).thenAccept(a -> Bukkit.getScheduler().runTask(plugin, () ->
                            lang().send(s, "rank.user", Lang.p("player", name), Lang.p("rank", a.rank()),
                                    Lang.p("time", a.expires() == 0 ? "∞" : Durations.format(a.expires() - System.currentTimeMillis() / 1000)))));
                }))));
        return root;
    }

    private void create(CommandContext<CommandSourceStack> ctx, int weight) {
        String name = str(ctx, "rango");
        Rank rank = plugin.ranks().create(name, weight);
        lang().send(sender(ctx), rank != null ? "rank.created" : "rank.cannot-create", Lang.p("rank", name), Lang.p("weight", weight));
    }

    private void setRank(CommandContext<CommandSourceStack> ctx, String time) {
        CommandSender s = sender(ctx);
        String name = str(ctx, "jugador");
        OfflinePlayer target = offline(name);
        Rank rank = plugin.ranks().rank(str(ctx, "rango"));
        if (target == null) {
            lang().send(s, "core.unknown-player", Lang.p("player", name));
            return;
        }
        if (rank == null) {
            lang().send(s, "rank.unknown", Lang.p("rank", str(ctx, "rango")));
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

    private void invsee(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, boolean ender)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(sender(ctx) instanceof Player viewer)) {
            lang().send(sender(ctx), "core.player-only");
            return;
        }
        plugin.inventories().open(viewer, players(ctx, "jugador").get(0), ender);
    }
}
