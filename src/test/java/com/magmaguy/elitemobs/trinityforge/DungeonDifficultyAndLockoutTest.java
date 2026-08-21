package com.magmaguy.elitemobs.trinityforge;

import com.magmaguy.elitemobs.config.DungeonsConfig;
import com.magmaguy.elitemobs.dungeons.DungeonBossLockoutHandler;
import com.magmaguy.elitemobs.instanced.dungeons.DynamicDungeonInstance;
import com.magmaguy.elitemobs.menus.DynamicDungeonBrowser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-08-21 ユーザー要望2件の回帰テスト。
 *
 * <ol>
 *   <li>「別の職業を上げる際にダンジョンのレベルが高すぎるので、ノーマル/ハード/ミシックを
 *   戦闘レベル ±5 から選ぶのではなく、戦闘レベルの 100%/75%/50% から選んでそのレベルを反映してほしい」</li>
 *   <li>「EM ダンジョンの討伐時にロックアウトのチャットとタイトル通知が出ないようにしてほしい」</li>
 * </ol>
 *
 * <p>割合の算数は純関数なので直接呼ぶ。残り2つ（難易度補正の二重適用を消したこと・通知にゲートが
 * 付いたこと）は、動かすのに生きたインスタンス／プレイヤー／DB が要るので、この fork の既存の流儀
 * （{@link Javap} でコンパイル済みクラスを逆アセンブルし、当該メソッドのバイトコードだけを見る）で
 * 固定する。<b>クラス単位の定数プール検査では捕まらない</b>——どちらも「クラスのどこかには名前が
 * 残るが、当該メソッドからだけ消える／付く」形の変更だから。
 */
class DungeonDifficultyAndLockoutTest {

    // ---------------------------------------------------------------- 割合

    @Test
    @DisplayName("難易度は戦闘レベルの 50% / 75% / 100% に対応する")
    void difficultiesMapToCombatLevelPercentages() {
        // EM のコンテンツパッケージ側のキーは normal/hard/mythic のまま（全ダンジョンの yml を
        // 書き換えずに意味だけ差し替えているので、この対応が崩れると全難易度がずれる）。
        assertEquals(50, DungeonsConfig.getDynamicDungeonCombatLevelPercent("normal"), "normal = イージー");
        assertEquals(75, DungeonsConfig.getDynamicDungeonCombatLevelPercent("hard"), "hard = ノーマル");
        assertEquals(100, DungeonsConfig.getDynamicDungeonCombatLevelPercent("mythic"), "mythic = ハード");
    }

    /**
     * 静的フィールドは設定のロードまで 0 なので、フォールバックが無いと全難易度が 0% になる。
     * その壊れ方は例外もログも出さず「なぜかモブが全部レベル1」としてしか表に出ない。
     */
    @Test
    @DisplayName("設定が未ロードでも 0% にはならない(全モブがレベル1になる無言死を防ぐ)")
    void unloadedConfigFallsBackInsteadOfCollapsingToLevelOne() {
        for (String difficulty : new String[]{"normal", "hard", "mythic"}) {
            assertTrue(DungeonsConfig.getDynamicDungeonCombatLevelPercent(difficulty) > 0,
                    difficulty + " の割合が 0 以下になっている");
        }
        assertEquals(100, DungeonsConfig.getDynamicDungeonCombatLevelPercent("does_not_exist"),
                "知らない難易度名は素の戦闘レベル(100%)へ倒すこと");
        assertEquals(100, DungeonsConfig.getDynamicDungeonCombatLevelPercent(null),
                "null も素の戦闘レベル(100%)へ倒すこと");
    }

    @Test
    @DisplayName("戦闘レベル100 なら イージー50 / ノーマル75 / ハード100")
    void mobLevelIsTheRoundedPercentageOfTheCombatLevel() {
        assertEquals(50, DungeonsConfig.resolveDynamicDungeonMobLevel(100, "normal"));
        assertEquals(75, DungeonsConfig.resolveDynamicDungeonMobLevel(100, "hard"));
        assertEquals(100, DungeonsConfig.resolveDynamicDungeonMobLevel(100, "mythic"));
        // 端数は四捨五入。切り捨てにすると 75% 帯だけが常に1レベル損をする。
        assertEquals(17, DungeonsConfig.resolveDynamicDungeonMobLevel(33, "normal"), "33 の 50% = 16.5 -> 17");
        assertEquals(25, DungeonsConfig.resolveDynamicDungeonMobLevel(33, "hard"), "33 の 75% = 24.75 -> 25");
    }

    @Test
    @DisplayName("低レベルでもレベル0以下にはならない")
    void mobLevelNeverDropsBelowOne() {
        assertEquals(1, DungeonsConfig.resolveDynamicDungeonMobLevel(1, "normal"));
        assertEquals(1, DungeonsConfig.resolveDynamicDungeonMobLevel(0, "normal"), "戦闘レベル0でも1へ倒す");
        assertEquals(1, DungeonsConfig.resolveDynamicDungeonMobLevel(-5, "mythic"), "負値でも1へ倒す");
    }

