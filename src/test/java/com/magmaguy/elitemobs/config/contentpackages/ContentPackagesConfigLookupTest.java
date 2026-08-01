package com.magmaguy.elitemobs.config.contentpackages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression test for {@link ContentPackagesConfig#getAnyPackage(String)}.
 * <p>
 * {@code ContentPackagesConfig} splits its packages into two indexes and deliberately keeps every
 * {@code isEnchantmentChallenge()} package OUT of {@code dungeonPackages}. Entering a dungeon used to
 * resolve through {@code dungeonPackages} alone, so every enchantment-challenge sanctum reported
 * "Failed to get data for dungeon ...!" on entry (reported from the live server as
 * 「エンチャント試練1~10が存在しない」, 2026-08-01). These tests pin the both-index lookup.
 * <p>
 * The static indexes are populated by reflection: the real constructor reads config files from a live
 * plugin data folder, which is unavailable headless.
 */
class ContentPackagesConfigLookupTest {

    @AfterEach
    void clearIndexes() throws Exception {
        setIndex("dungeonPackages", new HashMap<>());
        setIndex("enchantedChallengeDungeonPackages", new HashMap<>());
    }

    @Test
    @DisplayName("getAnyPackage finds an ordinary dungeon package")
    void findsOrdinaryDungeonPackage() throws Exception {
        ContentPackagesConfigFields ordinary = fields("the_deep_mines_dungeon.yml");
        setIndex("dungeonPackages", Map.of("the_deep_mines_dungeon.yml", ordinary));
        setIndex("enchantedChallengeDungeonPackages", new HashMap<>());

        assertSame(ordinary, ContentPackagesConfig.getAnyPackage("the_deep_mines_dungeon.yml"));
    }

    @Test
    @DisplayName("getAnyPackage finds an enchantment-challenge package that dungeonPackages excludes")
    void findsEnchantmentChallengePackage() throws Exception {
        ContentPackagesConfigFields challenge = fields("enchantment_challenge_1_sanctum.yml");
        setIndex("dungeonPackages", new HashMap<>());
        setIndex("enchantedChallengeDungeonPackages",
                Map.of("enchantment_challenge_1_sanctum.yml", challenge));

        // The old lookup (dungeonPackages only) returned null here — that null was the bug.
        assertNull(ContentPackagesConfig.getDungeonPackages().get("enchantment_challenge_1_sanctum.yml"));
        assertNotNull(ContentPackagesConfig.getAnyPackage("enchantment_challenge_1_sanctum.yml"));
        assertSame(challenge, ContentPackagesConfig.getAnyPackage("enchantment_challenge_1_sanctum.yml"));
    }

    @Test
    @DisplayName("getAnyPackage returns null for an unknown name and tolerates null")
    void unknownAndNullAreNull() throws Exception {
        setIndex("dungeonPackages", new HashMap<>());
        setIndex("enchantedChallengeDungeonPackages", new HashMap<>());

        assertNull(ContentPackagesConfig.getAnyPackage("no_such_dungeon.yml"));
        assertNull(ContentPackagesConfig.getAnyPackage(null));
    }

    private static ContentPackagesConfigFields fields(String filename) {
        return new ContentPackagesConfigFields(filename, true);
    }

    @SuppressWarnings("unchecked")
    private static void setIndex(String fieldName, Map<String, ContentPackagesConfigFields> value)
            throws Exception {
        Field field = ContentPackagesConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
