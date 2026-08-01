package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/**
 * Minimal {@link Player} test double for driving {@link TrinityForgeDungeonGateListener} without a live
 * server. Only {@code hasPermission} and {@code sendMessage} are meaningfully implemented; every other
 * call returns a type default, following the same idiom as {@link FakeBukkitServer} /
 * {@code TrinityForgeLoadConfigMigrationTest}'s fake {@code Plugin}.
 */
final class FakePlayer {

    private FakePlayer() {
    }

    /**
     * @param permissions  the set of permission nodes {@code hasPermission} should answer {@code true}
     *                     for; any other node answers {@code false}
     * @param messagesOut  every string passed to {@code sendMessage(String)} is appended here, in call
     *                     order; pass {@code null} to ignore messages
     */
    static Player create(Set<String> permissions, List<String> messagesOut) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "hasPermission":
                    return args != null && args.length > 0 && permissions.contains(String.valueOf(args[0]));
                case "sendMessage":
                    if (messagesOut != null && args != null && args.length > 0 && args[0] instanceof String s) {
                        messagesOut.add(s);
                    }
                    return null;
                case "getName":
                    return "FakePlayer";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "FakePlayer";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == char.class) return (char) 0;
        return 0;
    }
}
