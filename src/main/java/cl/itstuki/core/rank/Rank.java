package cl.itstuki.core.rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

/** Un rango definido en ranks.yml. */
public final class Rank {
    private final String name;
    private String display;
    private String prefix;
    private String suffix;
    private int weight;
    private String chatColor;
    private String chatFormat;
    private final List<String> permissions;
    private final List<String> inherits;

    public Rank(String name, int weight) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.display = name;
        this.prefix = "";
        this.suffix = "";
        this.weight = weight;
        this.chatColor = "";
        this.chatFormat = "";
        this.permissions = new ArrayList<>();
        this.inherits = new ArrayList<>();
    }

    static Rank load(String name, ConfigurationSection section) {
        Rank rank = new Rank(name, section.getInt("weight", 0));
        rank.display = section.getString("display", name);
        rank.prefix = section.getString("prefix", "");
        rank.suffix = section.getString("suffix", "");
        rank.chatColor = section.getString("chat-color", "");
        rank.chatFormat = section.getString("chat-format", "");
        rank.permissions.addAll(section.getStringList("permissions"));
        rank.inherits.addAll(section.getStringList("inherits").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
        return rank;
    }

    void save(ConfigurationSection section) {
        section.set("display", display);
        section.set("weight", weight);
        section.set("prefix", prefix);
        section.set("suffix", suffix);
        section.set("chat-color", chatColor.isEmpty() ? null : chatColor);
        section.set("chat-format", chatFormat.isEmpty() ? null : chatFormat);
        section.set("permissions", permissions);
        section.set("inherits", inherits);
    }

    public String name() { return name; }
    public String display() { return display; }
    public void display(String display) { this.display = display; }
    public String prefix() { return prefix; }
    public void prefix(String prefix) { this.prefix = prefix; }
    public String suffix() { return suffix; }
    public void suffix(String suffix) { this.suffix = suffix; }
    public int weight() { return weight; }
    public void weight(int weight) { this.weight = weight; }
    /** Color del mensaje en el chat: nombre (white, gray…) o #RRGGBB. Vacío = el del formato. */
    public String chatColor() { return chatColor; }
    public void chatColor(String chatColor) { this.chatColor = chatColor; }
    /** Formato de chat propio del rango (vacío = chat.format de config.yml). */
    public String chatFormat() { return chatFormat; }
    public List<String> permissions() { return permissions; }
    public List<String> inherits() { return inherits; }
}
