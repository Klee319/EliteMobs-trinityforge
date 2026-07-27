package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.TrinityForge;
import com.trinityforge.config.ConfigManager;
import com.trinityforge.config.domains.CraftQualityConfig;
import com.trinityforge.pdc.ItemData;
import com.trinityforge.stats.CraftQualityPolicy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stamps elite loot drops with a TrinityForge rollSeed + quality (fork spec section 5).
 * <p>
 * TrinityForge derives item stats from {@code rollSeed + quality + an adjustable parameter table} rather than
 * baking fixed values, so stamping the seed/quality is enough for TrinityForge to (re)derive and re-balance the
 * item later via {@code /trinityforge reload}. This class only attaches the entropy; the stat derivation and
 * its balance live entirely in TrinityForge.
 * <p>
 * This used to be an {@code EliteMobDeathEvent} listener walking {@code getEntityDeathEvent().getDrops()}, but
 * that list is a decoy for elite loot: {@link com.magmaguy.elitemobs.items.LootTables#onDeath} clears it
 * ({@code getDrops().clear()}) before the real elite items are generated and handed to the player directly via
 * {@code world.dropItem}/{@code inventory.addItem}, so the listener never saw the actual elite drops. Worse, when
 * a mob uses vanilla loot ({@code isVanillaLoot() == true}) the vanilla drops are left in that list and the old
 * listener stamped them by mistake. The stamp is now applied by {@code LootTables} at the exact points where an
 * elite-generated {@link ItemStack} is handed to the player, so vanilla drops are never touched.
 * <p>
 * <b>Quality distribution (enemy-strength driven).</b> The quality is a balance concern owned by TrinityForge, so
 * the numbers live in TrinityForge's {@code stats/craft-quality.yml drop} section — none are baked here. When
 * {@code drop.enabled}, the quality is a normal (bell) draw whose mode rises with the enemy's strength (its
 * EliteMobs level), mirroring how a crafter's production-skill level drives craft quality: a stronger enemy makes
 * high-quality drops more likely. The enemy strength for the loot currently being handed out is supplied by
 * {@code LootTables#generatePlayerLoot} through {@link #beginDropContext(int, Player)}/{@link #endDropContext()} (all elite
 * loot generation is synchronous on the server main thread, so a per-thread context is a safe, low-churn way to
 * reach the many drop helpers without threading the level through every signature). When the context is absent
 * (drop disabled, TrinityForge unavailable, or a caller outside the loot pipeline) the quality falls back to a
 * uniform draw over the valid quality range, seeded by the rollSeed so it stays deterministic.
 */
public final class TrinityForgeLootListener {

    /** Sentinel: no enemy-strength context is active, so the drop-quality bell cannot be applied. */
    private static final int UNKNOWN_ENEMY_STRENGTH = -1;

    /** Enemy strength + receiving player of the loot currently being generated on this thread. */
    private record DropContext(int enemyStrength, Player player) {
    }

    /**
     * Context of the loot currently being generated, scoped to the main-thread call that set it.
     * {@code LootTables#generatePlayerLoot} sets it around one mob's per-player loot generation and
     * clears it in a {@code finally}. A {@link ThreadLocal} (rather than a plain static) keeps an errant
     * off-thread stamp from reading a stale value.
     */
    private static final ThreadLocal<DropContext> DROP_CONTEXT = new ThreadLocal<>();

    private TrinityForgeLootListener() {
    }

    /** @deprecated player-less variant kept for binary compatibility; luck bonus stays 0. */
    @Deprecated
    public static void beginDropContext(int enemyStrength) {
        beginDropContext(enemyStrength, null);
    }

    /**
     * Marks the enemy strength (EliteMobs level) and the receiving player for the elite loot about to be
     * generated on this thread, so the subsequent {@link #stampLootDrop(ItemStack)} calls can scale quality to
     * the mob's strength and the player's mobドロップボーナス ({@code power_mobdropbonus_add}). Must be
     * paired with {@link #endDropContext()} in a {@code finally}. Safe to call when TrinityForge is absent (it
     * only sets a thread-local).
     *
     * @param enemyStrength the mob's EliteMobs level (its "strength"); negative values are treated as unknown
     * @param player        the player this loot is generated for ({@code null} = no luck bonus)
     */
    public static void beginDropContext(int enemyStrength, Player player) {
        DROP_CONTEXT.set(new DropContext(Math.max(UNKNOWN_ENEMY_STRENGTH, enemyStrength), player));
    }

    /** Clears the context set by {@link #beginDropContext(int, Player)}. Idempotent. */
    public static void endDropContext() {
        DROP_CONTEXT.remove();
    }

