package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.api.EliteMobDamagedByPlayerEvent;
import com.magmaguy.elitemobs.api.EliteMobExitCombatEvent;
import com.magmaguy.elitemobs.api.EliteMobTargetPlayerEvent;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.magmacore.util.Logger;
import com.trinityforge.TrinityForge;
import com.trinityforge.hate.HateService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Hate-based targeting (fork spec section 4).
 * <p>
 * Tanks accumulate hate by damaging an elite mob; when the mob (re)selects a target, it is redirected to the
 * highest-hate player via the underlying {@code EntityTargetLivingEntityEvent#setTarget}.
 * <p>
 * <b>Threat: TrinityForge is the single book-keeper while present (ヘイト一本化).</b> When TrinityForge is
 * present, its own {@code HateListener} records every mob's incoming player damage into the shared
 * {@link HateService} (bounded store with per-mob/global caps, TTL eviction, optional decay and a fully
 * deterministic tie-break), and the fork <em>reads</em> {@link HateService#topAttacker(UUID)} from it to
 * redirect targeting. The fork <b>never writes to TrinityForge</b> (it never calls {@code recordDamage}) and,
 * while TF's hate service resolves, it does <b>not</b> record into its own {@link HateTable} either — one hit,
 * one ledger. The fork-local table is used exclusively while TrinityForge is absent or unresolvable, so the two
 * stores never track the same fight in parallel (previously the fork shadow-recorded every hit, which meant the
 * same damage was book-kept twice with different multipliers; see {@link #onEliteDamagedByPlayer}).
 * <p>
 * <b>Fail-safe / degraded standalone.</b> If the {@code hate-targeting} toggle is off the fork disables its
 * targeting override entirely and EliteMobs' native targeting stands (and nothing is recorded at all). If the
 * toggle is on, the fork records into its {@link HateTable} only while TrinityForge is unavailable; it
 * <em>reads</em> from TrinityForge when available and from the fork-local table otherwise (or on a failed read),
 * so hate targeting still degrades gracefully. Every TrinityForge call is wrapped so a delegation failure — any
 * {@link RuntimeException} or
 * {@link LinkageError}, including {@link NoClassDefFoundError} and {@link NoSuchMethodError} from a binary-
 * incompatible TrinityForge — can never propagate into EliteMobs' combat/targeting: a failed {@code topAttacker}
 * read falls straight through to the fork-local table instead of aborting the retarget. The local table's own
 * cleanup handlers below always run, keeping the shadow store leak-free without ever touching TrinityForge's
 * table.
 */
public class TrinityForgeTargetingListener implements Listener {

    /**
     * Record hate into the fork-local {@link HateTable} <b>only while TrinityForge's hate service is
     * unavailable</b> (standalone / degraded operation).
     * <p>
     * ヘイト記録の一本化: TrinityForge が存命の間は、TF 自身の {@code HateListener} が同じ被弾を共有
     * {@link HateService} へ記録する(ロール係数 {@code hateThreatMultiplier} と {@code threatPerDamage}
     * を折り込む唯一の権威)。その間フォーク側もローカル表へ書き込むと、同一ヒットの帳簿が2冊になり
     * (係数の掛かり方も異なる)、mid-fight で TF 読み取りが失敗してローカル表へフォールバックした瞬間に
     * ターゲット順位が食い違う「二重計上」状態だった。今は TF が読める間はローカル記録自体を行わず、
     * TF が居ない/読めない間のみローカル表が生きる(その間の記録が targeting のフォールバック源)。
     * トレードオフ: TF 稼働中に {@code topAttacker} 読み取りだけが突然失敗した場合、ローカル表は
     * 空のことがあり、そのモブは一時的にネイティブターゲティングへ戻る(次のヒットからローカル記録が
     * 溜まり始めるので自己回復する)。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEliteDamagedByPlayer(EliteMobDamagedByPlayerEvent event) {
        if (!TrinityForgeIntegration.isHateTargetingEnabled()) return;
        if (tfHateService() != null) return; // TF存命: 記録はTFのHateListenerに一本化(二重計上防止)
        Player player = event.getPlayer();
        if (player == null) return;
        double dealt = event.getDamage();
        if (dealt <= 0) return;

        UUID mobUuid = mobUuid(event.getEliteMobEntity());
        if (mobUuid == null) return;
        HateTable.addHate(mobUuid, player.getUniqueId(), dealt);
    }

    /** Redirect targeting to the highest-hate player. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEliteTargetPlayer(EliteMobTargetPlayerEvent event) {
        if (!TrinityForgeIntegration.isHateTargetingEnabled()) return;
        if (event.entityTargetLivingEntityEvent() == null) return;
        UUID mobUuid = mobUuid(event.getEliteMobEntity());
        if (mobUuid == null) return;

        // A candidate is only usable if it is online, alive and in the mob's world. The fork-local lookup
        // uses this to skip an ineligible leader and roll down to the next-best aggressor instead of whiffing.
        Entity mob = event.getEntity();
        Predicate<UUID> eligible = uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isDead()) return false;
            return mob == null || p.getWorld().equals(mob.getWorld());
        };

        Optional<UUID> top;
        HateService hate = tfHateService();
        if (hate != null) {
            try {
                top = hate.topAttacker(mobUuid);
            } catch (Throwable t) {
                // TrinityForge delegation failed (NoClassDefFoundError, runtime error, etc.): fall straight
                // through to the fork-local table rather than aborting the retarget and breaking targeting.
                Logger.warn("TrinityForge hate topAttacker lookup failed, falling back to local table: " + t);
                top = HateTable.highestHate(mobUuid, eligible);
            }
        } else {
            // TrinityForge unavailable: use the fork-local table (eligibility-filtered to the next-best).
            top = HateTable.highestHate(mobUuid, eligible);
        }
        if (top == null || top.isEmpty()) return;

        Player current = event.getPlayer();
        if (current != null && top.get().equals(current.getUniqueId())) return; // already targeting top-hate player

        // Re-check eligibility on the resolved UUID: the TrinityForge path is not pre-filtered, and even the
        // local path may have raced with the player dying/leaving between selection and here.
        Player target = Bukkit.getPlayer(top.get());
        if (target == null || !target.isOnline() || target.isDead()) return;
        // Only redirect within the same world to avoid forcing an invalid cross-world target.
        if (mob != null && !target.getWorld().equals(mob.getWorld())) return;

        try {
            event.entityTargetLivingEntityEvent().setTarget(target);
        } catch (Throwable t) {
            Logger.warn("TrinityForge hate targeting override failed: " + t);
        }
    }

    /**
     * Forget hate for a mob that leaves combat. Cleanup runs unconditionally (even if the toggle is off or
     * TrinityForge has gone away) so accumulated entries in the fork-local fallback table can never leak after
     * the feature is disabled. This only touches the fork-local {@link HateTable}; TrinityForge's own table is
     * evicted by TrinityForge's {@code HateListener}, so there is no double management.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEliteExitCombat(EliteMobExitCombatEvent event) {
        UUID mobUuid = mobUuid(event.getEliteMobEntity());
        if (mobUuid != null) HateTable.clear(mobUuid);
    }

    /** Secondary cleanup: mobs that die without firing exit-combat (chunk unload, admin removal, etc.). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEliteDeath(com.magmaguy.elitemobs.api.EliteMobDeathEvent event) {
        UUID mobUuid = mobUuid(event.getEliteEntity());
        if (mobUuid != null) HateTable.clear(mobUuid);
    }

    /** Drop a disconnecting player's hate so a stale UUID can't pin the top-hate slot forever. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        HateTable.forgetPlayer(event.getPlayer().getUniqueId());
    }

    /**
     * Drop a dying player's hate so a corpse can't hold the top-hate slot. Cleanup runs unconditionally so the
     * fork-local fallback table stays leak-free; it only touches {@link HateTable} (TrinityForge evicts its own
     * table via its {@code HateListener}). The eligibility filter in {@link #onEliteTargetPlayer} already skips
     * dead players mid-fight, but this frees the entry outright once death is final.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        HateTable.forgetPlayer(event.getEntity().getUniqueId());
    }

    /**
     * Resolves TrinityForge's shared {@link HateService}, or {@code null} when hate should fall back to the
     * fork-local table. Returns null when TrinityForge has no live plugin instance or has not exposed a hate
     * service; any classloading/runtime failure is swallowed to null so the fallback path takes over. The catch
     * spans both {@link RuntimeException} and {@link LinkageError} so a binary-incompatible TrinityForge that
     * throws {@link NoSuchMethodError} (or any other {@link LinkageError} beyond {@link NoClassDefFoundError})
     * degrades to the fork-local table rather than leaking out of an event handler. The {@code hate-targeting}
     * toggle itself is checked by the callers via {@link TrinityForgeIntegration#isHateTargetingEnabled()}
     * before this is consulted.
     */
    private static HateService tfHateService() {
        try {
            if (TrinityForge.getInstance() == null) return null;
            return TrinityForgeIntegration.hateService();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /**
     * Resolves the mob UUID used as the fallback HateTable key. Falls back to
     * {@link EliteEntity#getUnsyncedLivingEntity()} when {@link EliteEntity#getLivingEntity()} has already been
     * nulled out (death and the ELITE_NOT_VALID exit-combat path both null the synced reference before this
     * listener runs), so cleanup can still find the correct hate-table entry instead of silently leaking it.
     */
    private static UUID mobUuid(EliteEntity eliteEntity) {
        LivingEntity livingEntity = mobLivingEntity(eliteEntity);
        return livingEntity == null ? null : livingEntity.getUniqueId();
    }

    /**
     * Resolves the Bukkit {@link LivingEntity} backing an elite mob, preferring the synced reference and
     * falling back to the unsynced one (see {@link #mobUuid}). TrinityForge's {@code recordDamage} needs the
     * live entity (for its UUID and world), not just the UUID.
     */
    private static LivingEntity mobLivingEntity(EliteEntity eliteEntity) {
        if (eliteEntity == null) return null;
        LivingEntity livingEntity = eliteEntity.getLivingEntity();
        if (livingEntity == null) livingEntity = eliteEntity.getUnsyncedLivingEntity();
        return livingEntity;
    }
}
