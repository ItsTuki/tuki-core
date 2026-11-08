package cl.itstuki.core;

import cl.itstuki.core.api.TukiCoreAPI;
import cl.itstuki.core.chat.ChatListener;
import cl.itstuki.core.chat.JoinListener;
import cl.itstuki.core.chat.MuteManager;
import cl.itstuki.core.chat.PrivateMessages;
import cl.itstuki.core.chat.StaffChat;
import cl.itstuki.core.chat.TabManager;
import cl.itstuki.core.command.CoreCommands;
import cl.itstuki.core.hook.PlaceholderHook;
import cl.itstuki.core.lang.Lang;
import cl.itstuki.core.rank.RankManager;
import cl.itstuki.core.staff.ClientCheck;
import cl.itstuki.core.staff.DevMode;
import cl.itstuki.core.staff.VanishManager;
import cl.itstuki.core.util.Credits;
import cl.itstuki.core.util.Text;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

/** TukiCore (edición Legacy, 1.8.8 – 26.x): rangos, chat y moderación. Compatible con TukiUHC. */
public final class TukiCore extends JavaPlugin {
    private Lang lang;
    private RankManager ranks;
    private MuteManager mutes;
    private TabManager tab;
    private StaffChat staffChat;
    private PrivateMessages messages;
    private VanishManager vanish;
    private ClientCheck clientCheck;
    private DevMode devMode;
    private final PlaceholderHook placeholders = new PlaceholderHook();
    private BukkitAudiences adventure;

    @Override
    public void onEnable() {
        adventure = BukkitAudiences.create(this);
        saveDefaultConfig();
        lang = new Lang(this);
        lang.load(getConfig().getString("language", "es"));
        ranks = new RankManager(this);
        ranks.load();
        mutes = new MuteManager(this);
        tab = new TabManager(this);
        staffChat = new StaffChat(this);
        messages = new PrivateMessages(this);
        vanish = new VanishManager(this);
        clientCheck = new ClientCheck(this);
        devMode = new DevMode(this);
        ranks.startBackend();
        TukiCoreAPI.init(this);
        placeholders.enable(this);
        lang.playerTags(player -> TagResolver.resolver(
                TagResolver.resolver("rank", (args, ctx) -> Tag.selfClosingInserting(
                        player == null ? net.kyori.adventure.text.Component.empty() : Text.legacy(ranks.prefix(player)))),
                placeholders.tags(player)));

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(vanish, this);
        vanish.registerPickup();
        getServer().getPluginManager().registerEvents(clientCheck, this);
        clientCheck.registerChannel();
        vanish.start();
        getServer().getPluginManager().registerEvents(devMode, this);
        devMode.start();
        tab.start();

        new CoreCommands(this).register();
        Credits.enableBanner(this, ranks.all().size(), ranks.backend().name());
    }

    @Override
    public void onDisable() {
        if (devMode != null) {
            devMode.stop();
        }
        placeholders.disable();
        if (clientCheck != null) {
            clientCheck.unregisterChannel();
        }
        if (tab != null) {
            tab.cleanup();
        }
        if (ranks != null) {
            ranks.stopBackend();
        }
        TukiCoreAPI.init(null);
        Credits.disableBanner(this);
        if (adventure != null) {
            adventure.close();
            adventure = null;
        }
    }

    public void reloadAll() {
        reloadConfig();
        lang.load(getConfig().getString("language", "es"));
        ranks.stopBackend();
        ranks.load();
        ranks.startBackend();
        tab.refreshAll();
    }

    public BukkitAudiences adventure() {
        if (adventure == null) {
            throw new IllegalStateException("TukiCore no está activo");
        }
        return adventure;
    }

    /** Mensaje con formato a cualquier jugador o a la consola (en 1.8 no hay componentes nativos). */
    public void msg(CommandSender to, Component message) {
        adventure().sender(to).sendMessage(message);
    }

    public void actionBar(Player player, Component message) {
        adventure().player(player).sendActionBar(message);
    }

    public Lang lang() { return lang; }
    public RankManager ranks() { return ranks; }
    public MuteManager mutes() { return mutes; }
    public TabManager tab() { return tab; }
    public StaffChat staffChat() { return staffChat; }
    public PrivateMessages messages() { return messages; }
    public VanishManager vanish() { return vanish; }
    public ClientCheck clientCheck() { return clientCheck; }
    public DevMode devMode() { return devMode; }
}
