package com.magmaguy.elitemobs.trinityforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TrinityForgeConfigMigration}: the fix for "a new default reaches a live server
 * without ever appearing in its config file".
 * <p>
 * The scenario being pinned is a server that upgraded the jar with a {@code trinityforge.yml} it has
 * already edited. {@code saveResource(..., false)} does nothing for it, so before this migration the
 * new {@code elite-drop-sources:} / {@code native-display-suppression:} sections never landed on disk
 * and the admin had no key to turn the new behaviour off with.
 */
class TrinityForgeConfigMigrationTest {

    @Test
    @DisplayName("a section is split together with the comment block that documents it")
    void blocksCarryTheirDocumentation() {
        String shipped = String.join("\n",
                "# file header",
                "",
                "# doc for alpha",
                "alpha: true",
                "",
                "# doc for beta line 1",
                "# doc for beta line 2",
                "beta:",
                "  # nested comment",
                "  child: 1",
                "");

        LinkedHashMap<String, String> blocks = TrinityForgeConfigMigration.splitTopLevelBlocks(shipped);

        assertEquals(List.of("alpha", "beta"), new ArrayList<>(blocks.keySet()), "file order must be preserved");
        assertEquals("# doc for alpha\nalpha: true", blocks.get("alpha"));
        assertEquals("# doc for beta line 1\n# doc for beta line 2\nbeta:\n  # nested comment\n  child: 1",
                blocks.get("beta"));
        assertFalse(blocks.get("alpha").contains("file header"),
                "the top-of-file header must not be glued onto the first key (it would be duplicated)");
    }

    @Test
    @DisplayName("indented keys and comment-only lines are not mistaken for sections")
    void onlyTopLevelKeysAreSections() {
        String shipped = String.join("\n",
                "parent:",
                "  child: 1",
                "  # commented-out-example: 2",
                "list:",
                "  - item: 3",
                "");

        assertEquals(List.of("parent", "list"),
                new ArrayList<>(TrinityForgeConfigMigration.splitTopLevelBlocks(shipped).keySet()));
    }

    @Test
    @DisplayName("nothing is appended when the on-disk file already has every section")
    void completeFileGetsNoAppendix() {
        LinkedHashMap<String, String> blocks =
                TrinityForgeConfigMigration.splitTopLevelBlocks("alpha: true\n\nbeta: false\n");
        List<String> added = new ArrayList<>();

        assertEquals("", TrinityForgeConfigMigration.buildAppendix(blocks, Set.of("alpha", "beta"), added));
        assertTrue(added.isEmpty());
    }

    @Test
    @DisplayName("only the missing sections are appended, in file order")
    void onlyMissingSectionsAreAppended() {
        LinkedHashMap<String, String> blocks = TrinityForgeConfigMigration.splitTopLevelBlocks(
                "alpha: true\n\n# doc\nbeta: false\n\ngamma: 3\n");
        List<String> added = new ArrayList<>();

        String appendix = TrinityForgeConfigMigration.buildAppendix(blocks, Set.of("alpha"), added);

        assertEquals(List.of("beta", "gamma"), added);
        assertFalse(appendix.contains("alpha"), "an existing key must never be re-emitted (it would override the "
                + "admin's value: a duplicate YAML key resolves to the LAST one)");
        assertTrue(appendix.contains("beta: false"));
        assertTrue(appendix.contains("# doc"), "the documentation comment must come along");
        assertTrue(appendix.contains("gamma: 3"));
    }

    @Test
    @DisplayName("an existing file keeps its edited values and gains the new sections")
    void existingFileIsAppendedNotRewritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trinityforge.yml");
        // The admin edited gear-neutralization and deleted a comment. Neither may be touched.
        Files.writeString(file, "gear-neutralization: false\nhate-targeting: true\n", StandardCharsets.UTF_8);
        String shipped = String.join("\n",
                "# doc for gear-neutralization",
                "gear-neutralization: true",
                "",
                "# doc for hate-targeting",
                "hate-targeting: true",
                "",
                "# doc for elite-drop-sources",
                "elite-drop-sources:",
                "  random-loot: false",
                "");

        List<String> added = TrinityForgeConfigMigration.appendMissingKeys(
                file, Set.of("gear-neutralization", "hate-targeting"), shipped);
        String result = Files.readString(file, StandardCharsets.UTF_8);

