package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import com.magmaguy.elitemobs.instanced.MatchInstance;
import com.magmaguy.elitemobs.instanced.dungeons.DungeonInstance;
import com.magmaguy.elitemobs.items.customloottable.SharedLootTable;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * TrinityForge の追加ドロップを EliteMobs の共有戦利品テーブル (emloot、need/greed) へ流す入口
 * (2026-08-09、「複数名で潜入したときのダンジョンドロップを emloot で回収したい」)。
 *
 * <p><b>なぜ必要になったか。</b> 2026-08-09 に {@code elite-drop-sources.boss-unique-loot} を
 * {@code false} で確定させたため、共有戦利品テーブルを唯一作っていた {@code CustomBossDeath} の
 * 戦利品配布が走らなくなった。つまり <b>EliteMobs 側から emloot が生成されることは二度と無い</b>。
 * need/greed という「機構」だけは複数人ダンジョンで活かしたい、というのが今回の方針なので、
 * 中身を TrinityForge の追加ドロップに差し替える。
 *
 * <p><b>呼ばれ方。</b> TrinityForge は EliteMobs にコンパイル依存しないので、TrinityForge 側からは
 * {@code com.trinityforge.mobs.EliteMobsSharedLootBridge} がリフレクションでここを呼ぶ
 * ({@code EliteMobsInstanceBridge} と同じ作法)。したがって<b>このクラスの完全修飾名・メソッド名・
 * 引数の型を変えると、向こう側は無言で fail-soft (地面へ落とす) に戻る</b>。名前を変えるときは
 * TrinityForge 側のブリッジ定数も同時に直すこと。
 *
 * <p><b>引き取る条件 (すべて満たしたときだけ)。</b>
 * <ol>
 *   <li>対象がこの EliteMobs が追跡しているエリートモブであること。</li>
 *   <li>そのモブに<b>2人以上のダメージ寄与者</b>がいること。1人しかいないなら分配する相手が
 *       いないので、地面へ落とすほうが素直 (共有テーブルは1人だと1tick後に即インベントリへ
 *       押し込む挙動になり、素材が黙って手元に湧いたように見える)。</li>
 *   <li>そのダメージ寄与者の誰かが<b>インスタンス化ダンジョンの中にいる</b>こと。フィールドの
 *       共闘で need/greed のGUIを開かせるのは過剰なので、ダンジョンに限定する。</li>
 * </ol>
 * どれか1つでも欠ければ {@code false} を返し、TrinityForge は従来どおり
 * {@code EntityDeathEvent#getDrops()} へ積む。
 *
 * <p><b>fail-soft。</b> 例外・{@code NoClassDefFoundError} は握り潰して {@code false} を返す。
 * 分配の橋が落ちてもドロップそのものは失われない (地面へ落ちる) ようにするため。
 */
public final class TrinityForgeSharedLoot {

    private TrinityForgeSharedLoot() {
    }

    /**
     * TrinityForge の追加ドロップ1スタックを共有戦利品テーブルへ入れる。
     *
     * @param entity    死亡したモブ
     * @param itemStack TrinityForge が追加しようとしているアイテム
     * @return {@code true} = 引き取った (呼び出し側は地面へ落とさないこと)。
     *         {@code false} = 対象外なので呼び出し側が従来どおり処理する。
     */
    public static boolean offerDungeonLoot(Entity entity, ItemStack itemStack) {
        if (entity == null || itemStack == null || itemStack.getType().isAir()) return false;
        try {
            return share(entity, itemStack);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge shared-loot bridge failed for " + itemStack.getType() + ": " + e);
            return false;
        }
    }

    private static boolean share(Entity entity, ItemStack itemStack) {
        EliteEntity eliteEntity = EntityTracker.getEliteMobEntity(entity);
        if (eliteEntity == null) return false;

        Map<Player, Double> damagers = eliteEntity.getDamagers();
        if (damagers == null || damagers.size() < 2) return false;
        if (!inInstancedDungeon(List.copyOf(damagers.keySet()))) return false;

        SharedLootTable sharedLootTable = SharedLootTable.getSharedLootTables().get(eliteEntity);
        // ここが唯一の生成点になる (boss-unique-loot: false で CustomBossDeath は走らないため)。
        // コンストラクタが自分を sharedLootTables へ登録し、60秒後の分配も予約するので、
        // 同じモブの2スタック目以降は上の get で拾えて同じテーブルに集まる。
        if (sharedLootTable == null) sharedLootTable = new SharedLootTable(eliteEntity);
        sharedLootTable.addTrinityForgeLoot(itemStack);
        return true;
    }

    /** ダメージ寄与者の誰か1人でもインスタンス化ダンジョンの中にいれば true。 */
    private static boolean inInstancedDungeon(List<Player> damagers) {
        for (Player damager : damagers) {
            MatchInstance matchInstance = MatchInstance.getPlayerInstance(damager);
            if (matchInstance instanceof DungeonInstance) return true;
        }
        return false;
    }
}
