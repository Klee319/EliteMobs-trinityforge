package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.api.EliteMobDamagedByEliteMobEvent;
import com.magmaguy.elitemobs.api.EliteMobDamagedByPlayerEvent;
import com.magmaguy.elitemobs.api.PlayerDamagedByEliteMobEvent;
import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.utils.EntityFinder;
import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.TrinityForge;
import com.trinityforge.combat.AttackStats;
import com.trinityforge.combat.EliteCombatDelegation;
import com.trinityforge.combat.SymmetricCombatService;
import com.trinityforge.combat.WeaponAttackStatResolver;
import com.trinityforge.pdc.MobData;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataHolder;

/**
 * Delegates the final damage of every elite combat interaction to TrinityForge's symmetric pipeline
 * (fork spec section 2).
 * <p>
 * EliteMobs computes its own (now gear-neutralized — see {@link TrinityForgeIntegration} and the patched
 * calculators) base damage during the {@code HIGH} priority skill-bonus pass. This listener runs at
 * {@code HIGHEST}, so it sees that gear-neutral base and overrides it with the value TrinityForge returns
 * for the same attacker/victim pair. EliteMobs is therefore reduced to "supply the base, TrinityForge owns
 * the final number".
 * <p>
 * A PLAYER attacker's melee routes the player's REAL TrinityForge attack stats (crit/penetration/bonus —
 * resolved from the mainhand via {@link WeaponAttackStatResolver}) so that crit vs elites is governed by
 * TrinityForge's crit stat (#3 crit一本化); EliteMobs' own native ×1.5 crit has been removed. A MOB
 * attacker (elite→player / elite→elite) carries no player offensive stats, so it stays
 * {@link AttackStats#plain(double)} with zero. The EliteMobs-supplied base is an already-finalized damage
 * number, so every path uses the FLAT entry point ({@link SymmetricCombatService#physicalFinalDamageFlat})
 * — only the victim's defense (and dodge) apply, without re-scaling by combat level or the physical.base
 * coefficient (#6). Magical damage flows through the ArsPaper fork, not here. Every handler is defensive:
 * any failure leaves EliteMobs' own damage untouched rather than breaking combat.
 */
public class TrinityForgeCombatListener implements Listener {

