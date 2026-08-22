package com.magmaguy.elitemobs.instanced.dungeons;

/**
 * {@code level: dynamic} のモブのレベルを<b>誰が決めるか</b>の唯一の判定(TrinityForge 追加、2026-08-22)。
 *
 * <p><b>ダイナミックダンジョンの中では「プレイヤーがメニューで選んだ挑戦レベル」が唯一の正</b>で、
 * 素の EliteMobs の挙動 ── 近くのプレイヤーを走査して<b>いちばん強い人</b>に合わせる ── は使わない。
 *
 * <p><b>なぜこの判定だけを切り出したか。</b> 実際の壊れ方は
 * {@code CustomBossEntity#getDynamicLevel} と {@code CustomBossEntity#spawn} の<b>2箇所</b>に跨る:
 *
 * <ul>
 *   <li>{@code getDynamicLevel} が周囲プレイヤーの最大戦闘レベルを採ってしまう(値が壊れる)</li>
 *   <li>{@code spawn} が {@code dynamicLevel = true} にしてボスを 5 秒ごとの
 *       {@code dynamicLevelUpdater} に載せてしまう(直しても毎回取り消される)</li>
 * </ul>
 *
 * <p>片方だけ直すと「スポーン直後は正しいのに 5 秒後に化ける」「フェーズが進むと化ける」という、
 * <b>実機で数分殴らないと現れない</b>壊れ方になる。どちらの分岐も同じ 2 つの述語を通すことで、
 * 生きた Bukkit サーバ無しでも規則そのものを固定できるようにしてある
 * ({@code DynamicDungeonBossLevelTest})。
 *
 * <p><b>実害(2026-08-22 のユーザー報告)。</b> 34 レベルで作ったダークカテドラルで、道中の雑魚は
 * {@code [34]} のままなのに最終ボスだけ {@code 『67』}(= パーティ最強の戦闘レベル)になっていた。
 * 最大HPは {@code 150 × 1.072^L × 1.375 × 1.236 × 30} なので 8.1 万 → 80.6 万でおよそ 10 倍。
 * 100 レベルの人が 50 レベルで部屋を作れば最終ボスだけ約 800 万 HP になる。
 * フェーズを持たない雑魚は再スポーンを通らないので、この壊れ方はフェーズボスにしか出ない
 * ── 「道中はワンパンなのにボスだけ倒せない」という報告の形はこれで説明が付く。
 */
public final class DynamicDungeonLevelPolicy {

    private DynamicDungeonLevelPolicy() {
    }

    /**
     * このモブのレベルを周囲のプレイヤーに追随させてよいか。
     *
     * <p>{@code false} を返した個体は {@code CustomBossEntity} の
     * {@code dynamicLevelBossEntities}(5 秒ごとにレベルを引き直す集合)に<b>載せてはならない</b>。
     * 載せると挑戦レベルへ直しても 5 秒後に上書きされる。
     *
     * @param instancedLevel {@code DynamicDungeonInstance#getMobLevel()}。インスタンス外なら 0
     */
    public static boolean tracksNearbyPlayers(int instancedLevel) {
        return instancedLevel <= 0;
    }

    /**
     * 実際に与えるレベル。インスタンス内の挑戦レベルは<b>常に</b>周囲プレイヤー由来の値に優先する。
     *
     * @param instancedLevel           インスタンスの挑戦レベル。インスタンス外なら 0
     * @param nearbyHighestCombatLevel 周囲プレイヤーの戦闘レベルの最大値(素の EliteMobs 経路の結果)
     * @return 1 以上のレベル
     */
    public static int resolve(int instancedLevel, int nearbyHighestCombatLevel) {
        if (!tracksNearbyPlayers(instancedLevel)) return instancedLevel;
        return Math.max(1, nearbyHighestCombatLevel);
    }
}
