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
import cl.itstuki.core.staff.VanishManager;
import cl.itstuki.core.util.Credits;
import cl.itstuki.core.util.Text;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.plugin.java.JavaPlugin;

/** TukiCore: rangos, chat y moderación para servidores Paper. Compatible con TukiUHC. */
public final class TukiCore extends JavaPlugin {
    private Lang lang;
    private RankManager ranks;
    private MuteManager mutes;
    private TabManager tab;
    private StaffChat staffChat;
    private PrivateMessages messages;
    private VanishManager vanish;
    private ClientCheck clientCheck;
    private final PlaceholderHook placeholders = new PlaceholderHook();

    @Override
    public void onEnable() {
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
        getServer().getPluginManager().registerEvents(clientCheck, this);
        vanish.start();
        tab.start();

        CoreCommands commands = new CoreCommands(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));
        Credits.enableBanner(this, ranks.all().size(), ranks.backend().name());
    }

    @Override
    public void onDisable() {
        placeholders.disable();
        if (ranks != null) {
            ranks.stopBackend();
        }
        TukiCoreAPI.init(null);
        Credits.disableBanner(this);
    }

    public void reloadAll() {
        reloadConfig();
        lang.load(getConfig().getString("language", "es"));
        ranks.stopBackend();
        ranks.load();
        ranks.startBackend();
        tab.refreshAll();
    }

    public Lang lang() { return lang; }
    public RankManager ranks() { return ranks; }
    public MuteManager mutes() { return mutes; }
    public TabManager tab() { return tab; }
    public StaffChat staffChat() { return staffChat; }
    public PrivateMessages messages() { return messages; }
    public VanishManager vanish() { return vanish; }
    public ClientCheck clientCheck() { return clientCheck; }
}