    /**
     * CMB-02 (課題3, 2026-07-25): opens the {@link EliteCombatDelegation} window for a player→elite
     * melee/projectile hit BEFORE EliteMobs' own {@code onEliteMobAttacked} (registered at the default
     * {@code NORMAL} priority on this same raw event) runs. Bukkit dispatches every priority tier for one
     * event within a single synchronous {@code callEvent} call, so marking here at {@code LOWEST} (the
     * very first tier) guarantees the mark is already active by the time {@code onEliteMobAttacked} fires
     * its {@code EliteMobDamagedByPlayerEvent} (which this class's {@link #onEliteDamagedByPlayer} applies
     * TrinityForge's full physical pipeline to) AND by the time TrinityForge's own {@code CombatListener}
     * (registered at {@code HIGH} on this same raw event) gets its later turn — which is exactly the
     * window that needs guarding, since that {@code HIGH} handler would otherwise re-derive the elite's
     * defense/dodge/crit/penetration and re-apply the combat-level scale a second time (see
     * {@link EliteCombatDelegation}'s javadoc). Paired with {@link #onEliteHitDelegationEnd} below;
     * {@code ignoreCancelled} is deliberately left at its default {@code false} so the mark/clear pair
     * stays symmetric even if a later handler (e.g. the anti-autoclicker throttle) cancels the event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEliteHitDelegationBegin(EntityDamageByEntityEvent event) {
        if (isDelegatedEliteHit(event)) {
            EliteCombatDelegation.mark();
        }
    }

    /**
     * Clears the CMB-02 mark opened by {@link #onEliteHitDelegationBegin}. {@code MONITOR} guarantees
     * this runs dead last among every registered listener for this one raw event — after EliteMobs' own
     * {@code NORMAL} handler and after TrinityForge's {@code HIGH} handler have both already had their
     * turn — so the marked window never leaks past this single event dispatch into an unrelated later hit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEliteHitDelegationEnd(EntityDamageByEntityEvent event) {
        if (isDelegatedEliteHit(event)) {
            EliteCombatDelegation.clear();
        }
    }

    /**
     * True for exactly the player→elite hits that {@code EliteMobDamagedByPlayerEvent}'s
     * {@code onEliteMobAttacked} will actually route through this class's {@link #onEliteDamagedByPlayer}
     * (mirrors that method's own early-exit guards so the marked window matches its real processing
     * window) — restricted to the 3 causes TrinityForge's own {@code CombatListener} also processes
     * ({@code ENTITY_ATTACK}/{@code ENTITY_SWEEP_ATTACK}/player-shot {@code PROJECTILE}). A
     * {@code THORNS}-cause hit here is 課題2's reflect-stat territory — TrinityForge's own
     * {@code onVanillaThornsProc} already cancels it upstream at {@code LOWEST} on the SAME event type, so
     * it never reaches {@code onEliteMobAttacked} either way; excluding it here just avoids a pointless mark.
     */
    private static boolean isDelegatedEliteHit(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean relevantCause = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE;
        if (!relevantCause) {
            return false;
        }
        if (event.getEntity().getType() == EntityType.ENDER_DRAGON
                && event.getEntity() instanceof EnderDragon dragon
                && dragon.getPhase() == EnderDragon.Phase.DYING) {
            return false;
        }
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE && !(event.getDamager() instanceof Projectile)) {
            return false;
        }
        LivingEntity livingEntity = EntityFinder.filterRangedDamagers(event.getDamager());
        if (!(livingEntity instanceof Player)) {
            return false;
        }
        EliteEntity eliteEntity = EntityTracker.getEliteMobEntity(event.getEntity());
        return eliteEntity != null && eliteEntity.isValid();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEliteDamagedByPlayer(EliteMobDamagedByPlayerEvent event) {
        if (!TrinityForgeIntegration.isCombatDelegationEnabled()) return;
        double base = event.getDamage();
        if (base <= 0) return;
        Player player = event.getPlayer();
        PersistentDataHolder victim = livingEntityOf(event.getEliteMobEntity());
        if (player == null || victim == null) return;
        // #3: player's real TF attack stats (crit/penetration/bonus) fold into the elite-facing damage.
        applyPhysical(event::setDamage, victim, base, playerAttackStats(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamagedByElite(PlayerDamagedByEliteMobEvent event) {
        if (!TrinityForgeIntegration.isCombatDelegationEnabled()) return;
        double base = event.getDamage();
        if (base <= 0) return;
        Player player = event.getPlayer();
        LivingEntity attacker = event.getAttacker();
        if (player == null || attacker == null) return;
        // Elite ABILITY damage (script/power DAMAGE actions, marked by ScriptAction while its synthetic
        // target.damage(...) call runs) is the mob's "magic": route it through TrinityForge's MAGICAL
        // component so 魔法耐性/魔法防御 apply. EliteMobs' own formula already folded the mob level into
        // the base, so the FLAT magical entry point is used (no re-scaling).
        if (TrinityForgeAbilityDamage.isAbilityDamage()) {
            applyMagical(event::setDamage, player, base);
            return;
        }
        // Double-application guard: a mob-profiles `attack:` stamp (MOB_ATTACK_POWER on the PDC, written by
        // TrinityForgeSpawnListener) hands this elite's outgoing MELEE to TrinityForge's OWN CombatListener
        // (physicalFinalDamageFromMob at HIGH on the underlying EntityDamageByEntityEvent — i.e. AFTER
        // EliteMobs' NORMAL-priority damage filter fired this event). Applying the player's defense here too
        // would mitigate the same hit twice, so a stamped elite's melee is left for TrinityForge to own
        // end-to-end. Melee only: TrinityForge's mob-attacker path ignores projectile/explosion causes, so
        // those still need this listener's flat delegation even on a stamped elite.
        if (isMeleeCause(event) && hasTrinityForgeAttackStamp(attacker)) return;
        // Mob attacker: its level scaling is already baked into the base by EliteMobs' LevelScaling, and it
        // carries no player offensive stats — so plain(0) and the player (victim) defense is what applies.
        applyPhysical(event::setDamage, player, base, AttackStats.plain(0));
    }

    /**
     * Magical twin of {@link #applyPhysical}: folds the elite's already-finalized ability base through
     * TrinityForge's MAGICAL flat entry point (victim's magical defense + dodge only). A negative result
     * (possible when TrinityForge's {@code magical.min-component-damage} is configured negative) is
     * clamped to 0 here — EliteMobs' damage event cannot express healing.
     */
    private void applyMagical(java.util.function.DoubleConsumer setter, PersistentDataHolder victim,
                              double base) {
        try {
            SymmetricCombatService combat = TrinityForgeIntegration.combatService();
            if (combat == null) return;
            double finalDamage = combat.magicalFinalDamageFlat(victim, base, AttackStats.plain(0));
            if (finalDamage < 0) finalDamage = 0;
            setter.accept(finalDamage);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge magical damage delegation failed, falling back to EliteMobs damage: "
                    + e.getMessage());
        }
    }

    /** Fail-closed to "no stamp" so a TrinityForge classloading hiccup never silently drops mitigation. */
    private static boolean hasTrinityForgeAttackStamp(LivingEntity attacker) {
        try {
            return MobData.of(attacker).hasAttackProfile();
        } catch (NoClassDefFoundError | RuntimeException e) {
            return false;
        }
    }

    /** True when the underlying hit is a direct melee/sweep (the causes TrinityForge's mob path owns). */
    private static boolean isMeleeCause(PlayerDamagedByEliteMobEvent event) {
        org.bukkit.event.entity.EntityDamageByEntityEvent underlying = event.getEntityDamageByEntityEvent();
        if (underlying == null) return false;
        return switch (underlying.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEliteDamagedByElite(EliteMobDamagedByEliteMobEvent event) {
        if (!TrinityForgeIntegration.isCombatDelegationEnabled()) return;
        double base = event.getDamage();
        if (base <= 0) return;
        LivingEntity attacker = livingEntityOf(event.getDamager());
        PersistentDataHolder victim = livingEntityOf(event.getDamagee());
        if (attacker == null || victim == null) return;
        applyPhysical(event::setDamage, victim, base, AttackStats.plain(0));
    }

    private void applyPhysical(java.util.function.DoubleConsumer setter, PersistentDataHolder victim,
                               double base, AttackStats attack) {
        try {
            SymmetricCombatService combat = TrinityForgeIntegration.combatService();
            if (combat == null) return;
            // #6: base is EliteMobs' already-finalized damage → FLAT entry point applies only the victim's
            // defense (+ dodge) and the supplied attack stats, WITHOUT re-scaling by combat level or the
            // physical.base coefficient. Called through the cached service so an absent TrinityForge (null
            // service) is a safe no-op rather than a NoClassDefFoundError.
            double finalDamage = combat.physicalFinalDamageFlat(victim, base, attack);
            if (finalDamage < 0) finalDamage = 0;
            setter.accept(finalDamage);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge damage delegation failed, falling back to EliteMobs damage: " + e.getMessage());
        }
    }

    /**
     * Resolves a player's attacker-side {@link AttackStats} (crit/penetration/bonus-damage) from their
     * mainhand item via TrinityForge's {@link WeaponAttackStatResolver}. Fails open to
     * {@link AttackStats#plain(double)} with zero on any absence/error so combat never breaks. The
     * resolver's own {@code defaultDamage} is irrelevant here — {@link SymmetricCombatService#physicalFinalDamageFlat}
     * overrides it with the EliteMobs base.
     */
    private static AttackStats playerAttackStats(Player player) {
        try {
            TrinityForge tf = TrinityForge.getInstance();
            if (tf == null) return AttackStats.plain(0);
            WeaponAttackStatResolver resolver = tf.weaponAttackStats();
            if (resolver == null) return AttackStats.plain(0);
            AttackStats stats = resolver.forItem(player.getInventory().getItemInMainHand());
            return stats != null ? stats : AttackStats.plain(0);
        } catch (NoClassDefFoundError | RuntimeException e) {
            return AttackStats.plain(0);
        }
    }

    private static LivingEntity livingEntityOf(EliteEntity eliteEntity) {
        if (eliteEntity == null) return null;
        return eliteEntity.getLivingEntity();
    }
}
