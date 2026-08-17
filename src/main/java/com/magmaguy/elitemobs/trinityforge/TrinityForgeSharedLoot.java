package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import com.magmaguy.elitemobs.instanced.MatchInstance;
import com.magmaguy.elitemobs.instanced.dungeons.DungeonInstance;
import com.magmaguy.elitemobs.items.customloottable.SharedLootTable;
import com.magmaguy.elitemobs.mobconstructor.EliteEntity;
import com.magmaguy.magmacore.util.Logger;
import org.bukkit.ChatColor;
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
 * <p><b>入口は2つある (2026-08-18)。</b> {@link #offerDungeonLoot} は need/greed で<b>1人だけ</b>に
 * 渡す従来の経路、{@link #offerPerPlayerLoot} は<b>寄与者全員に1個ずつ</b>渡す経路。
 * どちらを使うかを決めるのは TrinityForge 側 ({@code MobDropRoller#isProgressionDrop}) で、
 * 引き取り条件は上の3つで共通。
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

    /**
     * TrinityForge の追加ドロップ1スタックを<b>ダメージ寄与者全員に1個ずつ</b>配る
     * (2026-08-18、「複数人で潜ると進行アイテムが1人にしか渡らない」)。
     *
     * <p><b>なぜ need/greed と分けるのか。</b> {@link #offerDungeonLoot} が使う共有戦利品テーブルは
     * 1スタックにつき当選者を1人しか選ばない ({@code SharedLootTable#rollLoot})。スレッドのような
     * ランダム報酬ならそれで正しいが、ダンジョン印・試練の鍵・かけらのような<b>確率1.0の進行
     * アイテム</b>まで同じ経路に乗ると、2人で潜ったとき片方が次の試練に入れず図鑑も埋まらない。
     * そこで TrinityForge 側が「これは進行アイテムだ」と判断したスタックだけこちらへ回す。
     *
     * <p>引き取る条件は {@link #offerDungeonLoot} と同一 (エリートモブ・寄与者2人以上・
     * インスタンス化ダンジョン内)。1人しかいないなら分配の必要が無いので {@code false} を返し、
     * TrinityForge 側が従来どおり地面へ落とす。
     *
     * @return {@code true} = 配り終えた (呼び出し側は地面へ落とさないこと)。
     */
    public static boolean offerPerPlayerLoot(Entity entity, ItemStack itemStack) {
        if (entity == null || itemStack == null || itemStack.getType().isAir()) return false;
        try {
            return givePerPlayer(entity, itemStack);
        } catch (NoClassDefFoundError | RuntimeException e) {
            Logger.warn("TrinityForge per-player loot bridge failed for " + itemStack.getType() + ": " + e);
            return false;
        }
    }

    private static boolean givePerPlayer(Entity entity, ItemStack itemStack) {
        List<Player> receivers = eligibleReceivers(entity);
        if (receivers == null) return false;
        for (Player receiver : receivers) {
            // clone() 必須: 同じ ItemStack インスタンスを複数人の addItem へ渡すと、
            // 1人ぶん入れた時点で amount が 0 になり2人目以降が無言で受け取れない。
            ItemStack copy = itemStack.clone();
            for (ItemStack leftover : receiver.getInventory().addItem(copy).values()) {
                receiver.getWorld().dropItemNaturally(receiver.getLocation(), leftover);
            }
            receiver.sendMessage(ChatColor.GREEN + "受け取った: " + ChatColor.WHITE + displayNameOf(itemStack));
        }
        return true;
    }

    /** 表示名が無い素材でも「1x source gem」のように読める名前にする。 */
    private static String displayNameOf(ItemStack itemStack) {
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }
        return itemStack.getAmount() + "x " + itemStack.getType().toString().toLowerCase().replace("_", " ");
    }

    private static boolean share(Entity entity, ItemStack itemStack) {
        List<Player> receivers = eligibleReceivers(entity);
        if (receivers == null) return false;
        EliteEntity eliteEntity = EntityTracker.getEliteMobEntity(entity);

        SharedLootTable sharedLootTable = SharedLootTable.getSharedLootTables().get(eliteEntity);
        // ここが唯一の生成点になる (boss-unique-loot: false で CustomBossDeath は走らないため)。
        // コンストラクタが自分を sharedLootTables へ登録し、60秒後の分配も予約するので、
        // 同じモブの2スタック目以降は上の get で拾えて同じテーブルに集まる。
        if (sharedLootTable == null) sharedLootTable = new SharedLootTable(eliteEntity);
        sharedLootTable.addTrinityForgeLoot(itemStack);
        return true;
    }

    /**
     * 引き取り条件 (エリートモブ・ダメージ寄与者2人以上・インスタンス化ダンジョン内) を満たすときだけ
     * 寄与者一覧を返す。満たさなければ {@code null} — 呼び出し側はどちらの経路も {@code false} で
     * 返し、TrinityForge が従来どおり地面へ落とす。need/greed と全員配布で条件を分けないための共通化。
     */
    private static List<Player> eligibleReceivers(Entity entity) {
        EliteEntity eliteEntity = EntityTracker.getEliteMobEntity(entity);
        if (eliteEntity == null) return null;
        Map<Player, Double> damagers = eliteEntity.getDamagers();
        if (damagers == null || damagers.size() < 2) return null;
        List<Player> receivers = List.copyOf(damagers.keySet());
        if (!inInstancedDungeon(receivers)) return null;
        return receivers;
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
