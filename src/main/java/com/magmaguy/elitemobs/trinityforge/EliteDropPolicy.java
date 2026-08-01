package com.magmaguy.elitemobs.trinityforge;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Every decision that {@code trinityforge.yml}'s {@code elite-drop-sources} section makes, in one
 * place, expressed as small deterministic functions.
 * <p>
 * <b>Why this class exists.</b> The gates used to be inline boolean expressions spread over
 * {@code LootTables}, {@code DefaultDropsHandler}, {@code CustomBossDeath} and the three currency-shower
 * call sites. Those call sites need a live Bukkit server, a spawned elite and a player to reach, so the
 * only thing the unit tests could actually reach was
 * {@link TrinityForgeIntegration}'s getters — which meant deleting a gate from a call site left every
 * test green (verified by mutation on 2026-08-01). Concentrating the decision here makes the decision
 * itself directly executable from a test, and {@code TrinityForgeGateWiringTest} pins the fact that the
 * production classes still call into it.
 * <p>
 * Each method takes the <em>upstream</em> EliteMobs condition as an argument and combines it with the
 * TrinityForge gate, so the test fixes the whole condition rather than half of it. Every gate fails
 * OPEN when TrinityForge is absent (see {@link TrinityForgeIntegration#isRandomEliteLootAllowed()} and
 * friends): a standalone EliteMobs install must behave exactly like upstream.
 */
public final class EliteDropPolicy {

    private EliteDropPolicy() {
    }

    /**
     * EliteMobs' guild-currency ({@code EliteCoin}) shower. Gated by
     * {@code elite-drop-sources.currency-shower}.
     *
     * @param mobHasBonusCoinsPower true when the elite carries the {@code bonus_coins.yml} power, which
     *                              runs its own (separately gated) shower in {@code BonusCoins} — the
     *                              generic shower in {@code LootTables} must stand down for those mobs
     *                              exactly like upstream. Pass {@code false} from the call sites that
     *                              <em>are</em> the bonus-coins path.
     */
    public static boolean shouldRunCurrencyShower(boolean mobHasBonusCoinsPower) {
        return !mobHasBonusCoinsPower && TrinityForgeIntegration.isCurrencyShowerAllowed();
    }

    /**
     * EliteMobs' own random elite loot pool (procedural / weighed / fixed / limited / scalable). Nothing
     * in it is described by any TrinityForge config, so it is the main source of "items I never
     * configured are dropping". Gated by {@code elite-drop-sources.random-loot}.
     */
    public static boolean shouldGenerateRandomEliteLoot() {
        return TrinityForgeIntegration.isRandomEliteLootAllowed();
    }

    /**
     * EliteMobs' {@code SpecialItemSystems} bonus drop. Gated by {@code elite-drop-sources.special-loot}.
     *
     * @param specialLootConfigured EliteMobs' own {@code SpecialItemSystems.yml dropSpecialLoot}
     */
    public static boolean shouldRollSpecialLoot(boolean specialLootConfigured) {
        return specialLootConfigured && TrinityForgeIntegration.isSpecialLootAllowed();
    }

    /**
     * EliteMobs' elite item scrolls. Gated by {@code elite-drop-sources.elite-scroll}.
     *
     * @param eliteScrollsConfigured EliteMobs' own {@code ItemSettings.yml useEliteItemScrolls}
     */
    public static boolean shouldRollEliteScroll(boolean eliteScrollsConfigured) {
        return eliteScrollsConfigured && TrinityForgeIntegration.isEliteScrollAllowed();
    }

    /**
     * EliteMobs' vanilla-drop duplication ({@code ItemSettings.yml defaultLootMultiplier}). Gated by
     * {@code elite-drop-sources.vanilla-loot-multiplier}.
     *
     * @param defaultLootMultiplier EliteMobs' configured multiplier; {@code 0} disables it upstream
     */
    public static boolean shouldApplyVanillaLootMultiplier(double defaultLootMultiplier) {
        return defaultLootMultiplier != 0 && TrinityForgeIntegration.isVanillaLootMultiplierAllowed();
    }

    /**
     * A custom boss' own authored {@code uniqueLootList}. Gated by
     * {@code elite-drop-sources.boss-unique-loot} (default: allowed — this is intentional EliteMobs
     * content, not a random pool).
     */
    public static boolean shouldDropBossUniqueLoot() {
        return TrinityForgeIntegration.isBossUniqueLootAllowed();
    }

    /**
     * Applies {@code elite-drop-sources.vanilla-loot} to an elite's vanilla death drops, clearing the
     * list in place when they are not allowed to survive.
     * <p>
     * Upstream EliteMobs only clears the list for mobs configured with {@code dropsVanillaLoot: false};
     * {@code vanilla-loot: false} extends the clear to EVERY elite, so a mob TrinityForge has no drop
     * table for drops nothing at all instead of its vanilla loot.
     * <p>
     * The list is mutated rather than returned so this method (and not the caller) owns the whole
     * decision — a test can hand it a plain {@link java.util.ArrayList} and observe the real outcome.
     *
     * @param drops               the live {@code EntityDeathEvent#getDrops()} list, or {@code null} when
     *                            the elite died without a Bukkit death event (EliteMobs fires
     *                            {@code EliteMobDeathEvent} without one for e.g. the ender dragon)
     * @param mobDropsVanillaLoot the elite's own {@code dropsVanillaLoot} setting
     * @return true when the list was cleared
     */
    public static boolean clearVanillaDropsIfBlocked(List<ItemStack> drops, boolean mobDropsVanillaLoot) {
        if (drops == null) return false;
        if (mobDropsVanillaLoot && TrinityForgeIntegration.isVanillaLootAllowed()) return false;
        drops.clear();
        return true;
    }
}
