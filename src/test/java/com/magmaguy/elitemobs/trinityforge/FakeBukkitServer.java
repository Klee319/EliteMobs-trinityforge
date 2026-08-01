package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.Bukkit;
import org.bukkit.Server;

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
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getLogger":
                    return logger;
                case "getName":
                    return "FakeBukkitServer";
                case "getVersion":
                case "getBukkitVersion":
                    return "test";
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
