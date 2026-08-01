package com.magmaguy.elitemobs.trinityforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the WIRING: every production class that is supposed to consult a TrinityForge gate must still
 * reference the policy method that makes that decision.
 * <p>
 * <b>Why this test exists.</b> A review on 2026-08-01 mutated the fork by deleting the gate from
 * {@code LootTables#generatePlayerLoot}, the {@code vanilla-loot} clause from
 * {@code LootTables#onDeath} and the whole suppression block from {@code EliteEntity#setNameVisible},
 * and all 31 tests stayed green — the suite only ever touched {@link TrinityForgeIntegration}'s
 * getters. Behavioural tests ({@link EliteDropPolicyTest}, {@link NativeDisplayPolicyTest},
 * {@link EliteEntityNametagTest}) fix what each decision RETURNS; this one fixes the fact that the
 * production code still ASKS. Together, removing a gate from any of these call sites fails the build.
 * <p>
 * The check reads the compiled class file and looks for the method name in its constant pool: a
 * {@code javac}-emitted method reference puts the callee's owner and name there, and deleting the call
 * removes them. It is deliberately dumb — no bytecode library, no reflection into private methods — so
 * it cannot rot on its own. Its one blind spot, stated plainly: a call whose RESULT is ignored
 * ({@code Policy.shouldX(); doItAnyway();}) still leaves the constant-pool entry, which is why the
 * behavioural tests above are the primary defence and this is the backstop.
 */
class TrinityForgeGateWiringTest {

    @Test
    @DisplayName("LootTables consults every drop gate it owns")
    void lootTablesIsWired() throws IOException {
        String bytecode = constantPoolOf("com/magmaguy/elitemobs/items/LootTables");
        assertReferences(bytecode, "LootTables", "EliteDropPolicy",
                "shouldRunCurrencyShower", "shouldGenerateRandomEliteLoot", "shouldRollSpecialLoot",
                "shouldRollEliteScroll", "clearVanillaDropsIfBlocked");
    }

    @Test
    @DisplayName("DefaultDropsHandler consults the vanilla-loot-multiplier gate")
    void defaultDropsHandlerIsWired() throws IOException {
        String bytecode = constantPoolOf("com/magmaguy/elitemobs/items/DefaultDropsHandler");
        assertReferences(bytecode, "DefaultDropsHandler", "EliteDropPolicy", "shouldApplyVanillaLootMultiplier");
    }

    @Test
    @DisplayName("CustomBossDeath consults the boss-unique-loot gate")
    void customBossDeathIsWired() throws IOException {
        String bytecode = constantPoolOf("com/magmaguy/elitemobs/mobconstructor/custombosses/CustomBossDeath");
        assertReferences(bytecode, "CustomBossDeath", "EliteDropPolicy", "shouldDropBossUniqueLoot");
    }

    @Test
    @DisplayName("all four currency-shower paths consult the same gate")
    void everyCurrencyShowerPathIsWired() throws IOException {
        // 2026-08-01 round2: only LootTables was gated, so the bonus_coins power, the Lua scripting API
        // and custom loot tables all kept paying out with currency-shower: false.
        String[][] paths = {
                {"com/magmaguy/elitemobs/items/LootTables", "LootTables"},
                {"com/magmaguy/elitemobs/powers/BonusCoins", "BonusCoins"},
                {"com/magmaguy/elitemobs/powers/lua/LuaPowerSupport", "LuaPowerSupport"},
                {"com/magmaguy/elitemobs/items/customloottable/CurrencyCustomLootEntry", "CurrencyCustomLootEntry"},
        };
        for (String[] path : paths) {
            assertReferences(constantPoolOf(path[0]), path[1], "EliteDropPolicy", "shouldRunCurrencyShower");
        }
    }

    @Test
    @DisplayName("every native-display path consults the display policy")
    void displaySuppressionIsWired() throws IOException {
        assertReferences(constantPoolOf("com/magmaguy/elitemobs/mobconstructor/EliteEntity"),
                "EliteEntity", "NativeDisplayPolicy", "resolveNametagVisible", "resolveSpawnNametagVisible");
        assertReferences(constantPoolOf("com/magmaguy/elitemobs/mobconstructor/custombosses/CustomBossEntity"),
                "CustomBossEntity", "NativeDisplayPolicy",
                "resolveCustomBossNametagVisible", "resolveCustomModelNametagVisible", "allowBossTrackingBar");
        assertReferences(constantPoolOf("com/magmaguy/elitemobs/mobconstructor/custombosses/CustomBossMegaConsumer"),
                "CustomBossMegaConsumer", "NativeDisplayPolicy", "resolveSpawnNametagVisible");
    }

    @Test
    @DisplayName("the existing-config migration is actually called from loadConfig")
    void configMigrationIsWired() throws IOException {
        // Without this call a server that already has trinityforge.yml never sees the new sections and
        // silently runs whatever the jar defaults to.
        assertReferences(constantPoolOf("com/magmaguy/elitemobs/trinityforge/TrinityForgeIntegration"),
                "TrinityForgeIntegration", "TrinityForgeConfigMigration", "appendMissingKeys");
    }

    @Test
    @DisplayName("the dungeon browser preflights with previewRequiredEntry, never with hasEntryGate")
    void dungeonBrowserUsesTheEscapeAwarePreview() throws IOException {
        // 2026-08-01 round2 regression guard. TrinityForge's checkRequiredEntry/previewRequiredEntry
        // both bail out with "allowed" when gates.yml defines ZERO gates (TF commit 113ff86) — the
        // shipped gates.yml is literally "gates: {}", so without that escape every dungeon is closed to
        // ordinary players. DungeonGateService#hasEntryGate has NO such escape: it answers "this
        // lookup key has no gate", which on a default install is true for every dungeon.
        // Preflighting the browser with hasEntryGate therefore rebuilds the exact blockade TF had just
        // removed, and it disagrees with the join itself (browser closed, join allowed).
        String dungeonCommands = constantPoolOf("com/magmaguy/elitemobs/commands/DungeonCommands");
        assertTrue(dungeonCommands.contains("previewDungeonEntryAllowed"),
                "DungeonCommands no longer preflights the dungeon browser — the instanced-dungeon "
                        + "browsers do not fire PlayerPreTeleportEvent, so nothing else gates them.");
        assertFalse(dungeonCommands.contains("hasEntryGate"),
                "DungeonCommands must not gate on hasEntryGate: it has no zero-gate escape, so with the "
                        + "shipped 'gates: {}' it blocks every dungeon for ordinary players.");
        assertFalse(constantPoolOf("com/magmaguy/elitemobs/trinityforge/TrinityForgeDungeonGateListener")
                        .contains("hasEntryGate"),
                "the gate listener must not reintroduce a hasEntryGate-based blockade either.");
    }

    @Test
    @DisplayName("the pre-clone check previews, and only the committed join consumes the key")
    void instanceSetupDoesNotConsumeTheKeyTwice() throws IOException {
        // DungeonInstance runs a gate check before the (expensive) world clone AND again from
        // addNewPlayer for the creating player. Both used to call checkDungeonEntryAllowed, which
        // consumes the gate's key-item — so entering a key-gated dungeon cost two keys.
        for (String dungeon : new String[]{
                "com/magmaguy/elitemobs/instanced/dungeons/DungeonInstance",
                "com/magmaguy/elitemobs/instanced/dungeons/DynamicDungeonInstance"}) {
            assertTrue(constantPoolOf(dungeon).contains("previewDungeonEntryAllowed"),
                    dungeon + " must preflight with the NON-consuming preview; the consuming "
                            + "checkDungeonEntryAllowed belongs to addNewPlayer alone.");
        }
    }

    private static void assertReferences(String bytecode, String owner, String policyClass, String... methods) {
        assertTrue(bytecode.contains(policyClass),
                owner + " no longer references " + policyClass + " at all — a TrinityForge gate was removed.");
        for (String method : methods) {
            assertTrue(bytecode.contains(method),
                    owner + " no longer calls " + policyClass + "." + method + "(...) — that gate is now dead. "
                            + "If the call was moved on purpose, move this assertion with it; do not delete it.");
        }
    }

    /** Reads a compiled class as raw bytes. ISO-8859-1 keeps every byte addressable as a char. */
    private static String constantPoolOf(String internalName) throws IOException {
        String resource = internalName + ".class";
        try (InputStream in = TrinityForgeGateWiringTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "compiled class not found on the test classpath: " + resource
                    + " (renamed or deleted?)");
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