        assertEquals(List.of("elite-drop-sources"), added);
        assertTrue(result.startsWith("gear-neutralization: false"), "the admin's value must survive verbatim");
        assertEquals(1, countOccurrences(result, "gear-neutralization:"), "no duplicate key may be introduced");
        assertEquals(1, countOccurrences(result, "hate-targeting:"));
        assertTrue(result.contains("elite-drop-sources:"), "the new section must now be visible in the file");
        assertTrue(result.contains("random-loot: false"));
        assertTrue(result.contains(TrinityForgeConfigMigration.APPENDIX_HEADER.split("\n")[1]),
                "the appended block must say where it came from");
    }

    @Test
    @DisplayName("a second run is a no-op (the migration is idempotent)")
    void migrationIsIdempotent(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trinityforge.yml");
        Files.writeString(file, "alpha: true\n", StandardCharsets.UTF_8);
        String shipped = "alpha: true\n\nbeta: false\n";

        TrinityForgeConfigMigration.appendMissingKeys(file, Set.of("alpha"), shipped);
        String afterFirst = Files.readString(file, StandardCharsets.UTF_8);

        // Second startup: the loader now reports beta as present.
        List<String> added = TrinityForgeConfigMigration.appendMissingKeys(file, Set.of("alpha", "beta"), shipped);

        assertTrue(added.isEmpty());
        assertEquals(afterFirst, Files.readString(file, StandardCharsets.UTF_8), "the file must not grow every boot");
    }

    @Test
    @DisplayName("the sections whose defaults changed on 2026-08-01 are migratable out of the real resource")
    void shippedResourceExposesTheNewSections() throws IOException {
        String shipped = shippedResource();
        LinkedHashMap<String, String> blocks = TrinityForgeConfigMigration.splitTopLevelBlocks(shipped);

        // These two sections are exactly the ones that a jar swap would otherwise apply invisibly:
        // every random drop source blocked, every nametag hidden.
        assertTrue(blocks.containsKey("elite-drop-sources"), "shipped trinityforge.yml lost elite-drop-sources");
        assertTrue(blocks.containsKey("native-display-suppression"),
                "shipped trinityforge.yml lost native-display-suppression");
        assertTrue(blocks.containsKey("dungeon-entry-gate"),
                "shipped trinityforge.yml lost the dungeon-entry-gate emergency-stop switch (HIGH-2)");
        assertTrue(blocks.get("dungeon-entry-gate").endsWith("dungeon-entry-gate: true"),
                "the shipped default must be true (= current delegated behaviour), not the old false default");

        String dropSources = blocks.get("elite-drop-sources");
        for (String key : new String[]{"random-loot:", "special-loot:", "elite-scroll:",
                "vanilla-loot-multiplier:", "vanilla-loot:", "currency-shower:", "boss-unique-loot:"}) {
            assertTrue(dropSources.contains(key), "elite-drop-sources block is missing " + key);
        }
        for (String key : new String[]{"nametag:", "custom-model-nametag:", "boss-tracking-bar:"}) {
            assertTrue(blocks.get("native-display-suppression").contains(key),
                    "native-display-suppression block is missing " + key);
        }
    }

    @Test
    @DisplayName("an old file that predates both sections gets exactly them appended")
    void preUpgradeFileGetsBothNewSections(@TempDir Path dir) throws IOException {
        String shipped = shippedResource();
        LinkedHashMap<String, String> blocks = TrinityForgeConfigMigration.splitTopLevelBlocks(shipped);
        Set<String> preUpgradeKeys = new java.util.LinkedHashSet<>(blocks.keySet());
        preUpgradeKeys.remove("elite-drop-sources");
        preUpgradeKeys.remove("native-display-suppression");

        Path file = dir.resolve("trinityforge.yml");
        Files.writeString(file, "gear-neutralization: true\n", StandardCharsets.UTF_8);

        List<String> added = TrinityForgeConfigMigration.appendMissingKeys(file, preUpgradeKeys, shipped);

        assertEquals(List.of("native-display-suppression", "elite-drop-sources"), added,
                "both new sections must be written out, in the order the shipped file declares them");
        String result = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(result.contains("random-loot: false"), "the admin must be able to see and flip the new default");
        assertTrue(result.contains("nametag: true"));
    }

    @Test
    @DisplayName("a server whose file predates dungeon-entry-gate gets it appended, defaulting to true")
    void preUpgradeFileGainsDungeonEntryGate(@TempDir Path dir) throws IOException {
        // HIGH-2: the old dungeon-entry-gate.enabled key (default false) was removed on 2026-08-01
        // without a replacement, turning the gate from opt-in-off to always-on with no way to disable
        // it. The new dungeon-entry-gate key (default true, matches the now-current delegated behaviour)
        // must reach an already-existing server file the same way every other new section does.
        String shipped = shippedResource();
        LinkedHashMap<String, String> blocks = TrinityForgeConfigMigration.splitTopLevelBlocks(shipped);
        Set<String> preUpgradeKeys = new java.util.LinkedHashSet<>(blocks.keySet());
        preUpgradeKeys.remove("dungeon-entry-gate");

        Path file = dir.resolve("trinityforge.yml");
        Files.writeString(file, "gear-neutralization: true\n", StandardCharsets.UTF_8);

        List<String> added = TrinityForgeConfigMigration.appendMissingKeys(file, preUpgradeKeys, shipped);

        assertTrue(added.contains("dungeon-entry-gate"), "dungeon-entry-gate must be appended for a pre-upgrade file");
        String result = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(result.contains("dungeon-entry-gate: true"),
                "the appended key must carry the new true default, not the old false default");
    }

    private static String shippedResource() throws IOException {
        try (InputStream in = TrinityForgeConfigMigrationTest.class.getResourceAsStream("/trinityforge.yml")) {
            assertNotNull(in, "trinityforge.yml is not on the classpath — is it still in src/main/resources?");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }
}
