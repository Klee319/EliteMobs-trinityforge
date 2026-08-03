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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
     * 2026-08-02 ダメージランキング修正: {@link #onEliteDamagedByPlayer} が player→elite の一撃を
     * 素のバニラBASEダメージへ意図的に戻す({@code vanillaBase}分岐)ため、この直後に
     * {@code EliteMobDamagedByPlayerEvent} 内部の {@code eliteEntity.addDamager(player, damage)} が
     * その未確定の小さい値をダメージランキング({@code EliteEntity#getDamagers()}/{@code aggro})へ
     * 記録してしまう。実際に敵HPへ適用される最終値は、この直後に同じ生イベントの {@code HIGH} で走る
     * TF 自身の {@code CombatListener} が計算する — つまり addDamager が呼ばれる時点では正しい値が
     * まだ存在しない。
     * <p>
     * ここで書き戻す挙動自体({@code event.setDamage(vanillaBase)})は「エリートをワンパンできる」
     * 事故の修正(クラスjavadoc参照)なので変更しない。代わりに、この時点で addDamager に記録された
     * 値を退避しておき、生イベントが確定した後({@code MONITOR})に本当の最終ダメージとの差分だけを
     * 追加補正する({@code addDamager} は累積加算なので差分適用で総量を合わせられる)。
     * キーはエリートの{@link LivingEntity}のUUID — 1つの生イベント処理は同期的(メインスレッド)で、
     * 同じ一撃の間に別の一撃が割り込むことはない。
     */
    private static final Map<UUID, PendingDamagerCorrection> pendingDamagerCorrections = new ConcurrentHashMap<>();

    private record PendingDamagerCorrection(Player player, double recordedDamage) {
    }

    // ------------------------------------------------------------------------------------------
    // CMB-02 (課題3, 2026-07-25) の二重適用ガードについて — 2026-07-28 に撤去した。
    //
    // 以前はこのクラスが LOWEST/MONITOR の対で EliteCombatDelegation.mark()/clear() を張り、
    // 「player→elite はこの listener が値付け済み」と TF の CombatListener に伝えていた。
    // 2026-07-28 に onEliteDamagedByPlayer が価格付けをやめ、EliteMobs の式の出力を素のバニラ
    // ダメージへ戻すだけになったので、CombatListener が二重に適用する対象がそもそも無くなった。
    //
    // ★復活させないこと: マークが立っていると CombatListener は event.getDamage() をそのまま
    //   最終ダメージとして採用する。今の onEliteDamagedByPlayer が入れている値は「素のバニラ
    //   ダメージ」なので、マークを戻すと素手で1ダメージしか出なくなる。
    // ------------------------------------------------------------------------------------------

    /**
     * 2026-07-28「エリートをワンパンできる(素手で30万ダメージ)」の修正。
     *
     * <p>真因: player→elite のダメージだけ EliteMobs 自身の式が生き残っていた。EliteMobs は
     * {@code baseDamage = 自分の指数HP式(2.1875×2^(Lv/5)) ÷ TARGET_HITS_TO_KILL_MOB(3)} を
     * 「プレイヤーの1発ダメージ」に据えており(常に3発で倒せる設計)、Lv93 帯でちょうど 30万になる。
     * ところが TF はエリートの<em>実</em>最大体力を自前のもっと平らな式で上書きしているため、
     * EliteMobs が想定する HP と実 HP が桁違いに乖離し、素手でも一撃で溶けていた。
     * この listener は従来その 30万に victim の防御を掛けるだけだったので、桁は落ちなかった。
     *
     * <p>方針(ユーザー決定 2026-07-28): <b>TF が player→elite も完全に引き取る</b>。ここでは
     * EliteMobs の式の出力を捨て、素のバニラダメージへ戻すだけにする。実際の価格付け
     * (attack-power 置換 / エンチャント / メイス / スイープ / チャージ減衰 / 会心 / 貫通 /
     * エリートの防御・回避 / AoE / 出血 / 戦闘EXP)は、この直後に同じ生イベントの {@code HIGH} で
     * 走る TF 自身の {@code CombatListener} が一手に行う。
     *
     * <p>この listener が価格付けをやめたので、CMB-02 の二重適用ガード
     * ({@link EliteCombatDelegation}) も player→elite では張らない — 張ると CombatListener が
     * 「fork が値付け済み」と判断して {@code event.getDamage()} をそのまま採用してしまい、
     * バニラ素の値(素手なら1)が最終ダメージになってしまう。
     *
     * <p>生イベントを伴わない合成ダメージ(スキル由来など {@code EntityDamageByEntityEvent} が無い
     * 経路)は CombatListener を通らないため、従来どおりこの場で FLAT パイプラインへ通す。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEliteDamagedByPlayer(EliteMobDamagedByPlayerEvent event) {
        if (!TrinityForgeIntegration.isCombatDelegationEnabled()) return;
        double base = event.getDamage();
        if (base <= 0) return;
        Player player = event.getPlayer();
        PersistentDataHolder victim = livingEntityOf(event.getEliteMobEntity());
        if (player == null || victim == null) return;
        EntityDamageByEntityEvent underlying = event.getEntityDamageByEntityEvent();
        if (underlying != null) {
            // EliteMobs はこの後 event.setDamage(BASE, ...) で書き戻すだけなので、この時点の生イベントは
            // まだプレイヤーの素のバニラダメージを保持している。それを EliteMobs の式の出力と差し替える。
            double vanillaBase = underlying.getDamage(EntityDamageEvent.DamageModifier.BASE);
            if (Double.isFinite(vanillaBase) && vanillaBase > 0) {
                event.setDamage(vanillaBase);
                // ダメージランキング補正の予約(このクラスのjavadoc参照)。この直後に EliteMobs 内部が
                // addDamager(player, vanillaBase) を呼ぶので、生イベントが HIGH(TF CombatListener)まで
                // 確定した後の MONITOR で本当の最終値との差分を追加する。
                LivingEntity eliteLiving = livingEntityOf(event.getEliteMobEntity());
                if (eliteLiving != null) {
                    pendingDamagerCorrections.put(eliteLiving.getUniqueId(),
                            new PendingDamagerCorrection(player, vanillaBase));
                }
                return;
            }
        }
        // #3: player's real TF attack stats (crit/penetration/bonus) fold into the elite-facing damage.
        applyPhysical(event::setDamage, victim, base, playerAttackStats(player));
    }

    /**
     * 2026-08-02 ダメージランキング修正の後段。同じ生イベントが {@code MONITOR} まで到達した時点では、
     * TF自身の {@code CombatListener}(HIGH)が既に最終ダメージを確定させている
     * ({@code getFinalDamage()} = 実際にHPから引かれる量。エリートは装甲系modifierを0クランプしている
     * ため通常は {@code getDamage()} と一致する)。{@link #onEliteDamagedByPlayer} が退避しておいた
     * 「addDamagerに記録済みの値(vanillaBase)」との差分だけを追加することで、二重計上せずに
     * ランキング/aggroの総量を本当の最終ダメージへ合わせる。{@code addDamager} は累積加算なので
     * 差分適用が正しい(総量の付け替えであって上書きではない)。
     * <p>
     * イベントがキャンセルされた場合は実際には何もダメージが通っていないので補正しない(マップの
     * エントリだけ掃除してリークを防ぐ)。予約が無い(= このクラスの vanillaBase 分岐を通らなかった、
     * つまりプレイヤー攻撃以外/委譲無効時)ヒットは何もしない。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRawDamageFinalized(EntityDamageByEntityEvent event) {
        PendingDamagerCorrection pending = pendingDamagerCorrections.remove(event.getEntity().getUniqueId());
        if (pending == null) return;
        if (event.isCancelled()) return;
        EliteEntity eliteEntity = EntityTracker.getEliteMobEntity(event.getEntity());
        if (eliteEntity == null) return;
        double trueFinal = event.getFinalDamage();
        double delta = trueFinal - pending.recordedDamage();
        if (delta == 0) return;
        eliteEntity.addDamager(pending.player(), delta);
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
        // end-to-end.
        //
        // 2026-07-30: PROJECTILE も同じ扱いに変わった。TrinityForge 側の resolveMobAttacker が
        // 「飛び道具の発射者がモブなら、その一撃も TF が価格付けする」ようになったため
        // (それまでモブの矢だけ TF スケールが乗っていなかった)、ここで先に価格付けすると
        // 同じ一撃にプレイヤーの守備/回避が2回掛かる。爆発など TF が持たない cause は従来どおり
        // この listener の flat 委譲が担う。
        if (isTrinityForgeOwnedCause(event) && hasTrinityForgeAttackStamp(attacker)) return;
        // Mob attacker: its level scaling is already baked into the base by EliteMobs' LevelScaling, and it
        // carries no player offensive stats — so plain(0) and the player (victim) defense is what applies.
        // 2026-08-02 (実装1): この分岐は「フル attack プロファイルは持たないが magic-ratio だけは
        // combat/mob-overrides.yml で単独指定されているかもしれない」約396体のダンジョンモブの経路
        // (上のガードで既にフル attack-power スタンプ持ちは弾かれている)。stampAttack() 側の
        // MOB_ATTACK_MAGIC_RATIO は hasAttackProfile() のゲート外で無条件に書かれる
        // (MobData#attackMagicRatio javadoc 参照)ので、ここで読んでも安全 — 未設定なら既定0.0で
        // 従来どおり完全物理のまま。SymmetricCombatService#physicalFinalDamageFlat がこの比率で
        // 物理/魔法へ分割する(実装1本体)。
        applyPhysical(event::setDamage, player, base,
                AttackStats.plain(0).withMagicRatio(mobAttackMagicRatio(attacker)));
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

    /**
     * True when the underlying hit's cause is one TrinityForge's own mob→player path owns
     * ({@code CombatListener#resolveMobAttacker}): direct melee/sweep, or a projectile whose shooter is
     * the mob. Keep this in sync with that method — a cause listed here MUST be handled there, or the
     * hit loses its mitigation entirely instead of being mitigated twice.
     */
    private static boolean isTrinityForgeOwnedCause(PlayerDamagedByEliteMobEvent event) {
        org.bukkit.event.entity.EntityDamageByEntityEvent underlying = event.getEntityDamageByEntityEvent();
        if (underlying == null) return false;
        return switch (underlying.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> true;
            case PROJECTILE -> underlying.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof LivingEntity shooter
                    && !(shooter instanceof Player);
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
        // 2026-08-02 (実装1): elite→elite も同じく攻撃側の magic-ratio を尊重する。
        applyPhysical(event::setDamage, victim, base,
                AttackStats.plain(0).withMagicRatio(mobAttackMagicRatio(attacker)));
    }

    /**
     * Fail-closed to 0.0 (完全物理、従来どおり) so a TrinityForge classloading hiccup never silently
     * routes a normal attack through the magical component. Mirrors {@link #hasTrinityForgeAttackStamp}'s
     * guard style. See {@code MobData#attackMagicRatio()} — this reads {@code MOB_ATTACK_MAGIC_RATIO}
     * independently of {@code hasAttackProfile()}, so it works for the ~396 dungeon mobs that only carry
     * a magic-ratio override without a full attack-power stamp.
     */
    private static double mobAttackMagicRatio(LivingEntity attacker) {
        try {
            return MobData.of(attacker).attackMagicRatio();
        } catch (NoClassDefFoundError | RuntimeException e) {
            return 0.0;
        }
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
