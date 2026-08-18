package com.magmaguy.elitemobs.instanced.dungeons;

import com.magmaguy.elitemobs.api.EliteMobSpawnEvent;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.elitemobs.mobconstructor.custombosses.InstancedBossEntity;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * ダイナミックダンジョンのインスタンス内で湧いた EliteMobs を、必ず「プレイヤーが選んだ挑戦レベル」へ
 * 揃える(2026-08-18 W-80)。
 *
 * <p><b>なぜ必要か。</b> ブループリントに配置されたモブは {@link InstancedBossEntity} のコンストラクタで
 * 選択レベルを受け取るが、そこを通らない個体 ── ボスが召喚した増援、フェーズ2/3の本体、API 経由の
 * スポーン、インスタンスワールドでの自然湧き ── は {@code level: dynamic} の既定経路
 * ({@code CustomBossEntity#getDynamicLevel}) に落ちる。その経路は<b>近くのプレイヤーの EliteMobs 装備tier</b>
 * ({@code ElitePlayerInventory#getNaturalMobSpawnLevel}) でレベルを決めるが、TrinityForge は EliteMobs の
 * アイテム体系を一切使わないので tier は実質 0 のまま ── つまり<b>レベル1相当の張りぼて</b>が
 * 高レベルダンジョンの中に混ざる。選んだレベルが難易度に効いていない箇所は、そこだけだった。
 *
 * <p><b>優先度が LOWEST である理由。</b> TrinityForge 側の PDC 刻印
 * ({@code TrinityForgeSpawnListener}, {@code HIGH}) は {@code eliteEntity.getLevel()} を読んで
 * {@code MOB_LEVEL} を書く。レベルの上書きはその<b>前</b>に済ませておかないと、TF から見えるレベルが
 * 古いままになり、HP・攻撃力・撃破EXP・ダンジョン報酬の上乗せが全部ずれる。
 *
 * <p><b>ここでは報酬に一切触らない。</b> 報酬側は TrinityForge の {@code combat/damage.yml} の
 * {@code dungeon-level-reward} が、モブのレベル(= ここで揃えた値)を見て掛ける。
 */
public class DynamicDungeonLevelListener implements Listener {

    /**
     * この elite のレベルを {@code level} へ揃える。すでに一致していれば何もしない。
     *
     * <p>{@link InstancedBossEntity} は {@code setEntityLevel} を持っており、最大HPの引き直しに加えて
     * TrinityForge の {@code MOB_LEVEL} 刻印まで書き直す(刻印はスポーン時に1回しか書かれないため)。
     * それ以外の elite はスポーン直後にしかここへ来ない ── TF の刻印はこのあと {@code HIGH} で走るので、
     * レベルと最大HPだけ直せば足りる。
     */
    static void applySelectedLevel(EliteEntity eliteEntity, int level) {
        if (eliteEntity == null || level <= 0) return;
        if (eliteEntity.getLevel() == level) return;
        if (eliteEntity instanceof InstancedBossEntity instancedBossEntity) {
            instancedBossEntity.setEntityLevel(level);
            return;
        }
        eliteEntity.setLevel(level);
        eliteEntity.setMaxHealth();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEliteSpawn(EliteMobSpawnEvent event) {
        LivingEntity livingEntity = event.getEntity();
        EliteEntity eliteEntity = event.getEliteMobEntity();
        if (livingEntity == null || eliteEntity == null) return;

        DynamicDungeonInstance instance;
        try {
            instance = DynamicDungeonInstance.getForWorld(livingEntity.getWorld());
        } catch (RuntimeException e) {
            // インスタンス一覧の走査で落ちてもスポーンそのものは止めない(レベルが揃わないだけ)。
            Logger.warn("Failed to resolve the dynamic dungeon instance for a spawning elite: " + e.getMessage());
            return;
        }
        if (instance == null) return;

        // 選んだレベルそのものではなく難易度補正込みの実効レベル(normal -5 / hard ±0 / mythic +5)。
        int mobLevel = instance.getMobLevel();
        if (mobLevel <= 0 || eliteEntity.getLevel() == mobLevel) return;
        applySelectedLevel(eliteEntity, mobLevel);
    }
}
