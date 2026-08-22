package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.instanced.dungeons.DynamicDungeonLevelPolicy;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-22 ユーザー報告の回帰テスト。
 *
 * <blockquote>
 * ・ダンジョンの敵が殴ったらそのダンジョンに入ってるプレイヤーの中で1番戦闘レベルの高い人に
 * 合わせられる ⇒ 僕(100Lv)が50lvで部屋を作ってボス戦まで行ったら敵の体力は100レベの10Mとかになる<br>
 * ・34lvのダークカテドラルで自分の剣が50lv武器＆55lv装備の状況で、道中はワンパンできる。
 * しかし最後のボスだけ明らかに倒せる設計されてない
 * </blockquote>
 *
 * <p><b>この2件は同じ1つのバグ。</b> フェーズボスは体力が閾値を割るたび
 * {@code PhaseBossEntity#switchPhase} で「remove → setCustomBossesConfigFields(フェーズ設定) → spawn」を
 * やり直す。{@code setCustomBossesConfigFields} の末尾の {@code super.setLevel(config.getLevel())} は
 * {@code level: dynamic} では <b>-1</b> なので、インスタンスが与えた挑戦レベルがそこで消える。
 * 続く {@code spawn} が {@code level == -1} の分岐に落ち、素の EliteMobs 経路
 * ({@code getDynamicLevel} = 周囲プレイヤーの戦闘レベルの最大値)でレベルを決め直していた。
 * ダークカテドラルの phase_0 の閾値は 0.9999 ── <b>最初の一撃で</b>ここを通る。
 * フェーズを持たない道中の雑魚は通らないので、選んだレベルのまま残る
 * （実ログでも同一インスタンス内で雑魚 {@code [34]} / ボス {@code 『67』} になっていた）。
 *
 * <p>最大HPは {@code 150 × 1.072^L × 1.375 × 1.236 × 30}。34 → 8.1 万、67 → 80.6 万でおよそ 10 倍、
 * 100 なら約 800 万でユーザーの言う「10M」とほぼ一致する。
 *
 * <p><b>なぜ判定だけを切り出して固定するか。</b> 壊れ方が2箇所に跨っている ──
 * 値を決める {@code getDynamicLevel} と、5 秒ごとの {@code dynamicLevelUpdater} へ載せる
 * {@code spawn} の {@code dynamicLevel = true}。片方だけ直すと「スポーン直後は正しいのに
 * 5 秒後に化ける」形になり、<b>実機で数分殴らないと現れない</b>。生きた Bukkit サーバ・
 * インスタンスワールド・EliteMobs のプレイヤーデータ無しに走らせられないので、
 * 規則そのものは {@link DynamicDungeonLevelPolicy} の純関数で、2つの分岐がそこを通ることは
 * この fork の既存の流儀（{@link Javap} でメソッド単位のバイトコードを見る）で固定する。
 */
class DynamicDungeonBossLevelTest {

    // ------------------------------------------------------------------ 規則そのもの

    @Test
    @DisplayName("インスタンス内は挑戦レベルが勝つ(パーティ最強の戦闘レベルに合わせない)")
    void instancedLevelBeatsTheStrongestNearbyPlayer() {
        // 34レベルで作った部屋に戦闘レベル67の人が居ても、モブは34のまま。
        assertEquals(34, DynamicDungeonLevelPolicy.resolve(34, 67));
        // 100レベルの人が50レベルで部屋を作った場合（報告そのもの）。
        assertEquals(50, DynamicDungeonLevelPolicy.resolve(50, 100));
        // 逆に挑戦レベルの方が高くても、挑戦レベルを下げない。
        assertEquals(80, DynamicDungeonLevelPolicy.resolve(80, 12));
    }

    @Test
    @DisplayName("インスタンス外は素の EliteMobs のまま(周囲プレイヤー追随)")
    void outsideAnInstanceTheVanillaBehaviourIsKept() {
        assertEquals(67, DynamicDungeonLevelPolicy.resolve(0, 67));
        // 周囲に誰も居ない場合の EliteMobs の既定は 1。0 やマイナスへ落とさない。
        assertEquals(1, DynamicDungeonLevelPolicy.resolve(0, 1));
        assertEquals(1, DynamicDungeonLevelPolicy.resolve(0, 0));
        assertEquals(1, DynamicDungeonLevelPolicy.resolve(0, -3));
    }

    @Test
    @DisplayName("インスタンス内のモブは5秒ごとのレベル更新に載せない")
    void instancedMobsAreNotTrackedByThePeriodicUpdater() {
        assertFalse(DynamicDungeonLevelPolicy.tracksNearbyPlayers(34),
                "インスタンス内のモブを updater に載せると、挑戦レベルへ直しても5秒後に上書きされる");
        assertFalse(DynamicDungeonLevelPolicy.tracksNearbyPlayers(1));
        assertTrue(DynamicDungeonLevelPolicy.tracksNearbyPlayers(0),
                "インスタンス外は素の EliteMobs どおり周囲プレイヤーへ追随させる");
        assertTrue(DynamicDungeonLevelPolicy.tracksNearbyPlayers(-1),
                "インスタンスが引けなかったとき(-1)も素の挙動へ倒す");
    }

    /** 挑戦レベルは常に 1 以上（{@code DynamicDungeonInstance#getMobLevel} が保証）だが、規則側でも崩さない。 */
    @Test
    @DisplayName("どの入力でもレベル0以下は返さない")
    void neverResolvesToZeroOrBelow() {
        for (int instanced : new int[]{-5, 0, 1, 34, 100}) {
            for (int nearby : new int[]{-5, 0, 1, 67, 100}) {
                assertTrue(DynamicDungeonLevelPolicy.resolve(instanced, nearby) >= 1,
                        "instanced=" + instanced + " nearby=" + nearby);
            }
        }
    }

    // ------------------------------------------------------------------ 2つの分岐の配線

    @Test
    @DisplayName("getDynamicLevel はインスタンスを見てから周囲プレイヤーへ落ちる")
    void getDynamicLevelConsultsTheInstanceFirst() throws Exception {
        String method = Javap.sliceMethod(Javap.disassemble(CustomBossEntity.class),
                "void getDynamicLevel(");
        assertTrue(method.contains("dynamicDungeonLevel"),
                "getDynamicLevel がダンジョンインスタンスを見ていない。フェーズが進むたびに "
                        + "パーティ最強の戦闘レベルへ化ける。\n" + method);
        assertTrue(method.contains("DynamicDungeonLevelPolicy.resolve"),
                "getDynamicLevel が判定を通っていない。\n" + method);
    }

    @Test
    @DisplayName("spawn はインスタンス内のボスを5秒ごとのレベル更新に登録しない")
    void spawnDoesNotRegisterInstancedBossesWithTheUpdater() throws Exception {
        String method = Javap.sliceMethod(Javap.disassemble(CustomBossEntity.class),
                "void spawn(boolean)");
        assertTrue(method.contains("DynamicDungeonLevelPolicy.tracksNearbyPlayers"),
                "spawn が無条件に dynamicLevel = true にしている。getDynamicLevel を直しても "
                        + "5秒ごとの dynamicLevelUpdater が挑戦レベルを取り消し続ける。\n" + method);
    }
}