    @Test
    @DisplayName("難易度が上がるほどモブレベルも上がる(順序が逆転しない)")
    void higherDifficultyAlwaysMeansHigherMobLevel() {
        for (int combatLevel : new int[]{10, 25, 50, 100, 200}) {
            int easy = DungeonsConfig.resolveDynamicDungeonMobLevel(combatLevel, "normal");
            int normal = DungeonsConfig.resolveDynamicDungeonMobLevel(combatLevel, "hard");
            int hard = DungeonsConfig.resolveDynamicDungeonMobLevel(combatLevel, "mythic");
            assertTrue(easy <= normal && normal <= hard,
                    "戦闘レベル " + combatLevel + " で順序が壊れている: " + easy + " / " + normal + " / " + hard);
        }
    }

    // ------------------------------------------------- 難易度補正の二重適用

    /**
     * 割合はメニューで難易度を押した時点で {@code selectedLevel} に確定済みなので、
     * {@code getMobLevel()} が更に旧来の ±5 補正を足すと二重適用になる
     * （「イージーを選んだのにモブが5レベル高い」）。
     */
    @Test
    @DisplayName("getMobLevel は旧来の難易度補正(±5)をもう足さない")
    void mobLevelNoLongerAddsTheOldRelativeOffset() throws Exception {
        String disassembly = Javap.disassemble(DynamicDungeonInstance.class);
        String method = Javap.sliceMethod(disassembly, "int getMobLevel(");
        assertFalse(method.contains("getDifficultyMobLevelOffset"),
                "getMobLevel が getDifficultyMobLevelOffset を呼び戻している。割合方式では "
                        + "selectedLevel に難易度が織り込み済みなので、これを足すと二重適用になる。\n" + method);
    }

    // ----------------------------------------------------- ロックアウト通知

    @Test
    @DisplayName("ロックアウト通知は設定のゲートを通る(チャット/タイトル/アクションバーの3つとも)")
    void lockoutNotificationIsGatedByConfig() throws Exception {
        String disassembly = Javap.disassemble(DungeonBossLockoutHandler.class);
        String method = Javap.sliceMethod(disassembly, "void notifyLockout(");
        assertTrue(method.contains("isDungeonLockoutNotificationEnabled"),
                "notifyLockout から設定ゲートが消えている。ロックアウト済みのボスを倒すたびに "
                        + "チャット・タイトル・アクションバーが復活する。\n" + method);
    }

    /**
     * 通知を止めても「ロックアウト中は戦利品が出ない」挙動は変わらないこと。ゲートを
     * {@code processLockouts} 側へ付けてしまうと、通知どころか<b>ロックアウト自体が無効化</b>され、
     * 何度でも報酬が出るようになる（この取り違えは静かに経済を壊す）。
     */
    @Test
    @DisplayName("ロックアウトの記録・判定そのものにはゲートを付けない(報酬抑止は生かす)")
    void lockoutBookkeepingStaysUngated() throws Exception {
        String disassembly = Javap.disassemble(DungeonBossLockoutHandler.class);
        String method = Javap.sliceMethod(disassembly, "processLockouts(");
        assertFalse(method.contains("isDungeonLockoutNotificationEnabled"),
                "processLockouts に通知ゲートが付いている。これはロックアウトの記録・判定そのものを "
                        + "止めるので、報酬が無限に出るようになる。\n" + method);
    }

    // ------------------------------------------------------ レベル選択の廃止

    /**
     * レベル選択メニュー（戦闘レベル -5 から +5 刻み）は難易度メニューへ統合して廃止した。
     * 復活すると「レベルを選んでから割合も掛かる」二段構えになり、実効レベルが読めなくなる。
     */
    @Test
    @DisplayName("ダイナミックダンジョンのレベル選択メニューは復活していない")
    void theOldLevelSelectionMenuIsGone() throws Exception {
        String disassembly = Javap.disassemble(DynamicDungeonBrowser.class);
        // 空振り防止: 逆アセンブルが本当にこのクラスのものであることを、残っている側で先に確かめる。
        // これが無いと「クラスを取り違えて中身が空」でも下の assertFalse は素通りする。
        assertTrue(disassembly.contains("showDifficultySelectionMenu"),
                "難易度メニュー自体が見つからない。逆アセンブル対象を取り違えている。\n" + disassembly);
        assertFalse(disassembly.contains("showLevelSelectionMenu"),
                "showLevelSelectionMenu が戻っている。難易度が割合そのものになったので、"
                        + "レベルを別に選ばせると実効レベルが二段の掛け算になる。\n" + disassembly);
    }
}