    /**
     * Stamps a freshly generated elite loot {@link ItemStack} with a TrinityForge rollSeed + quality, if the
     * loot-stat-stamp toggle is enabled and TrinityForge is available. Safe to call unconditionally: no-op when
     * TrinityForge is absent, the toggle is off, the stack is null/air, or the item already carries a rollSeed
     * (e.g. a cloned template that was stamped previously). Quality scales with the enemy strength set via
     * {@link #beginDropContext(int)} when available.
     *
     * @param drop the elite-loot item about to be dropped in the world or added to the player's inventory
     */
    public static void stampLootDrop(ItemStack drop) {
        if (!TrinityForgeIntegration.isLootStatStampEnabled()) return;
        DropContext ctx = DROP_CONTEXT.get();
        int enemyStrength = ctx == null ? UNKNOWN_ENEMY_STRENGTH : ctx.enemyStrength();
        Player player = ctx == null ? null : ctx.player();
        try {
            stamp(drop, enemyStrength, player);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge loot stamping failed for "
                    + (drop == null ? "null" : drop.getType()) + ": " + e);
        }
    }

    private static void stamp(ItemStack drop, int enemyStrength, Player player) {
        if (drop == null || drop.getType().isAir()) return;
        ItemMeta meta = drop.getItemMeta();
        if (meta == null) return;

        ItemData itemData = ItemData.of(meta);
        if (itemData.hasRollSeed()) return; // already a TrinityForge-derived item

        long rollSeed = ThreadLocalRandom.current().nextLong();
        itemData.setRollSeed(rollSeed);
        itemData.setQuality(rollQuality(rollSeed, enemyStrength, luckModeBonus(player)));
        drop.setItemMeta(meta);
    }

    /**
     * The stamped quality for a drop. When TrinityForge's {@code drop} quality model is enabled and an enemy
     * strength is known, draws from a normal (bell) distribution whose mode rises with that strength plus the
     * receiver's mobドロップボーナス (1点につき mode+1, same split-normal as crafts/drops);
     * otherwise falls back to a uniform draw over the valid quality range. Deterministic for a given
     * {@code rollSeed} and mode.
     */
    private static int rollQuality(long rollSeed, int enemyStrength, int luckModeBonus) {
        ConfigManager config = TrinityForgeIntegration.config();
        int maxQuality = config != null ? config.quality().maxQuality() : ItemData.MAX_QUALITY;
        if (config != null && enemyStrength >= 0) {
            CraftQualityConfig cq = config.craftQuality();
            if (cq != null && cq.dropEnabled()) {
                int mode = CraftQualityPolicy.modeFromLevel(
                        enemyStrength, cq.dropStrengthPerQuality(), cq.dropBaseQuality())
                        + luckModeBonus;
                double standardNormal = new SplittableRandom(rollSeed).nextGaussian();
                // Up/down spread (σ) is the global quality model in stats/quality.yml, shared with crafts.
                // Drops are enemy-driven, so no per-player upswing perk — just the base split-normal.
                return CraftQualityPolicy.resolveDropQuality(mode, standardNormal,
                        config.quality().spreadUp(), config.quality().spreadDown(), maxQuality);
            }
        }
        return uniformQuality(rollSeed, maxQuality);
    }

    /**
     * 受取プレイヤーのmobドロップボーナス→品質mode加算 (1点につき mode+1、端数は確率的切り上げ)。
     * ソースは専用stat {@code power_mobdropbonus_add} (TrinityForge {@code PlayerMobDropBonusSource})。
     * 幸運(power_luckbonus_add / LUCKポーション)はspec上「ドロップとクラフト以外」のため、ここでは
     * 参照しない(2026-07-22切替)。TrinityForge 不在・例外時は 0 に落ちる (fail-open)。
     */
    private static int luckModeBonus(Player player) {
        if (player == null) return 0;
        int bonus = 0;
        // mobドロップ品質は幸運(lootLuck/LUCKポーション)ではなく専用stat
        // power_mobdropbonus_add (TF PlayerMobDropBonusSource) が担う — 幸運spec=「ドロップと
        // クラフト以外」(2026-07-22切替。旧実装はLUCKポーション+lootLuckを合算していた)。
        try {
            TrinityForge tf = TrinityForge.getInstance();
            com.trinityforge.stats.PlayerMobDropBonusSource dropBonus =
                    tf == null ? null : tf.mobDropBonus();
            if (dropBonus != null) {
                bonus += dropBonus.qualityModeBonus(player, ThreadLocalRandom.current());
            }
        } catch (NoClassDefFoundError | RuntimeException e) {
            // TrinityForge absent/incompatible: skilltree channel contributes 0
        }
        return bonus;
    }

    /** Deterministic uniform quality over {@code [MIN_QUALITY, maxQuality]}, seeded by the rollSeed. */
    private static int uniformQuality(long rollSeed, int maxQuality) {
        int hi = Math.max(ItemData.MIN_QUALITY, maxQuality);
        int span = hi - ItemData.MIN_QUALITY + 1;
        return ItemData.MIN_QUALITY + new SplittableRandom(rollSeed).nextInt(span);
    }
}
