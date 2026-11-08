package cl.itstuki.core.hook;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.rank.Rank;
import cl.itstuki.core.util.Durations;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** %tukicore_rank%, %tukicore_prefix%, %tukicore_suffix%, %tukicore_rank_display%, %tukicore_weight%, %tukicore_muted%… */
final class CoreExpansion extends PlaceholderExpansion {
    private final TukiCore plugin;

    CoreExpansion(TukiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "tukicore";
    }

    @Override
    public String getAuthor() {
        return "ItsTuki";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        switch (params) {
            case "chat_muted":
                return plugin.mutes().chatMuted() ? "true" : "false";
            case "ranks":
                return String.valueOf(plugin.ranks().all().size());
            case "backend":
                return plugin.ranks().backend().name();
            case "online":
                return String.valueOf(org.bukkit.Bukkit.getOnlinePlayers().size() - plugin.vanish().count());
            case "vanished_count":
                return String.valueOf(plugin.vanish().count());
            default:
                break;
        }
        if (offline == null) {
            return "";
        }
        if (params.equals("muted")) {
            return plugin.mutes().mute(offline.getUniqueId()) != null ? "true" : "false";
        }
        if (params.equals("mute_left")) {
            cl.itstuki.core.chat.MuteManager.Mute mute = plugin.mutes().mute(offline.getUniqueId());
            return mute == null ? "" : Durations.format(mute.secondsLeft());
        }
        Player player = offline.getPlayer();
        if (player == null) {
            return "";
        }
        Rank rank = plugin.ranks().rankOf(player);
        switch (params) {
            case "rank":
                return rank.name();
            case "rank_display":
                return rank.display();
            case "prefix":
                return plugin.ranks().prefix(player);
            case "suffix":
                return plugin.ranks().suffix(player);
            case "weight":
                return String.valueOf(rank.weight());
            case "vanished":
                return plugin.vanish().isVanished(player) ? "true" : "false";
            case "client":
                return plugin.clientCheck().inspect(player).loader();
            case "client_brand":
                return plugin.clientCheck().inspect(player).brand();
            default:
                return null;
        }
    }
}
