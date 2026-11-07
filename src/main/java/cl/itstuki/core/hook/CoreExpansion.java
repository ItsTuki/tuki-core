package cl.itstuki.core.hook;

import cl.itstuki.core.TukiCore;
import cl.itstuki.core.rank.Rank;
import cl.itstuki.core.util.Durations;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

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
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offline, String params) {
        switch (params) {
            case "chat_muted":
                return plugin.mutes().chatMuted() ? "true" : "false";
            case "ranks":
                return String.valueOf(plugin.ranks().all().size());
            case "backend":
                return plugin.ranks().backend().name();
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
            var mute = plugin.mutes().mute(offline.getUniqueId());
            return mute == null ? "" : Durations.format(mute.secondsLeft());
        }
        Player player = offline.getPlayer();
        if (player == null) {
            return "";
        }
        Rank rank = plugin.ranks().rankOf(player);
        return switch (params) {
            case "rank" -> rank.name();
            case "rank_display" -> rank.display();
            case "prefix" -> plugin.ranks().prefix(player);
            case "suffix" -> plugin.ranks().suffix(player);
            case "weight" -> String.valueOf(rank.weight());
            default -> null;
        };
    }
}
