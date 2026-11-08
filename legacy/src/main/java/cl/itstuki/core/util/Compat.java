package cl.itstuki.core.util;

import com.cryptomorin.xseries.XSound;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Lo que cambia entre versiones (1.8.8 – 26.x) resuelto en un solo lugar, casi todo por reflexión. */
public final class Compat {
    /** 8 para 1.8.8, 12 para 1.12.2… Con el esquema nuevo (26.1, 26.3) vale el año: 26. */
    public static final int MINOR;
    private static final String NAME;

    static {
        Matcher m = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(Bukkit.getBukkitVersion());
        int minor = 8;
        String name = "1.8";
        if (m.find()) {
            int major = Integer.parseInt(m.group(1));
            minor = major == 1 ? Integer.parseInt(m.group(2)) : major;
            name = m.group(0);
        }
        MINOR = minor;
        NAME = name;
    }

    private Compat() {
    }

    public static boolean atLeast(int minor) {
        return MINOR >= minor;
    }

    public static String versionName() {
        return NAME;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) {
        try {
            return type.getMethod(name, args);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static final Method UPDATE_COMMANDS = method(Player.class, "updateCommands");
    private static final Method HIDE = method(Player.class, "hidePlayer", Plugin.class, Player.class);
    private static final Method SHOW = method(Player.class, "showPlayer", Plugin.class, Player.class);
    private static final Method HIDE_OLD = method(Player.class, "hidePlayer", Player.class);
    private static final Method SHOW_OLD = method(Player.class, "showPlayer", Player.class);
    private static final Method COLLIDABLE = method(Player.class, "setCollidable", boolean.class);
    private static final Method BRAND = method(Player.class, "getClientBrandName");

    private static Object call(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /** 1.13+: reenvía la lista de comandos (para que aparezcan/desaparezcan al cambiar permisos). */
    public static void updateCommands(Player player) {
        call(UPDATE_COMMANDS, player);
    }

    public static void hide(Player viewer, Plugin plugin, Player target) {
        if (HIDE != null) {
            call(HIDE, viewer, plugin, target);
        } else {
            call(HIDE_OLD, viewer, target);
        }
    }

    public static void show(Player viewer, Plugin plugin, Player target) {
        if (SHOW != null) {
            call(SHOW, viewer, plugin, target);
        } else {
            call(SHOW_OLD, viewer, target);
        }
    }

    /** 1.9+: sin colisiones (en 1.8 los jugadores no se empujan igual). */
    public static void setCollidable(Player player, boolean collidable) {
        call(COLLIDABLE, player, collidable);
    }

    /** Marca del cliente que da el servidor (Paper moderno), o null si esta versión no la expone. */
    public static String clientBrand(Player player) {
        Object brand = call(BRAND, player);
        return brand instanceof String ? (String) brand : null;
    }

    public static void ping(Player player) {
        try {
            if (XSound.BLOCK_NOTE_BLOCK_PLING.isSupported()) {
                XSound.BLOCK_NOTE_BLOCK_PLING.play(player, 1f, 1.6f);
            }
        } catch (RuntimeException ignored) {
            // Sin sonido en esta versión.
        }
    }

    // ── Rendimiento ──────────────────────────────────────────

    /** TPS de 1, 5 y 15 minutos: Paper/Spigot los exponen distinto según la versión. */
    public static double[] tps() {
        Object server = Bukkit.getServer();
        Object result = call(method(server.getClass(), "getTPS"), server);
        if (result instanceof double[]) {
            return (double[]) result;
        }
        try {
            Method getServer = server.getClass().getMethod("getServer");
            Object nms = getServer.invoke(server);
            Field recent = findField(nms.getClass(), "recentTps");
            if (recent != null) {
                return (double[]) recent.get(nms);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Sin datos de TPS.
        }
        return new double[] {20, 20, 20};
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Sigue con la clase padre.
            }
        }
        return null;
    }

    /** Milisegundos por tick (Paper 1.15+); -1 si no se sabe. */
    public static double mspt() {
        Object server = Bukkit.getServer();
        Object result = call(method(server.getClass(), "getAverageTickTime"), server);
        return result instanceof Double ? (Double) result : -1;
    }
}
