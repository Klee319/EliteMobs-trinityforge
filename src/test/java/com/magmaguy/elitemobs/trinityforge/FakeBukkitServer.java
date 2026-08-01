package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs a do-nothing {@link Server} into {@link Bukkit} so production code that logs through
 * MagmaCore's {@code Logger} (which goes to {@code Bukkit.getLogger()}) can be exercised from a unit
 * test without a server.
 * <p>
 * Deliberately NOT a general-purpose mock: every call other than {@code getLogger} returns a type
 * default. If a test starts depending on real server behaviour it should say so loudly by failing here
 * rather than quietly getting {@code null}.
 * <p>
 * {@link Bukkit#setServer(Server)} is a one-shot singleton, so this is idempotent and JVM-wide; the
 * logger is muted so a passing build stays readable.
 */
final class FakeBukkitServer {

    private static boolean installed = false;

    private FakeBukkitServer() {
    }

    static synchronized void install() {
        if (installed || Bukkit.getServer() != null) {
            installed = true;
            return;
        }
        Logger logger = Logger.getLogger("FakeBukkitServer");
        logger.setLevel(Level.OFF);
        PluginManager pluginManager = noPluginsEnabledPluginManager();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLogger":
                    return logger;
                case "getName":
                    return "FakeBukkitServer";
                case "getVersion":
                case "getBukkitVersion":
                    return "test";
                case "getPluginManager":
                    // Without this, Bukkit.getPluginManager() returns null (PluginManager isn't
                    // primitive) and any production code that checks isPluginEnabled(...) — e.g.
                    // CustomBossEntity#setNameVisible's LibsDisguises check — NPEs instead of taking the
                    // "not installed" branch a bare test JVM actually is in.
                    return pluginManager;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "FakeBukkitServer";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        Bukkit.setServer((Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(), new Class<?>[]{Server.class}, handler));
        installed = true;
    }

    /** A {@link PluginManager} that reports every plugin (LibsDisguises included) as not enabled. */
    private static PluginManager noPluginsEnabledPluginManager() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "isPluginEnabled":
                    return false;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "FakeBukkitServer.PluginManager";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == char.class) return (char) 0;
        return 0;
    }
}
