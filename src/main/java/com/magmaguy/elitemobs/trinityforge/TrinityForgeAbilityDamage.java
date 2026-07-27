package com.magmaguy.elitemobs.trinityforge;

/**
 * Transient marker distinguishing an elite's ABILITY damage (script/power {@code DAMAGE} actions,
 * which reach the player through a synthetic {@code target.damage(amount, elite)} call whose Bukkit
 * cause is indistinguishable from a real melee hit) from its genuine melee swings.
 * <p>
 * Ability entry points ({@code ScriptAction.runDamage}, the Lua power API's damage bindings) mark
 * immediately before dealing the damage; the mark is consumed synchronously in the same tick by
 * {@link TrinityForgeCombatListener#onPlayerDamagedByElite} (the whole chain —
 * {@code target.damage} → {@code EntityDamageByEntityEvent} → EliteMobs' damage
 * filter → {@code PlayerDamagedByEliteMobEvent} — runs synchronously on the main thread) and routes
 * the hit through TrinityForge's MAGICAL component (魔法耐性/魔法防御 apply) instead of the physical
 * one. The marking side always clears in a {@code finally} so a cancelled/short-circuited damage
 * call can never leak the mark onto the elite's next real melee hit.
 * <p>
 * The mark is a DEPTH COUNTER, not a boolean: a marked {@code target.damage(...)} can synchronously
 * trigger another marked ability damage (e.g. a boss script bound to
 * {@code PlayerDamagedByEliteMobEvent} running a nested {@code DAMAGE} action), and the inner
 * {@code finally} must not strip the outer call's mark before the outer hit is classified.
 * <p>
 * TrinityForge references are confined to the guarded mirror calls inside {@link #mark()}/
 * {@link #clear()} (never in signatures) so {@code ScriptAction} can call this class
 * unconditionally without risking a {@link NoClassDefFoundError} when TrinityForge is absent.
 */
public final class TrinityForgeAbilityDamage {

    private static int depth = 0;

    private TrinityForgeAbilityDamage() {
    }

    /**
     * Marks the immediately following synchronous damage call as elite ability damage. Also mirrors
     * the mark into TrinityForge's own {@code MobAbilityDamage} so TrinityForge's CombatListener
     * (which sees the same underlying melee-cause event) stands down instead of re-routing the
     * ability through its physical mob-melee path. Reflection-free but guarded: when TrinityForge
     * is absent the mirror is a silent no-op.
     */
    public static void mark() {
        depth++;
        try {
            com.trinityforge.combat.MobAbilityDamage.mark();
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            // TrinityForge absent — local mark still lets the fork's own listener classify the hit.
        }
    }

    /** Clears one mark; call in a {@code finally} around the damage call. */
    public static void clear() {
        if (depth > 0) depth--;
        try {
            com.trinityforge.combat.MobAbilityDamage.clear();
        } catch (NoClassDefFoundError | RuntimeException ignored) {
        }
    }

    /** True while inside a marked ability-damage call. Does not clear (the marker's finally does). */
    public static boolean isAbilityDamage() {
        return depth > 0;
    }
}
