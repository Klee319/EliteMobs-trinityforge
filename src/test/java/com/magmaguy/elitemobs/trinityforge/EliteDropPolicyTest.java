package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for {@link EliteDropPolicy} — the decisions the production drop paths actually run.
 * <p>
 * These assert the WHOLE condition (EliteMobs' own upstream condition AND the TrinityForge gate), not
 * just the {@link TrinityForgeIntegration} getter, so weakening either half fails here. The companion
 * {@code TrinityForgeGateWiringTest} pins the fact that the production classes still call in.
 */
class EliteDropPolicyTest {

    @AfterEach
    void reset() {
        IntegrationState.reset();
    }

    // ---------------------------------------------------------------- currency shower

    @Test
    @DisplayName("currency shower: blocked by the switch, and never runs for a bonus_coins mob")
    void currencyShower() {
        IntegrationState.set("available", true);

        IntegrationState.set("allowCurrencyShower", true);
        assertTrue(EliteDropPolicy.shouldRunCurrencyShower(false),
                "allowed + no bonus_coins power -> the generic shower must run");
        assertFalse(EliteDropPolicy.shouldRunCurrencyShower(true),
                "upstream behaviour: a mob with the bonus_coins power gets its shower from BonusCoins");

        IntegrationState.set("allowCurrencyShower", false);
        assertFalse(EliteDropPolicy.shouldRunCurrencyShower(false), "currency-shower: false must block it");
        assertFalse(EliteDropPolicy.shouldRunCurrencyShower(true));
    }

    @Test
    @DisplayName("currency shower fails OPEN when TrinityForge is absent")
    void currencyShowerStandalone() {
        IntegrationState.set("available", false);
        IntegrationState.set("allowCurrencyShower", false);
        assertTrue(EliteDropPolicy.shouldRunCurrencyShower(false));
        // The upstream half of the condition still applies in standalone mode.
        assertFalse(EliteDropPolicy.shouldRunCurrencyShower(true));
    }

    // ---------------------------------------------------------------- random / special / scroll

    @Test
    @DisplayName("random elite loot follows elite-drop-sources.random-loot")
    void randomEliteLoot() {
        IntegrationState.set("available", true);
        IntegrationState.set("allowRandomEliteLoot", false);
        assertFalse(EliteDropPolicy.shouldGenerateRandomEliteLoot());
        IntegrationState.set("allowRandomEliteLoot", true);
        assertTrue(EliteDropPolicy.shouldGenerateRandomEliteLoot());

        IntegrationState.set("available", false);
        IntegrationState.set("allowRandomEliteLoot", false);
        assertTrue(EliteDropPolicy.shouldGenerateRandomEliteLoot(), "standalone EliteMobs must be untouched");
    }

    @Test
    @DisplayName("special loot needs BOTH EliteMobs' dropSpecialLoot and the TrinityForge gate")
    void specialLoot() {
        IntegrationState.set("available", true);

        IntegrationState.set("allowSpecialLoot", true);
        assertTrue(EliteDropPolicy.shouldRollSpecialLoot(true));
        assertFalse(EliteDropPolicy.shouldRollSpecialLoot(false), "EliteMobs' own switch must still win");

        IntegrationState.set("allowSpecialLoot", false);
        assertFalse(EliteDropPolicy.shouldRollSpecialLoot(true));
        assertFalse(EliteDropPolicy.shouldRollSpecialLoot(false));
    }

    @Test
    @DisplayName("elite scrolls need BOTH useEliteItemScrolls and the TrinityForge gate")
    void eliteScroll() {
        IntegrationState.set("available", true);

        IntegrationState.set("allowEliteScroll", true);
        assertTrue(EliteDropPolicy.shouldRollEliteScroll(true));
        assertFalse(EliteDropPolicy.shouldRollEliteScroll(false));

        IntegrationState.set("allowEliteScroll", false);
        assertFalse(EliteDropPolicy.shouldRollEliteScroll(true));
    }

    // ---------------------------------------------------------------- vanilla loot multiplier

    @Test
    @DisplayName("vanilla loot multiplier: 0 disables it upstream, the gate disables it independently")
    void vanillaLootMultiplier() {
        IntegrationState.set("available", true);

        IntegrationState.set("allowVanillaLootMultiplier", true);
        assertTrue(EliteDropPolicy.shouldApplyVanillaLootMultiplier(2.0));
        assertFalse(EliteDropPolicy.shouldApplyVanillaLootMultiplier(0),
                "defaultLootMultiplier 0 is upstream's own off switch");

        IntegrationState.set("allowVanillaLootMultiplier", false);
        assertFalse(EliteDropPolicy.shouldApplyVanillaLootMultiplier(2.0));

        IntegrationState.set("available", false);
        assertTrue(EliteDropPolicy.shouldApplyVanillaLootMultiplier(2.0), "standalone EliteMobs must be untouched");
    }

    // ---------------------------------------------------------------- boss unique loot

    @Test
    @DisplayName("boss uniqueLootList follows elite-drop-sources.boss-unique-loot")
    void bossUniqueLoot() {
        IntegrationState.set("available", true);
        IntegrationState.set("allowBossUniqueLoot", true);
        assertTrue(EliteDropPolicy.shouldDropBossUniqueLoot());
        IntegrationState.set("allowBossUniqueLoot", false);
        assertFalse(EliteDropPolicy.shouldDropBossUniqueLoot());

        IntegrationState.set("available", false);
        assertTrue(EliteDropPolicy.shouldDropBossUniqueLoot());
    }

    // ---------------------------------------------------------------- vanilla drops (real list mutation)

    @Test
    @DisplayName("dropsVanillaLoot: false clears the drop list exactly like upstream")
    void vanillaDropsClearedForNonVanillaLootMobs() {
        IntegrationState.set("available", true);
        IntegrationState.set("allowVanillaLoot", true);

        List<ItemStack> drops = drops(3);
        assertTrue(EliteDropPolicy.clearVanillaDropsIfBlocked(drops, false));
        assertEquals(0, drops.size(), "upstream behaviour must be preserved");
    }

    @Test
    @DisplayName("vanilla-loot: true keeps a dropsVanillaLoot mob's drops")
    void vanillaDropsKept() {
        IntegrationState.set("available", true);
        IntegrationState.set("allowVanillaLoot", true);

        List<ItemStack> drops = drops(3);
        assertFalse(EliteDropPolicy.clearVanillaDropsIfBlocked(drops, true));
        assertEquals(3, drops.size());
    }

    @Test
    @DisplayName("vanilla-loot: false clears the drops of EVERY elite, not just dropsVanillaLoot: false ones")
    void vanillaDropsClearedByGate() {
        IntegrationState.set("available", true);
        IntegrationState.set("allowVanillaLoot", false);

        List<ItemStack> drops = drops(3);
        assertTrue(EliteDropPolicy.clearVanillaDropsIfBlocked(drops, true));
        assertEquals(0, drops.size(), "this is the whole point of elite-drop-sources.vanilla-loot");
    }

    @Test
    @DisplayName("vanilla drops are untouched when TrinityForge is absent")
    void vanillaDropsStandalone() {
        IntegrationState.set("available", false);
        IntegrationState.set("allowVanillaLoot", false);

        List<ItemStack> drops = drops(3);
        assertFalse(EliteDropPolicy.clearVanillaDropsIfBlocked(drops, true));
        assertEquals(3, drops.size());
    }

    @Test
    @DisplayName("a null drop list (elite death with no Bukkit death event) is tolerated")
    void nullDropList() {
        IntegrationState.set("available", true);
        IntegrationState.set("allowVanillaLoot", false);
        assertFalse(EliteDropPolicy.clearVanillaDropsIfBlocked(null, true));
    }

    /** A plain list of placeholder stacks — no Bukkit server needed, only the ItemStack type. */
    private static List<ItemStack> drops(int size) {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < size; i++) drops.add(null);
        return drops;
    }
}
