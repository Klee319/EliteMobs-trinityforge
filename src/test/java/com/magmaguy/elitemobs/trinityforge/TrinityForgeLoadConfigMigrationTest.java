package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives the REAL {@code TrinityForgeIntegration#loadConfig} against a fake {@link Plugin} to prove
 * that an already-existing {@code trinityforge.yml} actually gains the sections a newer jar ships.
 * <p>
 * This is the test that dies if the migration call is dropped out of {@code loadConfig}: the file is
 * then left exactly as the admin had it, and the new defaults apply with no key to change them by —
 * which is the whole HIGH-2 finding. A bytecode wiring assertion cannot catch that (the private
 * migration method would still be there, just never called), so this drives the entry point instead.
 */
class TrinityForgeLoadConfigMigrationTest {

    @BeforeEach
    void installServer() {
        // loadConfig announces what it appended through MagmaCore's Logger, which writes to
        // Bukkit.getLogger(). Announcing the change is part of the requirement, so the test provides a
        // server rather than the production code skipping the log.
        FakeBukkitServer.install();
    }

    @AfterEach
    void reset() {
        IntegrationState.reset();
    }

    @Test
    @DisplayName("loadConfig adds the new sections to an existing file and keeps the admin's values")
    void existingConfigGainsNewSections(@TempDir Path dataFolder) throws Exception {
        // A server that installed the fork before 2026-08-01: no elite-drop-sources, no
        // native-display-suppression, and one toggle the admin turned off by hand.
        Path config = dataFolder.resolve("trinityforge.yml");
        Files.writeString(config, String.join("\n",
                "# my own note",
                "gear-neutralization: false",
                "combat-delegation: true",
                ""), StandardCharsets.UTF_8);

        loadConfig(pluginFor(dataFolder));

        String result = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(result.contains("# my own note"), "the admin's own comment must survive");
        assertTrue(result.contains("gear-neutralization: false"), "the admin's value must survive");
        assertEquals(1, occurrences(result, "\ngear-neutralization:") + occurrences("\n" + result, "\ngear-neutralization:") - 1,
                "no duplicate key: a duplicated YAML key silently resolves to the LAST one, i.e. the shipped default");
        assertTrue(result.contains("elite-drop-sources:"),
                "a jar swap must not change drop behaviour without the key appearing in the file");
        assertTrue(result.contains("native-display-suppression:"),
                "a jar swap must not hide every nametag without the key appearing in the file");
        assertTrue(result.contains("random-loot:"));
        assertTrue(result.contains("nametag:"));
    }

    @Test
    @DisplayName("loadConfig leaves an already-complete file byte-for-byte alone")
    void completeConfigIsNotRewritten(@TempDir Path dataFolder) throws Exception {
        Path config = dataFolder.resolve("trinityforge.yml");
        // Start from the shipped resource itself: every key is present.
        Files.writeString(config, shippedResource(), StandardCharsets.UTF_8);
        String before = Files.readString(config, StandardCharsets.UTF_8);

        loadConfig(pluginFor(dataFolder));

        assertEquals(before, Files.readString(config, StandardCharsets.UTF_8),
                "the file must not grow or be reformatted on every boot");
    }

    @Test
    @DisplayName("loadConfig reads the values it just wrote (the appended defaults take effect immediately)")
    void appendedDefaultsAreLoaded(@TempDir Path dataFolder) throws Exception {
        Path config = dataFolder.resolve("trinityforge.yml");
        Files.writeString(config, "gear-neutralization: true\n", StandardCharsets.UTF_8);
        // Poison the in-memory state so a value can only become correct by being read from the file.
        IntegrationState.set("allowRandomEliteLoot", true);
        IntegrationState.set("suppressBossTrackingBar", true);

        loadConfig(pluginFor(dataFolder));

        IntegrationState.set("available", true);
        assertFalse(TrinityForgeIntegration.isRandomEliteLootAllowed(),
                "elite-drop-sources.random-loot ships as false and must be read back from the migrated file");
        assertFalse(TrinityForgeIntegration.isSuppressBossTrackingBarEnabled(),
                "native-display-suppression.boss-tracking-bar ships as false and must be read back");
    }

    private static void loadConfig(Plugin plugin) throws Exception {
        Method method = TrinityForgeIntegration.class.getDeclaredMethod("loadConfig", Plugin.class);
        method.setAccessible(true);
        method.invoke(null, plugin);
    }

    private static String shippedResource() throws Exception {
        try (InputStream in = TrinityForgeLoadConfigMigrationTest.class.getResourceAsStream("/trinityforge.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Minimal {@link Plugin} stand-in: a data folder and the jar's own resource stream. {@code
     * saveResource} fails the test — with the file already present it must never be reached, and if it
     * ever were it would be the "overwrite the admin's file" bug this whole feature exists to avoid.
     */
    private static Plugin pluginFor(Path dataFolder) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDataFolder":
                    return dataFolder.toFile();
                case "getResource":
                    return TrinityForgeLoadConfigMigrationTest.class.getResourceAsStream("/" + args[0]);
                case "saveResource":
                    return fail("loadConfig must not call saveResource when the config file already exists");
                case "getName":
                    return "EliteMobs";
                case "getLogger":
                    // migrateExistingConfig announces the appended sections through plugin.getLogger().
                    // A null here made the announcement NPE *inside* the try-block and the catch block's
                    // own getLogger() NPE again, so the migration looked broken when only the fake was.
                    return java.util.logging.Logger.getLogger("EliteMobsTestPlugin");
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "FakePlugin";
                default:
                    return method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
            }
        };
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, handler);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == char.class) return (char) 0;
        return 0;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }
}
