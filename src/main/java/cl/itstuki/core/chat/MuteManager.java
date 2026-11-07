package cl.itstuki.core.chat;

import cl.itstuki.core.TukiCore;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/** Silencios de jugadores (mutes.yml) y silencio global del chat. */
public final class MuteManager {
    public record Mute(long until, String reason, String by) {
        public boolean permanent() {
            return until == 0;
        }

        public long secondsLeft() {
            return permanent() ? 0 : Math.max(0, until - System.currentTimeMillis() / 1000);
        }
    }

    private final TukiCore plugin;
    private final File file;
    private YamlConfiguration data;
    private volatile boolean chatMuted;

    public MuteManager(TukiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized @Nullable Mute mute(UUID id) {
        String path = "mutes." + id;
        if (!data.contains(path)) {
            return null;
        }
        Mute mute = new Mute(data.getLong(path + ".until"), data.getString(path + ".reason", ""), data.getString(path + ".by", ""));
        if (!mute.permanent() && mute.secondsLeft() <= 0) {
            data.set(path, null);
            save();
            return null;
        }
        return mute;
    }

    public synchronized void mute(UUID id, String name, long seconds, String reason, String by) {
        String path = "mutes." + id;
        data.set(path + ".name", name);
        data.set(path + ".until", seconds > 0 ? System.currentTimeMillis() / 1000 + seconds : 0);
        data.set(path + ".reason", reason);
        data.set(path + ".by", by);
        save();
    }

    public synchronized boolean unmute(UUID id) {
        String path = "mutes." + id;
        if (!data.contains(path)) {
            return false;
        }
        data.set(path, null);
        save();
        return true;
    }

    public boolean chatMuted() {
        return chatMuted;
    }

    public void chatMuted(boolean muted) {
        this.chatMuted = muted;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar mutes.yml: " + ex.getMessage());
        }
    }
}
